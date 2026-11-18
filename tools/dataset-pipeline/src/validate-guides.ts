import type { DatabaseSync } from 'node:sqlite';

/**
 * Checks on the prompt 11 additions: the guide layer, availability overrides and per-encounter
 * odds exclusions. Same rule as validate.ts: a failure is the data being wrong, so fix the
 * data, never the check.
 */
type Row = Record<string, unknown>;
type Fail = (check: string, detail: string) => void;

function rows(db: DatabaseSync, sql: string): Row[] {
  return db.prepare(sql).all() as Row[];
}

export function guideChecks(db: DatabaseSync, fail: Fail): void {
  // A version exclusive says "the other version has it". If the other version does not, the
  // override has removed the variant from the pair altogether, which no source claims.
  for (const r of rows(
    db,
    'SELECT ga.variantId, ga.gameId, ga.exclusiveTo FROM game_availability ga ' +
      'LEFT JOIN game_availability other ON other.variantId = ga.variantId AND other.gameId = ga.exclusiveTo ' +
      'WHERE ga.exclusiveTo IS NOT NULL AND (other.obtainable IS NULL OR other.obtainable = 0)',
  )) {
    fail(
      'override-narrows',
      String(r.variantId) + ' is called exclusive to ' + String(r.exclusiveTo) + ' in ' +
        String(r.gameId) + ', but ' + String(r.exclusiveTo) + ' does not offer it either',
    );
  }

  // An exclusion names a modifier the encounter would otherwise get. One the method does
  // not have is a typo that would silently leave the odds unchanged.
  const modifiers = new Set(
    rows(db, 'SELECT gameId, methodId, id FROM odds_modifier').map(
      (m) => m.gameId + '|' + m.methodId + '|' + m.id,
    ),
  );
  for (const e of rows(db, 'SELECT id, gameId, methodId, oddsExclude FROM encounter WHERE oddsExclude IS NOT NULL')) {
    for (const id of String(e.oddsExclude).split(',')) {
      if (!modifiers.has(e.gameId + '|' + e.methodId + '|' + id)) {
        fail(
          'odds-exclude-known',
          'encounter ' + String(e.id) + ' excludes ' + id + ', which ' + String(e.gameId) + ' ' +
            String(e.methodId) + ' does not have',
        );
      }
    }
  }

  // --- references inside the guide layer ------------------------------------------
  for (const [table, column, parent, parentKey] of [
    ['guide_target', 'variantId', 'variant', 'id'],
    ['guide_target', 'guideId', 'guide', 'id'],
    ['build', 'variantId', 'variant', 'id'],
    ['build_part', 'buildId', 'build', 'id'],
    ['guide_step', 'ownerId', 'guide', 'id'],
    ['guide_advice', 'guideId', 'guide', 'id'],
    ['guide_link', 'guideId', 'guide', 'id'],
    ['guide', 'methodId', 'encounter_method', 'id'],
  ] as const) {
    for (const r of rows(
      db,
      'SELECT ' + table + '.' + column + ' AS ref FROM ' + table + ' LEFT JOIN ' + parent + ' ON ' +
        table + '.' + column + ' = ' + parent + '.' + parentKey + ' WHERE ' + table + '.' + column +
        ' IS NOT NULL AND ' + parent + '.' + parentKey + ' IS NULL',
    )) {
      fail('guide-ref', table + '.' + column + ' names ' + String(r.ref) + ', which is not a ' + parent);
    }
  }
  for (const r of rows(
    db,
    'SELECT g.id, g.procedureId FROM guide g LEFT JOIN guide p ON p.id = g.procedureId ' +
      "WHERE g.procedureId IS NOT NULL AND (p.id IS NULL OR p.kind != 'procedure' OR p.gameSet != g.gameSet)",
  )) {
    fail('guide-ref', 'guide ' + String(r.id) + ' follows ' + String(r.procedureId) + ', which is not a procedure of its game');
  }
  for (const r of rows(
    db,
    "SELECT l.guideId, l.kind, l.refId FROM guide_link l JOIN guide g ON g.id = l.guideId " +
      "LEFT JOIN build b ON l.kind = 'build' AND b.id = l.refId AND b.gameSet = g.gameSet " +
      "LEFT JOIN requirement r ON l.kind = 'requirement' AND r.id = l.refId AND r.gameSet = g.gameSet " +
      'WHERE b.id IS NULL AND r.id IS NULL',
  )) {
    fail('guide-ref', 'guide ' + String(r.guideId) + ' links ' + String(r.kind) + ' ' + String(r.refId) + ', which its game does not have');
  }

  // A step or a farm source that sends you somewhere has to name a place the game has, or
  // the DLC it needs cannot be derived from it.
  for (const [table, owner, setSql] of [
    ['guide_step', 'ownerId', 'SELECT gameSet FROM guide WHERE id = t.ownerId'],
    [
      'farm_source',
      'subjectId',
      "SELECT gameSet FROM build WHERE t.subjectKind = 'build' AND id = t.subjectId " +
        "UNION SELECT gameSet FROM requirement WHERE t.subjectKind = 'requirement' AND id = t.subjectId",
    ],
  ] as const) {
    for (const r of rows(
      db,
      'SELECT t.' + owner + ' AS owner, t.locationId FROM ' + table + ' t WHERE t.locationId IS NOT NULL ' +
        'AND NOT EXISTS (SELECT 1 FROM location l WHERE l.id = t.locationId AND l.gameSet IN (' + setSql + '))',
    )) {
      fail('location-exists', table + ' of ' + String(r.owner) + ' is at ' + String(r.locationId) + ', which its game has no location for');
    }
  }
  for (const r of rows(
    db,
    "SELECT f.subjectKind, f.subjectId FROM farm_source f LEFT JOIN build b ON f.subjectKind = 'build' AND b.id = f.subjectId " +
      "LEFT JOIN requirement r ON f.subjectKind = 'requirement' AND r.id = f.subjectId WHERE b.id IS NULL AND r.id IS NULL",
  )) {
    fail('guide-ref', 'a farm source names ' + String(r.subjectKind) + ' ' + String(r.subjectId) + ', which does not exist');
  }

  // "How do I get it" must have an answer for every build a guide asks for, and for every
  // item or service it needs. A DLC or story step is bought or played, not farmed.
  for (const r of rows(
    db,
    "SELECT DISTINCT l.kind, l.refId FROM guide_link l LEFT JOIN requirement r ON l.kind = 'requirement' AND r.id = l.refId " +
      "WHERE (l.kind = 'build' OR r.kind IN ('item', 'service')) " +
      'AND NOT EXISTS (SELECT 1 FROM farm_source f WHERE f.subjectKind = l.kind AND f.subjectId = l.refId)',
  )) {
    fail('build-farmed', String(r.kind) + ' ' + String(r.refId) + ' is needed by a guide but has no farm chain');
  }

  // A guide sends you after a variant. Its game family has to offer it, unlocked, and the
  // curation has to record the guide's method for it there: a guide without an encounter
  // row would put a tutorial beside "no method recorded yet".
  for (const r of rows(
    db,
    'SELECT t.guideId, t.variantId, g.methodId, g.gameSet FROM guide_target t JOIN guide g ON g.id = t.guideId ' +
      'WHERE NOT EXISTS (SELECT 1 FROM game_availability ga JOIN game gm ON gm.id = ga.gameId ' +
      '  WHERE ga.variantId = t.variantId AND gm.gameSet = g.gameSet AND ga.obtainable = 1 AND ga.shinyLocked = 0)',
  )) {
    fail('guide-target-offered', 'guide ' + String(r.guideId) + ' targets ' + String(r.variantId) + ', which ' + String(r.gameSet) + ' does not offer unlocked');
  }
  for (const r of rows(
    db,
    'SELECT t.guideId, t.variantId, g.methodId, g.gameSet FROM guide_target t JOIN guide g ON g.id = t.guideId ' +
      'WHERE NOT EXISTS (SELECT 1 FROM encounter e JOIN game gm ON gm.id = e.gameId ' +
      '  WHERE e.variantId = t.variantId AND e.methodId = g.methodId AND gm.gameSet = g.gameSet AND e.shinyLocked = 0) ' +
      'OR NOT EXISTS (SELECT 1 FROM odds_modifier o JOIN game gm ON gm.id = o.gameId ' +
      '  WHERE o.methodId = g.methodId AND gm.gameSet = g.gameSet)',
  )) {
    fail(
      'guide-method-curated',
      'guide ' + String(r.guideId) + ' hunts ' + String(r.variantId) + ' by ' + String(r.methodId) +
        ', but ' + String(r.gameSet) + ' has no unlocked encounter or no odds for that method',
    );
  }
}
