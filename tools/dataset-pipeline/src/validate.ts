import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { PRESET, paths } from './config.js';
import { loadCuratedLayer } from './curated.js';
import { loadRoomSchema } from './room-schema.js';
import { sharedSpriteFindings } from './sprite-resolution.js';

/**
 * Output validation. Every check here has failed for real at least once in some
 * project, or guards a number the app hard-codes.
 *
 * The rule: any failure is a non-zero exit. There is no warning level, because a
 * dataset that is "mostly right" is worse than one that refuses to ship.
 */

export interface Finding {
  check: string;
  detail: string;
}

type Row = Record<string, unknown>;

function count(db: DatabaseSync, sql: string, ...params: unknown[]): number {
  const row = db.prepare(sql).get(...(params as never[])) as Row | undefined;
  if (!row) return 0;
  return Number(Object.values(row)[0] ?? 0);
}

export function validate(options: { requireSprites?: boolean } = {}): Finding[] {
  const findings: Finding[] = [];
  const fail = (check: string, detail: string) => findings.push({ check, detail });

  if (!fs.existsSync(paths.assetDb)) {
    return [{ check: 'asset-exists', detail: 'reference.db has not been built' }];
  }

  const db = new DatabaseSync(paths.assetDb, { readOnly: true });

  try {
    // --- Room will refuse the asset without these, with no useful message ---------
    const schema = loadRoomSchema();
    const master = db
      .prepare('SELECT identity_hash AS h FROM room_master_table WHERE id = 42')
      .get() as Row | undefined;
    if (!master) {
      fail('room-identity', 'room_master_table has no row 42');
    } else if (master.h !== schema.identityHash) {
      fail(
        'room-identity',
        'identity hash ' + master.h + ' does not match the compiled schema ' +
          schema.identityHash + '. Rebuild after `./gradlew :core:data:assembleDebug`.',
      );
    }
    const userVersion = count(db, 'PRAGMA user_version');
    if (userVersion !== schema.version) {
      fail('user-version', 'PRAGMA user_version is ' + userVersion + ', expected ' + schema.version);
    }

    // --- the numbers the app and the docs both assert ----------------------------
    const boxes = count(db, 'SELECT COUNT(*) FROM box WHERE presetId = ?', PRESET.id);
    if (boxes !== PRESET.expectedBoxCount) {
      fail('box-count', 'got ' + boxes + ', expected ' + PRESET.expectedBoxCount);
    }

    const slots = count(db, 'SELECT COUNT(*) FROM slot WHERE presetId = ?', PRESET.id);
    if (slots !== PRESET.expectedFilledSlotCount) {
      fail('slot-count', 'got ' + slots + ', expected ' + PRESET.expectedFilledSlotCount);
    }

    const distinct = count(
      db,
      'SELECT COUNT(DISTINCT variantId) FROM slot WHERE presetId = ?',
      PRESET.id,
    );
    if (distinct !== PRESET.expectedDistinctVariantCount) {
      fail(
        'distinct-variants',
        'got ' + distinct + ', expected ' + PRESET.expectedDistinctVariantCount,
      );
    }

    // The seven duplicates are the reason copyIndex exists. If this number moves,
    // the app is about to over- or under-count a living dex.
    const duplicates = count(
      db,
      'SELECT COUNT(*) FROM slot WHERE presetId = ? AND copyIndex = 1',
      PRESET.id,
    );
    if (duplicates !== PRESET.expectedDuplicateCount) {
      fail(
        'duplicate-slots',
        'got ' + duplicates + ' slots with copyIndex=1, expected ' +
          PRESET.expectedDuplicateCount +
          '. If upstream changed the preset this is a real dataset change, not a bug -- ' +
          'update PRESET in config.ts and the numbers in docs/architecture.md together.',
      );
    }

    // copyIndex must be gapless per variant, or a slot resolves to a record key that
    // nothing can ever fill.
    const gappy = db
      .prepare(
        'SELECT variantId, COUNT(*) AS n, MAX(copyIndex) AS maxCopy FROM slot ' +
          'WHERE presetId = ? GROUP BY variantId HAVING maxCopy != n - 1',
      )
      .all(PRESET.id) as Row[];
    for (const row of gappy) {
      fail('copy-index-gap', String(row.variantId) + ' has non-contiguous copy indices');
    }

    // --- referential integrity ----------------------------------------------------
    const orphanSlots = count(
      db,
      'SELECT COUNT(*) FROM slot LEFT JOIN variant ON slot.variantId = variant.id ' +
        'WHERE variant.id IS NULL',
    );
    if (orphanSlots > 0) {
      fail('slot-resolves', orphanSlots + ' slots reference a variant that is not in the dataset');
    }

    const orphanVariants = count(
      db,
      'SELECT COUNT(*) FROM variant LEFT JOIN species ON variant.dexNum = species.dexNum ' +
        'WHERE species.dexNum IS NULL',
    );
    if (orphanVariants > 0) {
      fail('variant-species', orphanVariants + ' variants have no species row');
    }

    for (const [table, column, parent, parentKey] of [
      ['game_availability', 'variantId', 'variant', 'id'],
      ['game_availability', 'gameId', 'game', 'id'],
      ['encounter', 'variantId', 'variant', 'id'],
      ['encounter', 'gameId', 'game', 'id'],
      ['encounter', 'methodId', 'encounter_method', 'id'],
      ['odds_modifier', 'gameId', 'game', 'id'],
      ['odds_modifier', 'methodId', 'encounter_method', 'id'],
    ] as const) {
      const orphans = count(
        db,
        'SELECT COUNT(*) FROM ' + table + ' LEFT JOIN ' + parent +
          ' ON ' + table + '.' + column + ' = ' + parent + '.' + parentKey +
          ' WHERE ' + parent + '.' + parentKey + ' IS NULL',
      );
      if (orphans > 0) {
        fail(
          'curated-orphan',
          orphans + ' rows in ' + table + '.' + column + ' do not resolve to ' + parent +
            '. An upstream removal can cause this -- fix the curated YAML, do not drop the check.',
        );
      }
    }

    // A shiny lock on something with no shiny at all is a curation mistake.
    const impossibleLocks = count(
      db,
      'SELECT COUNT(*) FROM game_availability ga JOIN variant v ON ga.variantId = v.id ' +
        'WHERE ga.shinyLocked = 1 AND v.shinyReleased = 0',
    );
    if (impossibleLocks > 0) {
      fail('lock-sanity', impossibleLocks + ' shiny locks apply to variants with no shiny released');
    }

    // Search matches it and TalkBack reads it; two identical names are two rows nobody can
    // tell apart. Upstream 6.8.2 named four Unown letters plain "Unown".
    const sameNames = db
      .prepare(
        "SELECT displayName, GROUP_CONCAT(id, ', ') AS ids FROM variant " +
          'GROUP BY displayName HAVING COUNT(*) > 1',
      )
      .all() as Row[];
    for (const row of sameNames) {
      fail('display-name-unique', '"' + String(row.displayName) + '" names ' + String(row.ids));
    }

    const spriteCuration = loadCuratedLayer().sprites;
    const known = new Set(
      (db.prepare('SELECT id FROM variant').all() as Row[]).map((r) => String(r.id)),
    );
    for (const id of [
      ...spriteCuration.overrides.map((o) => o.variantId),
      ...spriteCuration.shared.flatMap((g) => g.variants),
    ]) {
      if (!known.has(id)) fail('curated-orphan', 'sprites.yaml names ' + id + ', which is not a variant');
    }

    const metaRows = count(db, 'SELECT COUNT(*) FROM dataset_meta');
    if (metaRows !== 1) fail('dataset-meta', 'expected exactly one row, got ' + metaRows);

    // --- sprites ------------------------------------------------------------------
    if (options.requireSprites) {
      const variants = db.prepare('SELECT id, spriteFile FROM variant').all() as Row[];
      let missing = 0;
      for (const v of variants) {
        const file = path.join(paths.assetSprites, path.basename(String(v.spriteFile)));
        if (!fs.existsSync(file) || fs.statSync(file).size === 0) missing++;
      }
      if (missing > 0) {
        fail('sprite-present', missing + ' of ' + variants.length + ' variants have no sprite file');
      }

      // Measures correctness where sprite-present measures coverage: a variant showing
      // another variant's picture is found here, and nowhere else.
      const hashes = new Map<string, string>();
      for (const v of variants) {
        const file = path.join(paths.assetSprites, path.basename(String(v.spriteFile)));
        if (!fs.existsSync(file)) continue;
        hashes.set(String(v.id), createHash('sha256').update(fs.readFileSync(file)).digest('hex'));
      }
      for (const f of sharedSpriteFindings(hashes, spriteCuration.shared)) {
        fail('sprite-shared', f.variants.join(', ') + ' ' + f.detail);
      }
    }
  } finally {
    db.close();
  }

  return findings;
}

export function reportAndExit(findings: Finding[]): void {
  if (findings.length === 0) {
    process.stdout.write('dataset validation: all checks passed\n');
    return;
  }
  process.stderr.write('dataset validation FAILED (' + findings.length + '):\n');
  for (const f of findings) {
    process.stderr.write('  [' + f.check + '] ' + f.detail + '\n');
  }
  process.exitCode = 1;
}
