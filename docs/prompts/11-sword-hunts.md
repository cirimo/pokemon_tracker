# Prompt 11 — Sword: what only Sword gives me, the easy legendaries, and how to hunt them

> Run this in a fresh Claude Code session at the repo root, in **plan mode**, **after prompt 10**
> (the design refresh) so the new screens are built in the new language.
> Read `CLAUDE.md`, `docs/00-big-picture.md`, `docs/architecture.md` (§2, §3, §6),
> `docs/dataset-pipeline.md` (all of it, especially "Scope" and the validators),
> `docs/adr/0003-dataset-pipeline.md`, `docs/adr/0012-hunt-ranking.md`,
> `docs/adr/0014-game-order.md`, `docs/adr/0015-regular-catches.md`,
> `data/curated/odds-modifiers.yaml` (the Sword/Shield block), `data/curated/shiny-locks.yaml`,
> `data/curated/encounter-methods.yaml`, `data/curated/encounters/la.yaml` (the model for a
> curated game) and `tools/curation/draft_la.py`. Before any UI, **`docs/design-usage.md`** and
> whatever prompt 10 left in `docs/design-system.md` / `DESIGN.md`.

---

You are the feature engineer and the data curator on the **shiny living dex tracker**: an
Android app for one person (me) that mirrors my Pokémon HOME boxes.

## Where I am

I have started **Pokémon Sword**. Right now I am filling the Galar Pokédex to get the **Shiny
Charm**; when I have it, I start hunting in Sword. Before then I want to know exactly what is
worth hunting there and how, so I can prepare while I play.

My phone is connected over USB with my real records on it. Read them before deciding anything
about what I need: pull the newest backup from `/sdcard/PokemonList` **read-only**
(`adb pull`, with `MSYS_NO_PATHCONV=1` in Git Bash) and decode it with the backup code in
`:core:model`, or look at the app itself. Do not install, clear or run anything against
`net.pokedex`.

What the backup of 2026-11-15 said when this prompt was written: 766 of 1394 slots shiny (440
from Legends Z-A, 50 from Legends Arceus, 276 with no game recorded), none from Sword yet. My
games are Let's Go Eevee, Sword, Brilliant Diamond, Legends Arceus, Violet and Legends Z-A, and
the farm order is exactly that, so **Sword is second**: "Here first" gives Sword everything
Let's Go cannot. Re-read the newest backup; these numbers will have moved.

## What I want, in the order I would reach for it

1. **Only in Sword.** A detailed list of the slots I still need that I can get shiny in Sword and
   should get there: what it is, where and how it appears in Sword (area, weather, symbol or
   hidden, fishing, raid, fossil, egg, gift), whether it is a Sword version exclusive, whether it
   is shiny-locked, the best method and its odds with and without the charm.
2. **Easy legendaries in Sword.** Which legendaries Sword lets me shiny hunt at good odds, ranked
   by how easy they really are. Zygarde is the kind of thing I mean: Dynamax Adventures in the
   Crown Tundra are a flat 1/300, 1/100 with the Shiny Charm (already in
   `odds-modifiers.yaml`). Also every reset-based legendary that is *not* locked, and an honest
   list of the ones that are locked (Zacian, Zamazenta and Eternatus are curated; find the rest).
3. **An exact tutorial per hunt.** Step by step, as I would do it with the Switch in my hands:
   prerequisites (DLC, story progress, items, charm), the setup, the loop I repeat, how to tell
   it is shiny, what to do the moment it is, and the mistakes that lose it. For Dynamax
   Adventures that includes how the boss is chosen, saving a path and what it costs, the catch
   rules in the lair, and what happens to a non-shiny legendary I do not catch.
4. **What to farm first.** The Pokémon and the exact **sets** I should build before a hunt to make
   it faster or safer: species, ability, nature, moves, held item, level or EVs where they matter,
   and *why* each piece is there. Examples of the kind of thing: a False Swipe + sleep catcher for
   reset legendaries, a Synchronize lead for the nature I want, a Flame Body or Steam Engine
   hatcher and a foreign-language Ditto for Masuda, a fast multi-target sweeper for KO chains
   toward brilliant Pokémon.
5. **How to farm those.** For every prep Pokémon and item: where to catch or hatch it in Sword,
   which TR/TM or tutor gives the move and where that is, how to get the item, how to raise it
   quickly (Exp. Candy, Max Raids, Mints, Bottle Caps), and how to get things Sword alone cannot
   give (a foreign Ditto means a trade, say so).
6. **What to bring, per legendary.** A concrete example for each hunt: "for Zygarde, bring X with
   these moves, because …". Where I *cannot* bring my own (Dynamax Adventures use rentals), say
   that plainly and give rental guidance instead: which rentals to take against that boss and
   which to avoid, by type matchup and moves.

