# Prompt 10 — Design refresh: livelier, richer, same collection (with `/impeccable`)

> Run this in a fresh Claude Code session at the repo root, in **plan mode**.
> Read `CLAUDE.md`, `docs/00-big-picture.md`, `docs/design-system.md` (all of it),
> `docs/design-usage.md`, `docs/adr/0009-light-theme.md`, `docs/architecture.md` §8 (the pager
> budget and every measured state), and `docs/prompts/02-design.md` (the brief the current
> language answered). Then invoke the **`/impeccable`** skill and follow its setup.

---

You are the design director and design engineer on the **shiny living dex tracker**: an Android
app for one person (me) that mirrors my Pokémon HOME boxes. I have used it daily since prompt 8
shipped and it works. It does not yet *feel* the way I want it to.

## What I want

**Livelier, better, higher quality.** Today the app is correct, fast and restrained, and in
places it reads as flat: screens that are a column of rows, a Progress screen that reports
rather than rewards, a slot detail that is informative but not a moment, motion that is mostly
absent outside the catch celebration. I want opening the app to feel like opening a collection
I am proud of, and I want every screen to feel designed rather than assembled.

What I do **not** want: a costume (no fan-site pastiche, no official branding, logos or fonts),
effects competing for attention, or anything that makes the box pager slower. The one thing the
current language got exactly right is that the collection itself is the decoration. Keep that
idea; make everything around it worthy of it.

## Use `/impeccable` properly

The skill is web-first; this is native Android (Jetpack Compose, minSdk 26). Use the parts that
apply and say which you skipped and why.

1. **Setup.** Run the launcher once (`impeccable.cmd` on this Windows machine; the skill says
   where it lives). There is no `PRODUCT.md` or `DESIGN.md` yet.
2. **`init`** writes `PRODUCT.md`. Platform `android`. The product truth is in
   `docs/00-big-picture.md` and `CLAUDE.md`: one user, one phone, offline, a collection mirror
   plus the knowledge to fill it. Most surfaces are **Operate**; decide, and defend, whether the
   box view and slot detail are closer to **Experience** (the collection is the artifact).
3. **`document`** writes `DESIGN.md` from the code. **One source of truth:** `docs/design-system.md`
   is today's canonical language and CLAUDE.md points at it. Recommend how the two coexist
   (DESIGN.md as the skill's compact view that links to the rationale, or one replacing the
   other with CLAUDE.md updated in the same commit). Two documents that drift is the outcome
   to avoid.
4. **`critique`** and **`audit`** (the native variant, `audit.native.md`) on **every screen**:
   Boxes (pager, search mode, mark mode), Slot detail, Variant detail, Hunt, Progress, Settings,
   My games, Restore, the gallery. Judge from real renders, not code: the debug build on the
   emulator and on my phone (`net.pokedex.debug`), both themes, default and 200% text.
   Committed Roborazzi goldens are a valid second source; check they match current tokens.
5. **Direction**, then the refinement commands the critique justifies: `typeset`, `layout`,
   `colorize`, `animate`, `delight`, `bolder` where things are timid, `quieter` where they are
   loud, `clarify` for copy. **`polish`** last, on everything.
6. `live`, `generate` and the detector hook are browser/CSS tools. They do not apply to Compose;
   do not force them. Rendered comparisons come from the gallery, Roborazzi or
   `recordRoborazziDebug` runs you look at and do not commit.

## The decision you must bring me first: refine or replace?

`/impeccable` distinguishes **refinement** (keep the identity, raise the craft) from **redesign**
(choose a new world and treat the old look as evidence). Give me both, rendered, not described:

- **A. Evolve "Display case".** Same world, more life: e.g. type colour carried into headers and
  heroes, a typographic voice with more character, real depth and light on the surfaces around
  the grid, motion that connects screens, a Progress screen that feels like a trophy shelf.
- **B. A new world.** One alternative direction with a real point of view, still a fast native
  app and still a collector's app.

Show each on the same three screens (Boxes, Slot detail, Progress), both themes, as gallery
pages or rendered screenshots I can flip between. Recommend one and say why. I expect A, but
convince me rather than assume it.

## What holds whichever way you go

These are the four rules CLAUDE.md says outlive any token value. Breaking one needs an explicit
argument, my explicit yes, an ADR, and CLAUDE.md updated in the same commit:

- **Gold means shiny, and nothing else**: the caught-slot rim/pip, progress numerals and arcs,
  the catch celebration. "Livelier" must not mean gold on headers, buttons or badges.
