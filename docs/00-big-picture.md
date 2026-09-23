# Shiny Living Dex Tracker — Big Picture

## The problem

I hunt shinies across the Switch-era Pokémon games and collect them all in a single
Pokémon HOME account. My tracking lives in spreadsheets that have grown past the point
of usefulness: they can't tell me *what to hunt next*, *where it's catchable*, or
*how to catch it*, and they don't look like anything I want to open.

This app replaces the spreadsheets with one purpose-built tool.

## The north star

**The app is a mirror of my HOME boxes, plus the knowledge I need to fill them.**

Every screen should answer one of three questions:

1. **What do I still need?** — progress, gaps, what's close to done.
2. **Where can I get it?** — which Switch games have it, which have it shiny-locked.
3. **How do I catch it?** — the concrete method, odds, and prerequisites.

## Locked decisions

| Area | Decision |
|---|---|
| Platform | Android only, native Kotlin + Jetpack Compose. No multiplatform in v1. |
| Min SDK | 26 (Android 8) — revisit upward if it buys us anything |
| Data | Offline-first Room/SQLite. No backend, no auth, no network at runtime. |
| Durability | Explicit export/import (JSON) + automatic rolling local backups |
| Reference data | Static dataset compiled into the app at build time |
| Game scope | Switch era: LGPE, SwSh(+DLC), BDSP, PLA, SV(+DLC), Legends Z-A, and HOME as the hub |
| Dex definition | The PokéPC **"Grouped by Regions (Optimized)"** preset — 52 boxes, 1394 slots |
| v1 scope | Reference + checklist + planning. **No hunt engine.** |
| v2 scope | Active hunt engine: counters, odds, phases, session history |
| Visual direction | Premium "collector's app" — custom design language on M3 foundations |

### Why the PokéPC preset is the spine of the data model

I already track against `pokepc.net/livingdex?preset=grouped-balanced`. That preset is not
a filter over the National Dex — it is an **ordered list of 52 boxes × 30 slots**, where a
slot is a specific (species, form, gender, cosmetic variant) tuple, with Gigantamax omitted
and regional forms parked at the end of their generation's box.

So the app does **not** model "the dex" as a list of species. It models:

- a **DexPreset** — an ordered, versioned slot list
- a **Slot** — position + the exact variant it demands
- my **CatchRecord** — what I've actually got, attached to a slot

This makes the box grid the natural primary UI, makes progress arithmetic trivial and
exact, and makes alternative presets (Classic, National, a future custom layout) pure data
rather than a rewrite. It also means my existing progress transfers 1:1 instead of being
approximated.

**Data source:** [`github.com/pokepc/dataset`](https://github.com/pokepc/dataset) publishes
these presets as versioned JSON with Zod schemas. We ingest it at build time rather than
transcribing anything by hand. *Its license must be verified and honoured before we bundle
it — if the license doesn't permit redistribution, we fall back to generating an equivalent
layout from PokéAPI plus a checked-in slot-order file.*

### What the static dataset must contain

The hard part of this app is not the code, it's the data. PokéAPI gives us species, forms,
sprites, types and version-group membership. It does **not** give us the things that
actually decide a hunt:

- shiny locks (per game, per encounter)
- encounter method per game (grass, fishing, outbreak, raid, static, gift, breeding, fossil)
- method-specific odds modifiers (Masuda, Shiny Charm, chain/outbreak/sandwich tiers)
- evolution requirements that gate a form
- HOME transfer legality between games

Those need a **curated, checked-in, human-editable layer** that we own, merged with PokéAPI
during the build. The architecture must treat that curated layer as a first-class source
that survives dataset regeneration.

## Roadmap

- **M0 — Foundation.** Repo, modules, build config, CI, dataset pipeline producing a
  bundled DB, `CLAUDE.md`, ADRs.
- **M1 — Design system.** Tokens, components, motion, the box grid, in a live gallery screen.
- **M2 — See my dex.** Box view, slot detail, species/form reference, search and filter.
- **M3 — Track my dex.** Mark caught, origin game, notes, progress dashboards, backup/restore.
- **M4 — Plan my hunts.** "What should I hunt next", per-game availability, method guidance,
  wishlist/priority queue.
- **M5 — Hunt engine.** Counters, odds, phases, sessions, history. (The v2 promised above.)

## How this gets built

One prompt per milestone, each run as its own Claude Code session, in order:

1. [`prompts/01-architecture.md`](prompts/01-architecture.md) — produces the technical plan
   and the skeleton, before any feature code exists. **Done (M0).**
2. [`prompts/02-design.md`](prompts/02-design.md) — produces the design language and the
   component library, before any screen is designed ad hoc. **Done (M1)** — see
   [`design-system.md`](design-system.md) for the result and
   [`design-usage.md`](design-usage.md) for the rules it imposes on everything after.
3. [`prompts/03-see-my-dex.md`](prompts/03-see-my-dex.md) — the box view, slot detail,
   species reference, search and filter. **Next (M2).**
   Two follow-ups M2 found, to run before M3:
   - [`prompts/03a-sprites.md`](prompts/03a-sprites.md) — 364 cosmetic-form sprites are
     copies of their base form; give every form its own art and a validator that says so.
   - [`prompts/03b-baseline-profile.md`](prompts/03b-baseline-profile.md) — the pager misses
     the 120 Hz budget until its code is compiled ahead of time.
4. Further feature prompts, one per milestone, written as each one comes up.
