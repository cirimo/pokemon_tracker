# 0003 — The dataset pipeline is TypeScript, run on demand, with a content manifest

Status: accepted, 2026-09-22

## Context

Upstream sources plus our curated layer have to become a prebuilt SQLite asset. The
pipeline runs rarely, needs network access, and must be reproducible.

Upstream (`pokepc/dataset`) is a TypeScript monorepo that publishes its own Zod schemas
alongside the data.

## Options

**Kotlin, as a Gradle module.** One language across the repo. In practice the worst
option: JSON and SQLite scripting in Kotlin is verbose, and living inside Gradle invites
coupling the pipeline to app code and running it on every build.

**Python.** Fewest moving parts, `sqlite3` in the standard library, trivially
deterministic. We would hand-write our own model of upstream's shape.

**TypeScript.** Can validate upstream against the author's own Zod schemas rather than
against our re-transcription of them.

## Decision

**TypeScript**, at `tools/dataset-pipeline/`, not a Gradle module, run by hand.

Dependencies are deliberately thin: `zod` and `yaml` (both pure JS) and Node's built-in
`node:sqlite`, so building the database needs no native module at all. `sharp` is the
single native dependency and is used only by the sprite step, which is separate.

Run with `npm`. `pnpm` would be fine but adds a tool to install for no benefit here.

## Determinism

The builder fixes page size, inserts rows in sorted order, uses no `AUTOINCREMENT`,
freezes the build timestamp and `VACUUM`s last. Two consecutive runs were verified
byte-identical.

But byte identity is a property of the SQLite build Node happens to link, not of the
pipeline: SQLite writes a change counter into the header and a version bump can reshuffle
pages. So the **content manifest** is the real contract -- SHA-256 per table over
canonically sorted rows, plus one hash over all of them. CI checks the manifest first and
byte-identity second, so a SQLite upgrade produces a loud, diagnosable failure rather
than a silent divergence.

## Consequences

- Node and JVM toolchains both required to regenerate the dataset. Acceptable for a
  desktop-only tool.
- Changing a `ReferenceDatabase` entity requires `./gradlew :core:data:assembleDebug`
  before rebuilding the dataset, because the pipeline reads Room's exported schema.
- The curated layer is YAML so a source citation can sit as a comment beside the row it
  justifies, and a one-row correction is a one-line diff.
- Upstream schemas are read permissively (`.passthrough()`): an upstream *addition* does
  not break the build, an upstream *removal* does. That is the right asymmetry for a
  source we do not control.
