# The dataset pipeline

How `reference.db` and the shiny sprite set are produced, and how to edit the curated
layer by hand.

The pipeline is a desktop tool. It is **not** part of the Gradle build, it is not run by
CI on every push, and its outputs are checked in. Design rationale:
`docs/adr/0003-dataset-pipeline.md`.

---

## Quick reference

```bash
cd tools/dataset-pipeline
npm install                    # once

npm run build:dataset          # rebuild reference.db + the manifest, then validate
npm run build:sprites          # download and encode the shiny set (needs network)
npm run validate               # validate the checked-in asset
npm run validate -- --sprites  # also check every sprite is present and none is shared
npm test                       # pipeline unit tests
```

Outputs land in `core/data/src/main/assets/`:

```
dataset/reference.db            ~1 MB, plain git
dataset/dataset-manifest.json   content hashes + provenance
sprites/<nid>.webp              ~1400 files, ~15 MB, Git LFS
```

plus `tools/dataset-pipeline/sprite-sources.json`, which records where every sprite came
from. It is pipeline state, not an asset, so it stays out of the APK.

---

## Prerequisites

- **Node 22.5 or newer.** The pipeline uses the built-in `node:sqlite`, so building the
  database needs no native module. Only the sprite step has a native dependency (`sharp`).
- **The JVM toolchain**, because the pipeline reads the schema Room exports.

**If you changed anything in `core/data/.../reference/`, run this first:**

```bash
./gradlew :core:data:assembleDebug
```

That regenerates `core/data/schemas/…/N.json`, which carries both the exact `CREATE`
statements and the `identityHash` Room validates the asset against. Skipping it produces
a database Room refuses to open on the device, with no useful message.

---

## What the pipeline does

```
pinned upstream tag (pokepc/dataset 6.8.2)
        │
        ├── shallow clone into tools/dataset-pipeline/.upstream
        ├── validate against our reading of the upstream shape (Zod)
        │
data/curated/*.yaml
        ├── validate (Zod)
        │
        ▼
   merge ──→ emit DDL + room_master_table from the exported Room schema
         ──→ insert every table in sorted order
         ──→ VACUUM
         ──→ write content manifest
         ──→ validate output
```

### Determinism

Two consecutive runs produce a byte-identical `.db` — verified. That relies on a fixed
page size, sorted inserts, no `AUTOINCREMENT`, a frozen build timestamp and `VACUUM` last.

It also relies on the SQLite version Node links, which we do not control. So the
**content manifest** is the durable contract: SHA-256 per table over canonically sorted
rows, and it is what CI checks. CI does not check byte identity: the runner links a
different SQLite than a workstation does, so a correct dataset never matches byte for byte
(`docs/adr/0003-dataset-pipeline.md`).

Reproduce it:

```bash
npm run build:dataset
md5sum ../../core/data/src/main/assets/dataset/reference.db
npm run build:dataset
md5sum ../../core/data/src/main/assets/dataset/reference.db   # same
```

---

## Editing the curated layer

`data/curated/` is the part of the dataset nobody publishes machine-readably, so we own
it. It is YAML, hand-edited, version-controlled and diffable — the format was chosen so
the source citation can sit as a comment next to the claim it justifies, and a one-row
correction is a one-line diff.

Every file is Zod-validated with a precise pointer on failure, and every id is checked
against upstream, so a typo cannot ship.

### `games.yaml` — which games the app covers

The Switch-era subset plus HOME, with display order. `id` must match an upstream
`data/games/<id>.json`. Everything else about a game (generation, region, origin mark,
whether it supports shiny) is read from upstream.

Adding Champions or another game is one entry here.

### `shiny-locks.yaml` — where a shiny is impossible

The most valuable file in the repo. PokéAPI does not have shiny locks; PokéPC does not
have shiny locks; no machine-readable dataset of them exists — this was checked, not
assumed.

```yaml
- variantId: koraidon
  gameId: sv-s
  reason: Story-locked. The box legendary is never shiny in Scarlet.
  source: https://bulbapedia.bulbagarden.net/wiki/Shiny_Pok%C3%A9mon
```

Keep `source` on every row. Facts about a game are not copyrightable and the wording is
ours, but a future you will want to re-check a surprising row.

### `encounter-methods.yaml` — the vocabulary

Generic across games on purpose. Per-game detail goes in `encounters/`, odds go in
`odds-modifiers.yaml`. This keeps adding a game from meaning inventing a parallel
vocabulary.

### `encounters/<gameId>.yaml` — how to catch it, per game

One file per game, so a hunting session touches one file. `gameId` is declared once at
the top rather than repeated on every row.

```yaml
gameId: sv-s
encounters:
  - variantId: tauros-paldea
    methodId: mass-outbreak
    location: South Province
    notes: Combat Breed. The Blaze and Aqua breeds are Violet-side or post-game.
```

### `odds-modifiers.yaml` — the arithmetic

Two shapes, because the games use two mechanics:

- `rollsAdded` — the game rolls for shiny N extra times. Base rate is 1/4096 per roll
  from gen 6 onward. Shiny Charm, Masuda, outbreak tiers and KO chains all work this way.
- `denominator` — the method replaces the rate outright. Dynamax Adventures are a flat
  1/300 regardless of anything else, which no reroll arithmetic reproduces.

Exactly one of the two per row; the schema enforces it.

### Scope

`shiny-locks.yaml`, `odds-modifiers.yaml` and `encounters/` ship **seeded, not complete**.
They hold enough real rows to exercise the schema and the validators. Filling them is M4
work, one game at a time.

