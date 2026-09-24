# Prompt 5 — M4, "Plan my hunts"

> Run this in a fresh Claude Code session at the repo root, in **plan mode**.
> Read `docs/00-big-picture.md`, `docs/architecture.md`, `CLAUDE.md`,
> `docs/dataset-pipeline.md`, `docs/adr/0001-slot-identity.md`,
> `docs/adr/0007-backup-format.md` and, before you write a single line of UI,
> **`docs/design-usage.md`**.

---

You are the feature engineer on a **shiny living dex tracker**. It is an Android app for one
person (me) that mirrors my Pokémon HOME boxes and tells me what I still need. It is
sideloaded, offline, and has no backend. Treat the locked decisions in
`docs/00-big-picture.md` as constraints, and tell me directly if one of them is wrong.

M2 made the dex visible and M3 made it mine. **This session answers the question I open the
app with most often: what should I hunt next, in which game, and how?**

## The honest problem: the data is thin

M4 is only as good as the dataset behind it, and most of what a hunt needs is **not there
yet**. Measure it before you design anything. Do not trust this summary:

- **Complete, from upstream:** per-game obtainability (`obtainableIn`, `eventOnlyIn`), HOME
  transfer legality (`storableIn`, `transferOnlyIn`), and `shinyReleased`. That is already
  enough to answer "can I get this shiny at all, and in which of my games".
- **Seeded, not complete, curated by hand:** `data/curated/shiny-locks.yaml`,
  `odds-modifiers.yaml` and `encounters/`. At the time of writing there is one encounters
  file (`sv-s.yaml`) with four rows. Method guidance and odds exist for almost nothing.

So there are two halves, and they must not be confused:

1. **The planner**: the screens and the logic. They are built on what is complete, and they
   are honest about what is missing.
2. **The curation**: filling the curated layer, one game at a time, as desktop work through
   `tools/dataset-pipeline`. Never at runtime, and never from a Gradle build.

The planner must never guess. A variant with no curated encounter says "no method recorded
yet". It does not invent a plausible one, and it does not quietly drop out of the list. A
wrong method sends me into a game for a Pokémon that is not there, and that is worse than no
method at all.

## What already exists

Read it before you propose anything.

- `CatchRecord` (`:core:model`) already carries `priority: Int` and `favourite: Boolean`, and
  both already survive a backup round trip. M3 decided what `favourite` is for. Find that
  decision in the code and docs, and do not reopen it without saying so.
- `SlotState.Unavailable` means "obtainable, but not in a game you own". Check what the app
  currently thinks I own. I believe no "my games" setting exists yet, which would make that
  state's meaning a placeholder. Confirm or correct me.
- The Progress dashboard (M3) already cuts progress by several dimensions. The hunt list
  should link into it, not duplicate it.
- Progress is derived: `progressOf`, `progressByBox`. **Never store a count, a rank or a
  score.** A hunt ranking is derived on read, just like progress.

## Scope

In priority order. If the session runs long, the first item ships complete and the last
one waits.

1. **My games.** The set of games I own and actively play. It decides `Unavailable`, and it
   filters everything below. This is user data, so it goes in `UserDatabase` with a
   `Migration`, a `MigrationTestHelper` test, and a backup-format change that follows
   ADR 0007 (schema bump, older files still import, newer ones are refused).
2. **What to hunt next.** A ranked list of the needed slots I can actually get in my games.
   Propose the ranking and keep it explainable: every row says *why* it is where it is.
   Candidates include my `priority`, whether the shiny is obtainable in a game I own, known
   odds, how many slots one hunt would fill (duplicates, forms), and how close a box is to
   complete. The logic lives in `:core:model` with JVM tests, not in a ViewModel.
3. **Per-game availability on slot detail.** For this slot and each game I own: obtainable,
   shiny-locked, event-only or transfer-only, and the method and odds where curated. Missing
   data is shown as missing.
