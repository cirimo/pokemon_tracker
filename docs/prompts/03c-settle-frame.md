# Prompt 3c — The pager's settle frame

> Run this after 3b (baseline profile). It can run before or after M3, but not inside it.
> Fresh Claude Code session at the repo root, in **plan mode**. Read `CLAUDE.md` and
> `docs/architecture.md` §8 first, all of it: the budget, the table under "With a baseline
> profile", the trace findings and the regeneration notes. This is an investigation first. It
> changes code only where a measurement says so.

---

You are the performance engineer on a **shiny living dex tracker**: an Android app for one
person (me), sideloaded, offline, on a Galaxy S21 Ultra (Android 15, 120 Hz).

## Where things stand

The baseline profile (3b) did its job. As installed, the pager now runs at the same speed as
a fully compiled build: 4.5–5.3% janky, P99 13–14 ms. The budget is P99 ≤ 8.3 ms, and the
fully compiled build is itself at 14 ms, so **what remains is not JIT**.

A Perfetto trace of the 25-swipe run, with SurfaceFlinger's frame timeline, put 93 of 1517
frames past the app deadline. Only one of them is main-thread bound in the usual sense. They
fall into two clusters of 38:

- **The first frames of a swipe.** RenderThread's `flush commands` runs twice its usual 2 ms
  while the mid cores are still ramping from about 1.4 GHz to 1.9 GHz after the protocol's
  idle 0.5 s gap. That is the device and the test's pacing. **Out of scope**, except for the
  measurement question at the end.
- **The frame where the page settles. This session is about this one.** Its `doFrame` starts
  10–17 ms late because the main thread is busy outside a frame:
  - `compose:lazy:prefetch:idle_frame`, about 16 ms, made of
    `PausedComposition:applyChanges → Compose:onRemembered` (about 5–6 ms) and
    `AndroidOwner:measureAndLayout` (about 5.7 ms);
  - `AndroidOwner:outOfFrameExecutor → Compose:deactivate → Compose:onForgotten`, about
    9.4 ms, for the page that left.

  That is roughly 0.3 ms of remember and forget work **per tile**, 30 tiles a page. The trace
  did not go deep enough to say which remembered object costs it.

## What each tile remembers

These are the suspects. None of them is proven:

- `DexSprite` (`feature/dex/.../Sprites.kt`): Coil's `AsyncImage`, whose painter starts
  its request in `onRemembered` and cancels it in `onForgotten`.
- The shared-element state behind `slotSharedElement*` (`SpriteTransition`).
- `BoxSlot` (`:design-system`): a `MutableInteractionSource`, a `celebration` `Animatable`
  and a `LaunchedEffect(state, reduced)`.
- `BoxGrid`'s per-box `Animatable` and `LaunchedEffect`.

Also check a claim of our own. `BoxPager`'s KDoc says `beyondViewportPageCount` stays at 0
because "thirty bordered tiles compose fast enough". The trace says a page's prefetch is
about 16 ms. That claim is now a hypothesis to test, not a fact.

## The question

**Which remembered object, or objects, make a page cost 10–17 ms to bring in and take out, and
what is the smallest change that gets the settle frame inside 8.3 ms?**

Answer the first half with evidence before proposing the second. M2 once guessed at a grid
cause (the per-tile scale layer), and the numbers said no. Do not repeat that.

1. **Get composable-level names into the trace.** Compose composition tracing
   (`androidx.compose.runtime:runtime-tracing`) names each composable in Perfetto. It must
   not reach the release APK's runtime; confine it to the synthetic `benchmarkRelease`
   build (`net.pokedex.profiling`) or an equivalent. Check what version fits the pinned
   Compose BOM. **Never bump one toolchain version alone** (ADR 0008).
2. **Trace the same 25-swipe run on that build** and attribute the `onRemembered`,
   `onForgotten`, prefetch and measure time to named composables. The Perfetto Python
   package in a scratch virtualenv worked last time; iterate rows, no pandas needed.
3. **Report and stop.** Show the attribution with numbers, give a ranked list of candidate
   changes with the cost each is expected to remove, and recommend one.

## Constraints

- **Measure exactly as §8 does.** Use `tools/perf/measure-pager.sh <serial>` with three runs
  per state and a spread, not a best run. It prints the dexopt state first. The phone is on
  wireless adb; get the serial from `adb devices`. Compare against state 2 of the table.
- **Never run anything against `net.pokedex` on my phone except `installRelease`.** It
  holds my real catch records, and an uninstall deleted them once already. Traces and
  experiments go on `net.pokedex.profiling` or the debug build. Before any uninstall,
  `pm clear`, connected test or non-release install, check the application id.
- **A change to a `:design-system` component** keeps its gallery entry working and passes
  `:design-system:designCheck`. Its screenshots are re-recorded with the `record screenshots`
  workflow, never locally (CLAUDE.md).
- **The visual rules hold.** Borders not elevation, no ripple, no shader in the grid, the
  silhouette/colour rule for sprites, and gold means shiny. A fix that trades any of these
  for frames is not a fix.
- **Each experiment is its own measured A/B.** Revert what does not measure better, and
  record the rejected ones in §8, as M2 did with the scale layer.
- **After any change that ships, regenerate the baseline profile** (§8), commit it on its
  own, and re-measure.

## Also answer, briefly

Is the 0.5 s gap in our protocol hiding or exaggerating anything? The swipe-start cluster is
the CPU governor ramping after each idle gap, and a real session pages at an uneven rhythm.
Recommend whether §8 should add a second, gap-free run (for example 0.15 s apart) as a
standing measurement. Do not replace the existing one; its numbers are the baseline
everything else compares against.

## Deliverables

1. The attribution: which composables cost what in the settle frame, from a trace, with
   the method written into §8 so it can be rerun.
2. If I approve a change: the change, its A/B numbers, a regenerated profile and an
   updated §8 table. If no change is justified, say so and record why.
3. `BoxPager`'s KDoc corrected to match what was measured, whichever way it went.

Every commit builds: `./gradlew test detekt lintDebug :design-system:designCheck assembleDebug`.
One logical change per commit, imperative subject under 72 characters, no type prefixes.
