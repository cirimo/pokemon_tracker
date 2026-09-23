# Prompt 3 — M2, "See my dex"

> Run this in a fresh Claude Code session at the repo root, in **plan mode**.
> Read `docs/00-big-picture.md`, `docs/architecture.md`, `CLAUDE.md` and — before you write
> a single line of UI — **`docs/design-usage.md`**. That last one is enforced by the build,
> not by review.

---

You are the feature engineer on a **shiny living dex tracker**: an Android app for one
person (me) that mirrors my Pokémon HOME boxes and tells me what I still need. Read
`docs/00-big-picture.md` for the product context and the locked decisions. Treat those
decisions as constraints rather than suggestions, and tell me directly if one of them is
wrong instead of quietly working around it.

M0 built the skeleton. M1 built the design language and the component library. **This
session builds the first real screens.**

## What already exists

You are not starting from scratch, and most of your job is assembly rather than invention.

**Data.** `:core:data` exposes three repositories, already wired through Hilt and already
returning `:core:model` domain types:

- `ReferenceRepository` — `presets()`, `preset(id)`, `boxes(presetId)`, `slots(presetId)`,
  `variants()`, `variant(id)`, `games()`, `datasetMeta()`, `integrity(presetId)`. All
  `suspend`, all returning `Outcome<T>`.
- `CatchRepository` — `observeRecords(): Flow<Map<CatchKey, CatchRecord>>`, plus
  `setCaught(...)`, `update(...)`, `record(key)`.
- `SettingsRepository` — the active preset and the last-seen dataset/preset versions.

The shipped `reference.db` has 52 boxes, 1394 filled slots and 1387 variants. Shiny sprites
for all 1387 are bundled as 256px WebP under the app's assets.

**Domain logic.** `:core:model` already has `progressOf`, `progressByBox`,
`orphanedRecords` and `PresetDiff`. Progress is **derived on read, never stored** — see
CLAUDE.md on why. If you need new derivation logic, it goes here as pure JVM code with JVM
tests, not into a ViewModel.

**Design system.** `:design-system` has the theme and ~15 components, all of them built for
exactly this milestone: `BoxSlot`, `BoxGrid`, `BoxHeader`, `BoxPager`, `BoxSummary`,
`CaseSurface`, `CaughtToggle`, `UndoBar`, `SpeciesCard`, `SpeciesHeader`, `ProgressRing`,
`ProgressBar`, `ProgressReadout`, `StatTile`, `TypeBadge`, `GameBadge`, `MethodBadge`,
`FilterChipRow`, `SearchField`, `SortControl`, `EmptyState`, `ErrorState`, `LoadingState`,
`PokedexBottomSheet`, `PokedexDialog`, and the `slotSharedElement*` helpers.

**Run the gallery before you design anything.** Debug builds install a second launcher icon;
it shows every component in every state with theme and font-scale toggles. Knowing what
exists is the difference between assembling this milestone and rebuilding it.

**Navigation.** `PokedexNavHost` in `:app` assembles the graph; each feature contributes a
`NavGraphBuilder` extension, which is what lets features link to each other without
depending on each other. The intended shape is already written down in that file:

```
Boxes (start) -> SlotDetail(key) -> VariantDetail(variantId)
Search
Settings -> BackupRestore
```

`:feature:dex` currently contains only `SmokeScreen`, which exists to prove the stack works
end to end. **Delete it in this session** once a real screen replaces it.

## Scope

Three of the app's questions are in scope. One is not.

**In scope — "What do I still need?" and "Where can I get it?":**

1. **Box view.** The 52 boxes as a pager, mirroring HOME. Per-box progress, overall
   progress, and the box overview for jumping around.
2. **Slot detail.** Open a slot: which variant it demands, its sprite large, its types,
   which of my games have it, whether it is shiny-locked, and the caught toggle.
3. **Species / form reference.** The variant's forms, what it evolves from and under what
   condition, and where it sits in the dex.
4. **Search and filter.** Across 1394 slots: by name, by dex number, by type, by game, by
   caught state, by shiny-locked. This is the quality-of-life load and it deserves real
   attention.

**Out of scope, and do not drift into it:**

- Marking things caught is *v2 of this milestone* — M3. `CaughtToggle` should work in this
  session because a read-only dex is useless to test with, but **origin game, notes,
  favourites, priority, progress dashboards and backup/restore are M3**, not this one.
- The hunt engine, counters, odds and session history are M5. Not now.

If you find yourself designing a screen that answers "how do I catch it?", stop: that is M4.

## Think about these before you propose anything

I want your read on the product questions, not just the technical ones.

1. **What is the start destination?** The box pager is the obvious answer and may be the
   wrong one. When I open this app I am usually mid-hunt and want one specific thing. Make
   the case either way.
2. **How does search relate to the boxes?** A separate destination, a mode the box screen
   enters, or an overlay? 1394 slots is small enough to search in memory and large enough
   that the interaction matters.
