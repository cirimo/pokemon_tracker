# 0001 — Catch records are keyed by (variantId, copyIndex)

Status: accepted, 2026-09-22

## Context

The PokéPC preset is an ordered array of 52 boxes, each an array holding a variant id
string or `null`. A slot has no id of its own. Positions move whenever upstream inserts
a form anywhere earlier in the list, and upstream revises the preset.

Measured on tag 6.8.2: 1394 filled slots resolve to 1387 distinct variants. Seven
variants are demanded twice -- `unown`, `vivillon`, `flabebe`, `floette`, `florges`,
`furfrou` and `alcremie` each appear in their generation box and again in a dedicated
form box. A living dex requires owning two of each.

Catch records must survive a preset revision. They are the only data in the app that
cannot be regenerated.

## Options

**A. Key on (presetId, boxIndex, slotIndex).** Matches the UI exactly and makes the box
grid a trivial query. One inserted form upstream shifts every later position, silently
reassigning records to the wrong Pokemon. Not recoverable after the fact, because
nothing records what the old position meant.

**B. Key on variantId alone.** Stable and simple. Cannot represent owning two Unown-A,
so either the two duplicate slots share one record (progress reads 1387 while the grid
shows 1394) or the seventh duplicate is quietly dropped.

**C. Key on (variantId, copyIndex).** copyIndex is the ordinal among repeated
occurrences in preset order, assigned by the pipeline and stored on the slot row.

## Decision

**C.** A catch is a fact about a Pokemon owned; a preset is a view over those facts. The
record holds no reference to a preset, box or slot, and a slot *resolves to* a record key.

Reference rows also carry upstream's `nid` (`"0003-f"`), so a rename upstream is
detectable as *same nid, new id* and can be remapped rather than orphaning a record.

## Consequences

- A slot moving, or 1300 slots shifting, affects nothing.
- A variant dropped upstream leaves an orphaned record, which is **kept** and surfaced
  rather than deleted. You still own the Pokemon.
- Multiple presets coexist with no duplication: same records, different view.
- Progress must count slots, not distinct variants, and must look records up by key.
  `progressOf` takes a `Map<CatchKey, CatchRecord>` for exactly this reason.
- copyIndex must be gapless per variant or a slot resolves to a key nothing can fill.
  A pipeline validator asserts that, and asserts the duplicate count is exactly 7.
- Two implementations of the copyIndex assignment exist -- `assignCopyIndices` in
  `:core:model` and `buildSlotRows` in the pipeline -- and both are tested against the
  same cases. They must not diverge.
