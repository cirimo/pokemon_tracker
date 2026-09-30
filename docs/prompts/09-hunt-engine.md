# Prompt 9 — The hunt engine: count, odds, phases, sessions, history (M5)

> Run this in a fresh Claude Code session at the repo root, in **plan mode**.
> Read `CLAUDE.md`, `docs/00-big-picture.md` (the v2 scope), `docs/architecture.md` (§2, §3,
> §6 "Backup format" and "Durability", §8), `docs/adr/0001-slot-identity.md`,
> `docs/adr/0007-backup-format.md`, `docs/adr/0012-hunt-ranking.md`,
> `docs/adr/0015-regular-catches.md`, `docs/dataset-pipeline.md` ("Scope") and, before any
> UI, **`docs/design-usage.md`**. Read `docs/design-system.md` on the catch celebration and
> on gold.

---

You are the feature engineer on the **shiny living dex tracker**: an Android app for one person
(me) that mirrors my Pokémon HOME boxes. Prompt 8 (the regular dex) has shipped. This is M5, the
v2 that `docs/00-big-picture.md` promised: the app stops being only a plan and starts being the
thing I hold while I hunt.

## How I actually hunt

I pick a target from the hunt list, open the game, and grind: resets, eggs, outbreak clears,
encounters. Today I count in my head or in a separate counter app, and the number never reaches
the catch record. What I want, in the order I would reach for it:

- **Count.** One big target I can hit without looking, hundreds of times a session, with the
  phone propped next to the Switch. Minus one for a mis-tap. A count that survives the app being
  killed, the phone locking, and me forgetting it for a week.
- **Odds.** For the method I am using, what the chance per encounter is, and what my count means:
  "you were 63% likely to have found it by now". Honest when nothing is curated.
- **Phases.** A shiny that is not my target (a phase) is recorded, the count for it kept, and the
  hunt carries on. If the phase is a slot I need, it can be marked caught from there.
- **Sessions.** When I hunted and for how long, without me pressing a start button every time.
- **Finish.** When the target arrives: the catch celebration, the slot marked caught with the game
  and the date, and the count ("412 resets") in the notes or somewhere better.
- **History.** Finished and abandoned hunts, kept. Per slot, so slot detail can say "caught after
  412 resets in Violet".

## What already exists

Read it before proposing anything.

- `CatchRecord` (`:core:model`) is keyed by `CatchKey(variantId, copyIndex)`: `caught` (shiny),
  `regular`, origin game, caught-at, notes, priority, `favourite` (unused) and `updatedAt`.
  `user.db` is **v6**. Nothing about a hunt is stored today.
- The **hunt list** ranks hunts, not slots (ADR 0012): `HuntKey(dexNum, regionalForm)`, the needed
  slots it fills, my games that offer it shiny, and `Way` (game, encounter, `MethodOdds`).
  `huntPlan` derives all of it on read.
- **Odds** are exact (`Odds.ofRolls`, `1 - (4095/4096)^n`) and absent when not curated.
  `odds_modifier` rows are per game and method, and there are about twenty.
- **Curation is thin.** Legends Arceus has a method for 310 of its 347 shiny-obtainable
  variants; odds are curated for Arceus, Scarlet/Violet and Sword/Shield; every other game's
  encounters are seeded, not complete. Most hunts I start will have no curated method.
- **The catch toggle** in slot detail marks caught, prefills the game (`prefillOrigin`), and plays
  the catch celebration on the tile. Unticking loses nothing (`withCaught`).
- **Backups** are one JSON file, `schema` 1, additive keys only (ADR 0007). A newer schema is
  refused outright. The high-water mark counts shiny catches.
- **The pager** must not gain anything per tile (§8); a hunt is not shown in the grid today.

## Think about these before you propose anything

Give me a recommendation and the trade-off for each. I approve quickly and override only the
ones I care about.

- **What a hunt targets.** A slot (`CatchKey`), a `HuntKey` (the hunt list's unit, which can fill
  several slots: 28 Unown from one outbreak), or a variant? What happens when a hunt for Unown
  finds Unown-F: is that the target or a phase? Never key on slot position.
- **What is stored.** CLAUDE.md says never store a progress counter, because it is derivable. An
  encounter count is not derivable, so it must be stored, but as what: an integer on a hunt row,
  or an event log (one row per increment, per session)? Think about a write per tap (hundreds an
  hour), process death mid-session, undo of a mis-tap, and what sessions and "encounters per
  hour" need. The log is also what makes history honest; the integer is what is cheap.
- **Durability of a count.** Write-through on every tap, or batched? The rule is that a killed app
  loses nothing I counted. Say how you prove that.
- **The `user.db` migration.** New tables, v6 to v7, a `MigrationTestHelper` test, proven on
  `net.pokedex.debug` with real-shaped data before the real install moves. No foreign key into
  reference data; a hunt outlives a game or method the dataset drops.
- **Backups.** Are hunts in the backup file? If yes, additive (`hunts` as a new key) or a bump?
  Think about the direction that loses data (an older build restoring a newer file) and whether
  hundreds of kilobytes of tap events belong in a file that is written two minutes after every
  change. The high-water mark must never be moved by hunt data.
