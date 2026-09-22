# 0005 — Bundle one 256px shiny WebP set, and accept what that means legally

Status: accepted, 2026-09-22

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

PokéAPI sprite files are keyed by **pokemon** id, not **pokemon-form** id.

- Resolving by `refs.pkApiFormId`: **1259 / 1387** (91%). The 128 failures are all of
  Alcremie's 63 combinations, every Hisuian form, Paldean Tauros, the `-f` gender forms
  and the Galarian birds. No fallback set closes the gap -- `official-artwork`,
  `showdown` and the gen-5 set together leave 119 unresolved.
- Resolving by `refs.pkApiId`: **1387 / 1387** (100%) from `other/home/shiny` alone.

The pipeline tries `pkApiId` then `pkApiFormId`, and **fails the build** on any
unresolved variant. A missing tile is exactly the kind of defect that ships and then gets
lived with.

## Licensing, stated plainly

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

- APK about 27 MB. Sprites go in Git LFS; `reference.db` is under 1 MB and stays a plain
  blob.
- Distribution is sideload only, permanently.
- Adding non-shiny sprites later is a pipeline flag and roughly +15 MB.
- `variant.spriteFile` is keyed on upstream's `nid`, not the PokéAPI id, so asset names
  survive an upstream remap of its PokéAPI references.
