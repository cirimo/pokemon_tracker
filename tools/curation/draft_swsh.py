"""Drafts Sword curation from Bulbapedia, for a person to read before it is committed.

    python tools/curation/draft_swsh.py            # fetch pages that are not cached, then draft
    python tools/curation/draft_swsh.py --refetch  # fetch every page again

Writes to tools/curation/out/ and nothing else:

    swsh-sw.legendaries.yaml   Dynamax Adventure bosses and the Crown Tundra statics
    swsh.da-guides.yaml        a guide per Max Lair boss, with rental advice, for guides/swsh.yaml
    swsh-sw.species.yaml       everything else the species pages place in Sword

It never writes to data/curated. A draft is a starting point: read it, compare a sample
against the pages, then paste it into data/curated/encounters/swsh-sw.yaml and run
`npm run build:dataset`, whose validators catch rows the game cannot have.

Sources, as raw wikitext (action=raw), cached in tools/curation/.cache/:

    Dynamax_Adventure                  the special Pokemon at the end of a Dynamax Adventure
    List_of_in-game_event_Pokemon...   the Crown Tundra statics: giants, Regigigas, Swords
    <species> pages                    "Game locations" rows for Sword and the Expansion Pass

Reads the built reference.db for what Sword offers, what is locked, and which Crown Tundra
legendaries are online-only, so run `npm run build:dataset` first. Python 3.10+, standard
library only. Like draft_la.py, it is shaped to its sources, not a general tool.
"""
import pathlib
import re
import sqlite3
import sys
import time
import urllib.parse
import urllib.request

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent.parent
DB = ROOT / 'core/data/src/main/assets/dataset/reference.db'
CACHE = HERE / '.cache'
OUT = HERE / 'out'
GAME = 'swsh-sw'

BP = 'https://bulbapedia.bulbagarden.net/wiki/'
DA = 'Dynamax_Adventure'
EVENTS = 'List_of_in-game_event_Pokémon_in_Pokémon_Sword_and_Shield'
SRC_DA = BP + DA
SRC_EVENTS = BP + urllib.parse.quote(EVENTS)
SRC_CLUE = BP + 'Legendary_Clue%3F'
SRC_CHARM = BP + 'Shiny_Charm'

DA_PREREQ = 'The Crown Tundra: a Dynamax Adventure from the Max Lair on Slippery Slope.'
CLUE = (' Ultra Beasts and Necrozma appear only after reading the Legendary Clue?, left on the'
        ' Freeze Inn doormat once every Legendary Clue is done.')
ONLINE = (' Not in Sword\'s own Max Lair: join a Dynamax Adventure hosted on Shield, online. Once met,'
          ' its path stays unlocked.')
ONE_KEPT = ('Only one can ever be kept on a save file. Declining it at the end keeps it catchable, and'
            ' the scientist notes its path for free (three notes at a time).')

# The Crown Tundra statics, from the event list. Entry conditions are the page's, in our words.
GIANT_NOTE = 'The Shiny Charm does nothing for the legendary giants (Bulbapedia, Shiny Charm).'
STATICS = [
    ('regirock', 'static', 'Rock Peak Ruins, Giant\'s Bed', 'The Crown Tundra, after Legendary Clue 2.'
     ' A party Pokemon must hold an Everstone to enter.', GIANT_NOTE),
    ('regice', 'static', 'Iceberg Ruins, Snowslide Slope', 'The Crown Tundra, after Legendary Clue 2.'
     ' Your walking Pokemon must be a Cryogonal to enter.', GIANT_NOTE),
    ('registeel', 'static', 'Iron Ruins, Giant\'s Bed', 'The Crown Tundra, after Legendary Clue 2.'
     ' Whistle near the door to enter.', GIANT_NOTE),
    ('regieleki', 'static', 'Split-Decision Ruins, Three-Point Pass', 'The Crown Tundra, with Regirock,'
     ' Regice and Registeel in the party. Light the floor in Regieleki\'s eye pattern.',
     'Only one of Regieleki and Regidrago can be caught on a save file, chosen by the pattern. ' + GIANT_NOTE),
    ('regidrago', 'static', 'Split-Decision Ruins, Three-Point Pass', 'The Crown Tundra, with Regirock,'
     ' Regice and Registeel in the party. Light the floor in Regidrago\'s eye pattern.',
     'Only one of Regieleki and Regidrago can be caught on a save file, chosen by the pattern. ' + GIANT_NOTE),
    ('regigigas', 'raid', "Pokemon Den, Giant's Bed", 'The Crown Tundra: bring all five legendary giants to'
     ' the den, which wakes Regigigas in a solo Max Raid Battle.',
     'Unresolved: the same page says only one of Regieleki and Regidrago can be caught on a save, so a'
     ' second save or a trade seems needed for the fifth giant. Dens are not helped by the Shiny Charm.'),
    ('cobalion', 'static', 'Frigid Sea', 'The Crown Tundra, after the main story: collect Cobalion\'s 50'
     ' footprints for Sonia.', None),
    ('terrakion', 'static', 'Lakeside Cave', 'The Crown Tundra, after the main story: collect Terrakion\'s'
     ' 50 footprints for Sonia.', None),
    ('virizion', 'static', 'Giant\'s Bed', 'The Crown Tundra, after the main story: collect Virizion\'s 50'
     ' footprints for Sonia.', None),
]


