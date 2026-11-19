# 0016 — Hunt guides are curated reference data, keyed by variant, and prep is a tick per game

Status: accepted, 2026-11-18 (prompt 11)

## Context

I am playing Sword toward the Shiny Charm and want the app to tell me, per hunt, exactly what
to do with the Switch in my hands: the prerequisites, the loop, how to tell it is shiny, the
mistakes that lose it, the Pokémon to build first and how to farm them, and which rentals to
take where I cannot bring my own. Until now nothing in the app held prose longer than an
encounter's `notes`.

This is the part that can quietly lie, so every claim needs a source, and a wrong build (a
move the species cannot learn) has to be impossible to ship.

## Decisions

### Guides are a curated YAML layer built into reference tables

`data/curated/guides/<gameSet>.yaml`, Zod-validated like every curated file, built into ten
`ReferenceDatabase` tables (schema 3): `guide`, `guide_target`, `guide_step`, `build`,
`build_part`, `guide_link`, `guide_advice`, `requirement`, `farm_source`, `location`. Every
step, build part, advice line and farm link carries its own source.

*Rejected: bundled Markdown or text assets.* They are not queryable (the hunt list cannot ask
"is there a guide for this"), not validated, and a citation per claim becomes a convention
nobody checks.

### A guide targets variants; the hunt is derived

A guide names the variants it is for. `GuideBook.guidesFor(huntKey, variants, gameSet)`
derives the hunt with `huntKeyOf`, the same function the hunt list groups by. Variants rather
than `HuntKey`, because a hunt can span forms a guide does not reach: the Max Lair gives
Zygarde 50%, never the 10% Forme in the same hunt. Never a slot position (ADR 0001). Prompt 9's
hunt targets a `HuntKey` in one game, so a guide and a counter meet on the same screen without
either being reshaped.

### Shared procedures, shared builds

A procedure (a Dynamax Adventure, a soft reset) is a guide row of kind `procedure` with no
targets. A guide follows at most one, and its steps come after the procedure's in each section
(prepare, setup, loop, check, after, mistake). Forty-seven Max Lair guides share one procedure
and differ in their rental advice; five legendary-giant guides share the soft reset and one
False Swipe catcher build. Prep is therefore per game, not per guide: the prep list is every
build any guide of the family asks for, once.

### DLC is a property of a place

A location knows its DLC; a guide's DLC is derived from where its steps are and from DLC
requirements. Not a separate game: the Isle of Armor and the Crown Tundra are Sword.

### Validators keep it honest

Beside the schema's required source: a guide must target a variant its family offers
unlocked and have a curated encounter and odds for its method; every build and every item or
service requirement needs a farm chain; steps and farm sources must be at known locations;
and a build's moves and abilities are checked against PokeAPI's sword-shield learnsets at a
pinned commit (`data/derived/learnsets-swsh.json`), with abilities as they were in
Generation VIII. Fix the data, not the validator.

### Prep ticks are user data, per game

"Built the catcher", "have the Shiny Charm" are things I did, so they live in `user.db`:
`prep_tick(gameId, refId, doneAt)`, version 7, `MIGRATION_6_7` with a `MigrationTestHelper`
test, proven on `net.pokedex.debug` with real-shaped data. Per game, not per family: the Shiny
Charm in Sword is not the one in Shield. `refId` is a guide-layer id with no foreign key
(ADR 0002); a tick outlives a dataset that renames what it named. Backups carry
`settings.prep` additively (ADR 0007): nullable, because an empty list means "nothing ticked"
and a missing key, in every older file, says nothing.

The Shiny Charm flag is the same kind of fact, so it is a tick, not a setting. Odds always show
both figures (`MethodOdds.withCharm` beside the plain rate); the tick only changes which one
is emphasised.

### Upstream is corrected only by narrowing, with a source

Upstream lists identical availability for Sword and Shield. `availability-overrides.yaml`
clears `obtainable` for a version exclusive or a trade-only variant, or labels an online-only
legendary, citing Bulbapedia's tables. It may never widen upstream.

## Consequences

- The legendary ranking for a game (`legendaryPlan`) is odds first, then one the game reaches on
  its own before one that needs another player's game online, then where the game stands in my
  farm order, then slots and preset order, explained per row as ADR 0012's list is. Time
  per attempt and risk are said on the guide, not ranked: no source gives the first.
- Rental advice is drafted by `tools/curation/draft_swsh.py` from each boss's moves and the
  rental pool against the type chart, and read before it is committed.
- The next game's guides are a new `guides/<gameSet>.yaml`, not new code.
- `HuntGuide` became `Curation`, so "guide" means one thing.
