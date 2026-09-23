# 0010 — Search is a mode of the box screen, not a destination

Status: accepted, 2026-09-23. Amends the graph in `0006-navigation.md`.

## Context

The M0 graph drew Search as its own top-level destination beside Boxes. M2 built the
screens and had to decide what search is *for* in this app, and it is mostly one thing:
mid-hunt, find the one slot I care about, look at it, and get back to the box I was on.

## Options

**A separate destination.** Clean in the graph, and search gets its own back stack entry.
But leaving it discards the context you came from, "Show in box" becomes a cross-
destination navigation with arguments, and a filter configured there has nowhere to go
when you return to the boxes.

**An overlay above the pager.** Keeps the box underneath, but an overlay with its own
list, sheet, keyboard and back handling is a destination in everything but name, without
the saved-state and back-stack support a destination gets for free.

**A mode of the box screen.** The search field sits above the pager; focusing it swaps the
pager for results. The pager's state is owned above that switch, so closing search returns
to the same box. Search state lives in the box screen's ViewModel and SavedStateHandle.

## Decision

**A mode of the box screen.** Graph:

```
Boxes (start; search is a mode) ──→ SlotDetail(variantId, copyIndex) ──→ VariantDetail(variantId)
                                         └── "Show in box" pops back to Boxes on that box
Settings ──→ BackupRestore   (M3)
```

## Consequences

- One ViewModel owns the pager, search mode, the query and the filter, and one
  SavedStateHandle restores all of it after process death.
- "Show in box" is a result left on the Boxes back stack entry, then a pop. The entry's
  `savedStateHandle` is **not** the handle Hilt injects into that entry's ViewModel, so the
  destination forwards the result to the ViewModel as an event. Found by running it:
  written the other way it silently did nothing.
- A jump must close search *before* scrolling. `PagerState.scrollToPage` waits for the
  pager's first layout, and the pager is not laid out while search is showing.
- Search has no deep link of its own. For one person on one phone, that costs nothing.
