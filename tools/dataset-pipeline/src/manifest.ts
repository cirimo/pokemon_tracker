import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import type { DatabaseSync } from 'node:sqlite';
import { paths } from './config.js';

/**
 * The reproducibility contract.
 *
 * The brief asked for byte-identical output, and the builder does everything needed for
 * that (fixed page size, sorted inserts, frozen timestamp, VACUUM last). But byte
 * identity is a property of the SQLite build that happens to be linked into Node, not a
 * property of this pipeline: SQLite writes a change counter into the file header, and a
 * version bump can legitimately reshuffle page layout.
 *
 * So CI asserts two things, in this order:
 *   1. the content manifest matches -- this is the real contract, and it is what
 *      actually guarantees "same inputs, same dataset";
 *   2. the .db is byte-identical -- a stricter check, allowed to fail loudly when the
 *      SQLite version moves, rather than quietly degrading.
 */

export interface DatasetManifest {
  datasetVersion: number;
  presetVersion: number;
  upstreamTag: string;
  upstreamSha: string;
  spriteSet: string;
  spriteSize: number;
  builtAt: string;
  contentHash: string;
  tables: Record<string, string>;
  sprites?: { count: number; totalBytes: number; hash: string };
}

/** Tables we hash. room_master_table is excluded: it is Room bookkeeping, not content. */
function contentTables(db: DatabaseSync): string[] {
  const rows = db
    .prepare(
      "SELECT name FROM sqlite_master WHERE type = 'table' " +
        "AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table' ORDER BY name",
    )
    .all() as { name: string }[];
  return rows.map((r) => r.name);
}

/**
 * SHA-256 per table over its canonically sorted rows, plus one hash over all of them.
 *
 * Rows are serialised as JSON with sorted keys and then sorted as strings, so neither
 * column order nor insertion order can change the hash.
 */
export function contentManifest(db: DatabaseSync): {
  tables: Record<string, string>;
  overall: string;
} {
  const tables: Record<string, string> = {};
  for (const table of contentTables(db)) {
    // dataset_meta holds the hash itself; hashing it would be self-referential.
    if (table === 'dataset_meta') continue;
    const rows = db.prepare('SELECT * FROM ' + table).all() as Record<string, unknown>[];
    const lines = rows
      .map((row) =>
        JSON.stringify(
          Object.keys(row)
            .sort()
            .reduce<Record<string, unknown>>((acc, k) => {
              acc[k] = row[k];
              return acc;
            }, {}),
        ),
      )
      .sort();
    tables[table] = createHash('sha256').update(lines.join('\n')).digest('hex');
  }

  const overall = createHash('sha256')
    .update(
      Object.keys(tables)
        .sort()
        .map((t) => t + ':' + tables[t])
        .join('\n'),
    )
    .digest('hex');

  return { tables, overall };
}

export function writeManifest(manifest: DatasetManifest): void {
  fs.mkdirSync(path.dirname(paths.manifest), { recursive: true });
  fs.writeFileSync(paths.manifest, JSON.stringify(manifest, null, 2) + '\n');
}

export function readManifest(): DatasetManifest | null {
  if (!fs.existsSync(paths.manifest)) return null;
  return JSON.parse(fs.readFileSync(paths.manifest, 'utf8')) as DatasetManifest;
}