- **Odds with a count.** Show cumulative probability, expected encounters, or both? What the
  screen says when the method has no curated odds (not 1/4096: ADR 0012 already refused to claim
  that). Whether I can pick the method and modifiers myself (charm yes/no, outbreak cleared or
  not) when curation does not know them.
- **Phases.** What a phase records (variant, count at the time, the game), whether marking a
  needed phase caught goes through the normal catch path, and whether the count resets after a
  phase or keeps running.
- **Sessions.** Inferred from gaps between taps (how long a gap ends one?), or explicit
  start/stop? What "encounters per hour" is computed from.
- **The counter screen.** A destination of its own, reached from the hunt list and slot detail.
  How big the target is and where it sits for one-handed use with the phone propped up; keep
  screen on while it is open; volume keys to count (the app can read them only in the foreground;
  say whether that is worth it). Haptics per tap. What TalkBack says on each count without
  flooding. 200% text.
- **Several hunts at once.** I sometimes run two games side by side. One active hunt, or several,
  and how I switch.
- **Finishing.** The target caught: the celebration plays (it is the moment the app exists for),
  the slot is marked caught through the normal path, and the count lands where? A structured
  field is better than a sentence in the notes, but it is a schema question; say which.
- **Where hunts show outside the counter.** The hunt list row ("in progress, 412 resets"), slot
  detail, Progress. Not the grid: nothing new per tile.

## Ideas of my own, for you to judge

Tell me which are worth it now, which later, and which not at all.

- **A notification counter.** An ongoing notification with +1, so I can count from the lock
  screen. It needs `POST_NOTIFICATIONS` and a foreground service or a notification with actions;
  weigh that against the no-network, no-surprises posture of the app.
- **Odds bands.** A quiet marker when a hunt passes 1×, 2× and 3× the expected encounters:
  information, not a celebration, and never gold.
- **Stats.** Total encounters, shinies per game, average resets per method, from history.
- **A quick-start.** "Hunt this" on the top row of the hunt list, one tap from "Hunt next" on the
  box view.

## Scope

In priority order:

1. The hunt's shape in `:core:model`, the `user.db` v7 migration and its test, repository and
   mapper. Prove the migration on `net.pokedex.debug` with real-shaped data first.
2. Counting: the write path, its durability test (a count survives process death), undo.
3. Odds with a count, in `:core:model`, with JVM tests against the tables the curated rows cite.
4. The counter screen, and finishing a hunt into a catch record, celebration included.
5. Phases and sessions, as recommended.
6. History on slot detail and the hunt list; backups, as recommended.
7. Whichever of my ideas you recommended for now.
8. Profile the counter (a tap to the number on screen) and re-measure the pager, default and
   `TIGHT=1`.

Out of scope: curating more games' encounters (a dataset task of its own), the open data
questions about Sword/Shield exclusives and Legends Z-A locks, and the mark-mode frame cost
(already a task of its own, `docs/architecture.md` §8 state 9).

## Constraints

- The layering rule, module table and "never do these" in CLAUDE.md hold. Never key on slot
  position. Nothing about a hunt goes in `ReferenceDatabase` or across the two databases.
- `user.db` changes: a `Migration` and a `MigrationTestHelper` test in the same commit, never
  `fallbackToDestructiveMigration`.
- **My phone holds my real records.** Nothing runs against `net.pokedex` except
  `installRelease`, and only after the migration has been proven on `net.pokedex.debug` and a
  fresh backup exists in the SAF folder (`/sdcard/PokemonList`). Tell me before the real install
  is upgraded. In prompt 8 the permission check refused to let the session run `installRelease`
  against `net.pokedex` at all; if that happens, hand me the command for **PowerShell**, which is
  what my terminal runs: `$env:ANDROID_SERIAL = "<serial>"; .\gradlew.bat :app:installRelease`.
  Profiling and experiments use `net.pokedex.profiling`, never the release id.
- No network, ever. No new permission without saying why in the plan.
- Design: gold means shiny and nothing else, so a counter, an odds band or a phase gets no gold;
  the catch celebration is the one shimmer and plays for the finished hunt. New components get a
  gallery entry, `designCheck`, and screenshots recorded by the workflow.
- Nothing new in the box pager's tiles. The pager's P99 must not get worse.

## How to run the session

1. **Research, then stop.** Read the code above, run the app on the emulator, and time a Room
   write on the phone's debug build so the durability answer rests on a number. Present the
   questions with a recommendation each, and a build order. Stop for my approval.
2. **Build** in the approved order. Install and look on the phone (debug build) and emulator
   before saying anything works, in both themes and at 200% text. Count a few hundred taps on the
   phone, kill the app mid-count, and show the count survived.
3. **Close out.** Update `docs/architecture.md` (§4 ERD, §6, §8), ADR 0007 if the backup shape
   changes, `docs/design-system.md` for new components, and write an ADR for the hunt's shape and
   how a count is stored.

Every commit builds: `./gradlew test detekt lintDebug :design-system:designCheck assembleDebug`.
One logical change per commit, imperative subject under 72 characters, no type prefixes.
