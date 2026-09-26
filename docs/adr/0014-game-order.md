# 0014 — My games have a farm order, stored as a rank; where a slot is farmed is derived

Status: accepted, 2026-09-26

## Context

I farm game by game, in an order set by how easy each game makes it: Legends Z-A, then
Legends Arceus, then Violet, then the rest. The question I keep asking is not "where can I
get this shiny", which the app already answered, but "which of the slots I still need should
I farm in this game". That has two halves:

- **Only here**: among my games, only this one has it shiny.
- **Here first**: walking my games in order, this is the first that has it shiny. Anything
  Arceus can give me is an Arceus job, even if Violet has it too.

M4's `my_game` table was a set. It had no order to walk.

## Decisions

### The order is a rank column on `my_game`

`my_game.farmOrder INTEGER NOT NULL DEFAULT 0`, user.db version 5, with `MIGRATION_4_5` and a
`MigrationTestHelper` test. Lowest first. A newly ticked game is inserted at the end.

Ties are allowed and read in release order, which only the reference data knows, so the
order is resolved in `:core:model` (`farmOrderOf`), not in SQL. That is also the migration's
answer: every game chosen before the order existed gets 0, and they read in release order
until one is moved. No migration has to know the game list, which lives in the other
database file (ADR 0002).

*Rejected: a list of game ids in `user_settings`.* It is a second record of which games I
own, and unticking a game would have to remember to edit it. With a column, unticking a game
deletes its place with it.

A move writes the whole resolved order, not a swap of two ranks: before anyone has moved a
game every rank is 0, and only the resolved order says which two games are trading places.

### Set by buttons, not by dragging

"Farm order" on My games lists my games with a button to move each one earlier or later.
ADR 0012 rejected dragging for priority because moving one of 1394 rows rewrites every row
in between, each rewrite a record change, a backup and a merge conflict. None of that
applies to at most ten games that are not records. Buttons still win: no library, nothing to
discover by long-pressing, and TalkBack moves a game with the same two actions a finger does.

### The backup carries it as a key of its own

`settings.gameOrder`, additive, so the backup schema stays 1 (ADR 0007). Not `myGames`
reordered: every file written before this has `myGames` sorted alphabetically, and a newer
build could not tell that from a chosen order. An older build ignores the key. A file without
it leaves the order on the device alone. Replace takes the file's order; merge keeps the order
on the device, which is the one in use, and appends games new to it in the file's order.

### Where a slot is farmed is derived, never stored

`FarmPlan(dex, order)` gives each slot the first of my games that has it shiny, and whether it
is the only one. It is a function of the dex and the order and of nothing I record: catching
a slot does not move it to another game, it only stops it being needed. So it is built when my
games or their order change, and a search keystroke pays one array read per slot for the farm
filter rather than a walk of my games.

Evolutions need no special case. Checked against the dataset rather than assumed: upstream
marks an evolved form obtainable in the games where it can be evolved and in no other (Kingambit
in Scarlet and Violet only by evolving Bisharp; Armarouge in Scarlet only although Charcadet is
in both; Wyrdeer transfer-only in Scarlet and Violet, where Stantler cannot evolve). Of 684
evolved variants, none is shiny-obtainable in a game where what it evolves from is shiny-locked.

"Only here" is among my games, per version. A Violet exclusive I cannot reach because I own
Scarlet is not a farm job anywhere; it stays where it was, unavailable in the grid and out of
reach in the hunt list, and My games says what an unowned game would add ("Would put N more in
reach").

*Rejected, for now: a per-slot "I will get this in <game>" override.* It would not break the
derived rule, since it is my judgement like priority, but it costs a column, a migration and a
backup field for a case not yet met. Want priority and the hunt list filtered to that game do
the job today. If it comes, it belongs on the catch record, keyed by `CatchKey`.

## Consequences

- Search has "Farm in" (one game, here first or only here), carried in `DexFilter.farm` with a
  default so older encoded filters still decode.
- The hunt list, filtered to one game, defaults to that game's "here first" share; it can show
  "only here" or everything the game offers, which was the old behaviour. `Browse.Hunt.scope`
  defaults to the old behaviour, so an older route still means what it meant.
- Slot detail says, under the name, which of my games to farm the slot in.
- Progress shows a farm plan: my games in order, each with its "here first" and "only here"
  counts, each opening search on exactly those slots. A game with nothing left first says it
  is done, calmly and not in gold.
- The box grid observes the set of my games only, so a reorder does not rebuild its tiles.
