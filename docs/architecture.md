# Architecture

The technical design behind `docs/00-big-picture.md`. Decisions with alternatives worth
recording live in `docs/adr/`; this document is the shape of the system.

Everything numeric here was measured, not estimated. `docs/dataset-pipeline.md` has the
commands to reproduce each measurement.

---

## 1. What the data actually looks like

The PokéPC `grouped-balanced` preset, read from
[`pokepc/dataset`](https://github.com/pokepc/dataset) tag `6.8.2` (MIT licensed, so we
may bundle it with attribution):

| | |
|---|---|
| Boxes | **52** |
| Slot positions | 1404 |
| Filled slots | **1394** |
| Interior holes (`null`) | 10 |
| Distinct variants | **1387** |
| Variants demanded twice | **7** |

Three consequences that shape everything below.

**Boxes are not all 30 wide.** Thirty-eight are; the rest are 2, 6, 7, 8, 17, 20, 20, 22,
24, 25, 28, 28, 28 and 29. The grid renders ragged boxes.

**Slots have no identity.** A slot in the upstream JSON is a position in an array holding
either a variant id string or `null`. There is no slot id to key against.

**Seven variants are demanded twice.** `unown` (Johto 2 and the Unown Dex), `vivillon`,
`flabebe`, `floette`, `florges`, `furfrou` (Kalos 1 and their form boxes) and `alcremie`
(Galar 3 and Alcremie 1). A living dex needs two of each, so the denominator is 1394,
not 1387.

Upstream carries much more than assumed: types, base stats, `evolvesFrom`, localised
names, the full form taxonomy, and critically `obtainableIn` / `eventOnlyIn` /
`storableIn` / `transferOnlyIn` / `shinyReleased` plus `refs.pkApiId`. **HOME transfer
legality is therefore not curated work** — upstream answers it. PokéAPI is not a
pipeline dependency at all; see `docs/adr/0005-sprites.md`.

---

## 2. Slot identity — the decision everything rests on

> A catch record is keyed `(variantId, copyIndex)` and holds no reference to any preset,
> box or slot.

- `variantId` — the upstream string id (`"venusaur-f"`), the same token the preset stores.
- `copyIndex` — 0-based ordinal among repeated occurrences *in preset order*. Everything
  gets 0; the seven duplicates also get 1.
- The pipeline computes `copyIndex` per slot at build time and stores it on the slot row.

A slot *resolves to* a record key. It does not own one.

| Upstream change | Effect on records |
|---|---|
| Slot moves box or position | None. The key is unchanged |
| A form is inserted mid-list | None, for any of the ~1394 slots that shift |
| A variant gains a second occurrence | A new unfilled target appears |
| A variant is dropped from the preset | Its record is **kept** and shown as orphaned |
| Switching to another preset | Same records, different view |

Rename safety: reference rows also carry upstream's `nid` (`"0003-f"`), so the pipeline
can recognise a rename as *same nid, new id* and emit a remap rather than an orphan.

Full reasoning and the rejected alternatives: `docs/adr/0001-slot-identity.md`.

---

## 3. Two databases

`createFromAsset` only re-applies the prepackaged file during a **destructive** fallback.
When a real migration path exists, Room migrates and ignores the asset. A single database
holding both reference and user data therefore cannot be refreshed from a new asset
without dropping catch records.

So the split is physical, not conventional:

```
┌──────────── reference.db ────────────┐   ┌──────────── user.db ─────────────┐
│ shipped as an asset                  │   │ created empty on first run       │
│ read-only at runtime                 │   │ read/write                       │
│ replaced wholesale on a dataset bump │   │ migrated, NEVER destroyed        │
│ fallbackToDestructiveMigration: yes  │   │ fallbackToDestructiveMigration:  │
│                                      │   │   never, under any circumstances │
└──────────────────────────────────────┘   └──────────────────────────────────┘
```

No foreign key crosses the boundary — `catch_record.originGameId` is a plain string, so
a record outlives the reference row that used to explain it.

**The two never meet in SQL.** The active preset projects to ~1394 rows (≈280 KB); it is
loaded once and joined in memory against a `Flow<Map<CatchKey, CatchRecord>>`. This
avoids cross-database joins entirely — Room cannot join two `RoomDatabase` instances, and
`ATTACH` under WAL cannot commit atomically across files. Search and filtering are
in-memory operations over a list that fits comfortably in RAM.

### How a hand-built file satisfies Room

Room refuses any prepackaged database whose `room_master_table` identity hash does not
match the compiled entities, and that hash is undocumented to compute. We do not compute
it. `room.schemaLocation` exports `core/data/schemas/<Db>/<N>.json`, which contains both
the exact `createSql` per table and an `identityHash` field. The pipeline reads that
JSON, emits the DDL verbatim, inserts `room_master_table(42, <identityHash>)` and sets
`PRAGMA user_version`. Schema drift becomes a pipeline failure instead of a crash on the
device.

---

## 4. ERD