def fetch(page: str, refetch: bool) -> str:
    path = CACHE / (re.sub(r'[^A-Za-z0-9_]', '', page) + '.wiki')
    if refetch or not path.exists():
        url = 'https://bulbapedia.bulbagarden.net/w/index.php?title=' + urllib.parse.quote(page) + '&action=raw'
        request = urllib.request.Request(url, headers={'User-Agent': 'pokedex-curation (personal, low volume)'})
        with urllib.request.urlopen(request) as response:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(response.read())
        time.sleep(1)
    return path.read_text(encoding='utf-8')


def sword() -> dict:
    """Variants upstream offers in Sword, with our curated lock and override state."""
    db = sqlite3.connect(DB)
    rows = db.execute("""
        select v.id, v.dexNum, v.displayName, v.isDefault, v.isRegional, s.name, s.isUltraBeast,
               a.obtainable, a.shinyLocked, a.exclusiveTo, v.shinyReleased
        from game_availability a join variant v on v.id = a.variantId join species s on s.dexNum = v.dexNum
        where a.gameId = ?""", (GAME,))
    keys = ['id', 'dex', 'name', 'default', 'regional', 'species', 'ub', 'obtainable', 'locked', 'exclusive', 'shiny']
    return {r[0]: dict(zip(keys, r)) for r in rows}


def by_species_name(variants: dict) -> dict:
    """The default, non-regional variant of each species, by the name Bulbapedia uses."""
    return {v['species']: v for v in variants.values() if v['default'] and not v['regional']}


def da_bosses(text: str) -> list[str]:
    section = text[text.index('===Special Pokémon==='):text.index('==Trivia==')]
    return re.findall(r'^\| pokemon = (.+?)\s*$', section, re.M)


def legendaries(pages: dict, variants: dict) -> tuple[list, list]:
    names = by_species_name(variants)
    rows, skipped = [], []
    for boss in da_bosses(pages[DA]):
        v = names.get(boss)
        if v is None:
            skipped.append(boss + ' (no Sword variant by that name)')
            continue
        if not v['obtainable'] or v['locked'] or not v['shiny']:
            skipped.append(v['id'] + ' (not obtainable, locked or no shiny)')
            continue
        prereq = DA_PREREQ
        if v['ub'] or v['id'] == 'necrozma':
            prereq += CLUE
        if v['exclusive']:
            prereq += ONLINE
        rows.append((v['dex'], v['id'], 'dynamax-adventure', {
            'location': 'Max Lair', 'prerequisite': prereq, 'notes': ONE_KEPT, 'source': SRC_DA}))
    events = pages[EVENTS]
    for vid, method, location, prereq, notes in STATICS:
        if vid not in variants or not variants[vid]['obtainable']:
            skipped.append(vid + ' (not obtainable in Sword)')
            continue
        # The page marks each encounter's lock; only unlocked ones become rows.
        name = variants[vid]['species']
        block = events[events.index('|pokemon=' + name):]
        block = block[:block.index('\n}}')]
        if '|shinylock=no' not in block:
            skipped.append(vid + ' (the event list does not mark it unlocked)')
            continue
        fields = {'location': location, 'prerequisite': prereq}
        if notes:
            fields['notes'] = notes
        if method == 'static' and notes and 'Shiny Charm' in notes:
            fields['oddsExclude'] = '[shiny-charm]'
        fields['source'] = SRC_EVENTS
        rows.append((variants[vid]['dex'], vid, method, fields))
    return rows, skipped


