# Design usage

Rules for every feature session from M2 onward. One sentence version:

> **A feature module composes the design system. It does not extend it.**

`docs/design-system.md` is the *why*. This is the *what you may do*.

---

## Forbidden in feature code

These are not style preferences. They are a build failure —
`config/detekt/feature-rules.yml`, run by the `detektFeatureRules` task, scoped to
`feature/` only, and wired into `./gradlew detekt`.

| Forbidden | Use instead |
|---|---|
| `androidx.compose.ui.graphics.Color` | `PokedexTheme.colors.<role>` |
| `androidx.compose.ui.unit.dp` | `PokedexTheme.dimens.<token>` |
| `androidx.compose.ui.unit.sp` | `PokedexTheme.text` or `MaterialTheme.typography` |
| `androidx.compose.ui.text.TextStyle` | `PokedexTheme.text.<style>` |
| `androidx.compose.material.icons.Icons` | `PokedexIcons` |
| `darkColorScheme` / `lightColorScheme` | `PokedexTheme` |
| `dynamicDarkColorScheme` / `dynamicLightColorScheme` | nothing. Dynamic colour is off permanently |

It works by forbidding the **imports**, which is the cheap check and also the complete one:
you cannot write `16.dp` without importing `dp`, and you cannot write `Color(0xFF...)`
without importing `Color`. No type resolution needed, so it runs in a second.

Why this is worth enforcing rather than asking:

- A raw `Color` bypasses `ContrastTest` entirely, which only checks pairs declared in
  `ContrastPairs`. An unchecked colour is an unchecked accessibility claim.
- Ad-hoc dp is how a 12dp gap and a 13dp gap end up on the same screen.
- Raw `sp` usually arrives with a fixed line height, which is what breaks at 200% font scale.
- An ad-hoc `TextStyle` is a token nobody can find later.

**A genuine exception takes a `@Suppress` with a written reason.** That is deliberate: it
makes the exception visible in review instead of invisible in a diff.

---

## Existing component, or new one?

Ask in this order.

**1. Does a component already do this?** Check `design-system/src/main/kotlin/.../component/`
and run the gallery. The library is small enough to read in ten minutes, which is on purpose.

**2. Can an existing component take a parameter?** Prefer a new parameter with a sensible
default over a near-duplicate component. `ProgressReadout(compact = true)` beats
`CompactProgressReadout`.

**3. Is it used on more than one screen — or will it be?** If yes it belongs in
`:design-system`. If it is genuinely one screen's layout, it belongs in that feature.

**Layout is the feature's job. Components are the design system's job.** A `Column` of
existing components arranged for one screen is feature code and should stay there. A
reusable thing with its own states and its own visual rules is design-system code.

### Adding to the design system

Four things, in the same commit:

1. The component, in `component/`, with a KDoc that says *why* it is shaped that way.
2. `@Preview`s in both themes, and at `fontScale = 2f` if it holds text.
3. A gallery section in `gallery/GallerySections.kt`, showing every meaningful state.
4. A `ComponentScreenshotTest` entry — one line, via `captureBothThemes`.

If the component introduces a new colour pairing, **add it to `ContrastPairs`** in
`theme/Contrast.kt`. That table lives in main source precisely so there is somewhere honest
to declare it.

---

## Rules that are not mechanically checked

The detekt rules catch the mechanical mistakes. These are the ones that need you.

**Gold means shiny.** It has three places: the caught slot rim and pip, progress numerals
and arcs, and the catch celebration. Never a background, a header, a nav bar, a badge, or an
"important" button. If a screen needs emphasis, use `caseSurfaceHigh` and a rim.

**No type colour in the grid.** Types live on badges and the detail screen. Eighteen hues
across thirty tiles is noise, and it competes with the one gold the grid depends on.

**Borders, not elevation, in anything dense.** `PokedexElevation` has three tokens and they
are all for sheets, dialogs and menus.

**Never convey state by colour alone.** Every stateful component in the library carries a
second, non-colour signal — a pip, a glyph, a rim width, a tick. Match that.

**No text inside a grid tile.** This is what lets the grid survive 200% font scale.

**Every interactive thing gets a sentence, not a state name.** "Bulbasaur, not yet caught",
not "Needed". See `SlotState.describe`.

**Skeletons, not spinners.** `LoadingState` and `SkeletonBox` exist. The app loads into one
known layout, so the skeleton can be the real thing with the content removed — which means
the screen does not jump when data lands.

**Do not add a second way to animate.** Use `PokedexTheme.motion`. It is already resolved
for reduce-motion; a hand-written `tween` is not, and will ignore the user's accessibility
setting.

**One shared element per navigation**, via `slotSharedElementKey`. Do not invent a second
key scheme — a shared element whose two sides disagree silently does nothing.

---

## Screenshots come from CI, not from your machine

Roborazzi compares pixels exactly, and Robolectric's rendering is not byte-identical across
operating systems. A golden recorded on Windows or macOS fails on `ubuntu-latest` even when
nothing about the component changed.

So the committed goldens are produced by the `record screenshots` workflow, which runs on
the same runner image that verifies them:

1. Make the visual change and add or update the screenshot test.
2. Push. CI will fail verification, which is correct -- the images no longer match.
3. Run the **record screenshots** workflow by hand (Actions tab, or
   `gh workflow run "record screenshots"`), on your branch.
4. `git pull`. The recorded goldens arrive as a commit from `github-actions[bot]`.
5. **Look at them** before you trust them. The workflow records whatever the component
   currently renders, including a regression.

`./gradlew :design-system:recordRoborazziDebug` locally is still the fastest way to see
what a component draws. Just do not commit what it writes.

The workflow is deliberately manual. One that re-recorded whenever verification failed
would commit away every regression this gate exists to catch.

## Before you open a PR

```bash
./gradlew :design-system:designCheck   # contrast, targets, semantics, container contracts
./gradlew detekt lintDebug             # includes the feature rules above
```

Screenshot verification is not in there, and must not be run locally — see above. CI runs
it for you.

And install the debug build: the gallery is the second launcher icon. If you changed a
design-system component, look at it there in both themes and at 200% text before believing
the screenshots.
