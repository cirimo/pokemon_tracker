# Prompt 8 — The regular dex: owning a slot before it is shiny

> Run this in a fresh Claude Code session at the repo root, in **plan mode**.
> Read `CLAUDE.md`, `docs/architecture.md` (§2, §3, §6 "Backup format" and "Durability"),
> `docs/adr/0001-slot-identity.md`, `docs/adr/0005-sprites.md`, `docs/adr/0007-backup-format.md`,
> `docs/adr/0012-hunt-ranking.md`, `docs/design-decisions.md` and, before any UI,
> **`docs/design-usage.md`**. Read `docs/design-system.md` on the silhouette rule and on gold.

---

You are the feature engineer on the **shiny living dex tracker**: an Android app for one person
(me) that mirrors my Pokémon HOME boxes. Prompt 7 (where to farm) has shipped. This is a bigger
one, and the data in it is mine, so it is slower on purpose.

## What I want

Today a slot is either caught shiny or not. But a lot of my HOME boxes hold the **regular**
Pokémon in that slot: I have a Bulbasaur, just not a shiny one. I want to record that too:

- **See it.** A slot I own the regular one of looks different from a slot I have nothing for,
  and from a shiny one.
- **Count it.** Shiny progress stays the headline and never counts a regular catch. Next to it,
  a second number: the living dex, any colour.
- **Upgrade it.** "I have the regular one, the shiny would be an upgrade" is a list I want to
  filter by and hunt from. When I catch the shiny, the slot becomes shiny and the regular one
  is history.
- **Enter it fast.** I will be entering hundreds of these from HOME, box by box. Tapping into
  each slot's detail one at a time is too slow; this needs a bulk path.

## What already exists

Read it before proposing anything.

- `CatchRecord` (`:core:model`) is keyed by `CatchKey(variantId, copyIndex)` and has
  `caught: Boolean`, origin game, caught-at, notes, priority, `favourite` (unused) and `updatedAt`.
  `user.db` is v4. `statusOf` derives `Caught / Needed / NoShinyExists / Unavailable`. Progress
  is derived on read (`progressOf`), never stored.
- **Backups** are one JSON file (ADR 0007). `schema` is bumped only for breaking changes, a
  newer schema is refused outright, unknown keys are ignored, and records are sorted so two
  exports of the same data are identical. `BackupWriter` keeps a high-water mark by catch
  count and refuses to back up an empty database.
- **Sprites** are shiny only: 1387 × ~11 KB WebP, about 15 MB of a 30 MB APK budget.
  `docs/adr/0005-sprites.md` records where they come from and under what terms.
- **The grid.** An uncaught sprite is a flat silhouette and a caught one is full colour; that is
  what keeps 1394 slots from reading as a contact sheet. Gold is the caught rim and pip, the
  progress numerals and arcs, and the catch celebration, nothing else. `SlotState` has
  `Empty, Needed, Caught, ShinyLocked, Unavailable`. The settle-frame work in §8 is why
  nothing new may be per tile.
- **The hunt list** ranks needed slots (ADR 0012); a regular catch does not make a slot less
  needed.

## Think about these before you propose anything

Give me a recommendation and the trade-off for each. I approve quickly and override only the
ones I care about.

- **The record's shape.** A second boolean (`regular`), or `caught` replaced by an ownership
  state (`None / Regular / Shiny`)? One slot holds one Pokémon in HOME, so owning both at once
  means the regular one leaves. What does the migration write for existing rows (every current
  `caught = true` is a shiny), and what does "forget" do now?
- **Backups.** Is a regular mark additive (unknown key, no bump), or does it change what
  `caught` means and so need a bump? Think about the direction that loses data: a backup with
  regular marks restored by an older build. Think about the high-water mark too: does a regular
  catch count towards "the file with the most catches"? It must never make a real shiny
  backup look smaller and get pruned.
