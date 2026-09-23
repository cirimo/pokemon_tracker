# Design system

The language, the tokens, and why each one is what it is.

`docs/design-decisions.md` is the short, locked version written before any code existed.
This is the built version. Where they differ, one thing has changed and it has its own ADR:
`docs/adr/0009-light-theme.md`.

For the rules a *feature* session must follow, see [`design-usage.md`](design-usage.md).

---

## The direction: "Display case"

A warm near-black case with recessed slot wells and a hairline gold rim on the slots you
have filled. It reads as an **object** rather than a table — which is the entire point,
because the thing it replaces was a spreadsheet.

Restraint is the mechanism, not a constraint on it. One confident idea executed precisely
beats five effects competing, and in a collection app the idea has to survive being looked
at for hours.

Three rules outlive every value in this document:

1. **Gold means shiny, and nothing else.** It earns exactly three places: the caught slot
   rim and pip, progress numerals and arcs, and the catch celebration. Never backgrounds,
   navigation, headers or badges.
2. **Borders, not elevation, inside the grid.**
3. **No shader anywhere.**

No official Pokémon branding, logos or fonts.

---

## The decision that makes it work: the silhouette rule

An uncaught sprite renders as a **flat silhouette**. A caught one renders in **full shiny
colour**.

This is the single most consequential choice in the system, and it solves the hardest
visual problem the app has. Only shiny sprites are bundled (commit `97f6ac0`), so without
this rule every tile is a saturated 28dp thumbnail, thirty of them are on screen at once,
and a box reads as a contact sheet — the exact failure mode the brief called out.

With it:

- A box is quiet until you fill it. Colour arriving *is* the progress.
- You can read how a box is doing from across the room, before reading anything.
- Caught versus uncaught is unmistakable at grid density, without gold doing all the work.
- It costs one `ColorFilter.tint`. No layer, no shader, no per-tile allocation.

It also means the *container* carries the craft — the case edge, the box header, the
completion rule — while the tiles stay cheap. That is how comparable apps make dense grids
feel made rather than generated: decorating the tile is what produces a contact sheet.

---

## Colour

`theme/Color.kt`. Roles, not colour names: call sites ask for `rim` or `silhouette`, never
"the grey one". That is what makes a second theme possible without touching a component.

Every value was **solved for, not picked**. The rims, the accent and the silhouette are the
output of a WCAG contrast solve against the surface they actually sit on. Measured ratios
are in a comment beside each value and re-checked on every build.

### Dark

| Role | Value | Measured |
|---|---|---|
| `case` | `#121011` | — |
| `caseSurface` / `caseSurfaceHigh` | `#1A1718` / `#232021` | — |
| `slotWell` / `slotVoid` | `#0E0C0D` / `#151314` | — |
| `rim` | `#625F5C` | 3.07:1 on `slotWell` |
| `rimFocus` | `#807B78` | 4.53:1 on `case` |
| `rimCaught` | `#D8B26A` | 9.74:1 on `slotWell` |
| `accentGraphic` / `accentText` | `#D8B26A` | 8.90:1 / 9.47:1 |
| `progressTrack` | `#38342F` | arc reads 6.17:1 against it |
| `onCase` / `onCaseMuted` | `#EDE7E3` / `#A9A19E` | 15.47:1 / 7.47:1 |
| `silhouette` | `#6A6461` | 3.35:1 on `slotWell` |

The case is warm (`#121011`, not `#111111`) because a neutral near-black next to gold reads
as blue, and the direction depends on gold looking like metal rather than like yellow.

### Light

A real second palette: the case becomes paper and the wells become impressions. Two roles
genuinely invert rather than lighten — `accentText` drops to bronze `#6B4E0C`, and
`silhouette` goes *darker* than its surface.

`accentGraphic` `#7A5A10` and `accentText` `#6B4E0C` exist as two tokens **because of this
theme**. `#D8B26A` on paper is 1.9:1 and fails outright; in dark the two are the same
colour, so the split is invisible everywhere else. Full reasoning in
`docs/adr/0009-light-theme.md`.

### `progressTrack` — a bug worth recording

The track started life as `accentDim`, a muted gold. The first screenshots showed 0/29,
21/29 and 29/29 rendering as three identical gold circles: the arc was invisible against
its own track. It is now neutral, and "progress arc on its track" is a declared contrast
pair — the one a surface-only check would have missed, because an arc can be perfectly
compliant against the background and still unreadable against the track.

### Type colours — derived, not authored

`theme/TypeColors.kt`. Eighteen hues picked by eye and then contrast-checked is the trap:
three fail, you nudge those three, and they stop looking like a set.

Instead every swatch is one point in OKLCH with **lightness and chroma fixed and hue the
only variable**:

```
dark    container oklch(0.28, c × 0.45, h)    text oklch(0.86, c × 0.75, h)
light   container oklch(0.93, c × 0.30, h)    text oklch(0.44, c × 0.95, h)
```

