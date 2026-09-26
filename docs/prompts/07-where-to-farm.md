# Prompt 7 — Where do I farm it: game order, "only here", and a farm plan per game

> Run this in a fresh Claude Code session at the repo root, in **plan mode**.
> Read `CLAUDE.md`, `docs/architecture.md` (§6 and §8), `docs/adr/0010-search-is-a-mode.md`,
> `docs/adr/0012-hunt-ranking.md`, `docs/adr/0013-browse-context.md` and, before any UI,
> **`docs/design-usage.md`**.

---

You are the feature engineer on the **shiny living dex tracker**: an Android app for one person
(me) that mirrors my Pokémon HOME boxes. Prompt 6 (browsing between slots) has shipped.

## How I actually hunt

I farm game by game, in an order that is about how easy each game makes it:

1. **Legends Z-A.** Done: I have caught everything I can there.
2. **Legends Arceus.** Next.
3. **Violet.**
4. Everything else.

So the question I keep asking the app is not "where can I get this shiny", which it already
answers, but **"which of the slots I still need should I farm in *this* game?"** That has two
halves:

- **Only here.** The slots that, among my games, can be shiny in exactly one. If Violet is the
  only one of my games with a shiny Varoom, I farm it in Violet or not at all.
- **Here first.** Walk my games in my order. Each needed slot belongs to the first game that
  offers it shiny. Anything Arceus can give me is an Arceus job, even if Violet also has it;
  what is left for Violet is what Arceus cannot give me.

I want to filter by both, and see both per game.

## What already exists

Read it before proposing anything.

- **My games** (M4) is a set of `GameId`s in the `my_game` table of `user.db` (v4). It has **no
  order**. Backups carry it as `settings.myGames`, added without a schema bump (ADR 0007).
- `DexEntry.shinyGames` is the set of games where a variant is obtainable and not shiny-locked,
  per game rather than per pair (owning Scarlet does not make a Violet exclusive reachable).
  `standingOf(dex, variant, game)` gives the per-game standing.
- **Search** is a mode of the box screen (ADR 0010). Its filter is a `DexFilter` in the box
  screen's `SavedStateHandle`. "Get it shiny in" filters by game **pair**, any-of.
- The **hunt list** (`huntPlan`, ADR 0012) filters to one of my games or all of them, with the
  same "game only if still mine" rule as `huntGames`.
- **Progress** has "Needed in <game pair>", which opens search on that pair.
- **Slot detail** has "In your games": one line per game of mine with its standing and the
  recorded ways.
- **Browsing** (ADR 0013) carries a `Browse` context in the route and recomputes the list with
  the same `:core:model` function. Any new filter has to be expressible there, or browsing
  from it breaks.

## Think about these before you propose anything

Give me a recommendation and the trade-off for each. I approve quickly and override only the
ones I care about.

- **Game order.** Where it is stored (a rank column on `my_game`, or a list in settings), how I
  set it (up and down buttons, or drag; M4 said no drag for priority, is this different?),
  and what the default is for games added before an order existed. It is a `user.db` bump, so
  it needs a `Migration`, a `MigrationTestHelper` test, and a backup answer: is an ordered list
  in `settings.myGames` additive, and what does an older build do with it?
- **"Only here": among my games, or among all games?** "Only in Violet among all Switch games" is
  a different question from "only in Violet among the games I own". I think I mean the second,
  but tell me which one, and whether both are worth a switch.
- **Pairs versus versions.** "Only here" is per game, so Scarlet and Violet are two games. A
  Violet exclusive I cannot get because I do not own Scarlet is a different kind of problem;
  say where it shows up.
- **Evolutions.** A slot I get by evolving something farmed in Violet. Does it count as "Violet"
  for "only here" and "here first"? Check what `shinyGames` actually contains for evolved forms
  before deciding, and do not guess.
