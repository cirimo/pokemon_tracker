# Prompt 1 — Architecture

> Run this in a fresh Claude Code session at the repo root, in **plan mode**.
> Read `docs/00-big-picture.md` first. Do not write feature code in this session.
> Also read `docs/design-decisions.md` and satisfy the "Contract: what the architecture
> session must provide" section — the design session has already locked the visual
> direction and depends on this session scaffolding a home for it.

---

You are the principal engineer on a new Android app: a **shiny living dex tracker** for a
single user (me). Read `docs/00-big-picture.md` for the product context and the locked
decisions — treat those decisions as constraints, not suggestions, and tell me directly if
one of them is wrong rather than silently working around it.

Your job in this session is to design the technical foundation and then build the skeleton.
No features, no screens beyond a smoke-test screen.

## Constraints

- Android only. Kotlin, Jetpack Compose, Material 3. minSdk 26, targetSdk latest stable.
- Gradle with Kotlin DSL and a version catalog. Convention plugins over copy-pasted config.
- Offline-first. **The shipped app makes no network calls at runtime.** Network access
  belongs only to build-time tooling.
- Single user, single device, no auth, no backend.
- The reference dataset is compiled into the app as a prebuilt, read-only asset.
- The app must survive a phone loss via user-driven export/import.

## Research first, decide second

Before proposing anything, research and report findings on:

1. **The PokéPC dataset.** `github.com/pokepc/dataset` — its schema, how box presets are
   encoded, how it identifies species/form/gender/cosmetic variants, its release/versioning
   story, and **its license**. Determine whether we may redistribute it inside a personal
   app. If not, design the fallback. Specifically locate the preset matching
   "Grouped by Regions (Optimized)" / `grouped-balanced` (52 boxes, 1394 slots) and document
   its exact slot encoding.
2. **PokéAPI** as the source for species, forms, types, evolution chains, sprites and
   version-group membership. Decide what we take from it and what it cannot give us.
3. **Sprites and artwork.** Compare bundling (app size vs. offline guarantee) against
   on-demand fetch+cache. Measure: what does a full shiny sprite set for ~1400 slots
   actually weigh in WebP at the sizes our UI needs? Bundling is strongly preferred; prove
   whether it's viable.
4. **The curated knowledge layer** — shiny locks, per-game encounter methods, odds
   modifiers, HOME transfer legality. Identify credible sources, propose a schema, and
   propose how I maintain it as checked-in files without hand-editing a binary DB.
5. **Room + prepopulated database** — `createFromAsset`, migration strategy when the
   dataset updates but my catch records must not be touched, and how to keep reference
   data and user data cleanly separated.

Report what you found, with sources and with the open questions, **before** you propose a
design. Where a decision is genuinely close, give me the trade-off and a recommendation
rather than picking silently.

## Design the following

### Module structure
Propose a multi-module Gradle layout. Justify each module's existence — I'd rather have
four meaningful modules than twelve ceremonial ones. Cover where the design system lives
(a separate session builds it), where the dataset pipeline lives, and how feature modules
stay independent of each other.

### Data model
This is the part that matters most. Model, at minimum:

- `Species`, `Form`, `Variant` — and be precise about what distinguishes them
- `DexPreset` — versioned, ordered; `Box`; `Slot` (position → required variant)
- `Game` and `GameAvailability` — is this variant obtainable in this game, and is it
  shiny-locked there
- `EncounterMethod` and the odds modifiers that apply to it
- `CatchRecord` — my user data: caught state, origin game, date, notes, favourite/priority
- `UserSettings`

Pay explicit attention to:
- **Reference data vs. user data separation.** Reference data is replaced wholesale on
  dataset updates. User data is sacred and must never be lost by one.
- **Stable slot identity.** If the preset is revised upstream (a slot moves, a form is
  added), my catch records must survive. Design the identity key with this in mind and
  explain your reasoning. This is the single highest-risk decision in the app — treat it
  that way.
- **Multiple presets coexisting.** A catch is a fact about a variant I own; a preset is a
  view over those facts. Model it so switching presets doesn't duplicate or lose data.
- Progress/completion as a derived query, not a stored counter.

Deliver this as an ERD plus the actual Room entity definitions.

### Dataset build pipeline
Design a reproducible pipeline that turns upstream sources + our curated layer into a
prebuilt SQLite asset. Requirements:

- Runs on demand, not on every build. Output is checked in (or fetched from a release).
- Deterministic: same inputs → byte-identical output.
- Validates its output (row counts, referential integrity, "every slot resolves to a real
  variant", "the preset has exactly 52 boxes and 1394 filled slots").
- The curated layer is human-editable text, version-controlled, and diffable.
- Recommend the language/tooling. Be honest about whether this belongs in Kotlin, or in
  Python/TypeScript as a separate tool — don't force it into the app's language for tidiness.

### Application architecture
- Layering and the dependency rule; where domain logic lives.
- DI: recommend Hilt vs. Koin vs. manual, and justify it for a solo project of this size.
- Async and reactive strategy: coroutines/Flow, threading, where suspension boundaries sit.
- State management for Compose: UI state modelling, event handling, and how a screen with
  1400 items stays at 60fps.
- Navigation: recommend an approach and define the navigation graph shape.
- Error handling and a result type — including "the dataset asset is corrupt" and
  "import file is from a newer schema".
- Backup/restore format: a stable, documented, versioned, human-readable JSON schema with
  a forward-compatibility story.

### Engineering practice
- Testing strategy: what gets unit tested, what gets a Room instrumentation test, what gets
  a Compose UI test, what is not worth testing. Be opinionated — I want real confidence,
  not coverage theatre.
- Build variants, signing, and how I install this on my own phone.
- CI on GitHub Actions: build, test, lint, and dataset validation.
- Static analysis: ktlint/detekt config.
- Performance budgets: cold start, box-grid scroll, search latency. Give numbers.

## Deliverables

1. `docs/architecture.md` — the full design, with the ERD and the diagrams.
2. `docs/adr/0001-*.md` … — one ADR per significant decision (dex/slot identity, dataset
   pipeline, DI, sprites, navigation, backup format). Context → options → decision →
   consequences. Keep them short.
3. `docs/dataset-pipeline.md` — how to regenerate the dataset, and how I edit the curated
   layer.
4. `CLAUDE.md` — conventions for every future session: module boundaries, naming, the
   layering rule, testing expectations, commit style, and what never to do.
5. The **skeleton**: Gradle setup, version catalog, convention plugins, module stubs,
   DI wiring, Room entities and DAOs, a placeholder dataset asset, and a single smoke-test
   screen proving the stack works end to end. It must build and its tests must pass.
   It must also include the `:design-system` module, a debug-only gallery host, a Compose
   convention plugin, and version catalog entries pinned to the **stable `material3` 1.4.0
   line** (not the 1.5.0-alpha Expressive line) plus Roborazzi, Robolectric and
   `compose-ui-test` — per the contract in `docs/design-decisions.md`.

## How to work

- Plan mode first. Present the research findings and the proposed design, and **stop for my
  approval** before writing the skeleton.
- Ask me questions whenever a decision depends on how I actually play or how I want to use
  the app. Don't guess at product behaviour.
- Flag anything where you think I've chosen wrong.
