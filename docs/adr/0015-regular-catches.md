# 0015 — A regular catch is a second fact beside the shiny, and `caught` keeps meaning shiny

Status: accepted, 2026-09-30

## Context

Until prompt 8 a slot was caught shiny or not. Many of my HOME boxes hold the regular
Pokémon in a slot whose shiny I do not have yet, and I want that recorded: seen in the grid,
counted as a living dex beside the shiny one, filterable as "the shiny would be an upgrade",
and entered in bulk, because there are hundreds of them.

One HOME slot holds one Pokémon. When the shiny arrives, the regular one leaves.

The data this changes is the only data the app cannot regenerate, and the real install
holds 466 records. Whatever shape is chosen has to migrate them without rewriting any.

## Decisions

### `caught` keeps its meaning; `regular` is a new column

`catch_record.regular INTEGER NOT NULL DEFAULT 0`, user.db version 6, `MIGRATION_5_6`, with
a `MigrationTestHelper` test that checks every column of every row survives. Every existing
row gets 0, which is right rather than merely safe: until now the app recorded only shinies.

In `:core:model`, `CatchRecord` gains `regular: Boolean` and a derived
`ownership: Ownership` (`None / Regular / Shiny`), shiny winning. Screens read ownership.

*Rejected: replace `caught` with an ownership column.* The cleaner shape on paper. But
SQLite on minSdk 26 cannot drop or rename a column, so it means rebuilding `catch_record`
on the real records, and the backup key `caught` would change meaning, which is a breaking
change to the file (below). The two-column shape migrates with one `ALTER TABLE`, and one
word keeps one meaning in the record, the column and the backup.

*Rejected: renaming `caught` to `shiny` in the domain only.* It would make one fact three
names across the mapper and the backup, for a rename the compiler cannot check in JSON.

### The regular mark is kept under a shiny

Catching the shiny of a regular slot does not clear `regular`. The slot reads as shiny
everywhere, since shiny wins, and the regular one is history in every count and picture.
The bit is kept for one reason: the catch toggle's rule since M3 is that an accidental untick
loses nothing, and without it, unticking a mis-tapped upgrade would drop the slot to nothing.
With it, the regular comes back. Slot detail hides the regular switch while the shiny is
caught, so the bit cannot be edited where it cannot be seen.

### Regular marks record no game or date

`originGameId` and `caughtAt` stay the shiny's. A regular mark is entered from HOME in bulk,
and nobody records where a regular came from. When the shiny arrives it gets the usual
prefill and today's date, as a fresh catch would, so "recent catches" and "caught in" keep
meaning shiny. Notes are the slot's, as before.

### Forget keeps a regular mark

"Forget these details" deleted the record, and its dialog promises only the game, date and
notes. On a regular slot that would have taken the mark too. `forgotten` in `:core:model` keeps
the mark and drops everything else; the regular switch is how a mark comes off.

### Status and ownership are separate questions

`statusOf` and `SlotStatus` stay about the shiny: needed, caught, no shiny exists,
unavailable. `ownershipOf` is its own function. Shiny progress, the hunt list and "needed"
read only status, so a regular catch cannot move them by construction; the JVM tests assert
it anyway. The grid draws `Regular` when the regular one is held and the shiny is not,
whatever the shiny's status, because what a slot holds is what the grid is for.

`livingProgressOf` counts slots holding anything. It is derived on read like every count.

### Upgrades are said, not ranked

The hunt list's fixed order (ADR 0012) is unchanged. A row says how many of its slots are
held in regular, and the farm plan on Progress says how many of each game's slots are.

*Rejected: rank upgrades higher.* An upgrade adds nothing to the living dex, so by that
measure it is worth less, not more. *Rejected: rank them lower.* The shiny is the same shiny
either way, and ADR 0012's order is about the shiny. Either rule would add a second goal to
an order built to answer one question. Priority is how I say "these first".

### Bulk entry is mark mode, and its undo is a ledger

In the box view, "Mark regulars" switches what a tap does: it marks or unmarks the regular
one and writes at once, instead of opening the slot. "Mark all" marks every empty slot of the
box. A tap never takes a shiny off. Undo returns every slot the session touched to the value
it had when the session first touched it (`MarkLedger`, JVM-tested); a slot tapped back to where
it started drops out of the count.

*Rejected: a selection, then "apply".* Selection is state per tile, and the pager must not
gain any (`docs/architecture.md` §8): a thirty-way selection in the pager's pages would be the
per-tile registration §8 took out. Mark mode needs none. The switch is one screen-level value,
and a tile changes the way it does for a catch, through its record.

*Rejected: multi-select in search results.* Search is not where HOME is organised, and the
point is to mirror HOME box by box.

The ledger lives in memory. After process death every mark is already saved; what is gone is
only the way to take a session back as one.

## Consequences

- Backups carry `regular` additively, with no schema bump (ADR 0007).
- The high-water mark still counts shinies only. Regular marks are protected by the rolling
  set and the pre-import snapshots, not by it (ADR 0007).
- A regular record with no slot is an orphan and is kept; a preset change that removes a
  slot held in regular is reported as a loss, as a shiny one is.
- The design system gains a sixth slot state (`docs/design-system.md`).