## What already exists

Read it before proposing anything.

- **Odds for Sword/Shield are curated** (`odds-modifiers.yaml`): Shiny Charm as +2 rolls, the
  Number Battled tiers for brilliant Pokémon, Masuda +5, and Dynamax Adventure as a denominator
  (1/300, 1/100 with the charm, replacing rather than adding). **Encounters for Sword are not**:
  `data/curated/encounters/` has `la.yaml` and `sv-s.yaml` only. The coverage report
  (`npm run coverage -- swsh-sw --list`) is the to-do list.
- **Locks for Sword/Shield** are three story legendaries. Everything else is unknown.
- `encounter-methods.yaml` already has `brilliant`, `raid`, `static`, `gift`, `breeding`,
  `fossil`, `fishing` and `dynamax-adventure` with Sword/Shield in mind; `wild` covers symbol and
  hidden encounters today. Say whether Sword needs more (weather-dependent spawns, hidden vs
  symbol, Max Raid dens) or whether those belong in `location`, `prerequisite` and `notes`.
- **"Only here" and "Here first"** (prompt 7, ADR 0014) already answer "what can I get shiny only
  in this one of my games" and "what is this game's job in my farm order". Both are only as good
  as the curation behind them; with Sword uncurated they cannot be trusted for Sword today.
- **The hunt list** (ADR 0012) ranks hunts by priority, method recorded in my games, slots
  filled, box nearly done. **Slot detail** has "In your games" with the recorded ways per game.
  **Progress** has a farm plan per game.
- **Nothing in the app holds prose longer than a `notes` field.** A tutorial with steps, a build
  with six fields and a "how to farm it" chain is a new kind of reference data.
- **Prompt 9 (the hunt engine)** is written but not built. Do not build it here; do not design
  anything it would have to undo. A guide and a counter will meet on the same screen one day.

## Think about these before you propose anything

Give me a recommendation and the trade-off for each. I approve quickly and override only the ones
I care about.

- **"Only in Sword" means what.** Among *my* games (the existing "Only here", so Sword must be in
  My games), among all Switch-era games (a true Sword/Shield exclusive in HOME terms), or
  Sword-not-Shield (version exclusives)? I think I mean the first, with the other two visible as
  labels. Check against my real data which answer changes the list.
- **DLC.** Ask me whether I own the Isle of Armor and the Crown Tundra before building anything
  that depends on them; most easy legendaries do. Model DLC as a prerequisite the guide shows,
  not as a separate game.
- **Curation scope and order.** Sword's full encounter table is large (base game, both DLCs,
  Wild Area weather). Propose an order that gets me value soonest: the legendaries and their
  locks first, then what "only in Sword" needs for slots I actually lack, then the rest. Write a
  `draft_swsh` drafter in `tools/curation/` like `draft_la.py` if the sources are shaped for it;
  it writes to `tools/curation/out/` for review, never into `data/curated` directly.
- **Where guides live.** A curated YAML layer (for example `data/curated/guides/swsh.yaml`) built
  into new `ReferenceDatabase` tables (guide, step, build, farm source), or bundled text assets?
  I expect the first, because it is versioned, validated and queryable, and because it keeps the
  "every row cites a source" rule. Say what the tables are, how a guide attaches to a hunt
  (`HuntKey` and game, never a slot position), and how a build is shared by several guides
  (one False Swipe catcher serves every reset legendary).
- **Validators.** What makes a guide wrong enough to refuse the build: a step with no source, a
  build that names a move the species cannot learn in Sword, a guide for a variant Sword does not
  offer or has locked, a farm source in a location that does not exist. Add them to the pipeline
  with the same "fix the data, not the validator" rule.
- **Accuracy.** This is the part that can quietly lie to me. Every encounter, lock, step and build
  cites a source (Bulbapedia and Serebii first; a well-known guide only where those are silent).
  Where sources disagree, say so in the row and in your report. Never fill a gap from memory; an
  unplaced row is a row in the "missing" count, not a guess.
- **Where it shows in the app.** A "Sword" view of its own (a game page reachable from My games,
  the hunt list or Progress), a guide screen reached from a hunt and from slot detail, and the
  existing filters made trustworthy by the curation. Say which destination owns the legendary
  list and the prep list, and whether "prep" is per game or per guide.
- **My progress in the prep.** Should I be able to tick off "built the False Swipe catcher",
  "have a foreign Ditto", "have the Shiny Charm"? That is user data, so `user.db` (v7, with a
  `Migration` and a `MigrationTestHelper` test) and a question for backups (ADR 0007, additive
  key). Or is it not worth a migration yet? Recommend.
