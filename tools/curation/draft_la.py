"""Drafts Legends Arceus curation from Bulbapedia, for a person to read before it is committed.

    python tools/curation/draft_la.py            # fetch pages that are not cached, then draft
    python tools/curation/draft_la.py --refetch  # fetch every page again

Writes two files to tools/curation/out/ and nothing else:

    la.encounters.yaml   the `encounters:` rows for data/curated/encounters/la.yaml
    la.locks.yaml        the Legends Arceus rows for data/curated/shiny-locks.yaml

It never writes to data/curated. A draft is a starting point: read it, compare a sample
against the pages, then paste it in and run `npm run build:dataset`, whose validators
(encounter-obtainable, lock-contradiction, evolution-from) catch rows the game cannot have.
How the committed file was made is recorded in its header.

Sources, as raw wikitext (action=raw), cached in tools/curation/.cache/:

    Mass_outbreak           the Legends Arceus table: mass and massive mass outbreaks per area
    Space-time_distortion   common and rare distortion spawns per area
    <area> pages            the Pokemon that spawn in each area's overworld

Forms are matched on Bulbapedia's menu-sprite codes ("Menu LA 058H.png" is Hisuian
Growlithe, 201QU is Unown ?, 412G is Sandy Cloak Burmy), never on names, because the names
("Burmy", "Unown") do not say which form.

Reads the built reference.db for what Legends Arceus offers, so run `npm run build:dataset`
first if the dataset is stale. Python 3.10+, standard library only.
"""
import pathlib
import re
import sqlite3
import sys
import urllib.parse
import urllib.request

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent.parent
DB = ROOT / 'core/data/src/main/assets/dataset/reference.db'
CACHE = HERE / '.cache'
OUT = HERE / 'out'

AREAS = ['Obsidian Fieldlands', 'Crimson Mirelands', 'Cobalt Coastlands', 'Coronet Highlands', 'Alabaster Icelands']
PAGES = ['Mass_outbreak', 'Space-time_distortion'] + [a.replace(' ', '_') for a in AREAS]
BP = 'https://bulbapedia.bulbagarden.net/wiki/'
SRC_MO = BP + 'Mass_outbreak'
SRC_STD = BP + 'Space-time_distortion'
SRC_SHINY = BP + 'Shiny_Pok%C3%A9mon'
SRC_UPSTREAM = 'https://github.com/pokepc/dataset/tree/6.8.2'

# The three first partners are a gift from Professor Laventon, and gifts are never shiny
# (Shiny_Pokemon, Legends Arceus). They are also in distortions and outbreaks, so the lock
# is on the gift, not the game.
FIRST_PARTNERS = ['rowlet', 'cyndaquil', 'oshawott']

# Order of a variant's rows in the file: the best method first.
ORDER = ['mass-outbreak', 'massive-mass-outbreak', 'space-time-distortion', 'wild', 'gift', 'evolution']


def fetch(page: str, refetch: bool) -> str:
    path = CACHE / (page + '.wiki')
    if refetch or not path.exists():
        url = 'https://bulbapedia.bulbagarden.net/w/index.php?title=' + urllib.parse.quote(page) + '&action=raw'
        request = urllib.request.Request(url, headers={'User-Agent': 'pokedex-curation (personal, low volume)'})
        with urllib.request.urlopen(request) as response:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(response.read())
    return path.read_text(encoding='utf-8')


def load_variants() -> dict:
    db = sqlite3.connect(DB)
    rows = db.execute("""
        select v.id, v.dexNum, v.formId, v.isRegional, v.isFemaleForm, v.evolvesFromId, v.evolveCondition,
               s.isLegendary, s.isMythical
        from game_availability a join variant v on v.id = a.variantId join species s on s.dexNum = v.dexNum
        where a.gameId = 'la' and a.obtainable""")
    keys = ['id', 'dex', 'form', 'regional', 'female', 'evolves', 'cond', 'legend', 'myth']
    return {r[0]: dict(zip(keys, r)) for r in rows}


def code_of(v: dict, explicit_female: set) -> str | None:
    """The menu-sprite code Bulbapedia draws a variant with, or None if it has none of its own."""
    n = '%03d' % v['dex']
    form = v['form'] or ''
    if v['regional']:
        if form.startswith('hisui'):
            return n + 'H'
        if form.startswith('alola'):
            return n + 'A'
        if form == 'white-striped':
            return n + 'W'
        return None
    if v['dex'] == 201:
        return n + {'a': '', 'exclamation': 'EX', 'question': 'QU'}.get(form, form.upper())
    if v['dex'] in (422, 423):
        return n + ('E' if form == 'east' else '')
    if v['dex'] in (412, 413):
        return n + {'plant': '', 'sandy': 'G', 'trash': 'S'}[form]
    if v['female']:
        # Most gender forms share the male's sprite code and its spawns. Basculegion is
        # drawn separately ("902F"), so where a separate code exists, it is used.
        return n + ('F' if n in explicit_female else '')
    if form:
        return None  # Rotom's appliances and the like: no source places them
    return n