```
┌──────────── reference.db (read-only, replaced wholesale) ────────────────────┐
│                                                                              │
│  species ──1:N─→ variant ──1:N─→ slot ──N:1─→ box ──N:1─→ dex_preset         │
│                     │                                                        │
│                     ├──1:N─→ game_availability ──N:1─→ game                  │
│                     │          obtainable / eventOnly / storable /           │
│                     │          transferOnly  (upstream)                      │
│                     │          shinyLocked / shinyLockReason  (curated)      │
│                     │                                                        │
│                     └──1:N─→ encounter ──N:1─→ game                          │
│                                  └──N:1─→ encounter_method ──1:N─→           │
│                                                        odds_modifier         │
│  dataset_meta (one row: version, upstream tag, preset version, content hash) │
└──────────────────────────────────────────────────────────────────────────────┘
                                    ╎
              joined in memory on CatchKey(variantId, copyIndex)
                                    ╎
┌──────────── user.db (migrated, never destroyed) ─────────────────────────────┐
│  catch_record   PK (variantId, copyIndex)                                    │
│  user_settings  single row                                                   │
│  backup_log     rolling local backups                                        │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Species / Form / Variant

- **Species** — one National Dex number. Identity: `dexNum`.
- **Form** — a named alternative with its own reference entry: regional
  (`growlithe-hisui`), mega, battle-only, gender-differentiated (`venusaur-f`) or purely
  cosmetic (`alcremie-ruby-cream-berry`).
- **Variant** — the living-dex-addressable unit: exactly one row per thing a slot can
  demand. Upstream collapses species and form into one entity, so `variant` is the real
  table; `species` exists as the grouping the UI hangs off and to avoid a self-join on
  `baseSpecies` at query time.

Entity definitions: `core/data/src/main/kotlin/net/pokedex/core/data/{reference,user}/`.

---

## 5. Modules

| Module | Type | Why it exists |
|---|---|---|
| `:app` | application | Single Activity, nav graph, DI root, debug gallery host |
| `:core:model` | JVM library | Domain types plus the two pieces of real logic — progress derivation and preset diffing. No Android, so its tests run in milliseconds. Also the thing `:design-system` is forbidden to depend on |
| `:core:data` | Android library | Both databases, DAOs, repositories, backup/restore, and the dataset asset. The only module that knows SQLite exists |
| `:design-system` | Android library | Theme, tokens, components, gallery. Depends on nothing internal |
| `:feature:dex` | Android library | The first feature. At M0 it holds only the smoke screen |

Deliberately absent: `:core:database` (it would only hold the entity files `:core:data`
already owns — split it out the day Room build time actually hurts), `:core:ui`,
`:core:common`. The dataset pipeline is not a Gradle module at all.

Convention plugins in `build-logic/convention/`: `pokedex.android.application`,
`.library`, `.compose`, `.hilt`, `.room`, `.feature`, and `pokedex.jvm.library`. The
`feature` plugin encodes the dependency rule so a feature module gets the right deps by
construction, and CI re-checks it.

---

## 6. Application architecture

**DI: Hilt.** Koin reports wiring errors at runtime; on a project returned to after
months away, a build-time failure is worth the KSP cost. Manual DI would be fine at M0
but loses once ViewModels, WorkManager backups and `@HiltAndroidTest` arrive, and
retrofitting across five modules later is worse than paying now.
`docs/adr/0004-dependency-injection.md`.

**Async.** Coroutines and Flow throughout. DAOs return `Flow`; the suspension boundary is
the repository. `Dispatchers.IO` is injected via `@IoDispatcher`, never referenced
directly, so tests substitute a test dispatcher without a global rule. ViewModels expose
`StateFlow<UiState>` via `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`.

**Compose state.** One `@Immutable` state class per screen, one sealed event type, a
stateful route composable delegating to a stateless screen composable. The second is what
makes a screen previewable and testable without Hilt.

**60fps with 1400 items.** The screen is never 1400 tiles: a `HorizontalPager` of 52
boxes, each a `LazyVerticalGrid` of at most 30. Stable `key = "$box:$slot"`, `contentType`
set, tiles receive primitives and a stable callback so no lambda is allocated per item,
Coil is given an explicit `Size` so a 256px asset decodes downsampled, and — per
`docs/design-decisions.md` — borders instead of per-tile elevation, no ripple, no shader.

**Navigation.** Navigation Compose 2.9 with `@Serializable` type-safe routes. Not
Navigation 3, which is still moving. Each feature contributes a `NavGraphBuilder`
extension, so `:app` assembles the graph without knowing feature internals and two
features can link to each other without depending on each other.
`docs/adr/0006-navigation.md`. Shape, with search as a mode of the box screen rather
than a destination (`docs/adr/0010-search-is-a-mode.md`):

```
Boxes (start; search is a mode) ──→ SlotDetail(catchKey) ──→ VariantDetail(variantId)
Settings ──→ BackupRestore
```

**Errors.** `Outcome<T>` = `Ok | Err(AppError)` in `:core:model`. `AppError` splits into
`Fatal` (`DatasetMissing`, `DatasetCorrupt`, `DatasetVersionUnsupported`) and
`Recoverable` (`ImportSchemaTooNew`, `ImportMalformed`, `StorageFailure`, `Unexpected`).
The split is load-bearing: `Fatal` means the shipped reference data is unusable and the
only honest answer is "reinstall", which is a destructive suggestion and must never be
raised for anything touching user data.

**Backup format.** One versioned JSON file, documented in
`docs/adr/0007-backup-format.md`. `schema` is a single integer; a file from a newer schema
is **refused outright** rather than partially imported, with both version numbers shown.
Unknown keys are ignored, so additive changes do not bump `schema`. Records are sorted on
export so two exports of the same data are byte-identical and diffable.

---

## 7. Dataset pipeline

`tools/dataset-pipeline/`, TypeScript on Node, run on demand. Full workflow in
`docs/dataset-pipeline.md`; decision in `docs/adr/0003-dataset-pipeline.md`.

```
pinned upstream tag ──┐
                      ├──→ validate ──→ merge ──→ emit DDL from Room's exported