def scalar(value) -> str:
    text = str(value)
    if text.startswith('['):
        return text
    if ': ' in text or ' #' in text or text.startswith(('"', "'", '*', '&', '!', '{')) or text.endswith(':'):
        return '"' + text.replace('\\', '\\\\').replace('"', '\\"') + '"'
    return text


def write(name: str, rows: list) -> None:
    lines = []
    for _, vid, method, fields in sorted(rows):
        lines += ['  - variantId: ' + vid, '    methodId: ' + method]
        lines += ['    %s: %s' % (k, scalar(fields[k]))
                  for k in ['from', 'location', 'prerequisite', 'shinyLocked', 'oddsExclude', 'notes', 'source']
                  if k in fields]
        lines.append('')
    OUT.mkdir(exist_ok=True)
    (OUT / name).write_text('\n'.join(lines), encoding='utf-8', newline='\n')


# --- Rental advice for Dynamax Adventure guides -----------------------------------------
#
# You cannot bring your own Pokemon into the Max Lair, so a boss guide says which rentals
# to take and which to avoid. Drafted from the boss's typing and attacking moves and the
# rental pool, both on the Dynamax Adventure page, against the type chart on Bulbapedia's
# Type page (Generation VI onward, which Sword uses).

SRC_TYPE = BP + 'Type'
SRC_TA = BP + 'Thousand_Arrows_(move)'
SRC_WB = BP + 'Weather_Ball_(move)'
TYPES = ['Normal', 'Fire', 'Water', 'Electric', 'Grass', 'Ice', 'Fighting', 'Poison', 'Ground', 'Flying',
         'Psychic', 'Bug', 'Rock', 'Ghost', 'Dragon', 'Dark', 'Steel', 'Fairy']
# Attacking type -> defending types it does not hit for 1x.
CHART = {
    'Normal': {'Rock': .5, 'Ghost': 0, 'Steel': .5},
    'Fire': {'Fire': .5, 'Water': .5, 'Grass': 2, 'Ice': 2, 'Bug': 2, 'Rock': .5, 'Dragon': .5, 'Steel': 2},
    'Water': {'Fire': 2, 'Water': .5, 'Grass': .5, 'Ground': 2, 'Rock': 2, 'Dragon': .5},
    'Electric': {'Water': 2, 'Electric': .5, 'Grass': .5, 'Ground': 0, 'Flying': 2, 'Dragon': .5},
    'Grass': {'Fire': .5, 'Water': 2, 'Grass': .5, 'Poison': .5, 'Ground': 2, 'Flying': .5, 'Bug': .5,
              'Rock': 2, 'Dragon': .5, 'Steel': .5},
    'Ice': {'Fire': .5, 'Water': .5, 'Grass': 2, 'Ice': .5, 'Ground': 2, 'Flying': 2, 'Dragon': 2, 'Steel': .5},
    'Fighting': {'Normal': 2, 'Ice': 2, 'Poison': .5, 'Flying': .5, 'Psychic': .5, 'Bug': .5, 'Rock': 2,
                 'Ghost': 0, 'Dark': 2, 'Steel': 2, 'Fairy': .5},
    'Poison': {'Grass': 2, 'Poison': .5, 'Ground': .5, 'Rock': .5, 'Ghost': .5, 'Steel': 0, 'Fairy': 2},
    'Ground': {'Fire': 2, 'Electric': 2, 'Grass': .5, 'Poison': 2, 'Flying': 0, 'Bug': .5, 'Rock': 2, 'Steel': 2},
    'Flying': {'Electric': .5, 'Grass': 2, 'Fighting': 2, 'Bug': 2, 'Rock': .5, 'Steel': .5},
    'Psychic': {'Fighting': 2, 'Poison': 2, 'Psychic': .5, 'Dark': 0, 'Steel': .5},
    'Bug': {'Fire': .5, 'Grass': 2, 'Fighting': .5, 'Poison': .5, 'Flying': .5, 'Psychic': 2, 'Ghost': .5,
            'Dark': 2, 'Steel': .5, 'Fairy': .5},
    'Rock': {'Fire': 2, 'Ice': 2, 'Fighting': .5, 'Ground': .5, 'Flying': 2, 'Bug': 2, 'Steel': .5},
    'Ghost': {'Normal': 0, 'Psychic': 2, 'Ghost': 2, 'Dark': .5},
    'Dragon': {'Dragon': 2, 'Steel': .5, 'Fairy': 0},
    'Dark': {'Fighting': .5, 'Psychic': 2, 'Ghost': 2, 'Dark': .5, 'Fairy': .5},
    'Steel': {'Fire': .5, 'Water': .5, 'Electric': .5, 'Ice': 2, 'Rock': 2, 'Steel': .5, 'Fairy': 2},
    'Fairy': {'Fire': .5, 'Fighting': 2, 'Poison': .5, 'Dragon': 2, 'Dark': 2, 'Steel': .5},
}
# Thousand Arrows hits Flying-types and Levitate (Bulbapedia, Thousand Arrows).
HITS_AIRBORNE = {'Thousand Arrows'}


