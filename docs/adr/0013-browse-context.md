# 0013 — Slot detail pages through the list it came from, asked again from the route

Status: accepted, 2026-09-25

## Context

Slot detail showed one slot. Reaching the next meant going back to the box, the search
list or the hunt list and tapping again. For a box of thirty that is sixty taps. Prompt 6
asked for a left and right swipe, and for "next" to mean what the list it came from means:
the next slot in a box (holes skipped), the next search result under the filter that was
active, and possibly the next hunt.

`SlotDetailRoute(variantId, copyIndex)` knew nothing about where it was opened from. The
detail therefore has to learn the list somehow, and that list can be up to 1394 slots long.

## Decisions

### The route carries the question, not the answer

`SlotDetailRoute` gains `browse: String?`, an encoded `Browse` from `:core:model`:
`Box(boxIndex)`, `Search(filter)` or `Hunt(gameId?)`. The detail's ViewModel passes it to
`browseKeys`, which calls the same function that drew the list: `dex.layout`, `searchDex` or
`huntPlan`. Pure functions of the same inputs return the same list, so it is the list the
user was looking at.

*Rejected: the keys in the route.* 1394 keys in a navigation argument, in a saved-state
bundle and in the back stack, to say something the route can say in thirty bytes.

*Rejected: a shared in-memory holder that the list fills and the detail reads.* It shows
exactly the list on screen, but only until the process dies. After that the detail comes
back with no list, or with whatever an unrelated screen last left in it. Both the route and
the ViewModel's saved state survive process death; a singleton does not. It would also be
the first place two features shared mutable state.

### The list is frozen when the detail opens

The ViewModel asks once, with the records as they are then, and keeps the answer. Marking a
slot caught under a "needed" search leaves it where it was, now caught, and the page on
screen never jumps. Going back to the list shows the live results, where it has gone.

The one thing asking again can get wrong is the page on screen. After process death the list
is recomputed, and by then that slot may have been caught out of it. So `browseKeys` always
places the slot it is told to keep: search treats it as passing the refinements (it must
still match the text, which no record changes), and the hunt plan ranks it as if it were
still needed, keeping its priority. Other slots caught meanwhile do drop out. A slot the list
cannot place at all, for example one a newer dataset dropped, is shown on its own.

### A pager inside one destination, not a route per swipe

The detail is a `HorizontalPager` over the frozen keys, with `beyondViewportPageCount = 0`.
The ViewModel builds the slot on screen and its two neighbours, so a neighbour a drag
reveals has its data ready. The page on screen is in the ViewModel's `SavedStateHandle`,
because after a swipe it is no longer the route's slot.

*Rejected: navigating to a new route on each swipe.* Every swipe would re-run the enter
transition, grow or churn the back stack, and leave back ambiguous.

### Back goes home to the slot you ended on

As each page settles, the detail leaves its key on the entry that opened it (`browsedTo`,
the same mechanism as "Show in box"). The box view uses it as the shared element's origin
on the first frame back, so the sprite flies to the tile you ended on and not the one you
tapped. Search and the hunt list scroll that row into view if it is not visible. From a box,
browsing stops at the box's ends, so back never lands on a different box.

## Consequences

- From a box, from search and from the hunt list, the detail pages. Progress's recent
  catches, the species page and "Needed N times" open a slot on its own, with no stepper,
  exactly as before.
- A `Stepper` under the title ("Kanto 1 · 3 of 30") is the visible sign that the detail pages,
  and how TalkBack moves: its chevrons name the neighbour.
- The box pager gains one screen-level value and nothing per tile. The settle-frame work of
  docs/architecture.md §8 is untouched.
- A context an older build wrote that this one cannot decode is ignored, and the detail opens
  on its slot alone.
