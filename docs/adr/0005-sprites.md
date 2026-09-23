# 0005 — Bundle one 256px shiny WebP set, and accept what that means legally

Status: accepted, 2026-09-22. Amended 2026-09-23 (the id trap).

## Context

The app makes no network calls at runtime, so every sprite it can ever show must be in
the APK. 1387 distinct variants are needed; the grid renders 28-48dp tiles and the detail
screen wants a hero image.

## Measurement

Source: `PokeAPI/sprites`, `sprites/pokemon/other/home/shiny/<id>.png`, 512x512, avg
132 KB. Measured over a 34-sprite sample, extrapolated to 1394:

| size | q70 | q80 | q90 | lossless |
|---|---|---|---|---|
| 96px | 4.6 MB | 5.1 MB | 6.2 MB | 11.0 MB |
| 128px | 6.6 MB | 7.3 MB | 8.8 MB | 16.6 MB |
| 160px | 8.5 MB | 9.5 MB | 11.5 MB | 22.7 MB |
| 256px | 15.1 MB | 16.8 MB | 20.6 MB | 44.6 MB |

Bundling is comfortably viable. On-demand fetch and cache was never a serious contender
once the numbers were in: it would trade a hard offline guarantee for ~15 MB.

## Decision

**One shiny-only set at 256px, WebP q80, about 15-17 MB.** One artifact, one size. Coil
downsamples for the grid and the same file is the detail hero, so there is no sizing
logic in the app and no second artifact in the pipeline. Non-shiny sprites are not
bundled; this is a shiny tracker.

## The id trap, which cost the most to find

**Amended 2026-09-23. The conclusion this section first drew was wrong, and the way it
was wrong is the useful part.**

What was measured on 2026-09-22 is still true:

- Resolving by `refs.pkApiFormId`: **1259 / 1387** files came back.
- Resolving by `refs.pkApiId`: **1387 / 1387** files came back.

The conclusion drawn from it, "resolve by `pkApiId`, it is 100%", shipped 364 sprites
that were copies of another variant's. Both numbers measured whether *a* file came back,
not whether it was the *right* file:

- For a cosmetic or gender form, the pokemon id **is the base species**. `unown-b`,
  `vivillon-garden` and `venusaur-f` all resolved -- to Unown A, Meadow Vivillon and
  male Venusaur. The Unown Dex box showed 28 identical silhouettes.
- `pkApiFormId` is a pokemon-**form** id, and the sprite repository is keyed by pokemon
  id. Its "1259 resolved" were largely other Pokémon: form 10196 is Original Cap Pikachu,
  pokemon 10196 is Gigantamax Charizard. As the second fallback it gave all seven cap
  Pikachu someone else's sprite.

The lesson: **measure correctness, not coverage.** A resolution rate cannot tell a right
picture from a wrong one. Comparing the bytes can.

### What PokéAPI actually holds

`other/home/` names form art by form, not by id, and the pipeline never asked for it:

- `shiny/<dex>-<form>.png` -- every cosmetic form: all 28 Unown, 20 Vivillon, 10
  Furfrou trims, the Flabébé line's flowers, the seasons, Burmy's cloaks, all 63 Alcremie.
  The form is the PokéAPI form slug minus the species prefix.
- `shiny/female/<id>.png` -- 98 of the 103 female forms. Four more (Meowstic, Indeedee,
  Basculegion, Oinkologne) have their own pokemon id.
- `shiny/<id>.png` -- species, regional forms, anything with its own pokemon id.

Existence is read from one git tree listing of the repository at a pinned commit, not from
HTTP status, and the listing's blob hashes are what was compared: exact bytes.

### Decision

Resolution order, first hit wins (`sprite-resolution.ts`):

1. a curated override in `data/curated/sprites.yaml`;
2. `shiny/female/<pkApiId>.png` for a gender form;
3. `shiny/<dex>-<form>.png`;
4. `shiny/<pkApiId>.png`.

`pkApiFormId` is never used. The build still fails on anything unresolved.

**The fallback is explicit.** Only an override can reach normal-colour art, and only for a
variant whose shiny was never released: the seven cap Pikachu, which cannot be shiny and
have no shiny render. Only an override can correct a wrong upstream link: upstream points
Eternal Flower Floette at Red Flower Floette, while PokéAPI's own entry is pokemon 10061.

**Correctness is a validator.** `sprite-shared` fails if two variants share sprite bytes
unless `sprites.yaml` lists them as one group with a citation that the art really is the
same. It fails on the set this ADR originally produced and passes on the regenerated one.
Genuinely identical art, as of this amendment:

| Group | Why | Checked against |
|---|---|---|
| Alcremie, 7 groups of 9 | A shiny's cream is always the same; only the sweet shows | Bulbapedia; PokéSprite's shiny icons split the same way |
| Minior, all 7 cores | Every core shares one shiny look in current HOME | Bulbapedia |
| Sinistea, Polteageist, Poltchageist, Sinistcha | Authentic and phony differ by a stamp underneath | Bulbapedia |
| Torchic and Torchic-F | The male's speck is on its rear; PokéAPI's female file is byte-identical | Bulbapedia |

Minior is the one where the sources disagreed. PokéAPI's red core is the black,
multicoloured-fleck render Bulbapedia describes; its other six cores are one identical
purple render that matches neither. All seven now use the red core's file.

`sprite-sources.json` in the pipeline records, per variant, the rule, the path and the
blob hash it came from, at the recorded PokéAPI commit. A rebuild re-encodes a sprite only
when that source changed. Previously the builder skipped any file that existed, so a fix
to resolution changed nothing until someone deleted the right files by hand.

## Licensing, stated plainly

The amendment adds no source: every file still comes from `PokeAPI/sprites`, so what
follows applies unchanged. PokéSprite was used only to cross-check Alcremie and Minior;
nothing from it is bundled.

`PokeAPI/sprites/LICENCE.txt` opens with *"All image contents within are Copyright The
Pokémon Company"* and then applies CC0 to the repository. A CC0 dedication by a party
that does not hold the rights does not transfer rights in the images. The honest reading:
the artwork is Nintendo/TPC copyright regardless of the repository's label.

For a personally sideloaded, single-user app this is ordinary fan-project territory and
bundling is fine. **It would not survive a Play Store listing.** That is a real boundary
on this app and it belongs here rather than being rediscovered later.

The PokéPC *data* is a separate and genuinely clean matter: MIT, redistributable with
attribution.

## Consequences

- The sprite set is 14.4 MB, and the amendment left it there: the same 1387 files,
  re-sourced. Measured 2026-09-23: debug APK 29.7 MB, before and after; release APK
  17.5 MB at M2, not rebuilt since. "About 27 MB" in the first version of this ADR was an
  estimate. Sprites go in Git LFS; `reference.db` is under 1 MB and stays a plain blob.
- Distribution is sideload only, permanently.
- Adding non-shiny sprites later is a pipeline flag and roughly +15 MB.
- `variant.spriteFile` is keyed on upstream's `nid`, not the PokéAPI id, so asset names
  survive an upstream remap of its PokéAPI references.
