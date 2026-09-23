# Conventions

Read `docs/00-big-picture.md` for what this app is, `docs/architecture.md` for how it is
built, and `docs/adr/` for why each significant decision went the way it did. This file
is the short version: the rules that apply to every change.

Single user, single device, offline. There is no backend, no auth, no analytics, and no
multi-user story to design around. When a choice is between "general" and "right for one
person with a phone", pick the second.

## The layering rule

```
ui (Compose) -> viewmodel -> repository -> dao
```

Dependencies point one way. A lower layer never knows about a higher one.

- Domain types live in `:core:model` and carry no Room, Compose or Android annotations.
- Room entities never leave `:core:data`. Repositories return `:core:model` types, and
  `repository/Mappers.kt` is the membrane. It looks like boilerplate; it is the thing
  keeping features independent of the storage shape.
- ViewModels expose one `@Immutable` state class and take one sealed event type.

## Module boundaries

| Module | May depend on |
|---|---|
| `:app` | everything |
| `:feature:*` | `:core:model`, `:core:data`, `:design-system` |
| `:core:data` | `:core:model` |
| `:core:model` | nothing internal. Pure JVM, no Android |
| `:design-system` | nothing internal |

**No feature module may depend on another feature module.** Shared UI goes to
`:design-system`, shared logic to `:core:model`. CI enforces this; the
`pokedex.android.feature` convention plugin is what makes it the path of least
resistance.

Adding a module needs a reason bigger than tidiness. Four meaningful modules beat twelve
ceremonial ones.

## Never do these

- **Never key anything on slot position.** A catch record is keyed
  `(variantId, copyIndex)` and knows nothing about boxes or presets. The upstream preset
  is a positional array with no slot ids; keying on position corrupts every record the
  first time a form is inserted. `docs/adr/0001-slot-identity.md`.
- **Never store a progress counter.** Progress is `progressOf(slots, records)`, derived
  on read. A stored count is a second source of truth that drifts.
- **Never add `fallbackToDestructiveMigration` to `UserDatabase`.** It is correct on
  `ReferenceDatabase` and catastrophic here. Every version bump gets a `Migration` and a
  `MigrationTestHelper` test in the same commit.
- **Never make a network call at runtime.** The app has no `INTERNET` permission and
  should not gain one. Fetching belongs to `tools/dataset-pipeline`, which runs on a
  desktop.
- **Never regenerate the dataset from a Gradle build.** It is an on-demand tool; the
  output is checked in.
- **Never let a Room entity escape `:core:data`,** and never put a foreign key across
  the reference/user boundary. They are separate database files precisely so that
  cannot happen.
- **Never commit a keystore, a password, or `local.properties`.**
- **Never commit locally-recorded screenshots.** Roborazzi comparison is pixel-exact and
  Robolectric does not render identically across operating systems, so goldens recorded on
  a workstation fail on CI. Record them with the `record screenshots` workflow, which runs
  on the same image that verifies them, then pull. Running `recordRoborazziDebug` locally
  to *look* at a component is fine; committing what it writes is not.
- **Never bump one toolchain version alone.** AGP, Kotlin, Hilt and the Compose BOM are
  pinned as a set that only works together. `docs/adr/0008-toolchain-baseline.md`.

## Design system

`:design-system` owns theme, tokens and components. Features compose them; they do not
define their own colours, spacing or shapes.

The locked visual direction is "Display case" — `docs/design-system.md` is the built
language and its rationale, and **`docs/design-usage.md` is the one to read before writing
any feature UI**. It is enforced, not advisory: raw `Color(...)`, raw dp/sp and ad-hoc
`TextStyle` in `feature/` fail the build via `config/detekt/feature-rules.yml`.

Four rules that outlive any token value:

- **Gold means shiny, and nothing else.** It earns exactly three places: the caught-slot
  rim/pip, progress numerals and arcs, and the catch celebration. Never backgrounds,
  navigation, headers or badges.
- **No dynamic colour.** `PokedexTheme` takes no `dynamicColor` parameter on purpose. It
  does take `darkTheme`: there are two real themes, and light is authored rather than
  tinted. `docs/adr/0009-light-theme.md` records why that reverses the M0 position.
- **An uncaught sprite is a flat silhouette; a caught one is in full colour.** This is what
  keeps 1394 slots from reading as a contact sheet, and it is why the grid needs no other
  decoration.
- **Borders, not elevation, inside the grid.** No per-tile shadow, no ripple on tiles,
  and no shader anywhere in the grid (AGSL needs API 33; minSdk is 26).

Run the gallery with the debug build — it installs a second launcher icon. It is absent
from release because it lives in `app/src/debug`. Toggle theme and font scale at the top of
the screen; those are the two axes where the system breaks quietly.

## Testing

Test what can silently lose or corrupt data. Skip what a compiler or a generator already
guarantees.

| Worth testing | Not worth testing |
|---|---|
| Slot-to-record resolution and `copyIndex` | DAO one-liners Room generates |
| Preset diffing and stranded records | Hilt wiring |
| Progress derivation | ViewModel mapping with no branches |
| Backup encode/decode, and refusal of a newer schema | Layout, when a screenshot covers it |
| `UserDatabase` migrations | |
| That the shipped `reference.db` opens and has 52/1394/1387 | |
| Design-system components, via Roborazzi | |
| Contrast of every token pair, both themes | |
| Touch targets and TalkBack sentences | |

JVM tests in `:core:model` are the ones that carry real confidence, and they run in
milliseconds. Prefer moving logic there over testing it through a ViewModel.

Commands:

```bash
./gradlew test                                  # JVM unit tests, debug variant only
./gradlew :core:data:connectedDebugAndroidTest  # Room tests, needs a device
./gradlew :design-system:recordRoborazziDebug   # render screenshots locally, to LOOK at only
# Screenshot verification is CI-only. verifyRoborazziDebug fails on a workstation by
# design -- the goldens are recorded on ubuntu-latest and pixels differ across platforms.
./gradlew detekt lintDebug                      # static analysis, both fail the build
```

## Dataset

`docs/dataset-pipeline.md` has the full workflow. The short version:

- The curated layer is `data/curated/*.yaml`. Edit it by hand, keep the source citation
  as a comment next to the row it justifies.
- `cd tools/dataset-pipeline && npm run build:dataset` regenerates the asset. Commit the
  `.db` and the manifest together.
- Changing a `ReferenceDatabase` entity means `./gradlew :core:data:assembleDebug` first,
  so Room re-exports the schema, then rebuild the dataset. The pipeline reads that
  exported schema for the DDL and the identity hash.
- If a validator fails, the dataset is wrong. Fix the data, not the validator. The
  numbers it asserts (52 boxes, 1394 slots, 1387 variants, 7 duplicates) are also
  asserted by the app and by the docs — if upstream genuinely changed, update all of
  them in one commit.

## Style

- Kotlin official style, enforced by detekt with the ktlint rule set
  (`detekt-formatting`). One plugin, not two.
- Files are named after the concern they hold, not after the first declaration.
- Sources live in `src/<variant>/kotlin`, never `src/<variant>/java`.
- Comments explain *why*. The code already says what. A comment that restates the line
  above it is noise; a comment that records a constraint you would otherwise rediscover
  the hard way is the reason this codebase is navigable.

## Commits

Imperative subject under 72 characters, no type prefixes. Body explains why when it is
not obvious from the diff. One logical change per commit; a dataset regeneration is its
own commit, separate from the schema change that caused it.