Two things you do **not** have to curate, because upstream already answers them:

- **HOME transfer legality** — `storableIn` and `transferOnlyIn`.
- **Per-game obtainability** — `obtainableIn` and `eventOnlyIn`.

---

## Validators

Any failure is a non-zero exit. There is no warning level: a dataset that is mostly right
is worse than one that refuses to ship.

| Check | Catches |
|---|---|
| `room-identity` | The asset's identity hash does not match the compiled entities. Room would refuse to open it on the device |
| `user-version` | `PRAGMA user_version` does not match the Room schema version |
| `box-count` | Not 52 boxes |
| `slot-count` | Not 1394 filled slots |
| `distinct-variants` | Not 1387 distinct variants |
| `duplicate-slots` | Not exactly 7 slots with `copyIndex = 1` |
| `copy-index-gap` | A variant has non-contiguous copy indices, so a slot resolves to a key nothing can fill |
| `slot-resolves` | A slot references a variant that is not in the dataset |
| `variant-species` | A variant has no species row |
| `curated-orphan` | A curated row references a variant, game or method that does not exist (including `sprites.yaml`) |
| `encounter-obtainable` | A curated encounter for a variant upstream says that game does not offer. Upstream wins |
| `lock-sanity` | A shiny lock on a variant that has no shiny released at all |
| `dataset-meta` | The metadata row is missing or duplicated |
| `display-name-unique` | Two variants share a display name, so search shows rows nobody can tell apart |
| `sprite-present` | A variant has no sprite file (only with `--sprites`) |
| `sprite-shared` | Two variants share sprite bytes without a cited group in `sprites.yaml` (only with `--sprites`) |

**If a validator fails, the dataset is wrong. Fix the data, not the validator.**

The exception: if upstream genuinely revised the preset, the counts are a real change,
not a bug. Update `PRESET` in `tools/dataset-pipeline/src/config.ts`,
`ReferenceAssetTest.kt`, and the numbers in `docs/architecture.md` — in one commit, so
they cannot drift apart.

---

## Sprites

Source: `PokeAPI/sprites`, `sprites/pokemon/other/home/`, resolved against one git tree
listing at the current commit. Encoded to WebP at 256px q80, about 10 KB each, ~15 MB for
the full set. Why this order, and how the first one went wrong:
`docs/adr/0005-sprites.md`, "The id trap".

First hit wins (`src/sprite-resolution.ts`):

| Step | File | For |
|---|---|---|
| 1 | whatever `data/curated/sprites.yaml` overrides say | the few cases below |
| 2 | `shiny/female/<pkApiId>.png` | gender forms |
| 3 | `shiny/<dex>-<form>.png` | cosmetic forms: Unown letters, Vivillon patterns, Alcremie sweets... |
| 4 | `shiny/<pkApiId>.png` | species, regional forms, forms with their own pokemon id |

`refs.pkApiFormId` is never used: it is a pokemon-*form* id, and the sprite repository is
keyed by pokemon id, so it resolves to a different Pokémon.

`tools/dataset-pipeline/sprite-sources.json` records, for every variant, the rule, the
path and the git blob hash it came from, plus the PokéAPI commit. Commit it with the
sprites. A rebuild re-encodes only the sprites whose source changed, so a resolution fix
reaches the set without deleting anything by hand.

Filenames are keyed on upstream's `nid` (`0003-f.webp`), so they survive an upstream remap
of its PokéAPI references.

### `sprites.yaml` -- overrides and shared art

Two lists, each row cited:

- **`overrides`** pin a variant to one file. Use one when upstream links the wrong
  PokéAPI entry, or when a variant has no shiny at all and should show normal colours.
  A path outside `shiny/` is refused for any variant whose shiny was released.
- **`shared`** are groups whose art really is identical, such as a shiny Alcremie's nine
  creams. `sprite-shared` refuses any other two variants with the same bytes, and also
  refuses a listed group whose sprites no longer match.

When `sprite-shared` fails after a rebuild, the answer is almost never a new `shared`
row. Look at the two pictures first. A shared row is a claim about the game, so it needs
a source that says the art is the same.

## Bumping the upstream tag

1. Change `UPSTREAM.tag` in `src/config.ts`.
2. `npm run build:dataset`.
3. Read the manifest diff. A table hash that moved tells you what changed.
4. If box or slot counts moved, upstream revised the preset. That is a real dataset
   change: bump `PRESET_VERSION`, update the expected counts everywhere (see above), and
   expect the app to show its preset-review screen on next launch.
5. Commit the `.db` and the manifest together, in a commit of their own.

## Reproducing the measurements in the docs

```bash
# preset shape: 52 boxes, 1394 filled, 1387 distinct, 10 holes, 7 duplicates
curl -sL https://raw.githubusercontent.com/pokepc/dataset/6.8.2/data/boxpresets/modern/home/grouped-balanced.json -o gb.json
python -c "
import json, collections
d = json.load(open('gb.json'))
slots = [x for b in d['boxes'] for x in b['slots']]
filled = [x for x in slots if x]
print('boxes', len(d['boxes']), 'positions', len(slots), 'filled', len(filled))
print('distinct', len(set(filled)))
print('duplicates', [k for k, v in collections.Counter(filled).items() if v > 1])
"

# sprite weight: download a sample, encode at each size, extrapolate to 1394
# (the table in docs/adr/0005-sprites.md was produced this way with Pillow)
```