Because OKLCH lightness is perceptually uniform, holding L constant holds contrast roughly
constant. Measured spread: **9.38–9.67:1 in dark, 6.09–6.67:1 in light** — eighteen hues
within a quarter of a stop of each other, all far past AA, by construction rather than by
luck. A nineteenth type can be added tomorrow without re-checking the other eighteen.

Per-type chroma is the one hand-set number. Normal, Dark and Steel are near-neutral on
purpose so the set does not read as a rainbow, and the container multipliers pull every
swatch far enough down in chroma that a row of badges sits quietly against the case — and,
more importantly, never competes with gold.

Values are computed once and checked in as hex with the OKLCH triple beside each. Runtime
does no colour maths. **Type colour is forbidden in the grid**; it lives on badges and the
detail screen, and `TypeBadge` always renders the type's name.

### Dynamic colour: off, permanently

`PokedexTheme` takes no `dynamicColor` parameter. There is nothing to opt into.

Gold has to keep meaning "shiny". Monet rotates the accent to whatever the wallpaper is, at
which point the one colour the whole system depends on means nothing. It would also make it
impossible to contrast-check eighteen type colours against an accent we do not control.

Accepted cost: the app does not match the system accent. It does follow the system's
light/dark setting.

---

## Typography

`theme/Type.kt`. Two families with a clear division of labour:

- **`PokedexSans` is Inter.** Neutral, high x-height, and — the reason it was chosen — it
  has real tabular figures rather than synthesised ones.
- **`PokedexNumerals` is Instrument Serif**, and appears in exactly two places: large
  progress numerals and box headers. That restraint is where the premium reads from. A
  serif everywhere is a costume; a serif on "412 / 1394" is a label on a case.

Both are bundled (no network at runtime), both are SIL OFL, and both are **subset**:

| File | Coverage | Size |
|---|---|---|
| `res/font/inter.ttf` | `opsz` pinned at 16, `wght` kept variable 100–900 | 876KB → **73KB** |
| `res/font/instrument_serif.ttf` | one weight, static | 70KB → **32KB** |

One variable file covers every Inter weight instead of three static ones at ~300KB each.
Variable-font settings need API 26, which is exactly minSdk, so there is no guard and no
fallback — deliberate, not an oversight. The `@OptIn(ExperimentalTextApi::class)` this needs
is contained to one private function; nothing else in the module and nothing in any feature
sees it. Licences and the subsetting recipe are in `design-system/licenses/`.

**Numerals.** Every number in this app is read in comparison to another number — counts
against totals, dex numbers down a column, odds against odds. Proportional figures make
those columns jitter, so `tnum` is on for every style that can hold a digit. This is most of
why a dense screen of numbers looks composed.

---

## Spacing, shape, elevation

`theme/Dimens.kt`. Every dp in the app comes from here, and
`config/detekt/feature-rules.yml` makes that a build failure rather than a request.

Slots are **8dp**, not pill-shaped — a departure from M3 Expressive's corner scale. A
display case has square-ish wells.

`touchTargetMin` is 48dp and `slotMinSize` is 44dp: six columns at 44dp plus gutters is
296dp, which fits the narrowest phone we support.

**Elevation has three tokens and they are all for sheets, dialogs and menus.** `Modifier.
shadow` forces a `graphicsLayer`, which is a render node per element; at thirty visible
tiles that is the frame budget. There is deliberately no elevation token a grid component
could reach for.

---

## Motion

`theme/Motion.kt`. The design choice that matters: `PokedexMotion` is handed to call sites
**already resolved for reduce-motion**. There is no `if (reduceMotion)` for a component to
forget. When `Settings.Global.ANIMATOR_DURATION_SCALE` is 0, every accessor returns `snap()`
or zero and every animation in the app stops, without a single component knowing why.

Springs for anything the finger drives — the one idea worth taking wholesale from M3
Expressive. Tweens for what is not interruptible. One easing curve (`CaseEasing`) for
everything that is not a spring, because one easing used consistently reads as more
considered than five used approximately.

---

## Signature moments

**Catch.** Local to the tile, under 300ms. The rim springs to gold, the pip scales in, and a
single glint runs once around the rim. This is the *only* shimmer in the app, and it is a
`Brush.sweepGradient` allocated once inside `drawWithCache` and then rotated — not a shader.
One haptic.

The hard constraint was that this runs four hundred times, so it is built out of what it
does not do: no overlay, no particles, no sound, no blocked input, nothing outside the
tile's own bounds. A celebration you can trigger twice in a second without wincing is the
only kind worth having.

**Box completion.** A gold rule draws itself left to right, once, over half a second. It
fires only on the *transition* into complete, never on arriving at an already-complete box —
otherwise swiping through a finished region would be a parade.

Both are draw-phase animations: the animated value is read inside the draw lambda, so
neither recomposes anything.

**Slot → detail.** One shared element on the sprite, keyed by `slotSharedElementKey`. The
key scheme lives in `:design-system` because a shared element whose two sides disagree
silently does nothing, which is the worst failure mode an animation can have. Compose's own
guidance is that shared elements are costly and the API is still experimental, so: exactly
one per navigation, on the sprite, and nothing else.