def against(move_type: str, types: list[str], ability: str = '', move: str = '') -> float:
    m = 1.0
    for t in types:
        m *= CHART[move_type].get(t, 1)
    if move_type == 'Ground' and move in HITS_AIRBORNE and m == 0:
        m = 1.0
        for t in types:
            if t != 'Flying':
                m *= CHART['Ground'].get(t, 1)
    if move_type == 'Ground' and ability == 'Levitate' and move not in HITS_AIRBORNE:
        m = 0
    return m


def parse_mons(section: str) -> list[dict]:
    mons = []
    for block in section.split('{{Pokémon')[1:]:
        f = dict((k.strip(), v.strip()) for k, v in re.findall(r'\|\s*(\w+)\s*=\s*([^|\n}]+)', block))
        if 'pokemon' not in f:
            continue
        moves = [(f.get('move%d' % i, ''), f.get('move%dtype' % i, ''), f.get('move%dcat' % i, ''))
                 for i in range(1, 5)]
        mons.append({'name': f['pokemon'], 'form': f.get('form', ''),
                     'types': [t for t in (f.get('type1'), f.get('type2')) if t],
                     'ability': f.get('ability', ''), 'moves': [m for m in moves if m[0]]})
    return mons


def x(m: float) -> str:
    return {4: '4×', 2: '2×', 0.5: '½×', 0.25: '¼×', 0: 'no'}.get(m, '%g×' % m)


def advice_for(boss: dict, rentals: list[dict]) -> list[dict]:
    types = boss['types']
    typing = '/'.join(types)
    attacks = [(name, t) for name, t, cat in boss['moves'] if cat != 'Status']
    # Weather Ball takes the type of the weather, and a boss that sets weather will use it
    # that way (Bulbapedia, Weather Ball): count both, so the danger is not understated.
    moves = {name for name, _, _ in boss['moves']}
    weather = {'Rain Dance': 'Water', 'Sunny Day': 'Fire', 'Hail': 'Ice', 'Sandstorm': 'Rock'}
    set_by = [weather[m] for m in moves if m in weather]
    set_by += {'Drizzle': ['Water'], 'Drought': ['Fire']}.get(boss['ability'], [])
    attacks += [('Weather Ball', t) for t in set_by if 'Weather Ball' in moves]
    out = []
    picks = []
    for r in rentals:
        if r['form'] == '-Gigantamax':
            continue
        best = None
        for name, t, cat in r['moves']:
            if cat == 'Status':
                continue
            m = against(t, types)
            stab = t in r['types']
            score = m * (1.5 if stab else 1)
            if m >= 2 and (best is None or score > best[0]):
                best = (score, name, t, m)
        if best is None:
            continue
        danger = max((against(t, r['types'], r['ability'], name) for name, t in attacks), default=1)
        picks.append((-(best[0] / max(danger, 0.5)), r['name'], best, danger))
    seen = set()
    for _, name, (score, move, t, m), danger in sorted(picks):
        if name in seen:
            continue
        seen.add(name)
        why = '%s is %s against %s (%s)' % (move, x(m), boss['name'], typing)
        why += '; it takes %s from %s\'s best attack.' % (x(danger), boss['name']) if danger != 1 else '.'
        out.append({'kind': 'take', 'subject': '%s (%s)' % (name, move), 'why': why[0].upper() + why[1:]})
        if len(out) == 6:
            break
    by_type: dict[str, list[str]] = {}
    for name, t in attacks:
        by_type.setdefault(t, []).append(name)
    for t, names in by_type.items():
        weak = [d for d in TYPES if any(against(t, [d], move=n) >= 2 for n in names)]
        if not weak:
            continue
        if names == ['Weather Ball'] and t != 'Normal':
            out.append({'kind': 'avoid', 'subject': ', '.join(weak) + ' types', 'source': SRC_WB,
                        'why': 'Weather Ball turns %s in the weather %s brings, which hits them %s.'
                        % (t, boss['name'], x(2))})
            continue
        said = ' and '.join(names)
        out.append({'kind': 'avoid', 'subject': ', '.join(weak) + ' types',
                    'why': '%s %s %s, which hits them %s.' % (said, 'are' if len(names) > 1 else 'is', t, x(2))})
    useless = [t for t in TYPES if against(t, types) == 0]
    resisted = [t for t in TYPES if 0 < against(t, types) < 1]
    if useless:
        out.append({'kind': 'avoid', 'subject': 'Rentals that only have ' + ' or '.join(useless) + ' attacks',
                    'why': '%s does not affect %s (%s).' % (' and '.join(useless), boss['name'], typing)})
    if resisted:
        out.append({'kind': 'note', 'subject': 'Resisted', 'why': '%s (%s) resists %s attacks.'
                    % (boss['name'], typing, ', '.join(resisted))})
    if any(n in HITS_AIRBORNE for n, _ in attacks):
        out.append({'kind': 'note', 'subject': 'Thousand Arrows', 'source': SRC_TA,
                    'why': 'It hits Flying-types and Levitate too, so neither keeps a rental safe from Ground.'})
    return out