- **No dynamic colour.** Two authored themes, both first-class.
- **Uncaught is a flat silhouette, caught is full colour.**
- **Borders, not elevation, inside the grid.** No per-tile shadow, ripple or shader (AGSL needs
  API 33). Life goes *around* the grid, not into its 30 tiles.

And these are not up for debate:

- **Performance.** The box pager's P99 must not get worse (`docs/architecture.md` §8, state 9:
  12–13 ms on the phone). Re-measure with `tools/perf/measure-pager.sh` on the **profiling**
  build after the refresh, default and `TIGHT=1`. Any new motion is measured, not assumed.
- **Accessibility.** Every token pair stays contrast-checked in both themes (`ContrastTest`,
  `designCheck`); the 18 type colours stay derived (fixed OKLCH L and C), never hand-edited.
  Status never by colour alone, 48 dp targets, TalkBack sentences unchanged or better, 200% text,
  reduce-motion honoured by every new animation.
- **The enforcement stays.** Feature code uses tokens and components only; `feature-rules.yml`
  keeps failing the build on raw `Color`, dp/sp and `TextStyle`. If a refresh needs a new token
  or component, it goes in `:design-system` with a gallery entry, not in a feature.
- **Toolchain.** If a font or library is added, say why; no Expressive alpha APIs leaking into
  features (`docs/design-system.md`, "What we take from Expressive"). Do not bump AGP, Kotlin,
  Hilt or the Compose BOM alone (ADR 0008).
- **Screenshots come from CI.** Re-record goldens with the `record screenshots` workflow and pull
  them. Never commit goldens recorded on this machine.

## Things I have noticed, for you to judge

Tell me which are worth it, which later, which not at all:

- The catch celebration is the best moment in the app. Completing a **box** and finishing a
  **generation** deserve their own (smaller) acknowledgement.
- Slot detail could open with the sprite as a real hero: larger, on a surface that carries the
  species' type colour, with the caught state unmistakable.
- Progress is numbers. It could show *the collection*: per-generation shelves, recent catches,
  what is closest to done.
- Screen-to-screen transitions are plain. The slot-to-detail shared element exists; the rest
  could feel like one connected space.
- Empty states and the hunt list are the screens I see when there is nothing to celebrate; they
  are where the app most often feels like a template.
- A launcher icon and splash that belong to the new language.

Prompt 9 (the hunt engine's counter screen) and prompt 11 (Sword guides) will be built in
whatever language this prompt leaves behind. Make sure the components they will need (a large
tap target, a step-by-step guide layout, a stat block) are anticipated in the direction, even if
not built here.

## How to run the session

1. **Research, then stop.** `/impeccable` setup, `init`, `document`, then `critique` + native
   `audit` of every screen from real renders. Present: the critique's top problems ranked, the
   two rendered directions with a recommendation, the DESIGN.md/design-system.md answer, which
   of my ideas you recommend, and a build order. Make every question answerable with "yes to your
   recommendation". **Stop for my approval.**
2. **Build** in the approved order: tokens and components in `:design-system` first (gallery,
   `designCheck`, contrast), then screen by screen. Install the debug build on the emulator and on
   my phone and look at every changed screen in both themes and at 200% text before saying it is
   done. Run `polish` at the end and fix what it finds.
3. **Close out.** Re-record goldens through the workflow, re-measure the pager on the profiling
   build and add a state to `docs/architecture.md` §8, update `docs/design-system.md` (and
   `DESIGN.md` per the approved answer), `docs/design-usage.md` if a rule changed, and write an
   ADR if the direction changed rather than evolved.

## Constraints

- **My phone holds my real records.** Never run connected tests, `uninstall` or `pm clear` against
  `net.pokedex`, and never install anything but a release build under that id. Look at designs on
  `net.pokedex.debug`; measure on `net.pokedex.profiling`. If I want the refreshed release on my
  phone, I run it myself in **PowerShell**:
  `$env:ANDROID_SERIAL = "<serial>"; .\gradlew.bat :app:installRelease`, after a fresh backup
  exists in `/sdcard/PokemonList`.
- No `user.db` change is expected. If one appears, it needs a `Migration` and a
  `MigrationTestHelper` test in the same commit, and a reason.
- No network, no new permission.

Every commit builds: `./gradlew test detekt lintDebug :design-system:designCheck assembleDebug`.
One logical change per commit, imperative subject under 72 characters, no type prefixes.
