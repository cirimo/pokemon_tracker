# Prompt 6 — Browse between slots, and icons that vanish in dark mode

> Run this in a fresh Claude Code session at the repo root, in **plan mode**.
> Read `CLAUDE.md`, `docs/architecture.md` (§6 "Navigation" and §8), `docs/adr/0006-navigation.md`,
> `docs/adr/0010-search-is-a-mode.md`, `docs/adr/0012-hunt-ranking.md` and, before any UI,
> **`docs/design-usage.md`**.

---

You are the feature engineer on the **shiny living dex tracker**: an Android app for one person
(me) that mirrors my Pokémon HOME boxes. M4 shipped on 2026-09-24. I have been using it on the
phone, and two things came out of that. One is a bug, one is a feature. The bug goes first.

## 1. Bug: the back and settings buttons are invisible in dark mode

In the dark theme, the back arrow on every pushed screen and the settings gear on the box view
cannot be seen. Everything else on those screens is fine.

**Likely cause, to confirm before changing anything:** `PokedexTheme` provides a Material
colour scheme, but nothing provides `LocalContentColor`. Material's default content colour is
black, and the app's screens draw their background with `Modifier.background` rather than a
`Surface`, which is what would normally set it. Every `Icon` without an explicit `tint` then
draws black: the back button in `ScreenScaffold` (`design-system/.../component/Screen.kt`),
the gear and "Close search" in `BoxesScreen`, and the filter button in `SearchContent`. Text
is unaffected because every `Text` passes a colour.

What I want:

- Confirm the cause on the phone and in the gallery, in both themes.
- Fix it once, in `:design-system`, so no feature can get it wrong again. That is not a
  `tint = ...` added at five call sites. Say which of the options (provide `LocalContentColor`
  in `PokedexTheme`, or give `ScreenScaffold` and the box view a real content colour) you pick
  and why, and check it does not change a colour anything else already relies on.
- **Say why no check caught this.** `ContrastTest` checks token pairs, not what a component
  actually draws, and as far as I know `ScreenScaffold` has no Roborazzi screenshot at all.
  Add the check that would have caught it: a dark-theme screenshot of `ScreenScaffold` and of
  the box view's top bar, and, if it is cheap, a test that every icon button in the gallery
  draws a colour that passes contrast against its background. Goldens are recorded by the
  `record screenshots` workflow, never locally.
- Look for any other untinted `Icon` or `IconButton` while you are there.

## 2. Feature: swipe left and right in slot detail

When I open a Pokémon's detail, I want to swipe left and right to the previous and next one,
without going back first. What "next" means depends on where I came from:

- **From a box:** the next slot in that box. Holes are skipped.
- **From search:** the next result in the search list, in the order it was shown, with the
  filter that was active.
- **From the hunt list:** your call. Tell me whether it should be the next hunt (with the
  current game filter) or nothing, and why.
