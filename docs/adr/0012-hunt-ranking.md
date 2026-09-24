# 0012 — The hunt list is a fixed, explained ranking of hunts, not slots

Status: accepted, 2026-09-24

## Context

M4 answers "what should I hunt next, in which game, and how". The inputs are uneven. Upstream
answers completely which games offer each variant, and which of those can be shiny. The
curated layer answers "how" and "at what odds" for almost nothing yet: at the start of M4,
four encounter rows for one game. Most rows of any list will have no method.

One person uses the app. Whatever the order is, it has to be one that person trusts at a
glance, because they will act on the top row the same evening.

## Decisions

### A row is a hunt, not a slot

A hunt is the needed slots of one species that one of my games offers shiny, with each
regional form split out (`HuntKey(dexNum, regionalForm)`). Male and female Meowth, or 28
Unown, come from the same place; a Galarian Meowth does not. A duplicated variant contributes
both copies, and catching one leaves a hunt for the other.

*Rejected: one row per slot.* 1394 rows, with Unown alone taking 29 of them, and the fact
that one trip fills several is invisible.

*Rejected: whole evolution families.* A shiny Pichu does not make three slots; each stage is
its own Pokemon to own.

### The order is fixed, and every row says why it is there

1. **Priority**: want, normal, later. The only input that is the user's own judgement, so
   nothing outranks it.
2. **A method is recorded for one of my games.** Known is actionable tonight; unknown needs
   research first. This also keeps the rows with something to say at the top of a list where
   most rows have nothing.
3. **More slots from one hunt.**
4. **Fewer needed slots left in its box**, so a nearly done box gets finished.
5. **Preset order**, so ties are stable and the list reads like the boxes.

Odds are shown and choose between methods for a hunt, but do not rank hunts against each
other. A Legends Arceus outbreak is 1 in 128 and a Dynamax Adventure 1 in 100, but whether
either is worth tonight depends on things the app does not know.

*Rejected: a configurable ranking.* Weights on a settings screen for one user is a feature
built to avoid a decision. *Rejected: a single score.* A weighted sum cannot say why a row is
first; a lexicographic order can, in one short line.

### Priority is three buckets, stored in the existing Int

`1` want, `0` normal, `-1` later (`Priority.of` reads any positive as want, any negative as
later). `0` was already the default and the field already said "higher sorts earlier", so no
migration and no backup change.

*Rejected: a free order, dragged.* Dragging one row among 1394 rewrites every row between the
two positions. Each rewrite bumps `updatedAt`, triggers a backup and becomes a merge conflict
on the next restore, all to express an order the user does not actually hold beyond "these
first".

`favourite` stays in the record and the backup and is not used. Nothing in M3 gave it a
meaning, and the buckets cover what it might have meant.

### Odds are exact, and absent when not curated

n rolls at 1/4096 is shown as `1 - (4095/4096)^n`, which is what Bulbapedia's tables print
(1/1365.67 for the Shiny Charm, 1/128.49 for a fully set up Legends Arceus outbreak). The
M4 plan proposed the `4096/n` shortcut as "the published figure"; reading the tables the
curated rows cite showed the reverse, so the app matches its sources.

A method with no curated modifiers has no odds. Showing 1/4096 there would be a claim the
data does not make.

## Consequences

- `huntPlan` in `:core:model` derives the whole list on read from the dex, the records, my
  games and the curated guide. Nothing is stored, per CLAUDE.md.
- Needed slots none of my games offers shiny are listed last with a reason (other games,
  shiny-locked everywhere, event only, transfer only), never dropped.
- As curation fills a game, its hunts rise within their priority bucket on their own. That
  is intended: the list gets more useful exactly as fast as the data does.
