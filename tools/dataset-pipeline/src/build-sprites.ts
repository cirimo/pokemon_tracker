import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { SPRITES, paths } from './config.js';
import { loadCuratedLayer } from './curated.js';
import { readPokemon } from './upstream.js';
import { spriteCandidates, type SpriteRule } from './sprite-resolution.js';

/**
 * Downloads and encodes the shiny sprite set.
 *
 * Measured on a 34-sprite sample from PokeAPI other/home/shiny (512x512 PNG, avg
 * 132 KB), extrapolated to 1394 slots:
 *
 *     size   q70      q80      q90      lossless
 *     96px   4.6 MB   5.1 MB   6.2 MB   11.0 MB
 *     128px  6.6 MB   7.3 MB   8.8 MB   16.6 MB
 *     256px  15.1 MB  16.8 MB  20.6 MB  44.6 MB
 *
 * We ship ONE set at 256px q80, about 15 MB. Coil downsamples it for the 28-48dp grid
 * tiles and the same file is the detail hero, so there is one artifact and no sizing
 * logic in the app.
 *
 * Which file each variant gets is sprite-resolution.ts. This file only fetches.
 *
 * Existence is answered from one git tree listing of PokeAPI/sprites at a pinned commit,
 * not from HTTP status codes. That listing also carries each file's blob hash, which is
 * what sprite-sources.json records: a sprite is re-encoded only when the file it comes
 * from changed, so deleting assets by hand is never how a fix reaches the set.
 */

export interface SpriteResult {
  written: number;
  skipped: number;
  unresolved: string[];
  totalBytes: number;
  rules: Record<SpriteRule, number>;
}

interface SpriteSource {
  rule: SpriteRule;
  path: string;
  blob: string;
}

interface SpriteSources {
  repo: string;
  commit: string;
  sprites: Record<string, SpriteSource>;
}

async function githubJson(url: string): Promise<any> {
  const response = await fetch(url, { headers: { Accept: 'application/vnd.github+json' } });
  if (!response.ok) throw new Error(url + ': HTTP ' + response.status);
  return response.json();
}

/** Blob hash per path under other/home/, at the commit the branch points to now. */
async function listHomeSprites(): Promise<{ commit: string; blobs: Map<string, string> }> {
  const api = 'https://api.github.com/repos/' + SPRITES.repo;
  const commit = (await githubJson(api + '/commits/' + SPRITES.branch)).sha as string;
  let tree = (await githubJson(api + '/git/commits/' + commit)).tree.sha as string;
  // The whole repository is too large for one recursive listing, which GitHub truncates.
  for (const part of SPRITES.home.split('/')) {
    const listing = await githubJson(api + '/git/trees/' + tree);
    const entry = listing.tree.find((e: { path: string }) => e.path === part);
    if (!entry) throw new Error(SPRITES.home + ' not found in PokeAPI/sprites@' + commit);
    tree = entry.sha;
  }
  const listing = await githubJson(api + '/git/trees/' + tree + '?recursive=1');
  if (listing.truncated) throw new Error('the ' + SPRITES.home + ' listing was truncated');
  const blobs = new Map<string, string>();
  for (const e of listing.tree as { path: string; sha: string; type: string }[]) {
    if (e.type === 'blob') blobs.set(e.path, e.sha);
  }
  return { commit, blobs };
}

function readSources(): SpriteSources | null {
  if (!fs.existsSync(paths.spriteSources)) return null;
  return JSON.parse(fs.readFileSync(paths.spriteSources, 'utf8')) as SpriteSources;
}

function writeSources(sources: SpriteSources): void {
  const sorted = Object.fromEntries(
    Object.entries(sources.sprites).sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)),
  );
  fs.writeFileSync(
    paths.spriteSources,
    JSON.stringify({ ...sources, sprites: sorted }, null, 2) + '\n',
  );
}

export async function buildSprites(options: { limit?: number } = {}): Promise<SpriteResult> {
  // Imported lazily: sharp is the pipeline's only native dependency, and the
  // reference-db build must stay runnable without it.
  const sharp = (await import('sharp')).default;

  const db = new DatabaseSync(paths.assetDb, { readOnly: true });
  const variants = db
    .prepare('SELECT id, spriteFile FROM variant ORDER BY id')
    .all() as { id: string; spriteFile: string }[];
  db.close();

  const overrides = new Map(
    loadCuratedLayer().sprites.overrides.map((o) => [o.variantId, o.file]),
  );
  const { commit, blobs } = await listHomeSprites();
  const previous = readSources();
  const sources: SpriteSources = {
    repo: SPRITES.repo,
    commit,
    sprites: { ...(previous?.sprites ?? {}) },
  };

  const targets = options.limit ? variants.slice(0, options.limit) : variants;
  fs.mkdirSync(paths.assetSprites, { recursive: true });

  const result: SpriteResult = {
    written: 0,
    skipped: 0,
    unresolved: [],
    totalBytes: 0,
    rules: { override: 0, female: 0, form: 0, pokemon: 0 },
  };

  for (const variant of targets) {
    const upstream = readPokemon(variant.id);
    const chosen = spriteCandidates(upstream, overrides.get(variant.id)).find((c) =>
      blobs.has(c.path),
    );
    if (!chosen) {
      result.unresolved.push(variant.id);
      continue;
    }
    result.rules[chosen.rule]++;
    const source: SpriteSource = { ...chosen, blob: blobs.get(chosen.path)! };
    sources.sprites[variant.id] = source;

    const out = path.join(paths.assetSprites, path.basename(variant.spriteFile));
    const before = previous?.sprites[variant.id];
    if (before?.blob === source.blob && fs.existsSync(out) && fs.statSync(out).size > 0) {
      result.skipped++;
      result.totalBytes += fs.statSync(out).size;
      continue;
    }

    const url = 'https://raw.githubusercontent.com/' + SPRITES.repo + '/' + commit + '/' +
      SPRITES.home + '/' + chosen.path;
    const response = await fetch(url);
    if (!response.ok) throw new Error(url + ': HTTP ' + response.status);
    const png = Buffer.from(await response.arrayBuffer());

    const webp = await sharp(png)
      .resize(SPRITES.size, SPRITES.size, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
      .webp({ quality: SPRITES.quality, effort: 6 })
      .toBuffer();

    fs.writeFileSync(out, webp);
    result.written++;
    result.totalBytes += webp.byteLength;
  }

  // An unresolved sprite is a build failure, not a placeholder. A missing tile in the
  // grid is exactly the kind of thing that gets shipped and then lived with.
  if (result.unresolved.length > 0) {
    throw new Error(
      result.unresolved.length + ' variants have no sprite in PokeAPI ' + SPRITES.home +
        ' (add a curated override in data/curated/sprites.yaml if one is right):\n  ' +
        result.unresolved.slice(0, 20).join('\n  '),
    );
  }

  writeSources(sources);
  return result;
}
