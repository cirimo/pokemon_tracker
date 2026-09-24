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
pipeline dependency at all; only its sprite repository is, see `docs/adr/0005-sprites.md`.

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
│  user_settings  single row (incl. backup folder URI, last origin game)       │
│  backup_log     status of backups this install wrote; NOT the restore list   │
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
| `:feature:dex` | Android library | Box view, search, slot and species detail, the catch sheet, and Progress |
| `:feature:settings` | Android library | Settings, backup and restore, and the first-launch restore offer. Separate from the dex because the Storage Access Framework launchers and the restore flow share nothing with it |
| `:baselineprofile` | Android test (`com.android.test`) | Drives the release-like build on a phone to generate `:app`'s baseline profile. It exists because a device measurement put the pager over its frame budget under JIT (§8). It reaches `:app` through `targetProjectPath`, never a project dependency, and only `:app` may reference it; CI checks both, so it cannot become a path from one feature to another |

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
Boxes ──→ Progress ──→ (back to Boxes, on a box or on search "Needed in <game>")
Boxes ──→ Settings ──→ BackupRestore(fileUri?)
RestoreOffer: a sheet beside the NavHost, shown only on an empty database ──→ BackupRestore
```

Two features never name each other's routes. The box view reaches Settings through a
callback `:app` passes to `dexGraph`, and "Done" after a restore returns to the box view
through a callback `:app` passes to `settingsGraph`.

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
export so two exports of the same data are byte-identical and diffable. Each record carries
`updatedAt` (added in M3, additively), and a merge keeps whichever side changed a record last.

**Durability** (M3, `docs/adr/0011-backups-outside-the-sandbox.md`). On 2026-09-23 an
uninstall took every record, because the only copy lived inside the app's sandbox. Now:

- Automatic backups go to a folder the user picks once through the Storage Access Framework
  (`BackupLocation`, `SafBackupFolder`). The files belong to the user and outlive an
  uninstall. Until a folder is picked, or if its grant is lost, they go inside the app, and
  Settings says so with a warning.
- WorkManager writes one two minutes after the last change, flushes it when the app goes to
  the background, and runs daily as a net (`BackupScheduler`, `AutoBackupWorker`).
- `BackupWriter` (`:core:model`, JVM-tested) keeps the rolling set from destroying what it
  protects: no backup of an empty database, no rewrite of unchanged records, a high-water
  mark (the file with the most catches) that is never pruned, a separate pool of three
  pre-import snapshots, pruning scoped to the build's own file prefix, and a read-back check
  after every write.
- Import reads the current records, writes the pre-import snapshot and swaps or merges the
  table in one Room transaction. If the snapshot fails, nothing is imported.
- The first launch on an empty database offers a restore, which re-adopts the folder in the
  same tap.
- `allowBackup` is on for device-to-device transfer of `user.db` only; cloud backup carries
  nothing.

The whole loop (catch, leave the app, uninstall, reinstall, offer, pick folder, preview,
restore) was verified on the emulator with `net.pokedex.debug`, and `BackupRepositoryTest`
covers the durability path against real Room on a device.

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
contract is a content manifest — SHA-256 per table over canonically sorted rows, and it
is the only thing CI checks. Byte identity differs between the runner's SQLite and a
workstation's (`docs/adr/0003-dataset-pipeline.md`).

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

The M2 pager rows above are not trustworthy, and the ones below replace them. The app
reopens on the last box it showed (`user_settings.lastBoxIndex`), and the M2 runs did not
reset it, so later runs spent their swipes against the end of the pager and recorded idle
frames. `tools/perf/measure-pager.sh` now rewinds to box 1 and restarts the process before
every run. (Also tried and rejected at M2: taking `BoxSlot`'s per-tile scale layer off at
rest measured the same as leaving it on.)

**With a baseline profile** (2026-09-23): same phone, same protocol, three pager runs and ten
cold starts per state, reported as the spread. The state column is `dumpsys package dexopt`
read immediately before each run.

| State | dexopt | Cold start | Pager janky | P90 | P99 |
|---|---|---|---|---|---|
| 0. No AOT (`cmd package compile --reset`) | verify | 252–293 ms | 6.6–6.8% | 8–9 ms | 24 ms |
| 1. `installRelease` before the app profile (library rules only) | speed-profile, install-dm | 262–355 ms | 5.9–6.5% | 8–9 ms | 19–22 ms |
| 2. `installRelease` with the app profile | speed-profile, install-dm | 231–289 ms | 4.5–5.3% | 7 ms | 13–14 ms |
| 3. State 2 after background dexopt | speed-profile, install-dm | 233–282 ms | 5.1–5.3% | 7–9 ms | 14–16 ms |
| Ceiling: state 1 after `compile -m speed` | speed, cmdline | 198–248 ms | 5.1–5.4% | 8 ms | 14 ms |
| 4. After M3: `installRelease` with the regenerated profile (2026-09-24) | speed-profile, install-dm | 215–238 ms | 5.3–6.1% | 7 ms | 15–17 ms |
| 5. After the settle-frame fix: `installRelease` with the regenerated profile (2026-09-24) | speed-profile, install-dm | 231–293 ms | 3.1–3.5% | 7–9 ms | 11–13 ms |

State 4 is the M3 build after regenerating the profile with the journey extended to the catch
sheet, Progress and Settings, same phone and protocol. Cold start is a little faster than
state 2. The pager is a little worse: 0.5–1 point more janky frames, and P99 up 2–3 ms. Its
slow-UI-thread counts (38–50 per run) point at main-thread work, not rendering. M3 changed
nothing in the pager or the grid. It did add a clickable header row above the pager and an
app-scope observer of the records table for the backup scheduler, which is idle during
swipes. Whether any of that is the cause, or it is thermal variance between two days, is a
question for the settle-frame investigation (`prompts/03c-settle-frame.md`). It was not
chased here.

The profile brings an installed app to the fully compiled ceiling on its first launch. It
does not bring the pager inside 8.3 ms P99, and nothing that compiles code can, because the
ceiling itself is 14 ms. In state 3 the forced background dexopt found nothing worth
recompiling, which is why the reason stays `install-dm`.

*What the remaining jank is.* A Perfetto trace of the 25-swipe run on state 2, with
SurfaceFlinger's frame timeline, shows 93 of 1517 frames missing the app deadline. Only one of
them is main-thread bound, so JIT is no longer the problem. They fall in two clusters:

- **The first frames of a swipe (38).** The main thread's work is small (about 3 ms).
  RenderThread's `flush commands` runs twice its usual 2 ms while the mid cores run at about
  1.4 GHz rather than 1.9 GHz, which is the CPU governor ramping up again after the protocol's
  idle 0.5 s gap. That is a property of the device and of the test's pacing, not of app code.
- **The frame where the page settles (38).** Its `doFrame` starts 10–17 ms late because the
  main thread is busy outside a frame with Compose lazy-layout work. It prefetches the next
  page (`PausedComposition:applyChanges → Compose:onRemembered` about 5–6 ms,
  `measureAndLayout` about 5.7 ms) and deactivates the page that left
  (`Compose:deactivate → Compose:onForgotten` about 9.4 ms). That is remember and forget work
  for 30 tiles per page. Sprite decoding is not a contributor: it runs on a background
  dispatcher, and it does not appear among the threads holding RenderThread's CPU.

The settle frame is the next thing to investigate, and it is a grid-level question. Which
remembered object makes `onRemembered`/`onForgotten` cost about 0.3 ms per tile is not visible
at this trace's depth. Answer it with a method trace or a sampling trace before changing
anything.

### The settle frame, attributed (2026-09-24)

**Answer: the shared-element origin on every grid sprite.** `Modifier.slotOrigin` gives each of
a page's thirty sprites its own `rememberSharedContentState` and `sharedElement` registration.
Entering a page registers thirty `SharedElementEntry`s with the app-wide
`SharedTransitionScope`, and leaving one unregisters thirty. Each add walks the scope's
entry list to find its slot. Each remove does an `indexOf` on a `SnapshotStateList`, re-runs
`SharedElement.updateMatch` over the enabled entries (snapshot reads throughout) and
launches a coroutine. That is quadratic in live tiles. Only one of the thirty can ever take
part in a transition: the one tapped.

*Method.* Rerunnable as follows. None of it touches `net.pokedex`.

1. `./gradlew :app:installBenchmarkRelease` installs `net.pokedex.profiling`: release code,
   R8 and the baseline profile, plus `runtime-tracing` (benchmarkRelease only, see
   `app/build.gradle.kts`). Its compile state is `speed-profile, install-dm`, the same as state 2.
2. `tools/perf/trace-pager.sh <serial> <out.pftrace> [hz]` runs the §8 protocol under
   Perfetto. It collects atrace, SurfaceFlinger's frame timeline, composition tracing and
   callstack sampling. `NAMES=0` leaves composition tracing off, and an `hz` of 0 leaves
   sampling off. Use one named trace to learn the composables, three `NAMES=0` sampled
   traces for proportions, and three with neither for timings. Composition tracing writes a
   marker per composable and inflates what it measures. The kernel throttles sampling to
   about 440 Hz of main-thread time, which is why the sampled traces are pooled.
3. `python tools/perf/settle_frame.py <mapping.txt> <traces...>`, with the `perfetto`
   package installed in a scratch virtualenv. It sums each settle phase per swipe and buckets
   the samples in each phase by the object that owns them. The R8 mapping is
   `app/build/outputs/mapping/benchmarkRelease/mapping.txt` from the same build.
4. Confirm every bucket with an ablation: a throwaway profiling build without that object,
   measured with `PKG=net.pokedex.profiling tools/perf/measure-pager.sh`. A sample share is
   an estimate. The ablation is the number.

*Where the samples land* (three sampled traces, 75 swipes). Shared-element code owns 85% of
`onRemembered`, 75% of `onForgotten` and 15% of the prefetch's measure. What remains of
`onForgotten` is the `LaunchedEffect`s being cancelled (11%) and layout nodes deactivating (5%).
Coil's `AsyncImagePainter.onRemembered` is 0.03 ms a tile, so the request start that was a
suspect is negligible. `BoxSlot`'s `MutableInteractionSource`, press animations and
celebration `Animatable` never registered as an owner above 2%.

*Per swipe, main thread* (three unsampled traces each, median ms):

| Build | compose | apply | of it `onRemembered` | measure | deactivate | over-budget idle frame |
|---|---|---|---|---|---|---|
| Profiling build as is | 10.8 | 12.4 | 6.8 | 7.1 | 8.8 | 19.8 |
| A1: grid sprites without `slotOrigin` | 6.1 | 3.2 | 0.5 | 0.1 (p90 8.5) | 1.5 | 0 |
| A2: A1 and no sprite at all | 5.7 | 1.7 | 0.5 | 1.7 | 1.1 | 0 |

The last column is the settle frame's lateness. When the prefetch does not fit the idle time
between frames, the scheduler runs it as an `idle_frame` of its own, and the settling frame's
`doFrame` waits behind it. Without the shared-element registrations it fits.

*The same builds, untraced* (`measure-pager.sh`, three runs each, same session):

| Build | Pager janky | P90 | P99 | Slow UI | Slow draw |
|---|---|---|---|---|---|
| `net.pokedex` as installed (state 4) | 5.6–5.7% | 7 ms | 17–18 ms | 43–45 | 22–27 |
| Profiling build as is | 5.5–5.9% | 7–8 ms | 16–18 ms | 39–49 | 21–23 |
| A1: no `slotOrigin` in the grid | 3.1–3.2% | 7–8 ms | 12 ms | 17–22 | 19–22 |
| A2: A1 and no sprites | 1.0–1.5% | 5–6 ms | 9 ms | 4–8 | 2–7 |
| A3: A1 and `beyondViewportPageCount = 1` | 3.4–3.6% | 9 ms | 12 ms | 24–26 | 18–25 |

The profiling build measures the same as the installed app, so it is a fair stand-in. A2 is
not a candidate, because the silhouette rule needs the sprites. It shows that most of what
remains after A1 is RenderThread (slow draw), and that is the swipe-start cluster.

*Tested and rejected:* `beyondViewportPageCount = 1` (A3). Composing neighbours in advance
does not remove the remember and forget work. It moves that work to when `currentPage`
changes, mid-swipe, and adds a page's worth of live tiles. It measured worse than A1 on
janky frames and P90.

*The protocol's 0.5 s gap.* Each swipe of `measure-pager.sh` is 858 ms apart in practice,
because the 0.5 s sleep adds to the 180 ms gesture and about 180 ms of `adb` and `input`
start-up. With the loop run on the device and 0.15 s between swipes, the profiling build as
is measured 3.6–3.9% janky, P99 15–16 ms, slow UI 31–33 and slow draw 3–10. The gap exaggerates
the swipe-start cluster: with no idle gap the governor stays ramped and slow-draw frames
almost vanish. It hides nothing about the settle frame, whose slow-UI count holds.

So the tight run is now a second standing measurement: `TIGHT=1 tools/perf/measure-pager.sh
<serial>`. It runs the same 25 swipes, but loops them on the device. It does not replace the
default run, whose numbers remain the baseline everything compares against. Read the two
together: the default run's slow-draw count, minus the tight run's, is what the idle gap costs.

*What changed.* Only the slot being opened carries `slotOrigin` now (`BoxContent` keeps its
key, saveable). The other twenty-nine never had a matching destination, so the transition is
the same in both directions, which was checked frame by frame on the phone. On the profiling
build it measured the same as A1: 3.1–3.5% janky, P99 11–13 ms, slow UI 21–25. The settle
phases per swipe were compose 6.6 ms, apply 3.2 ms and deactivate 1.5 ms, and the median
over-budget idle frame was 0. After regenerating the profile, the installed app is state 5
in the table above:

| `net.pokedex`, state 5 | Pager janky | P90 | P99 | Slow UI | Slow draw |
|---|---|---|---|---|---|
| Default run | 3.1–3.5% | 7–9 ms | 11–13 ms | 22–25 | 20–23 |
| Tight run (`TIGHT=1`) | 2.9–3.5% | 7–8 ms | 12–13 ms | 24–28 | 4–7 |

The settle frame is no longer the problem. P99 is still over the 8.3 ms budget. What remains
is the swipe-start cluster, which the default run's slow-draw count shows and the tight run
largely removes, and the sprites' own cost. A2 bounds the sprites at about 3 ms of P99, mostly
RenderThread. Neither of these is settle-frame work, and neither is a change to make without
its own trace.

### Baseline profile: how it reaches the compiler, and regenerating it

This app is sideloaded, so the profile reaches ART without a store:

- `./gradlew installRelease` pushes the `.dm` that AGP builds next to the APK, and ART
  compiles it at install time (`speed-profile`, `install-dm`). This is the path in daily use,
  and it is compiled sooner than a Play install would be.
- A bare `adb install` of the APK gets no `.dm`, so the app starts under JIT.
  `profileinstaller`, declared in `:app`, writes the profile at first launch, and the next
  background dexopt compiles it. That job runs roughly daily, only while idle and charging.
- Background dexopt later adds rules for what JIT recorded in real use, so the installed app
  can only get better.

The profile is `app/src/main/generated/baselineProfiles/baseline-prof.txt`, checked in like
`reference.db`. Regenerate it on demand, with the phone connected, when the screens or
navigation it covers change: after M3's screens land, and after any change to the pager,
slot detail, search or the "All boxes" sheet.

```
./gradlew :app:generateBaselineProfile     # ANDROID_SERIAL=<serial from adb devices>
```

It runs against `net.pokedex.profiling`, a separate application id, because the run
uninstalls the build it tested. Under the release id that deleted the real install and its
catch records. A normal build never generates, and neither does CI (it has no device).
Commit the regenerated profile on its own. Then re-measure with
`tools/perf/measure-pager.sh <serial>` for each state above and update the table. Take
`TIGHT=1` as well.