data/curated/*.yaml ──┘                           schema ──→ insert sorted ──→
                                                  VACUUM ──→ validate output ──→
                                                  reference.db + manifest
```

Outputs are checked in under `core/data/src/main/assets/` (the module that owns
`ReferenceDatabase` owns the file it is populated from, and the AAR asset is merged into
the app at packaging — which also lets the Room instrumentation test open the asset we
actually ship).

Reproducibility: the `.db` **is** byte-identical across runs today (verified), but that
is a property of the SQLite build Node links, not of the pipeline. So the durable
contract is a content manifest — SHA-256 per table over canonically sorted rows. CI
checks the manifest first and byte-identity second.

---

## 8. Engineering practice

**Testing.** See `CLAUDE.md` for the table. The principle: test what can silently lose
data, skip what a generator already guarantees. `:core:model` carries the real confidence
because it is pure and fast.

**Static analysis.** detekt with the `detekt-formatting` (ktlint) rule set — one plugin,
not two. Both `detekt` and `lintDebug` fail the build rather than warn.

**Variants and install.** `debug` carries `applicationIdSuffix .debug` so it coexists
with release on the phone, and hosts the gallery launcher alias. `release` runs R8 with
resource shrinking. Signing comes from `~/.gradle/gradle.properties`
(`pokedexKeystorePath` and friends), never the repo; without it release falls back to the
debug key, which is fine for a personal sideload. Install with `./gradlew installRelease`.
No Play Store, no App Bundle — and per `docs/adr/0005-sprites.md`, a store listing is not
available to this app anyway.

**CI.** `build.yml` (assemble, test, detekt, lint, and a module-boundary check),
`dataset.yml` (regenerate and compare the manifest), `screenshot.yml` (Roborazzi verify).

**Performance budgets**, release build with a baseline profile, Pixel-class device:

| Budget | Target |
|---|---|
| Cold start to first frame | P50 ≤ 600 ms, P90 ≤ 900 ms |
| Reference DB open + full preset projection | ≤ 120 ms |
| Box pager swipe + grid scroll | no dropped frames over a 52-box run; P99 frame ≤ 8.3 ms at 120 Hz |
| Search over 1394 in-memory slots | ≤ 5 ms |
| Mark caught → UI settled | ≤ 100 ms |
| APK size | ≤ 30 MB |

The APK budget accounts for ~15 MB of shiny sprites (1387 × ~11 KB measured at 256px
q80), ~1 MB of `reference.db`, and the rest of the app.

**Measured at M2** (2026-09-23): release build, **no** baseline profile yet, on the
`Medium_Phone_API_36.1` emulator rather than a device, so read these as a floor on what
is wrong rather than a pass on what is right:

| Budget | Measured | |
|---|---|---|
| Cold start to first frame | median ~430 ms, worst 548 ms over 10 runs | within |
| DB open + preset projection | 185–250 ms | over, but off the critical path: it starts in `Application.onCreate` and the data was ready before the first frame in every run |
| Search over 1394 slots | 0.35–2.6 ms per keystroke; the worst is one letter matching 942 | within |
| Pager, 20-box swipe run | 60 Hz display: P50 17 ms, P90 26 ms, 9.8% janky. 7 of 663 frames slow on the UI thread, 60 slow in issuing draw commands | not a verdict: the draw-command cost is the emulator's GPU translation |

**On a device** (same day): Galaxy S21 Ultra, Android 15, 120 Hz, release build.

| Budget | Measured | |
|---|---|---|
| Cold start to first frame | 235–276 ms over 10 runs | within |
| DB open + preset projection | 43–86 ms | within |
| Pager, 25-box swipe run, as installed | 4.6–5.8% janky, P99 14–17 ms | **over** |
| The same, after `cmd package compile -m speed` | 0.8–1.4% janky, P90 8 ms | near |

The gap between the last two rows is JIT, not the grid: code that has not been compiled
ahead of time is what misses the 8.3 ms frame. A baseline profile is how an installed app
gets that compilation, so it is the next performance step, not a change to the tiles. (Tried
and rejected on the numbers: taking `BoxSlot`'s per-tile scale layer off at rest measured
the same as leaving it on.)