def areas(xs: list) -> str:
    xs = list(dict.fromkeys(xs))
    return xs[0] if len(xs) == 1 else ', '.join(xs[:-1]) + ' and ' + xs[-1]


def outbreak_table(text: str) -> dict:
    """Per code: areas with a mass outbreak, a massive one, and a massive one only as a second wave."""
    section = text[text.index('===={{g|Legends: Arceus}}===='):text.index('===[[Generation IX]]===')]
    section = section[section.index('{| class="roundtable sortable"'):]
    table = {}
    for row in section.split('\n|-'):
        m = re.search(r'Menu LA (\d{3}[A-Z]*)\.png', row)
        if not m:
            continue
        cells = [c.strip() for c in row.split('\n') if c.strip().startswith('|')]
        marks = cells[3:13]
        if len(marks) != 10:
            raise SystemExit('the outbreak table changed shape at ' + m.group(1) + '; read it before trusting a draft')
        entry = table.setdefault(m.group(1), {'mo': [], 'mmo': [], 'mmo2': []})
        for i, area in enumerate(AREAS):
            if '✓' in marks[2 * i]:
                entry['mo'].append(area)
            cell = marks[2 * i + 1]
            # "✓*": only in a massive mass outbreak of its pre-evolution, as the second wave.
            if '✓*' in cell:
                entry['mmo2'].append(area)
            elif '✓' in cell:
                entry['mmo'].append(area)
    return table


def distortion_spawns(text: str) -> dict:
    """Per code: (area, 'common' | 'rare')."""
    spawns = {}
    for area in AREAS:
        start = text.index('====[[' + area + ']]====')
        block = text[start:text.index('{{-}}', start)]
        for kind in ['Common', 'Rare']:
            k = block.index('=====' + kind + ' spawns=====')
            for code in re.findall(r'Menu LA (\d{3}[A-Z]*)\.png', block[k:block.index('|}', k)]):
                spawns.setdefault(code, []).append((area, kind.lower()))
    return spawns


def overworld_spawns(pages: dict) -> dict:
    """Per code: the areas whose overworld it spawns in."""
    spawns = {}
    for area in AREAS:
        page = pages[area.replace(' ', '_')]
        grid = page[page.index('==Pokémon=='):page.index('==Trivia==')]
        for code in re.findall(r'MSP/LA\|(\d{3}[A-Z]*)\|', grid):
            spawns.setdefault(code, []).append(area)
    return spawns


def already_locked() -> set:
    """Variants shiny-locks.yaml already locks in Legends Arceus."""
    text = (ROOT / 'data/curated/shiny-locks.yaml').read_text(encoding='utf-8')
    return set(re.findall(r'- variantId: (\S+)\n\s+gameId: la\n', text))


