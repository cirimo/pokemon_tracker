import { DatabaseSync } from 'node:sqlite';
import { paths } from './config.js';

/**
 * How much of one game the curated layer covers, read from the built asset.
 *
 * This is the curation tool, not a validator: an uncurated variant is the expected state
 * of most of the dataset, and the app says "no method recorded yet" for it. What a
 * curation session needs is the list of what is left, so it can work through a game
 * and know when it is done.
 *
 * Only variants the game offers and that have a shiny at all are counted -- there is
 * nothing to curate for the rest.
 */
export interface Coverage {
  gameId: string;
  /** Obtainable in the game, shiny released. */
  total: number;
  /** At least one encounter row that is not shiny-locked. */
  withMethod: string[];
  /** Locked game-wide, or every encounter row is locked. Curated, and the answer is "no". */
  locked: string[];
  /** Neither: the planner will say "no method recorded yet". */
  missing: string[];
}

type Row = Record<string, unknown>;

export function coverageOf(gameId: string): Coverage {
  const db = new DatabaseSync(paths.assetDb, { readOnly: true });
  try {
    const known = db.prepare('SELECT COUNT(*) AS n FROM game WHERE id = ?').get(gameId) as Row;
    if (Number(known.n) === 0) throw new Error('unknown game ' + gameId + '; see data/curated/games.yaml');

    const rows = db
      .prepare(
        'SELECT v.id, ga.shinyLocked AS gameLocked, ' +
          '(SELECT COUNT(*) FROM encounter e WHERE e.variantId = v.id AND e.gameId = ga.gameId ' +
          '   AND e.shinyLocked = 0) AS open, ' +
          '(SELECT COUNT(*) FROM encounter e WHERE e.variantId = v.id AND e.gameId = ga.gameId) AS rows ' +
          'FROM game_availability ga JOIN variant v ON v.id = ga.variantId ' +
          'WHERE ga.gameId = ? AND ga.obtainable = 1 AND v.shinyReleased = 1 ' +
          'ORDER BY v.dexNum, v.id',
      )
      .all(gameId) as Row[];

    const coverage: Coverage = { gameId, total: rows.length, withMethod: [], locked: [], missing: [] };
    for (const r of rows) {
      const id = String(r.id);
      if (Number(r.open) > 0) coverage.withMethod.push(id);
      else if (Number(r.gameLocked) === 1 || Number(r.rows) > 0) coverage.locked.push(id);
      else coverage.missing.push(id);
    }
    return coverage;
  } finally {
    db.close();
  }
}

export function printCoverage(c: Coverage, options: { list: boolean }): void {
  const pct = (n: number) => (c.total === 0 ? 0 : Math.round((n / c.total) * 100));
  const out = process.stdout;
  out.write(c.gameId + ': ' + c.total + ' shiny-obtainable variants\n');
  out.write('  method recorded  ' + c.withMethod.length + ' (' + pct(c.withMethod.length) + '%)\n');
  out.write('  shiny-locked     ' + c.locked.length + ' (' + pct(c.locked.length) + '%)\n');
  out.write('  missing          ' + c.missing.length + ' (' + pct(c.missing.length) + '%)\n');
  if (options.list && c.missing.length > 0) {
    out.write('\nmissing:\n');
    for (const id of c.missing) out.write('  ' + id + '\n');
  }
}
