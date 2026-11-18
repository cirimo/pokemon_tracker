---
name: Shiny Living Dex
description: A display case for one shiny living dex, mirroring HOME box by box.
colors:
  case-dark: "#121011"
  case-surface-dark: "#1A1718"
  case-surface-high-dark: "#232021"
  case-highlight-dark: "#4A4546"
  slot-well-dark: "#0E0C0D"
  slot-void-dark: "#151314"
  rim-dark: "#625F5C"
  rim-focus-dark: "#807B78"
  shiny-gold-dark: "#D8B26A"
  progress-track-dark: "#38342F"
  on-case-dark: "#EDE7E3"
  on-case-muted-dark: "#A9A19E"
  silhouette-dark: "#6A6461"
  silhouette-regular-dark: "#9C9591"
  error-text-dark: "#F2B8B5"
  error-graphic-dark: "#E0757A"
  case-light: "#F5F2ED"
  case-surface-light: "#EBE6DE"
  case-surface-high-light: "#E1DBD1"
  case-highlight-light: "#FFFFFF"
  slot-well-light: "#E6E1D9"
  slot-void-light: "#EFEBE4"
  rim-light: "#837E77"
  rim-focus-light: "#746F68"
  shiny-gold-graphic-light: "#7A5A10"
  shiny-gold-text-light: "#6B4E0C"
  progress-track-light: "#D8D1C4"
  on-case-light: "#1E1A18"
  on-case-muted-light: "#5B534D"
  silhouette-light: "#7A756E"
  silhouette-regular-light: "#45403B"
  error-text-light: "#7A2C2C"
  error-graphic-light: "#A33B3B"
typography:
  display-numerals:
    fontFamily: "Instrument Serif"
    fontSize: "44sp"
    fontWeight: 400
    lineHeight: "48sp"
    letterSpacing: "-0.5sp"
    fontFeature: "tnum"
  title-numerals:
    fontFamily: "Instrument Serif"
    fontSize: "22sp"
    fontWeight: 400
    lineHeight: "28sp"
    fontFeature: "tnum"
  hero-numerals:
    fontFamily: "Instrument Serif"
    fontSize: "72sp"
    fontWeight: 400
    lineHeight: "76sp"
    letterSpacing: "-1sp"
    fontFeature: "tnum"
  hero-title:
    fontFamily: "Instrument Serif"
    fontSize: "48sp"
    fontWeight: 400
    lineHeight: "52sp"
    letterSpacing: "-0.5sp"
  screen-title:
    fontFamily: "Instrument Serif"
    fontSize: "28sp"
    fontWeight: 400
    lineHeight: "32sp"
  section-title:
    fontFamily: "Instrument Serif"
    fontSize: "24sp"
    fontWeight: 400
    lineHeight: "30sp"
  box-title:
    fontFamily: "Instrument Serif"
    fontSize: "20sp"
    fontWeight: 400
    lineHeight: "26sp"
  title:
    fontFamily: "Inter"
    fontSize: "16sp"
    fontWeight: 500
    lineHeight: "24sp"
    fontFeature: "tnum"
  body:
    fontFamily: "Inter"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
  body-small:
    fontFamily: "Inter"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
  stat-value:
    fontFamily: "Inter"
    fontSize: "18sp"
    fontWeight: 600
    lineHeight: "22sp"
    fontFeature: "tnum"
  dex-number:
    fontFamily: "Inter"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "16sp"
    letterSpacing: "0.4sp"
    fontFeature: "tnum"
  badge-label:
    fontFamily: "Inter"
    fontSize: "11sp"
    fontWeight: 500
    lineHeight: "14sp"
    letterSpacing: "0.5sp"
rounded:
  badge: "6dp"
  slot: "8dp"
  slot-pressed: "14dp"
  field: "10dp"
  card: "12dp"
  dialog: "16dp"
  hero: "20dp"
  sheet: "20dp"
spacing:
  xxs: "2dp"
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
  xxl: "32dp"
  slot-gutter: "4dp"
  slot-min: "44dp"
  touch-target-min: "48dp"
components:
  slot-needed:
    backgroundColor: "{colors.slot-well-dark}"
    rounded: "{rounded.slot}"
    size: "44dp"
  slot-caught:
    backgroundColor: "{colors.slot-well-dark}"
    rounded: "{rounded.slot}"
    size: "44dp"
  box-case:
    backgroundColor: "{colors.case-surface-dark}"
    rounded: "{rounded.card}"
    padding: "16dp"
  caught-toggle:
    backgroundColor: "{colors.case-surface-dark}"
    textColor: "{colors.on-case-dark}"
    typography: "{typography.title}"
    rounded: "{rounded.card}"
    height: "56dp"
  type-badge:
    typography: "{typography.badge-label}"
    rounded: "{rounded.badge}"
    padding: "4dp 8dp"
---

# Design System: Shiny Living Dex