- **"Here first" is derived, never stored.** Like progress, it is a function of the dex, my
  games, their order and my records. Confirm that, and say what happens to "here first" for a
  slot I have just caught.
- **Marking by hand.** Sometimes I will want to farm something in Violet even though Arceus has
  it: a better method, a nicer form. Is a per-slot "I will get this in <game>" override worth a
  column, or does it fight the "derived, never stored" rule? If you recommend it, it belongs on
  the catch record, keyed by `CatchKey`, never by position.
- **Where the filters live.** A "Farm in" section in the search sheet ("Only here" / "Here first"
  per game)? A mode of the hunt list's game chips? Both? Remember browsing: a `Browse.Search`
  filter has to carry the new fields, and an older encoded filter must still decode.
- **A per-game farm plan.** Progress by game could say "Arceus: 41 only here, 88 here first",
  and tapping a number opens that list. Is that the right home, or does the hunt list already
  do it?

## Ideas of my own, for you to judge

Tell me which are worth it now, which later, and which not at all.

- **Slot detail says where it belongs.** One line under the name: "Farm in Violet, the only one
  of your games" or "Farm in Legends Arceus, first of your games". The per-game section stays.
- **The hunt list follows the order.** With the order known, the "all my games" list could group
  by the game each hunt belongs to, in my order, instead of one ranking across all of them.
- **A "trade or buy" list.** Needed slots that are shiny only in games I do not own, grouped by
  game. It is the out-of-reach list with a question attached: which one extra game would fill
  the most slots?
- **Done with a game.** When "here first" for a game reaches zero, say so on Progress, the way a
  finished box is celebrated. Not gold (gold means shiny): the celebration rules in
  `docs/design-usage.md` apply.

## Scope

In priority order:

1. Game order: storage, migration and test, backup, and the My games UI to set it.
2. `farmPlan` in `:core:model` (or whatever you name it): "only here" and "here first" per slot,
   derived, with JVM tests. Cover a slot shiny in one of my games; in two, where the order
   decides; in none; a pair split across versions; an evolution; and a slot caught meanwhile.
3. The filters in search, including browsing from them (a `Browse.Search` filter carries them;
   an older filter still decodes).
4. The farm line on slot detail, and the per-game counts on Progress or the hunt list, as you
   recommended.
5. Whichever of my ideas you recommended for now.

Out of scope: the non-shiny dex (prompt 8) and the hunt engine (M5).

## Constraints

- The layering rule, module table and "never do these" in CLAUDE.md hold. Never key anything on
  slot position.
- `user.db` changes: a `Migration` and a `MigrationTestHelper` test in the same commit, never
  `fallbackToDestructiveMigration`. Nothing runs against `net.pokedex` except `installRelease`;
  migrations are proven on `net.pokedex.debug` first, with real-shaped data. With the emulator
  and the phone both attached, pass `ANDROID_SERIAL` to every Gradle device task.
- Search stays within its budget: 5 ms per keystroke over 1394 slots. "Here first" must not add
  a per-keystroke walk of my games per slot. Precompute what does not change per keystroke.
- Nothing new in the box pager's tiles. The pager's P99 must not get worse.
- Design: gold means shiny and nothing else. New or changed `:design-system` components get a
  gallery entry, `designCheck`, and screenshots recorded by the workflow.

## How to run the session

1. **Research, then stop.** Read the code above, run the app on the emulator, and answer the
   evolution question from the data rather than from memory. Present the questions with a
   recommendation each, and a build order. Stop for my approval.
2. **Build** in the approved order. Install and look on the phone (debug build) and emulator
   before saying anything works, in both themes and at 200% text.
3. **Close out.** Update `docs/architecture.md` and ADR 0007 if the backup shape changes, and
   write an ADR for the game order if it is a decision with real alternatives.

Every commit builds: `./gradlew test detekt lintDebug :design-system:designCheck assembleDebug`.
One logical change per commit, imperative subject under 72 characters, no type prefixes.
