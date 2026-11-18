# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

One person: the owner, a shiny hunter who collects every Pokémon as a shiny in a single
Pokémon HOME account and plays the Switch-era games to fill it. There is no second user,
no account and no audience to design for. The app is opened daily, often mid-hunt, phone
in one hand and a console in the other, and also at rest, to look at the collection.

## Product Purpose

A mirror of the owner's HOME boxes plus the knowledge needed to fill them. It replaced a
set of spreadsheets that could not say what to hunt next, where it is catchable, or how.
Every screen answers one of three questions: what do I still need, where can I get it, how
do I catch it. Success is the app being opened instead of HOME or a spreadsheet, and a
filled slot feeling like an event.

## Positioning

The box grid is the PokéPC "Grouped by Regions (Optimized)" preset, 52 boxes and 1394
slots, so the app shows the collection in the exact order the owner keeps it in HOME rather
than as a species list. Progress is derived from that layout, and the hunt advice is scoped
to the games the owner actually plays, in the order they farm them.

## Operating Context

- Native Android, Kotlin and Jetpack Compose, minSdk 26. Daily device: a Galaxy S21 Ultra
  at 120 Hz.
- Offline: no backend, no auth, no analytics, no `INTERNET` permission. Reference data is
  compiled into the app; catch records live in a local database and are backed up as JSON
  to a folder the owner picks (`/sdcard/PokemonList`).
- Sideloaded, not distributed through a store.
- Rituals: marking a catch right after it happens; entering a box of regulars at a time;
  checking which box is closest to done; picking the next hunt by game.

## Capabilities and Constraints

- Box pager over 52 boxes, search as a mode with filters, mark mode for regulars.
- Slot detail with paging through the list it came from, catch details, hunt priority,
  per-game availability and recorded methods; variant (species and forms) detail.
- Hunt list ranked by an explained formula, filtered to the owner's games; farm order.
- Progress, by whole dex, region, box, game and farm plan; recent catches.
- Backups: automatic rolling and manual export, restore with a preview.
- Terminology: slot, box, preset, caught (shiny), regular (owned, not shiny), needed,
  shiny-locked, not in my games, farm order, hunt.
- Planned, not built: a hunt engine (counter, odds against the count, phases, sessions;
  prompt 9) and Sword hunting guides (step-by-step guide per hunt; prompt 11).
- Performance budget: the box pager's P99 frame time must not regress
  (`docs/architecture.md` §8).

## Brand Commitments

- No official Pokémon branding, logos or fonts. The identity is the app's own.
- Not a fan-site pastiche and not a costume. The collection itself is the decoration.
- Two authored themes, dark and light, both first-class. No dynamic colour.
- Gold means shiny, and nothing else. An uncaught sprite is a flat silhouette; a caught
  one is in full colour.

## Evidence on Hand

- The owner's real collection: 766 shinies and 768 in the living dex at the latest backup
  (2026-11-15), restorable into the debug build.
- 1387 bundled shiny sprites (HOME renders from PokeAPI, `docs/adr/0005-sprites.md`).
  There are no regular sprites, no illustrations and no photography, and none may be
  invented as if official.

## Product Principles

1. The collection is the artifact. Everything around the grid exists to frame it, and
   nothing competes with it.
2. A catch is an event. Recording one should feel like an acknowledgement, every one of
   the hundreds of times.
3. Answer the three questions in order: what I need, where, how.
4. One person, one phone. Choose what is right for the owner over what is general.
5. Never lose a record. Durability and honesty about data outrank convenience.

## Accessibility & Inclusion

WCAG AA contrast on every declared token pair in both themes, 48 dp touch targets,
TalkBack sentences rather than state names, status never by colour alone, layouts that
survive 200% text, and reduce-motion honoured by every animation. Enforced by
`./gradlew :design-system:designCheck`.
