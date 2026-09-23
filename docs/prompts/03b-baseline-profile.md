# Prompt 3b — A baseline profile, so the pager holds 120 Hz as installed

> Run this after 3a (sprites), before M3. Fresh Claude Code session at the repo root, in
> **plan mode**. Read `CLAUDE.md`, `docs/architecture.md` section 8 (the budgets and both
> "Measured at M2" tables) and **`docs/adr/0008-toolchain-baseline.md`** first. This
> session adds build infrastructure and a checked-in profile. It changes no feature code
> and no design-system component.

---

You are the performance engineer on a **shiny living dex tracker**: an Android app for one
person (me), sideloaded, never on the Play Store (`docs/adr/0005-sprites.md` says why).

## What was measured

On my phone (Galaxy S21 Ultra, Android 15, 120 Hz), release build:

| | Measured | Budget |
|---|---|---|
| Cold start to first frame | 235–276 ms | P50 ≤ 600 ms, within |
| DB open + preset projection | 43–86 ms | ≤ 120 ms, within |
| Pager, 25-box swipe run, as installed | 4.6–5.8% janky, P99 14–17 ms | P99 ≤ 8.3 ms, **over** |
| The same, after `cmd package compile -m speed` | 0.8–1.4% janky, P90 8 ms | near |

The gap between the last two rows is **JIT, not the grid**. Uncompiled code is what misses
the 8.3 ms frame. The M2 session tested the obvious grid suspect first: `BoxSlot` applies a
graphics layer to every tile even at rest. Removing it measured the same as leaving it, so
that change was reverted. **Do not touch the tiles.** A baseline profile is how an installed
app gets its hot paths compiled ahead of time.

## The question that decides whether this works

This app is **sideloaded** with `./gradlew installRelease`, not installed from Play. Before
building anything, find out and tell me **how a baseline profile actually reaches the
compiler on a sideloaded install** on Android 15:

- What `androidx.profileinstaller` does at first launch.
- When ART compiles from that profile: at install, at the next background dexopt, or never.
- Whether `adb install` of an APK with the profile embedded behaves differently from Play.

Then say how you will measure **the state I will really be in**:
- straight after install,
- after the profile has been compiled (simulated with `cmd package compile -m speed-profile`, or by triggering the background dexopt job).

A profile that only helps a Play install helps nobody here. If that is the answer, say so
and propose what does help.

**Stop and report after that research**, with a recommendation.

## Constraints

- **Never bump one toolchain version alone.** AGP 8.13.x, Kotlin 2.3.x, Hilt 2.58 and the
  Compose BOM are pinned as a set (ADR 0008, and the header of
  `gradle/libs.versions.toml`). Pick `androidx.baselineprofile`, `benchmark-macro` and
  `profileinstaller` versions that work with **AGP 8.13**. If none do, stop and tell me;
  do not move the toolchain to make a plugin fit.
- **A new module needs a reason bigger than tidiness** (CLAUDE.md). The measurement above
  is the reason. Put it in the commit body and add the module to the table in
  `docs/architecture.md` section 5. CI's module-boundary check (`build.yml`) only polices
  `feature/*` and `design-system`; make sure the new module cannot become a path for a
  feature to depend on a feature.
- **Generating the profile is on demand, like the dataset.** It needs a device, CI has none,
  and a normal `./gradlew build` must not try to run it. The generated profile is checked
  in, exactly as `reference.db` is.
- **The app stays offline.** Benchmark and profile dependencies stay out of the release
  APK's runtime, except `profileinstaller`, which is the point.
- A `benchmark` build type, if you need one: profileable, not debuggable, signed with the
  debug key the way release falls back today. **Never commit a keystore or a password.**

## The journey to profile

The paths I actually use:
1. Cold start to the box view.
2. Paging through at least 20 boxes, both directions.
3. Opening a slot detail and going back (this includes the shared element).
4. Opening search, typing a name, opening a result.
5. Opening the "All boxes" sheet and jumping.

## Measure exactly as M2 did

The before numbers are only comparable if the method is identical. My phone is on wireless
adb. The serial changes between sessions, so run `adb devices`. adb is at
`$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe` and is not on PATH.

```
install release -> am force-stop net.pokedex -> am start -W -n net.pokedex/net.pokedex.MainActivity
dumpsys gfxinfo net.pokedex reset
25 x  input swipe 918 960 162 960 180, 0.5 s apart
dumpsys gfxinfo net.pokedex   (Janky frames, 90th/99th percentile, Slow UI thread, Slow issue draw commands)
```

Measure at least three runs per state, plus ten cold starts from `am start -W`. Report the
spread, not a best run. Then compare:
1. As installed, no profile.
2. As installed, with the profile.
3. With the profile after it has been compiled.

If the pager is still over 8.3 ms P99 with the profile applied, **capture a system trace
before proposing a fix.** M2 guessed at a cause once and the numbers said no. Report what the
trace shows; do not ship a speculative change to a component.

## Deliverables

1. The profile module, the journey, and the generated profile checked in.
2. `profileinstaller` in `:app`, if the research says it is what makes a sideloaded install
   benefit.
3. The "On a device" table in `docs/architecture.md` updated with before and after, and the
   method written down so a later session can rerun it.
4. A short note, in `docs/architecture.md` or an ADR if a real decision was made, on how the
   profile is regenerated and when: after M3's screens land, for example.

Every commit builds: `./gradlew test detekt lintDebug :design-system:designCheck assembleDebug`.
One logical change per commit, imperative subject under 72 characters, no type prefixes.