- **Charm-aware odds.** I do not have the Shiny Charm yet. Show odds with and without it, or follow
  a "have the charm" flag per game? If a flag, where it lives (it is the same kind of fact as a
  prep tick).
- **Ranking legendaries.** "Easy" means what: odds per attempt, time per attempt, prerequisites,
  and risk of losing it. Propose a ranking you can explain in one line per legendary, the way
  ADR 0012 explains the hunt list.

## Ideas of my own, for you to judge

Tell me which are worth it now, which later, which not at all.

- **A Sword checklist** on Progress: charm, DLC, prep builds, legendaries done, "only in Sword"
  left.
- **Rental cheat sheet** for Dynamax Adventures as a compact card I can glance at during the
  rental pick, per boss.
- **"Hunt this next" in Sword** that combines the hunt list's ranking with the guide difficulty.
- **The same guide format for the next game** (Shield's exclusives later, or back to Violet), so
  Sword is the first instance of a pattern rather than a special case.

## Scope

In priority order:

1. Research and the decisions above, grounded in my real backup.
2. The guide data model: curated YAML schema, `ReferenceDatabase` entities, schema export
   (`./gradlew :core:data:assembleDebug` first), pipeline build and validators, domain types in
   `:core:model`, mappers, repository. A dataset regeneration is its own commit.
3. Sword curation in the approved order: legendaries and locks, their guides, the prep builds and
   how to farm them; then the encounters behind "only in Sword" for what I lack; then the coverage
   report's rest as far as the session gets, reporting the remaining count honestly.
4. The screens: the Sword legendary list, the guide (steps, builds, farm sources, what to bring),
   "only in Sword", and links from the hunt list and slot detail.
5. Prep ticks and the charm flag, if approved, with the `user.db` migration proven on
   `net.pokedex.debug` with real-shaped data first.
6. Whichever of my ideas you recommended for now.

Out of scope: the hunt engine (prompt 9), Shield's own exclusives beyond labelling them, other
games' curation, and any runtime network access.

## Constraints

- The layering rule, module table and "never do these" in CLAUDE.md hold. Never key on slot
  position. No foreign key across `reference.db` and `user.db`. Room entities stay in `:core:data`.
- **No network at runtime, ever.** Research and drafting happen in `tools/`, on this machine; the
  output is checked in. Never regenerate the dataset from a Gradle build.
- `ReferenceDatabase` keeps `fallbackToDestructiveMigration`; `UserDatabase` never gets it. Any
  `user.db` change ships with a `Migration` and a `MigrationTestHelper` test in the same commit.
- The dataset invariants (52 boxes, 1394 slots, 1387 variants, 7 duplicates) must still hold;
  the shipped `reference.db` test must still pass.
- Design: gold means shiny and nothing else, so difficulty, steps, a "locked" label or an "easy"
  badge get no gold. New components go in `:design-system` with a gallery entry, `designCheck`,
  contrast in both themes, and goldens recorded by the `record screenshots` workflow. Long guide
  text must read well at 200% and through TalkBack.
- Nothing new in the box pager's tiles; its P99 must not get worse.
- **My phone holds my real records.** Read the backup only. Nothing runs against `net.pokedex`
  except a release install, and if the permission check refuses to let you run it, hand me the
  command for **PowerShell**: `$env:ANDROID_SERIAL = "<serial>"; .\gradlew.bat :app:installRelease`,
  after a fresh backup exists in `/sdcard/PokemonList`. Look at work on `net.pokedex.debug`.
- Copyright: summarise sources in your own words and link them; do not paste guide text.

## How to run the session

1. **Research, then stop.** Read my backup, the code and the curated data above. Research Sword's
   legendaries, locks, Dynamax Adventure mechanics, and the prep builds from the sources, with
   links. Present: the three "only in Sword" readings with what each gives me from my real data,
   the legendary ranking with one line each and its source, a sample of one complete guide
   (Zygarde, or whichever ranks first) with its builds and farm chain, the data model, the
   questions above with a recommendation each, and a build order. Make every question answerable
   with "yes to your recommendation". **Stop for my approval.**
2. **Build** in the approved order. Install the debug build on the emulator and my phone and read
   every guide screen in both themes and at 200% text before saying it works. Spot-check five
   curated rows against their sources and say which.
3. **Close out.** Update `docs/dataset-pipeline.md` ("Scope", validators, the guide layer),
   `docs/architecture.md` (§3, §4 ERD if `user.db` changed, §6), `docs/design-system.md` for new
   components, `docs/00-big-picture.md`'s prompt list, and write an ADR for the guide layer's
   shape.

Every commit builds: `./gradlew test detekt lintDebug :design-system:designCheck assembleDebug`.
One logical change per commit, imperative subject under 72 characters, no type prefixes.