The compact, token-bearing view of the design language. The rationale behind every value,
the measured contrast ratios and the history of each decision live in
[`docs/design-system.md`](docs/design-system.md); the rules feature code must follow live in
[`docs/design-usage.md`](docs/design-usage.md). The Kotlin in
`design-system/src/main/kotlin/net/pokedex/designsystem/theme/` is the source the app runs
on. Where this file and the Kotlin disagree, the Kotlin is right and this file is stale.

## Overview

**Creative North Star: "The Display Case"**

A warm near-black case with recessed slot wells, and a hairline gold rim on the slots that
are filled. It reads as an object rather than a table, because the thing it replaced was a
spreadsheet. In light theme the case becomes paper and the wells become impressions.

Restraint is the mechanism. The collection is the decoration: a box is quiet until it is
filled, and colour arriving in a slot is the progress. The craft sits in the container
(the case edge, the box header, the completion rule) while the thirty tiles stay cheap.

**Key Characteristics:**
- Two authored themes, warm dark and warm paper; no dynamic colour.
- One accent, gold, which means shiny and nothing else.
- Silhouettes for what is missing, full colour for what is caught.
- Borders and tonal surfaces instead of elevation anywhere dense.
- A serif only for large numerals and box names; Inter with tabular figures for the rest.

## Colors

A warm neutral case and one metal accent. Every value was solved against the surface it sits
on; measured ratios are in `theme/Color.kt`.

### Primary
- **Shiny Gold** (dark `#D8B26A`; light graphic `#7A5A10`, light text `#6B4E0C`): the caught
  slot's rim and pip, progress numerals and arcs, and the catch celebration. Nowhere else.

### Neutral
- **Case** (`#121011` / `#F5F2ED`): the ground of every screen.
- **Case Surface** and **Case Surface High** (`#1A1718`, `#232021` / `#EBE6DE`, `#E1DBD1`):
  box cases, cards, sheets; High is how a screen asks for emphasis without gold.
- **Slot Well** (`#0E0C0D` / `#E6E1D9`): the recess a sprite sits in. **Slot Void**
  (`#151314` / `#EFEBE4`) is a position the preset leaves empty.
- **Rim** (`#625F5C` / `#837E77`): the hairline around a well, 3:1 against it.
- **Silhouette** (`#6A6461` / `#7A756E`) and **Silhouette Regular** (`#9C9591` / `#45403B`):
  a missing sprite, and an owned-but-not-shiny one, both flat.
- **On Case** and **On Case Muted** (`#EDE7E3`, `#A9A19E` / `#1E1A18`, `#5B534D`): text.
- **Progress Track** (`#38342F` / `#D8D1C4`): the unfilled arc, neutral on purpose.

### Type colours
Eighteen swatches derived in OKLCH with lightness and chroma fixed and hue the only variable
(`theme/TypeColors.kt`). They live on badges and the detail screen, never in the grid.

### Named Rules
**The Gold Means Shiny Rule.** Gold has exactly three places: the caught rim and pip,
progress numerals and arcs, and the catch celebration. Never a background, header,
navigation, badge or "important" button.

**The No Monet Rule.** No dynamic colour. Gold has to keep meaning shiny, and an accent the
wallpaper chooses cannot be contrast-checked.

## Typography

**Display Font:** Instrument Serif (bundled, one weight)
**Body Font:** Inter (bundled, variable `wght`, `opsz` pinned at 16)

**Character:** a neutral, high x-height workhorse with real tabular figures, and a narrow
serif used like a brass label on a case.

### Hierarchy
- **Hero numerals** (400, 72sp, 76sp): the collection count inside the Progress trophy. Gold.
- **Hero title** (400, 48sp, 52sp): a Pokémon's name under its detail plinth.
- **Screen title** (400, 28sp, 32sp): a screen's title and the open box's name.
- **Section title** (400, 24sp, 30sp): section headings and region shelves.
- **Display numerals** (400, 44sp, 48sp): the collection count on Boxes. Gold.
- **Title numerals** (400, 22sp, 28sp): a box's count beside its ring.
- **Box title** (400, 20sp, 26sp): box names and section headings.
- **Title** (Inter 500, 16sp): row titles, the caught toggle.
- **Body** (Inter 400, 16sp / 14sp): explanations and summaries.
- **Stat value** (Inter 600, 18sp, tabular): odds and counts in lists.
- **Dex number** (Inter 500, 12sp, tabular, +0.4sp): "No. 0939".
- **Badge label** (Inter 500, 11sp, +0.5sp): type, game and method badges.

### Named Rules
**The Brass Label Rule.** The serif appears only on large numerals and names: counts, box
and region names, screen and section titles, a Pokémon's name on its hero. Never in body
copy, rows or controls. A serif everywhere is a costume; a serif on "766 / 1394" is a label
on a case.

**The Upright Hierarchy Rule.** Screen title, then section title, then row: 28, 24, then
Inter 16. Nothing sits above a heading as a kicker; position and context go under it.

**The Tabular Rule.** Every style that can hold a digit sets `tnum`, because every number in
the app is read against another number.

