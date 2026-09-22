# 0004 — Hilt

Status: accepted, 2026-09-22

## Context

Five modules, ViewModels, two databases, an injected IO dispatcher, and eventually
WorkManager for rolling backups. One developer, working in bursts with long gaps.

## Options

**Manual DI.** No plugin, no code generation, no build cost. Genuinely adequate at M0.
An application-scoped container passed down, with ViewModel factories written by hand.

**Koin.** Small, no code generation, pleasant DSL. Wiring errors surface at runtime.

**Hilt.** Standard on Android, integrates with ViewModel, WorkManager and instrumentation
tests. Costs a Gradle plugin and KSP build time, and constrains which AGP and Kotlin
versions are usable (see ADR 0008).

## Decision

**Hilt.**

The deciding factor is the long gaps. On a project returned to after months away, a
missing binding should fail the build with a class name, not fail the app on the screen
that happens to use it. Manual DI is fine until ViewModel factories and WorkManager
arrive, and retrofitting Hilt across five modules later is more work than paying now.

## Consequences

- KSP runs on `:core:data`, `:feature:*` and `:app`. Build time cost accepted.
- Hilt's version is coupled to AGP and Kotlin; it is the constraint that fixed the whole
  toolchain in ADR 0008.
- `:core:model` and `:design-system` stay Hilt-free, which is what keeps their tests
  trivial and `:core:model` Android-free.
- Dispatchers are provided through `@IoDispatcher` / `@DefaultDispatcher` qualifiers
  rather than referenced directly, so tests can substitute one without a global rule.
