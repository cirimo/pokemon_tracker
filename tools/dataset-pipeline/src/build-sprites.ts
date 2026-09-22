import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { SPRITES, paths } from './config.js';
import { readPokemon } from './upstream.js';

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
 * We ship ONE set at 256px q80, about 17 MB. Coil downsamples it for the 28-48dp grid
 * tiles and the same file is the detail hero, so there is one artifact and no sizing
 * logic in the app.
 *
 * THE ID TRAP: PokeAPI sprite files are keyed by *pokemon* id, not *pokemon-form* id.
 * Resolving by refs.pkApiFormId covers only 1259 of 1387 variants -- all of Alcremie,
 * every Hisuian form, Paldean Tauros and the -f gender forms fail, and no fallback
 * sprite set closes the gap. Resolving by refs.pkApiId covers 1387 of 1387.
 */

export interface SpriteResult {
  written: number;
  skipped: number;
  unresolved: string[];
  totalBytes: number;
}

function spriteUrl(pokeApiId: string): string {
  return SPRITES.repo + '/' + SPRITES.set + '/' + pokeApiId + '.png';
}

async function fetchPng(url: string): Promise<Buffer | null> {
  const response = await fetch(url);
  if (!response.ok) return null;
  return Buffer.from(await response.arrayBuffer());
}

export async function buildSprites(options: { limit?: number } = {}): Promise<SpriteResult> {
  // Imported lazily: sharp is the pipeline's only native dependency, and the
  // reference-db build must stay runnable without it.
  const sharp = (await import('sharp')).default;

  const db = new DatabaseSync(paths.assetDb, { readOnly: true });
  const variants = db.prepare('SELECT id, nid, spriteFile FROM variant ORDER BY id').all() as {
    id: string;
    nid: string;
    spriteFile: string;
  }[];
  db.close();

  const targets = options.limit ? variants.slice(0, options.limit) : variants;
  fs.mkdirSync(paths.assetSprites, { recursive: true });

  const result: SpriteResult = { written: 0, skipped: 0, unresolved: [], totalBytes: 0 };

  for (const variant of targets) {
    const out = path.join(paths.assetSprites, path.basename(variant.spriteFile));
    if (fs.existsSync(out) && fs.statSync(out).size > 0) {
      result.skipped++;
      result.totalBytes += fs.statSync(out).size;
      continue;
    }

    const upstream = readPokemon(variant.id);
    // pkApiId first. See the class comment -- this order is the whole finding.
    const candidates = [upstream.refs.pkApiId, upstream.refs.pkApiFormId].filter(
      (c): c is string => !!c,
    );

    let png: Buffer | null = null;
    for (const candidate of candidates) {
      png = await fetchPng(spriteUrl(candidate));
      if (png) break;
    }
    if (!png) {
      result.unresolved.push(variant.id);
      continue;
    }

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
      result.unresolved.length +
        ' variants have no sprite in ' +
        SPRITES.set +
        ':\n  ' +
        result.unresolved.slice(0, 20).join('\n  '),
    );
  }

  return result;
}
