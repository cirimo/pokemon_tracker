"""Drafts Sword curation from Bulbapedia, for a person to read before it is committed.

    python tools/curation/draft_swsh.py            # fetch pages that are not cached, then draft
    python tools/curation/draft_swsh.py --refetch  # fetch every page again

Writes to tools/curation/out/ and nothing else:

    swsh-sw.legendaries.yaml   Dynamax Adventure bosses and the Crown Tundra statics
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


def main() -> None:
    refetch = '--refetch' in sys.argv
    pages = {page: fetch(page, refetch) for page in [DA, EVENTS]}
    variants = sword()
    rows, skipped = legendaries(pages, variants)
    write('swsh-sw.legendaries.yaml', rows)
    print('%d legendary rows' % len(rows))
    print('skipped:', *skipped, sep='\n  ')
    print('drafts in', OUT)


if __name__ == '__main__':
    main()