def da_guides(pages: dict, variants: dict, rows: list) -> list[str]:
    text = pages[DA]
    rentals = parse_mons(text[text.index('===Rentable and encounterable'):text.index('===Special Pokémon===')])
    bosses = {b['name']: b for b in parse_mons(text[text.index('===Special Pokémon==='):text.index('==Trivia==')])}
    names = by_species_name(variants)
    lines = []
    for _, vid, method, fields in sorted(rows):
        if method != 'dynamax-adventure':
            continue
        v = variants[vid]
        boss = bosses[v['species']]
        requires = ['crown-tundra', 'shiny-charm']
        if v['ub'] or vid == 'necrozma':
            requires.append('legendary-clue-q')
        if v['exclusive']:
            requires.append('nso')
        lines += ['  - id: %s-max-lair' % vid,
                  '    title: %s in a Dynamax Adventure' % v['species'],
                  '    method: dynamax-adventure',
                  '    procedure: dynamax-adventure',
                  '    variants: [%s]' % vid,
                  '    oneInSave: true',
                  '    summary: %s' % scalar('%s (%s) is a Max Lair boss: %s.' % (
                      v['species'], '/'.join(boss['types']),
                      ', '.join(m[0] for m in boss['moves']))),
                  '    requires:']
        for r in requires:
            lines.append('      - id: ' + r + ('\n        role: odds' if r == 'shiny-charm' else ''))
        if v['exclusive']:
            lines += ['    steps:',
                      '      - section: setup',
                      '        text: %s' % scalar('Not in your own Max Lair. Join a Shield player\'s Dynamax Adventure'
                                                  ' online whose boss is %s, and do not keep it unless it is shiny;'
                                                  ' from then on its path is yours to note.' % v['species']),
                      '        source: https://bulbapedia.bulbagarden.net/wiki/Version-exclusive_Pok%C3%A9mon']
        lines.append('    advice:')
        for a in advice_for(boss, rentals):
            lines += ['      - kind: ' + a['kind'], '        subject: ' + scalar(a['subject']),
                      '        why: ' + scalar(a['why']),
                      '        source: ' + a.get('source', SRC_DA if a['kind'] == 'take' else SRC_TYPE)]
        lines.append('')
    return lines


def main() -> None:
    refetch = '--refetch' in sys.argv
    pages = {page: fetch(page, refetch) for page in [DA, EVENTS]}
    variants = sword()
    rows, skipped = legendaries(pages, variants)
    write('swsh-sw.legendaries.yaml', rows)
    guides = da_guides(pages, variants, rows)
    (OUT / 'swsh.da-guides.yaml').write_text('\n'.join(guides), encoding='utf-8', newline='\n')
    print('%d legendary rows, %d Dynamax Adventure guides' % (len(rows), sum(1 for g in guides if g.startswith('  - id:'))))
    print('skipped:', *skipped, sep='\n  ')
    print('drafts in', OUT)


if __name__ == '__main__':
    main()