4. **Method guidance and odds.** Odds computed from `rollsAdded` / `denominator` in
   `:core:model`, with JVM tests. Decide which figure the app shows. Published odds use the
   approximation n/4096 (the Shiny Charm's 1/1365.3 is 4096/3). The exact chance for three
   rolls is 1 − (4095/4096)³, about 1/1365.7. The two drift apart at high roll counts. Show
   odds only where curated data backs them.
5. **Curation for one game.** Fill `encounters/`, `shiny-locks.yaml` and
   `odds-modifiers.yaml` for **the one game I tell you I am playing now**. Ask me. Cite a
   source on every row, as the files already do. Regenerate the dataset in its own commit
   (CLAUDE.md). The validators' numbers (52 / 1394 / 1387 / 7) must not move.

**Out of scope:** the hunt engine (counters, phases, sessions, history) is M5. Do not add a
counter, even a small one. Curating more than one game is out of scope too: the method and
the tooling matter more than the row count.

## Think about these before you propose anything

Give me a recommendation and the trade-off for each one. I approve recommendations quickly
and override only the ones I care about.

- **Where the hunt list lives.** A new top-level destination, a mode of the boxes screen
  like search, or a section of Progress? ADR 0006 says no nested graphs until M4 needs them.
  Does M4 need them?
- **The ranking.** Which inputs, in what order, and what happens on a tie. Should the
  ranking be configurable, or fixed and explained? One person uses this app.
- **`priority`.** It is an `Int` already. Is it a few buckets (want / normal / later), a
  free order I drag, or something else? What does dragging cost on a list of 1394?
- **Duplicates and forms.** Seven variants are demanded twice. A form hunt can fill several
  slots at once. Does the list show a hunt, or a slot?
- **Missing data at scale.** Most rows will say "no method recorded yet". How does the list
  stay useful and not become a wall of that sentence?
- **Is the curated schema right?** Before filling a game, check that `encounters/*.yaml`
  can express what that game needs: outbreaks, raids, fixed encounters, eggs, evolution-gated
  forms. If it cannot, change the schema first, in its own commit, with its validator.

## Constraints

- The layering rule, the module table and the "never do these" list in CLAUDE.md hold.
  In particular: no network, no stored counters, no Room entity outside `:core:data`, no
  foreign key between the two databases, and no `fallbackToDestructiveMigration` on
  `UserDatabase`.
- A new feature module needs a reason bigger than tidiness. Say whether the hunt planner
  belongs in `:feature:dex` or earns its own module.
- **My phone holds my real records.** Nothing runs against `net.pokedex` except
  `installRelease`. Tests, experiments and profiles use `net.pokedex.debug` or
  `net.pokedex.profiling`. Check the application id before any uninstall, `pm clear`,
  connected test or non-release install.
- Design: gold means shiny and nothing else, so a hunt list's "top pick" is not gold. Borders,
  not elevation. Silhouette until caught. Raw colours, dp and text styles in `feature/` fail
  detekt. A new `:design-system` component gets a gallery entry, passes
  `:design-system:designCheck`, and has its screenshots recorded by the `record screenshots`
  workflow, never locally.
- Performance: the pager's P99 is 11–13 ms, against a budget of 8.3 ms (§8, state 5). M4 must
  not make it worse. Nothing new is per-tile, and nothing new registers with a scope
  wider than the page (see `BoxPager`'s KDoc for why). After the screens land, extend the
  baseline profile journey to them, regenerate the profile in its own commit, and re-measure
  with both `tools/perf/measure-pager.sh` and `TIGHT=1`.

## How to run the session

1. **Research, then stop.** Measure the data (what upstream answers, what curated rows exist
   per game), read the code, and run the app on the emulator. Then present:
   - the data measurement;
   - the questions above, each with a recommendation;
   - the one game you want me to name for curation;
   - a build order.
   Stop for my approval. Do not write feature code before I answer.
2. **Build in the approved order.** My games, then the ranking in `:core:model` with its
   tests, then the screens, then the curation. Install and look on the emulator before you
   say anything works.
3. **Close out.** Update `docs/00-big-picture.md` (mark M4 done), `docs/architecture.md`
   (any new table, the navigation, and the §8 re-measure) and an ADR for any decision
   with real alternatives (the ranking is a likely one).

Every commit builds: `./gradlew test detekt lintDebug :design-system:designCheck assembleDebug`.
One logical change per commit, imperative subject under 72 characters, no type prefixes. A
dataset regeneration is its own commit.
