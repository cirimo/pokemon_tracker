# 0008 — AGP 8.13 + Kotlin 2.3 + Hilt 2.58, pinned as a set

Status: accepted, 2026-09-22

## Context

The plan for this session specified AGP 9.4.1 and Kotlin 2.4.20 as "the current stable
versions". Building the skeleton showed those two cannot be combined, and that AGP 9 is
not the upgrade it looks like.

What the POMs and build failures actually showed:

- **AGP 9.4.1 bundles KGP 2.2.10.** AGP 9 replaces the `org.jetbrains.kotlin.android`
  plugin with built-in Kotlin, pinned to the KGP it depends on. Adopting AGP 9 therefore
  *downgrades* Kotlin from 2.3.21 to 2.2.10.
- **AGP 9 removes the parameterized `CommonExtension` and the `applicationVariants` /
  `libraryVariants` APIs**, so every convention plugin would need rewriting, and six
  third-party Gradle plugins would be running against the new DSL.
- **Hilt 2.59+ requires AGP 9.0 as a minimum**; 2.58 is the last release supporting
  AGP 8.
- **Hilt 2.58 cannot read Kotlin 2.4 metadata** ("Provided Metadata instance has version
  2.4.0, while maximum supported version is 2.3.0").
- **Compose BOM 2026.08.00 and later require compileSdk 37**, which AGP 8.13 cannot use.
- **AndroidX lifecycle 2.10+, activity 1.12+ and core-ktx 1.18+ require AGP 9.1+.**

That is a closed loop: Kotlin 2.4 needs Hilt >= 2.59, which needs AGP >= 9, which pins
Kotlin to 2.2.10.

## Decision

Take the newest **coherent** set rather than the newest of each part:

| | |
|---|---|
| AGP | 8.13.2 |
| Gradle | 8.14.5 |
| Kotlin | 2.3.21 |
| KSP | 2.3.12 |
| Hilt | 2.58 |
| Compose BOM | 2026.06.01 — resolves `material3` to **1.4.0 stable** |
| Room | 2.8.5 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |

The M1 contract asked for the stable `material3` 1.4.0 line and not the 1.5.0-alpha
Expressive line. BOM 2026.06.01 satisfies that, so nothing is lost by being a BOM or two
behind.

AndroidX libraries are pinned to the last versions that support AGP 8.13 and
compileSdk 36: lifecycle 2.9.4, activity-compose 1.10.1, core-ktx 1.17.0,
navigation-compose 2.9.8, hilt-navigation-compose 1.2.0.

## Consequences

- **These versions move together or not at all.** Bumping Kotlin alone breaks Hilt;
  bumping AndroidX alone breaks the AAR metadata check. The version catalog says so at
  the top, and `CLAUDE.md` lists it under "never do these".
- Kotlin 2.3.21 rather than 2.4.20 is the only real loss, and it is one minor version.
- Lint's `GradleDependency` and `AndroidGradlePluginVersion` checks are disabled in the
  shared Android convention: they would otherwise report on every build that newer
  versions exist, which is true and deliberate.
- `build-logic` cannot compile against the KSP Gradle plugin (its Kotlin metadata is
  newer than Gradle's embedded compiler), so KSP is applied by plugin id rather than
  through its typed API. No functionality is lost.
- **Revisit when AGP 9 ships with a KGP of 2.3 or newer.** At that point AGP 9 becomes a
  real upgrade instead of a sideways move, and the convention plugins can be rewritten
  for the new DSL in one deliberate change.
