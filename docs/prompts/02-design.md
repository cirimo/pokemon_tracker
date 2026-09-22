# Prompt 2 — Design language & component library

> Run this **after** the architecture session lands. Fresh Claude Code session, plan mode.
> Read `docs/00-big-picture.md`, `docs/architecture.md` and `CLAUDE.md` first.

---

You are the design engineer on this Android app. Read `docs/00-big-picture.md` for context.
This session produces the **design language and the component library** — not screens, not
features. Everything built later must be assembled from what you make here.

## The bar

My previous Pokémon tracker worked and looked like a generic Material template. This one
should feel like a **premium collector's app**: something closer to a display case than a
spreadsheet. When I open it I want to feel the collection, and when I fill a slot I want the
app to acknowledge it.

Distinctive, but not costume-y. It must still feel like a fast, native Android app — not a
web app in a WebView, and not a fan-site pastiche. Restraint is part of the premium feel:
one confident idea executed precisely beats five effects competing.

**Do not use official Pokémon branding, logos, or fonts.** This is a personal tool; the
visual identity is ours.

## Research first

Before designing, study and report on:

- **Material 3 Expressive** — what's current in 2026, what's worth adopting, and where to
  deliberately depart from it. Name the departures and defend them.
- Reference apps worth stealing from: collection/inventory apps, card-collecting apps,
  music library apps, well-regarded Android apps with strong custom identity.
- The visual problem of **1400 sprite tiles on one screen** — how comparable apps make dense
  grids feel crafted rather than like a contact sheet.
- **Shiny as a visual concept.** Shiny is the whole point of this app. How do we express
  "shiny" in the UI language — gold/iridescence/shimmer — without it becoming noise on
  every surface? Where does it earn its place, and where is it forbidden?
- Compose performance realities for grids, shared-element transitions, and any shimmer or
  gradient work you propose. Design nothing you can't ship at 60fps.

## Design and build

### Foundations
- **Color.** A dark-first palette with a real light theme, not an afterthought. Define the
  shiny accent language. Decide explicitly on dynamic color (Monet): I lean toward a fixed,
  authored identity over letting the wallpaper drive it — argue the case either way.
- **Typography.** A full scale with a chosen typeface pairing. Numerals matter here: progress
  counts, dex numbers, odds. Tabular figures where they belong.
- **Spacing, radius, elevation, borders** — a token set, not ad-hoc dp values.
- **Iconography** — the family, the weight, the rules.
- **Motion.** Duration and easing tokens, and a small set of named, reusable transitions.
- **Type-color system.** 18 Pokémon types need a palette that is legible, contrast-checked
  in both themes, and doesn't fight the brand accent. This is harder than it looks — treat
  it as a real design problem and validate the contrast.

Implement all of this as Compose theme tokens with a documented rationale.

### Components
Build a real library, each with previews and states (loading, empty, error, pressed,
disabled, selected):

- **BoxSlot** — the atom of the app. A 30-slot HOME-style box grid tile. Must read clearly
  at grid density, distinguish empty / needed / caught / shiny-locked / not-in-my-games at a
  glance without relying on color alone, and support a satisfying press state.
- **BoxGrid** — the 6×5 box, and the box pager/overview.
- **SpeciesCard** and **SpeciesHeader** — the detail-screen hero.
- **ProgressRing / ProgressBar** — collection progress at several scales.
- **TypeBadge**, **GameBadge**, **MethodBadge**
- **FilterChipRow**, **SearchField**, **SortControl** — these carry the quality-of-life load;
  make them excellent.
- **StatTile**, **EmptyState**, **ErrorState**, **LoadingState** (skeletons, not spinners)
- **BottomSheet** and **Dialog** styling
- **CaughtToggle** — the single most-used control in the app. It deserves disproportionate
  attention: the tap target, the haptics, the animation, the undo path.

### Signature moments
Pick two or three and execute them properly:

- Marking a slot caught — the celebration. Restrained, fast, repeatable 400 times without
  becoming annoying. This is a hard constraint, not a nice-to-have.
- Completing a box.
- Opening a slot into its detail view (shared element).

### Accessibility — non-negotiable
- Contrast verified against WCAG AA, in both themes, for every token pair actually used.
- Status never conveyed by color alone.
- Full TalkBack semantics on every component, with meaningful slot descriptions.
- Respects system font scale up to 200% without breaking the grid.
- Honours reduce-motion.
- Minimum 48dp touch targets.

Ship a check I can run, not a claim in a document.

## Deliverables

1. `docs/design-system.md` — the language, the rationale, the rules, the departures from M3
   and why.
2. The implemented theme and component library in the design-system module.
3. A **gallery screen**, reachable in debug builds, showing every component in every state
   in both themes, at default and 200% font scale. This is how I review your work.
4. Screenshot tests for the core components.
5. `docs/design-usage.md` — the rules future feature sessions must follow. Specifically:
   when to reach for an existing component vs. build a new one, and the fact that raw
   `Color(...)`, raw dp spacing, and ad-hoc text styles are forbidden in feature code.

## How to work

- Plan mode first. Show me the research, the direction, and the token decisions, and
  **stop for approval** before building the library.
- Give me real options for the visual direction — not one take presented as inevitable.
  Show me the differences concretely, in rendered form, not prose.
- Build the gallery early and iterate against it.
- Push back if I ask for something that will look dated or hurt usability.
