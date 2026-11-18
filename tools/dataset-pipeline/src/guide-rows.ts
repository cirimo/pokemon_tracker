import type { CuratedGuideBook } from './curated.js';

/**
 * Turns the curated guide books (data/curated/guides/*.yaml) into the rows of the guide
 * tables. Pure, so the pipeline tests can check the shape without building a database.
 *
 * Ordinals come from the order in the YAML: a guide's steps read the way they are written,
 * and a farm chain is in the order you would walk it.
 */
export interface GuideRows {
  location: unknown[][];
  requirement: unknown[][];
  guide: unknown[][];
  guide_target: unknown[][];
  guide_step: unknown[][];
  build: unknown[][];
  build_part: unknown[][];
  guide_link: unknown[][];
  guide_advice: unknown[][];
  farm_source: unknown[][];
}

const bool = (v: boolean) => (v ? 1 : 0);

export function guideRows(books: CuratedGuideBook[]): GuideRows {
  const rows: GuideRows = {
    location: [],
    requirement: [],
    guide: [],
    guide_target: [],
    guide_step: [],
    build: [],
    build_part: [],
    guide_link: [],
    guide_advice: [],
    farm_source: [],
  };
  for (const book of books) {
    const set = book.gameSet;
    for (const l of book.locations) rows.location.push([set, l.id, l.name, l.dlc ?? null, l.source]);
    for (const r of book.requirements) {
      rows.requirement.push([set, r.id, r.label, r.kind, bool(r.optional ?? false), r.detail ?? null, r.source]);
      r.farm.forEach((f, i) =>
        rows.farm_source.push(['requirement', r.id, i, f.kind, f.location ?? null, f.detail, f.source]),
      );
    }
    for (const b of book.builds) {
      rows.build.push([b.id, set, b.variant, b.title, b.purpose]);
      b.parts.forEach((p, i) => rows.build_part.push([b.id, i, p.kind, p.value, p.why, p.source]));
      b.farm.forEach((f, i) =>
        rows.farm_source.push(['build', b.id, i, f.kind, f.location ?? null, f.detail, f.source]),
      );
    }
    for (const p of book.procedures) {
      rows.guide.push([p.id, set, 'procedure', p.title, null, null, null, 0]);
      p.steps.forEach((s, i) => rows.guide_step.push([p.id, i, s.section, s.text, s.location ?? null, s.source]));
    }
    for (const g of book.guides) {
      rows.guide.push([
        g.id,
        set,
        'guide',
        g.title,
        g.method,
        g.procedure ?? null,
        g.summary ?? null,
        bool(g.oneInSave ?? false),
      ]);
      for (const v of g.variants) rows.guide_target.push([g.id, v]);
      g.steps.forEach((s, i) => rows.guide_step.push([g.id, i, s.section, s.text, s.location ?? null, s.source]));
      for (const r of g.requires) rows.guide_link.push([g.id, 'requirement', r.id, r.role ?? null]);
      for (const b of g.builds) rows.guide_link.push([g.id, 'build', b.id, b.role ?? null]);
      g.advice.forEach((a, i) => rows.guide_advice.push([g.id, i, a.kind, a.subject, a.why, a.source]));
    }
  }
  return rows;
}

const COLUMNS: Record<keyof GuideRows, string> = {
  location: 'gameSet, id, name, dlc, sourceUrl',
  requirement: 'gameSet, id, label, kind, optional, detail, sourceUrl',
  guide: 'id, gameSet, kind, title, methodId, procedureId, summary, oneInSave',
  guide_target: 'guideId, variantId',
  guide_step: 'ownerId, ordinal, section, text, locationId, sourceUrl',
  build: 'id, gameSet, variantId, title, purpose',
  build_part: 'buildId, ordinal, kind, value, why, sourceUrl',
  guide_link: 'guideId, kind, refId, role',
  guide_advice: 'guideId, ordinal, kind, subject, why, sourceUrl',
  farm_source: 'subjectKind, subjectId, ordinal, kind, locationId, detail, sourceUrl',
};

/** Inserts every guide table, rows sorted on their key columns so rebuilds are byte-stable. */
export function insertGuideBooks(
  books: CuratedGuideBook[],
  insert: (sql: string, rows: unknown[][]) => void,
  compare: (a: unknown[], b: unknown[]) => number,
): void {
  const rows = guideRows(books);
  for (const table of Object.keys(COLUMNS) as (keyof GuideRows)[]) {
    const columns = COLUMNS[table];
    const marks = columns.split(',').map(() => '?').join(',');
    insert('INSERT INTO ' + table + ' (' + columns + ') VALUES (' + marks + ')', [...rows[table]].sort(compare));
  }
}
