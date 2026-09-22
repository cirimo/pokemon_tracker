import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));

export const repoRoot = path.resolve(here, '../../..');
export const pipelineRoot = path.resolve(here, '..');

/**
 * Upstream is pinned to an exact tag, never a branch.
 *
 * Two reasons. The obvious one is reproducibility. The less obvious one is that the
 * upstream repo carries a `data-next/` tree alongside `data/` -- an in-flight schema
 * rewrite -- so tracking main would eventually pull a different shape without warning.
 * Bump this deliberately, run `npm run build:dataset`, and read the diff.
 */
export const UPSTREAM = {
  repo: 'https://github.com/pokepc/dataset.git',
  tag: '6.8.2',
  /** MIT. Verified 2026-09-22. Attribution is reproduced in docs/dataset-pipeline.md. */
  license: 'MIT',
} as const;

export const PRESET = {
  /** PokePC "Grouped by Regions (Optimized)". The spine of the whole data model. */
  id: 'grouped-balanced',
  gameSet: 'home',
  /** Asserted by the validators. If upstream changes these, we want a loud failure. */
  expectedBoxCount: 52,
  expectedFilledSlotCount: 1394,
  expectedDistinctVariantCount: 1387,
  /** unown, vivillon, flabebe, floette, florges, furfrou, alcremie. */
  expectedDuplicateCount: 7,
} as const;

export const SPRITES = {
  repo: 'https://raw.githubusercontent.com/PokeAPI/sprites/master',
  /**
   * Resolve by pkApiId FIRST. PokeAPI sprite files are keyed by *pokemon* id, not
   * *pokemon-form* id: keying on pkApiFormId silently loses 128 of 1387 variants
   * (all of Alcremie, the Hisuian forms, Paldean Tauros). pkApiId covers 1387/1387.
   */
  set: 'sprites/pokemon/other/home/shiny',
  size: 256,
  quality: 80,
} as const;

/**
 * The assets live in :core:data, not :app.
 *
 * The module that owns ReferenceDatabase owns the file it is populated from, and an
 * AAR asset is merged into the app at packaging time, so :app gets it for free. The
 * practical payoff is that the Room instrumentation test in :core:data can open the
 * asset we actually ship rather than a copy of it.
 */
export const paths = {
  upstream: path.join(pipelineRoot, '.upstream'),
  curated: path.join(repoRoot, 'data/curated'),
  roomSchema: path.join(
    repoRoot,
    'core/data/schemas/net.pokedex.core.data.reference.ReferenceDatabase',
  ),
  assetDb: path.join(repoRoot, 'core/data/src/main/assets/dataset/reference.db'),
  assetSprites: path.join(repoRoot, 'core/data/src/main/assets/sprites'),
  manifest: path.join(repoRoot, 'core/data/src/main/assets/dataset/dataset-manifest.json'),
} as const;

/** Bump when the emitted content changes in a way the app should notice. */
export const DATASET_VERSION = 1;
export const PRESET_VERSION = 1;
