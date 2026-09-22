# Design decisions — locked (M1 handoff)

> Produced by the design session (`prompts/02-design.md`) **before** the architecture session ran.
> No code was written. This file exists so prompt 01 and the later design-build session inherit
> these decisions instead of re-litigating them.

## The direction: "Display case"

Warm near-black case, recessed slot wells, hairline gold rim on caught-shiny slots, square-ish
radii (8dp slots), **no type colour in the grid**. Chosen over a "binder / card sleeve" take and
an "instrument / data-table" take because it is the only one that reads as an *object* rather
than a table. Restraint is the point: one confident idea, precisely executed.

No official Pokémon branding, logos, or fonts.

## Locked

| Decision | Choice | Rationale |
|---|---|---|
| Visual direction | Display case (warm near-black + single gold accent) | Reads as an object, not a spreadsheet |
| Dynamic colour (Monet) | **Off** — fixed authored identity | Gold must mean "shiny". Monet rotates the accent to the wallpaper and gold stops meaning anything. It also makes contrast-verifying 18 type colours impossible against an accent we don't control. Accepted cost: won't match system theme. |
| Material 3 baseline | Stable `material3` **1.4.0** | Expressive components are 1.5.0-alpha behind `@ExperimentalMaterial3ExpressiveApi`. We take Expressive's *ideas* (spring motion, shape-morph on press, wider corner scale) in our own tokens rather than leaking alpha opt-ins into every feature session. |

## Departures from M3

1. No dynamic colour (above).
2. **Borders, not elevation shadows, inside the grid.** Per-tile shadow is the frame-budget
   killer at 30+ visible tiles. Shadow is permitted only on sheets and dialogs.
3. Reject Expressive's saturated playful palette — it reads as toy.
4. Tighter corner radii than Expressive's default; slots are 8dp, not pill-shaped.
5. No ripple in the grid — M3's ripple on a 28dp tile is mush.

## Hard constraints discovered in research

- **No shader anywhere in the grid, ever.** `RuntimeShader`/`RenderEffect` costs more than a
  direct draw, and AGSL requires API 33 while minSdk is 26. Any shader at all is progressive
  enhancement on a single surface, with a flat fallback and an API guard.
- `LazyVerticalGrid` virtualises fine — the grid is never 1400 tiles, it is 30 plus a pager.
  Craft comes from the *container* (box header, case edge, per-box progress), not from
  decorating tiles.
- **Gold earns exactly three places:** the caught-shiny slot rim/pip, progress numerals and
  arcs, and the catch celebration. Forbidden on backgrounds, navigation, headers, and all
  badges. **Exactly one shimmer exists in the app** — the catch celebration — never in the grid.

## Contract: what the architecture session must provide

1. A **`:design-system`** library module — no dependency on any feature module, none on
   `:data`/`:domain`. Theme, tokens, and components only.
2. A **debug-only gallery host** — a `debug` source set in `:app` with a launcher alias, or a
   separate `:design-system-gallery` module. Reachable on-device in debug, absent from release.
3. **Version catalog entries** for the Compose BOM (stable `material3` 1.4.0 line), Roborazzi,
   Robolectric, and `compose-ui-test`.
4. A **convention plugin** applying the Compose setup, shared by the design-system module and
   future feature modules.
5. `CLAUDE.md` reserves a section for design-system rules, pointing at `docs/design-usage.md`
   (written by the design-build session).

The full specification — token values, the 18-type OKLCH colour method, the component list, the
three signature moments, and the `./gradlew designCheck` accessibility gate — is carried in the
design session's plan file and is re-derived into `docs/design-system.md` when the library is
built.