- From anywhere else (Progress's recent catches, a species page, "Needed N times" copies):
  your call, and "no swiping there" is an acceptable answer.

### What already exists

Read it before proposing anything.

- `SlotDetailRoute(variantId, copyIndex)` is the only thing a detail screen knows. It carries
  no notion of where it was opened from.
- The box grid opens a slot through a shared-element transition, and only the tapped tile
  carries the origin (`BoxContent`'s `openedKey`, docs/architecture.md §8). The pager's settle
  frame was the M3/3c performance problem. Do not reintroduce per-tile work to make this
  feature possible.
- Search is a mode of the box screen, with its filter in the box screen's `SavedStateHandle`
  (ADR 0010). "Show in box" returns to the box screen through its back stack entry.
- `huntPlan` and `searchDex` are pure functions in `:core:model`: given the same inputs they
  return the same list.

### Think about these before you propose anything

Give me a recommendation and the trade-off for each. I approve quickly and override only the
ones I care about.

- **How the detail screen learns its list.** Passing 1394 keys through the route is wrong.
  Candidates: a small "browse context" in the route (box index, or the encoded search filter,
  or "hunt list with game X") from which the detail ViewModel recomputes the list with the
  same `:core:model` function, or a shared in-memory holder. Which one survives process death,
  and which one can show a list different from the one I was looking at?
- **Pager, or swipe gesture with a transition?** A `HorizontalPager` of whole detail pages is
  the obvious shape. Each page is a scrolling screen with a hero sprite, a catch toggle and a
  per-game section. What does it cost to compose a neighbour, and does
  `beyondViewportPageCount` stay at 0? A swipe that navigates to a new route is the
  alternative. Say which keeps back-navigation and the shared element sensible.
- **Back.** After swiping from Bulbasaur to Charmander and pressing back, where do I land? On
  the box, showing Charmander's box? On the search list, scrolled to Charmander? The shared
  element returns to the tile that opened the detail today. What should it do now?
- **The ends of the list.** Stop, or wrap around? Across boxes when opened from a box: does
  the last slot of Kanto 1 lead to the first of Kanto 2, or stop?
- **Duplicates.** The two Unown-A slots are different slots. Make sure swiping from a box
  never skips or merges them.
- **Discoverability and accessibility.** A swipe nobody knows about is a hidden feature. Is
  there a visible cue (a position line like "Kanto 1, 3 of 30", chevrons), and how does a
  TalkBack user move to the next slot? Touch targets and sentences, as usual.
- **What changes while I am swiping.** If I mark a slot caught and swipe on, does the list
  change under me? From search with a "needed" filter, a slot I just caught would drop out of
  the results. Recommend what happens, and never let the page I am on jump away.

## Scope

In priority order:

1. The dark-mode icon fix, with the missing check. Its own commit, before anything else.
2. Browse context in the route, and the list derived in `:core:model` with JVM tests: the next
   and previous slot for a box (holes, duplicates, box ends) and for search (same filter, same
   order).
3. The swipe in slot detail, from a box and from search.
4. From the hunt list and the other entry points, as you recommended.
5. The profile journey swipes in slot detail, then regenerate the profile on the phone (its
   own commit) and re-measure with `tools/perf/measure-pager.sh` and `TIGHT=1`.

**Also outstanding from M4:** the tight run after M4 (P99 14–15 ms) was taken straight after
the default run on a warm phone and is not attributed (§8, "Measured at M4"). Run `TIGHT=1`
alone on a cool phone early in this session, before any change, so there is a clean
baseline to compare against.

**Out of scope:** the hunt engine (M5). Swiping in the box pager itself, which already pages.

## Constraints

- The layering rule, module table and "never do these" in CLAUDE.md hold.
- **My phone holds my real records.** Nothing runs against `net.pokedex` except
  `installRelease`. Tests, experiments and profiles use `net.pokedex.debug` or
  `net.pokedex.profiling`. With the emulator and the phone both attached, pass
  `ANDROID_SERIAL` to every Gradle device task.
- Design: gold means shiny and nothing else. Borders, not elevation. Motion comes from
  `PokedexTheme.motion`, which already honours reduce-motion. A new or changed `:design-system`
  component gets a gallery entry, passes `:design-system:designCheck`, and has its screenshots
  recorded by the workflow.
- Performance: the box pager's P99 must not get worse. Nothing new is per tile, and nothing
  new registers with a scope wider than a page.

## How to run the session

1. **Research, then stop.** Confirm the dark-mode cause on the phone. Read the navigation and
   the detail screen. Then present the fix you want for item 1, the questions above with a
   recommendation each, and a build order. Stop for my approval.
2. **Build in the approved order.** Install and look on the phone (debug build) and emulator
   before saying anything works, in both themes and at 200% text.
3. **Close out.** Update `docs/architecture.md` (the navigation graph and §8) and write an ADR
   if the browse context is a decision with real alternatives. It probably is.

Every commit builds: `./gradlew test detekt lintDebug :design-system:designCheck assembleDebug`.
One logical change per commit, imperative subject under 72 characters, no type prefixes.