3. **What does a slot detail show when the variant appears in two slots?** Seven variants
   are duplicated in `grouped-balanced` — the same `VariantId` with a different
   `copyIndex`. The data model handles it; the UI has to say something honest about it.
4. **What happens to a slot the preset leaves empty?** There are ten interior holes.
5. **Filters: how many, and are they composable?** Five filters that combine is a different
   product from five filters that replace each other. I have opinions; ask me.
6. **State restoration.** If I am on box 37 with three filters active and the process dies,
   what comes back?

Report your thinking and ask me the product questions **before** you build. Where a decision
is genuinely close, give me the trade-off and a recommendation rather than picking silently.

## Constraints that are not negotiable

These are all enforced by the build, so you will find out anyway — but knowing them up
front is cheaper than finding out.

- **`docs/design-usage.md` is law.** No raw `Color(...)`, no raw `dp`/`sp`, no ad-hoc
  `TextStyle`, no importing `Icons` directly in `feature/` code.
  `config/detekt/feature-rules.yml` fails the build. If you need something the design system
  lacks, add it **to `:design-system`** with a gallery entry and a screenshot, in its own
  commit — do not inline it in a feature.
- **The layering rule**: `ui -> viewmodel -> repository -> dao`, dependencies one way only.
  ViewModels expose one `@Immutable` state class and take one sealed event type. Room
  entities never leave `:core:data`.
- **No feature module may depend on another feature module.** CI enforces it.
- **Never key anything on slot position.** A catch record is `(variantId, copyIndex)`.
  `docs/adr/0001-slot-identity.md`.
- **Never store a progress counter.** Derive it.
- **No network calls at runtime.** The app has no `INTERNET` permission.
- **Sprites load through Coil** from the bundled assets, and obey `SlotSpriteRendering`:
  uncaught renders as a flat silhouette, caught in full colour. That rule is why a box of
  thirty tiles does not read as a contact sheet — do not reinterpret it.

## Performance

The box grid is the thing people will judge. `docs/architecture.md` set budgets; hold to
them and measure rather than assert.

- Scrolling the box pager holds 60fps on a mid-range device. At most two boxes composed.
- Search results update without a visible stall as I type. 1394 slots fits in memory; the
  question is what you do on which dispatcher, not whether to paginate.
- Cold start to a usable box view, with the dataset read, stays inside the budget.
- Sprite loading does not jank the grid. Coil is configured; check what it is doing before
  adding caching of your own.

If you propose something you cannot ship at 60fps, say so and propose something else.

## Accessibility

Non-negotiable, and already gated for the components — but a *screen* can undo that.

- Every interactive element has a meaningful TalkBack description. Slots already do; your
  screens must not flatten or clobber them.
- Status never conveyed by colour alone.
- The layout survives 200% font scale. The grid is safe because tiles hold no text; your
  headers, filter rows and detail screens are the risk.
- Honour reduce-motion via `PokedexTheme.motion` rather than hand-rolling animations.
- Minimum 48dp touch targets.

## Testing

Per CLAUDE.md: test what can silently lose or corrupt data, skip what a compiler or
generator already guarantees.

Worth testing here: slot-to-record resolution including `copyIndex`, the filter and search
predicates (in `:core:model`, as pure functions, with JVM tests), progress derivation across
a box and across the preset, and how stranded records surface. Not worth testing: ViewModel
mapping with no branches, or layout a screenshot already covers.

Prefer moving logic into `:core:model` over testing it through a ViewModel.

## Deliverables

1. The screens, in `:feature:dex` (and a second feature module **only** if you can justify
   it — four meaningful modules beat twelve ceremonial ones).
2. Navigation wired into `PokedexNavHost`, with the shared-element transition from slot to
   detail using `slotSharedElementKey`.
3. `SmokeScreen` deleted.
4. Any new domain logic in `:core:model`, with JVM tests.
5. Any new component in `:design-system`, with a gallery entry and a screenshot test, in its
   own commit.
6. A short `docs/` note only if you made a decision a future session would otherwise have to
   rediscover. An ADR if you reversed something.
7. Everything green: `./gradlew test detekt lintDebug :design-system:designCheck assembleDebug`.

## How to work

- **Plan mode first.** Research the existing code, run the gallery, present your reading of
  the product questions above, and **stop for my approval** before building.
- Ask me questions whenever a decision depends on how I actually play or how I want to use
  the app. Don't guess at product behaviour — I hunt these games and I will have an opinion.
- **Install it on the emulator and look at it** before telling me it works. M1 shipped a
  component that passed 26 screenshot tests and crashed the moment it went into a real
  scrolling screen; screenshots with bounded height are not a substitute for running the
  thing.
- Flag anything where you think I've chosen wrong.
- Commits: imperative subject under 72 characters, no type prefixes, one logical change
  each, and every commit builds.