## Layout

A single column at phone width with 16dp side margins. The box is a fixed 6×5 grid of wells
at a 44dp minimum with 4dp gutters (296dp total, which fits the narrowest supported phone),
inside a case with 16dp padding, in a horizontal pager of 52 boxes. The grid never scales
with font size and holds no text, which is how it survives 200% text untouched. Sections
stack with 16–24dp between them; everything tappable is at least 48dp.

## Elevation & Depth

Flat by default, with depth by tone: case, case surface, case surface high, and the recessed
well. Elevation exists only for sheets (8dp), dialogs (12dp) and menus (6dp).

### Named Rules
**The Borders Not Elevation Rule.** Nothing dense casts a shadow. A per-tile shadow is a
render node per tile, and thirty of them are the frame budget.

**The No Shader Rule.** No `RuntimeShader` or `RenderEffect` anywhere: AGSL needs API 33
and minSdk is 26. Gradients are `Brush`es allocated in `drawWithCache`.

## Shapes

Square-ish rather than pill-shaped: slots at 8dp, badges at 6dp, fields at 10dp, cards and
cases at 12dp, dialogs at 16dp, sheets at 20dp on top. A pressed slot morphs its corner to
14dp instead of showing a ripple. Hairline rims are 1dp; the caught rim is 2dp.

## Components

### Box slot (signature)
- **States:** empty, needed, regular, caught, shiny-locked, not in my games, each with a
  non-colour mark: nothing, nothing, a hollow ring, a gold pip, a cross, a dash.
- **Caught:** 2dp gold rim, gold pip, sprite in full colour. **Needed:** 1dp rim, flat
  silhouette. **Regular:** stronger silhouette and a ring.
- **Press:** corner morphs 8→14dp and the tile scales; no ripple.
- **Catch:** the rim springs to gold, the pip scales in, one glint sweeps the rim once,
  under 300ms, inside the tile's own bounds, with one haptic.

### Box case and header
- A lit case at 12dp with a rim: the tone falls from case surface high at the top edge,
  and a hairline of case highlight catches that edge. Screen-title serif box name, title
  numerals and a ring.
- A gold rule under the header draws itself once when the box becomes complete.

### Detail plinth
- The slot at screen width (20dp corners): the type's container colour rising from the
  base, a soft light behind the sprite, a shadow pooled under it. Caught keeps the 2dp gold
  rim and the pip. The name sits under it in the hero title serif.

### The collection
- **Strip:** every box as one bar above the pager, gold to the height it is filled, the
  open box ringed. **Plate:** a box as thirty dots in slot order (gold disc shiny, grey disc
  regular, hollow ring needed). **Shelf:** a region's plates under its section title; a gold
  rule draws when the region completes. **Trophy:** a recent catch in a 96dp well with the
  caught rim; the whole dex as a 232dp three-quarter arc with the count in hero numerals.

### Hunts, figures, facts, steps
- **Hunt card:** sprite well, name, reasons, odds as the figure on the right; lit only when
  it is the one hunt the screen features. **Stat block:** screen-title serif figure with a
  label. **Info table:** rows of title and state with hairlines between, a padlock beside a
  lock. **Step list:** numbered markers joined by a line; done ticked, current ringed.

### Caught toggle
- A full-width 56dp button, not a switch: catching is an event, not a setting.

### Badges
- Type badges in their derived swatch and always naming the type; game badges with a
  padlock for shiny-locked; method badges neutral.

### Rows, cards and notices
- Setting rows and species cards on case surface with a rim, no elevation; notice cards
  with a glyph.

### Loading and empty
- Skeletons in the real layout rather than spinners; empty and error states as a block with
  a section-title heading and an optional action, and a slot's silhouette in place of the
  glyph when the empty screen is about a slot.

### Motion between screens
- A shared axis: forward slides in 24dp from the end and fades, back reverses it, 240ms on
  the case easing, a snap under reduce-motion. One shared element per navigation, the
  sprite, rides on top.

## Do's and Don'ts

### Do:
- **Do** take every colour, dp and text style from `PokedexTheme`; feature code that
  imports `Color`, `dp`, `sp` or `TextStyle` fails the build.
- **Do** give every stateful thing a second, non-colour signal and a TalkBack sentence.
- **Do** animate through `PokedexTheme.motion`, which is already resolved for reduce-motion.
- **Do** read animated values in the draw phase and allocate brushes in `drawWithCache`.

- **Do** put a screen's or section's context under its heading, never above it.

### Don't:
- **Don't** set the serif on body copy, rows, labels or buttons.
- **Don't** light every card; light is for the case and the one featured thing.
- **Don't** put gold on anything that is not a shiny, a progress numeral or arc, or the
  catch celebration.
- **Don't** put type colour, text, shadow, ripple or a shader inside the grid.
- **Don't** add a second shared-element key scheme; one shared element per navigation.
- **Don't** use official Pokémon branding, logos or fonts.
