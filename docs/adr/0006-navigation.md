# 0006 — Navigation Compose with type-safe routes, one graph contribution per feature

Status: accepted, 2026-09-22

## Context

A handful of destinations, single Activity, no deep links yet, no multi-pane
requirement. Features must not depend on each other, but they will need to link to each
other (a slot opens a variant, a variant lists its games).

## Options

**Navigation 3.** The direction of travel, back-stack-as-state, better for adaptive
layouts. Still `1.2.0-rc01` and moving. Adopting it now means absorbing API churn during
feature work.

**Navigation Compose 2.9 with `@Serializable` routes.** Stable, type-safe since 2.8, well
documented. String-route pitfalls are gone.

**A hand-rolled sealed-class stack.** Perfectly workable at this size, and genuinely
tempting for one user with six screens. But it means writing predictive-back, saved-state
and transition handling by hand.

## Decision

**Navigation Compose 2.9 with `@Serializable` route objects.**

Each feature owns its destinations and exposes one `NavGraphBuilder` extension
(`fun NavGraphBuilder.dexGraph()`). `:app` is the only module that sees the whole graph.
Two features can therefore link to each other through route types without a module
dependency.

Graph shape:

```
Boxes (start) ──→ SlotDetail(catchKey) ──→ VariantDetail(variantId)
Search
Settings ──→ BackupRestore
```

No nested graphs until M4 needs them.

## Consequences

- Route types are `@Serializable`, so feature modules apply the serialization plugin.
- Arguments are typed. `SlotDetail` takes a `CatchKey`, not a string that has to be
  parsed and validated at the destination.
- Migrating to Navigation 3 later touches `:app` and one file per feature, which is a
  bounded cost and a reasonable price for not tracking a release candidate.