- **How a regular slot looks.** Regular sprites are not bundled. Options, at least:
  - add them: about 15 MB more, over the APK budget, and a sprite-source question for ADR 0005;
  - keep the silhouette, but mark the slot as owned in a way that is not gold and not a
    second rim colour that reads as "caught";
  - show the shiny sprite in some treated form: say why it would or would not mislead.

  Pick one. Show it in the gallery beside the other states, check contrast in both themes, and
  make sure the grid still reads as "quiet until filled".
- **TalkBack.** A tile now has three owned states. Write the sentences ("Bulbasaur, regular
  caught, shiny still needed"?) and keep them distinct, as `AccessibilityTest` requires today.
- **Progress.** Where the living-dex number goes, whether each box header shows it, and whether
  the Progress screen splits every count in two or keeps shiny as the only breakdown. Regular
  numbers are never gold.
- **Bulk entry.** The fastest safe way to mark thirty slots regular: a select mode in the box
  grid, a "mark this box" action, or multi-select in search results. The grid path must not
  add per-tile state to the pager (§8), so explain how selection avoids it. There must be an
  undo, because the same gesture on the wrong box is thirty wrong records.
- **Filters and hunting.** A caught filter that gains "Regular only" / "Upgrade". Whether the hunt
  list marks upgrades, or ranks them differently: say whether an upgrade should rank higher
  (it finishes nothing new) or lower, against ADR 0012's fixed order, and whether that needs an
  ADR amendment.
- **Catch details for regular catches.** Origin game, date and notes for a regular one: kept,
  and what happens to them when the shiny arrives.

## Ideas of my own, for you to judge

- **An "upgrade" celebration.** When a regular slot turns shiny, the catch celebration plays as
  it does today; it is the moment the app exists for.
- **HOME entry by box.** Mirror HOME's own flow: pick a box, tap the slots you have, done. That
  may be the bulk path above.
- **Progress per game counts regular catches too,** so prompt 7's "here first" can say "12 of
  these you already have in regular".

## Scope

In priority order:

1. The record's shape, the `user.db` migration and its `MigrationTestHelper` test, and the
   repository and mapper changes. Prove the migration on `net.pokedex.debug` with a copy of
   real-shaped data before anything else is built on it.
2. Backup encode and decode, with the answer to "older build restores a newer file" tested in
   `:core:model`, and the high-water mark rule tested.
3. Status and progress derivation in `:core:model`, with JVM tests: shiny progress unchanged by
   regular catches, living-dex progress, upgrade.
4. The slot state in `:design-system`: gallery, contrast pairs, TalkBack sentences, screenshots
   from the workflow.
5. Slot detail (regular / shiny / none), then the bulk entry path.
6. Filters, and the hunt list, as recommended.
7. Profile the new paths and re-measure the pager (`measure-pager.sh`, default and `TIGHT=1`).

## Constraints

- **My phone holds my real records, and this milestone migrates them.** Nothing runs against
  `net.pokedex` except `installRelease`, and only after the migration has been proven on
  `net.pokedex.debug` and a fresh backup of the real install exists outside the app (the SAF
  backup folder). Tell me before the real install is upgraded, so I can take my own copy.
- Never `fallbackToDestructiveMigration` on `UserDatabase`. Never key on slot position. Never
  store a progress counter.
- Gold means shiny and nothing else. A regular catch gets no gold anywhere: not the rim, not the
  pip, not a numeral.
- Nothing new is per tile in the pager, and the pager's P99 must not get worse.
- APK budget 30 MB; if you recommend regular sprites, show the measured size.

## How to run the session

1. **Research, then stop.** Read the code above, look at the grid on the phone and emulator, and
   measure what the sprite option you favour costs. Present the questions with a recommendation
   each, and a build order. Stop for my approval.
2. **Build** in the approved order. Install and look on the phone (debug build) and emulator
   before saying anything works, in both themes and at 200% text.
3. **Close out.** Update ADR 0007 (backup format), `docs/architecture.md` (§2 if the record's
   meaning changes, §6 and §8) and `docs/design-system.md` for the new state, and write an ADR
   for the record's shape.

Every commit builds: `./gradlew test detekt lintDebug :design-system:designCheck assembleDebug`.
One logical change per commit, imperative subject under 72 characters, no type prefixes.