def draft(pages: dict, variants: dict):
    explicit_female = {c[:3] for c in re.findall(r'Menu LA (\d{3}F)\.png', pages['Mass_outbreak'])}
    by_code = {}
    for v in variants.values():
        code = code_of(v, explicit_female)
        if code:
            by_code.setdefault(code, []).append(v['id'])

    outbreaks = outbreak_table(pages['Mass_outbreak'])
    distortions = distortion_spawns(pages['Space-time_distortion'])
    overworld = overworld_spawns(pages)

    rows, unmatched = [], []
    for code, e in outbreaks.items():
        if code not in by_code:
            unmatched.append(code)
            continue
        for vid in by_code[code]:
            if e['mo']:
                rows.append((vid, 'mass-outbreak', {'location': areas(e['mo']), 'source': SRC_MO}))
                # The page's rule: an outbreak species also spawns in that area's overworld.
                # Species it names as never in outbreaks (fixed alphas, fliers, distortion-only)
                # get no wild row here, rather than a guessed one.
                places = [a for a in overworld.get(code, []) if a in e['mo']] or e['mo']
                rows.append((vid, 'wild', {'location': areas(places), 'source': BP + places[0].replace(' ', '_')}))
            if e['mmo'] or e['mmo2']:
                fields = {'location': areas(e['mmo'] + e['mmo2']), 'source': SRC_MO,
                          'prerequisite': 'Version 1.1.0 or later.'}
                if code.startswith('201'):
                    fields['prerequisite'] += ' Appears only once all 28 Unown forms are in the Unown Research Notes.'
                if code == '442':
                    fields['prerequisite'] += (' Appears only once all 108 wisps are found and Request 22, '
                                               '"Eerie Apparitions in the Night", is complete.')
                if e['mmo2'] and not e['mmo']:
                    fields['notes'] = "Not an outbreak of its own: it comes in the second wave of its pre-evolution's."
                elif e['mmo2']:
                    fields['notes'] = ('In ' + areas(e['mmo2']) + ", only in the second wave of its "
                                       "pre-evolution's outbreak.")
                rows.append((vid, 'massive-mass-outbreak', fields))

    for code, spots in distortions.items():
        if code not in by_code:
            unmatched.append(code)
            continue
        common = [a for a, k in spots if k == 'common']
        rare = [a for a, k in spots if k == 'rare']
        for vid in by_code[code]:
            fields = {'location': areas([a for a, _ in spots]), 'source': SRC_STD}
            if rare and not common:
                fields['notes'] = 'A rare spawn in the distortion.'
            elif rare:
                fields['notes'] = 'A rare spawn in the distortion in ' + areas(rare) + '.'
            rows.append((vid, 'space-time-distortion', fields))

    for vid in FIRST_PARTNERS:
        rows.append((vid, 'gift', {'location': 'Jubilife Village', 'shinyLocked': True, 'source': SRC_SHINY,
                                   'notes': 'The first partner from Professor Laventon. Gifts are never shiny.'}))

    # Evolutions, only for variants nothing above reaches directly.
    direct = {vid for vid, _, f in rows if not f.get('shinyLocked')}
    for vid, v in sorted(variants.items()):
        if vid in direct or v['legend'] or v['myth'] or not v['evolves']:
            continue
        source = v['evolves'] + '-f' if v['female'] and v['evolves'] + '-f' in variants else v['evolves']
        if source in direct:
            fields = {'source': SRC_UPSTREAM, 'from': source}
            if v['cond']:
                fields['prerequisite'] = 'Evolves ' + v['cond'] + '.'
            rows.append((vid, 'evolution', fields))

    # Bulbapedia: "all Legendary, Mythical, and Gift Pokemon are prevented from being Shiny".
    # A lock already in shiny-locks.yaml keeps its own, more specific, reason.
    locks = sorted((vid, 'Mythical' if v['myth'] else 'Legendary')
                   for vid, v in variants.items() if (v['legend'] or v['myth']) and vid not in already_locked())
    return rows, locks, unmatched


def scalar(value) -> str:
    if value is True:
        return 'true'
    text = str(value)
    if ': ' in text or ' #' in text or text.startswith(('"', "'", '*', '&', '!', '[', '{')):
        return '"' + text.replace('"', '\\"') + '"'
    return text


def main() -> None:
    pages = {page: fetch(page, '--refetch' in sys.argv) for page in PAGES}
    variants = load_variants()
    rows, locks, unmatched = draft(pages, variants)

    lines = []
    for vid, method, fields in sorted(rows, key=lambda r: (variants[r[0]]['dex'], r[0], ORDER.index(r[1]))):
        lines += ['  - variantId: ' + vid, '    methodId: ' + method]
        lines += ['    %s: %s' % (k, scalar(fields[k]))
                  for k in ['from', 'location', 'prerequisite', 'shinyLocked', 'notes', 'source'] if k in fields]
        lines.append('')
    OUT.mkdir(exist_ok=True)
    (OUT / 'la.encounters.yaml').write_text('\n'.join(lines), encoding='utf-8', newline='\n')

    lock_lines = []
    for vid, kind in locks:
        lock_lines += ['  - variantId: ' + vid, '    gameId: la',
                       '    reason: %s Pokemon are never shiny in Legends Arceus.' % kind,
                       '    source: ' + SRC_SHINY]
    (OUT / 'la.locks.yaml').write_text('\n'.join(lock_lines) + '\n', encoding='utf-8', newline='\n')

    covered = {vid for vid, _, f in rows if not f.get('shinyLocked')}
    missing = sorted(set(variants) - covered - {vid for vid, _ in locks} - already_locked())
    print('%d rows over %d variants, %d locks' % (len(rows), len(covered), len(locks)))
    print('codes in the sources that match no variant:', unmatched or 'none')
    print('%d variants no source places: %s' % (len(missing), ', '.join(missing)))
    print('drafts in', OUT)


if __name__ == '__main__':
    main()