---

## Departures from Material 3

1. **No dynamic colour.** Covered above.
2. **Borders, not elevation, inside the grid.** Per-tile shadow is a render node per tile.
3. **Reject Expressive's saturated palette.** It reads as toy. The app is a case for a
   collection somebody has spent years on.
4. **Tighter corner scale.** Slots at 8dp, not pill.
5. **No ripple in the grid.** M3's ripple on a 28dp tile is mush. The press morphs the
   corner and scales the tile instead — which is itself an Expressive idea, implemented in
   our own tokens.
6. **`CaughtToggle` is a button, not a `Switch`.** A switch says "a setting changed"; this
   says "I caught one", which is an event. Events want a target you can hit without looking,
   so it is a full-width 56dp pill rather than a 32dp thumb on the right-hand edge.

### What we take from Expressive without taking Expressive

`material3` 1.5.0 is still alpha and its components sit behind
`@ExperimentalMaterial3ExpressiveApi`. We stay on stable 1.4.0 (see
`docs/adr/0008-toolchain-baseline.md`) and implement the good ideas as our own tokens:
spring-based motion, shape morph on press, and a visible focus ring.

---

## Performance constraints

Established before anything was designed, so nothing was designed that cannot ship:

- **No shader, ever.** `RuntimeShader`/`RenderEffect` costs more than a direct draw, and
  AGSL needs API 33 while minSdk is 26.
- **`drawWithCache`, never bare `drawBehind`**, for anything allocating a `Brush` or `Path`.
  `drawBehind` recreates them every frame.
- **Animate in the draw phase.** Read the animated value inside the draw lambda and
  composition never runs.
- **Allocate shapes only while moving.** A pressed tile gets a new `RoundedCornerShape`; the
  other twenty-nine share one cached instance.
- **The grid is never 1400 tiles.** It is thirty plus a pager.
- **No text inside a grid tile.** This is also what lets the grid survive 200% font scale
  untouched — there is nothing in a tile for the font setting to grow.

---

## Accessibility

Not a section of claims. A gate you can run:

```bash
./gradlew :design-system:designCheck
```

| Check | What it enforces | Where |
|---|---|---|
| `ContrastTest` | Every pair in `ContrastPairs`, both themes, against its WCAG bar | anywhere |
| `ContrastTest` | All 18 type badges, plus the *uniformity* of the OKLCH ramp | anywhere |
| `AccessibilityTest` | 48dp touch targets; TalkBack sentences, distinct per state | anywhere |
| `BoxGridScrollingParentTest` | Container contracts a screenshot cannot see | anywhere |
| `ComponentScreenshotTest` | Both themes, 100% and 200% font scale | **CI only** |

Screenshots are the one CI-only gate, and the split is not arbitrary. Roborazzi compares
pixels exactly and Robolectric does not render identically across operating systems, so a
golden is valid only on the platform that recorded it — goldens recorded on Windows failed
on `ubuntu-latest`, and once recorded on CI they failed on Windows. Everything above the
line is platform-independent and runs anywhere; `verifyRoborazziDebug` on a workstation is
expected to fail and that failure means nothing.

Screenshot goldens are recorded by the `record screenshots` workflow rather than locally,
because Roborazzi compares pixels exactly and Robolectric does not render identically across
operating systems. The procedure is in [`design-usage.md`](design-usage.md).

`ContrastPairs` lives in **main** source, not in the test. A table only the tests can see is
a table that silently stops describing the app; a component that starts using a new pair
needs somewhere honest to declare it.

**Status is never colour alone.** The five slot states carry a gold pip, nothing, a cross, a
dash, and no rim at all. In light theme the caught and needed rims sit at 1.58:1 against
each other and genuinely cannot be told apart by luminance — which is exactly why caught
also has a thicker rim and a sprite in full colour. `ContrastPairs` deliberately does *not*
list that pair; listing it with a lowered bar would be pretending.

**Reduce motion** is honoured by construction (see Motion).

One finding worth recording: the first version of `BoxSlot` applied
`minimumInteractiveComponentSize()` *after* the caller's modifier, so a caller passing
`.size(28.dp)` silently won and the 48dp guarantee was fake. `AccessibilityTest` caught it.
The component now uses two boxes — the outer owns the touch target and the click, the inner
is the visual well.

---

## The gallery

Debug builds install a second launcher icon. It shows every component in every state, with
**theme** and **font scale** toggles at the top, because those are the two axes where a
design system quietly breaks.

The box section is wired live: tapping toggles a slot, so the catch sweep and the completion
rule can be felt rather than looked at. Motion is reviewed there by hand — screenshot tests
deliberately capture no mid-animation frame, because such a test fails on timing rather than
on appearance.

The gallery lives in the library so components and their examples move together, and is
referenced only from `app/src/debug`, so R8 strips it from release.
