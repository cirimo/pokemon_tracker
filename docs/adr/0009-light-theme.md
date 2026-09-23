# 0009 — A real light theme, reversing the M0 position

Status: accepted, 2026-09-22

## Context

The M0 skeleton shipped `PokedexTheme` with no `darkTheme` parameter and a comment saying
so on purpose:

> There is one scheme. A display case is a dark object; a light variant would be a
> different product, not a different mode.

That was a defensible reading of the "Display case" direction. It is also contradicted by
the design brief, which asks for "a dark-first palette with a real light theme, not an
afterthought", and by the accessibility requirement to contrast-check "every token pair
actually used, in both themes".

Building the palette made the real cost visible, and it is not the cost the M0 comment
assumed. The expensive part of a second theme is not authoring a second set of hex values.
It is that **one accent colour cannot serve both themes**: `#D8B26A` on paper is 1.9:1 and
fails WCAG AA outright, so light theme either drops gold from numerals — which breaks the
"gold means shiny" rule that the whole identity rests on — or gold becomes two tokens.

## Decision

Ship both themes. `PokedexTheme(darkTheme: Boolean = isSystemInDarkTheme())`.

Split the accent into two roles:

| Token | Bar | Dark | Light |
|---|---|---|---|
| `accentGraphic` | 3:1 (WCAG 1.4.11, non-text) | `#D8B26A` | `#7A5A10` |
| `accentText` | 4.5:1 (normal text) | `#D8B26A` | `#6B4E0C` |

In dark the two are the same colour, so the split costs nothing there and is invisible at
every call site. In light it is the thing that makes the theme possible at all.

Dark remains the direction the app is *designed from*. Light is an authored second palette
in which the case becomes paper and the wells become impressions — not a tint of the first.
Two roles genuinely invert rather than lighten: `accentText` drops to bronze, and
`silhouette` goes darker than its surface instead of lighter.

## Consequences

- Every token pair is declared in `ContrastPairs` and checked in **both** themes by
  `ContrastTest`. The 18 type colours are checked in both themes too. `designCheck` fails
  the build on any regression.
- The palette table and the type ramp are twice the size. This turned out to be cheap,
  because the type colours are derived from an OKLCH formula rather than authored — adding
  a theme is adding two lightness constants, not eighteen colours.
- Screenshot tests capture every component in both themes, so the image count doubled.
  Worth it: this is the axis where a design system silently breaks.
- **Caught and needed rims sit at 1.58:1 against each other in light theme.** They cannot
  be told apart by luminance. This is not a defect to be fixed by darkening the gold — it
  is why caught carries three non-colour signals: a gold pip, a 2dp rim rather than 1dp,
  and the sprite arriving in full colour. `ContrastPairs` deliberately does *not* list that
  pair, because listing it with a lowered bar would be a way of pretending colour tells
  them apart.
- `docs/design-decisions.md` and the M0 comment in `Theme.kt` are superseded on this point
  only. Everything else in that document — Monet off, borders not elevation, gold's three
  places, no shader — stands unchanged.

## Alternatives rejected

**Dark only.** Halves the palette work and keeps the object metaphor pure, but the app then
ignores the system setting, and the brief asked for light explicitly.

**One gold for both themes.** Would have meant either failing AA on every progress numeral
in light theme, or using a non-gold colour for numerals in light — which breaks the rule
that gold, and only gold, means shiny.

**A high-contrast dark variant instead of light.** Serves the accessibility need without a
second identity, but does not answer the brief and leaves the system setting ignored. Worth
revisiting as an *addition* if the dark theme ever proves hard to read in practice.
