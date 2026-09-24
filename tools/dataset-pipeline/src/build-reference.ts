import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { DATASET_VERSION, PRESET, PRESET_VERSION, paths, SPRITES, UPSTREAM } from './config.js';
import { loadCuratedLayer } from './curated.js';
import { emitDdl, emitRoomMasterTable, loadRoomSchema } from './room-schema.js';
import { ensureUpstream, readGame, readPokemon, readPreset, upstreamSha } from './upstream.js';
import { contentManifest, writeManifest } from './manifest.js';

/**
 * Builds app/src/main/assets/dataset/reference.db.
 *
 * Determinism rules, all of which matter:
 *  - rows are inserted in a fixed sorted order, never in iteration order
 *  - no AUTOINCREMENT anywhere, so no hidden sqlite_sequence state
 *  - journal_mode=DELETE and a fixed page_size, so the file header is stable
 *  - the built-at timestamp is frozen, because a real one defeats byte-determinism
 *  - VACUUM last
 *
 * Byte-identical output is still only as stable as the SQLite build Node ships, so the
 * durable reproducibility contract is the content manifest -- see manifest.ts.
 */

export interface SlotRow {
  presetId: string;
  boxIndex: number;
  slotIndex: number;
  variantId: string;
  copyIndex: number;
}

/**
 * Assigns copyIndex in preset order.
 *
 * grouped-balanced demands seven Pokemon twice (unown, vivillon, flabebe, floette,
 * florges, furfrou, alcremie), once in their generation box and once in a dedicated
 * form box. A living dex needs two of each, so the second occurrence gets copyIndex 1
 * and resolves to its own catch record. This mirrors assignCopyIndices() in
 * :core:model, and both are held to the same expectations by their tests.
 */
export function buildSlotRows(
  boxes: { name: string; slots: (string | null)[] }[],
  presetId: string,
): SlotRow[] {
  const seen = new Map<string, number>();
  const rows: SlotRow[] = [];
  boxes.forEach((box, boxIndex) => {
    box.slots.forEach((variantId, slotIndex) => {
      if (variantId === null) return; // a real hole, not an error: there are ten
      const copyIndex = seen.get(variantId) ?? 0;
      seen.set(variantId, copyIndex + 1);
      rows.push({ presetId, boxIndex, slotIndex, variantId, copyIndex });
    });
  });
  return rows;
}

const bool = (v: boolean) => (v ? 1 : 0);

/** Ids are [a-z0-9-] only, so a pipe cannot collide. */
const lockKey = (variantId: string, gameId: string) => variantId + "|" + gameId;

/** Sprite filename, keyed on nid so it survives an upstream PokeAPI remap. */
export function spriteFileFor(p: { nid: string }): string {
  return "sprites/" + p.nid + ".webp";
}

/**
 * The English display name, which search matches and TalkBack reads.
 *
 * Upstream's names.eng, except where upstream gives a non-default form the bare species
 * name: at 6.8.2 that is Unown N, O, U and W and Eternal Flower Floette, whose
 * names.eng is "Unown" / "Floette" while formNames.eng is correct. Those would otherwise
 * be five search rows indistinguishable from the base form. The rule is general rather
 * than a list of ids so the next such upstream slip is fixed too, and `display-name-unique`
 * fails if one gets through anyway.
 */
export function displayNameFor(
  p: { id: string; isDefault: boolean; names: Record<string, string>; formNames?: Record<string, string> },
  speciesName: string,
): string {
  const name = p.names.eng ?? p.id;
  const form = p.formNames?.eng;
  return !p.isDefault && form && name === speciesName ? name + " (" + form + ")" : name;
}

function compareRows(a: unknown[], x: unknown[]): number {
  for (let i = 0; i < Math.min(a.length, x.length); i++) {
    const l = a[i];
    const r = x[i];
    if (typeof l === "string" && typeof r === "string") {
      const c = l.localeCompare(r);
      if (c !== 0) return c;
    } else if (typeof l === "number" && typeof r === "number") {
      if (l !== r) return l - r;
    }
  }
  return 0;
}

export function buildReference(): void {
  ensureUpstream();
  const schema = loadRoomSchema();
  const preset = readPreset();
  const curated = loadCuratedLayer();

  const slotRows = buildSlotRows(preset.boxes, PRESET.id);
  const variantIds = [...new Set(slotRows.map((s) => s.variantId))].sort();
  const pokemon = new Map(variantIds.map((id) => [id, readPokemon(id)]));
  const games = curated.games
    .map((g) => ({ curated: g, upstream: readGame(g.id) }))
    .sort((a, x) => a.curated.sortOrder - x.curated.sortOrder);

  fs.mkdirSync(path.dirname(paths.assetDb), { recursive: true });
  fs.rmSync(paths.assetDb, { force: true });

  const db = new DatabaseSync(paths.assetDb);
  db.exec("PRAGMA page_size = 4096");
  db.exec("PRAGMA journal_mode = DELETE");
  for (const stmt of emitDdl(schema)) db.exec(stmt);
  for (const stmt of emitRoomMasterTable(schema)) db.exec(stmt);
  db.exec("PRAGMA user_version = " + schema.version);

  const insert = (sql: string, rows: unknown[][]) => {
    const stmt = db.prepare(sql);
    for (const row of rows) stmt.run(...(row as never[]));
  };

  // --- species -----------------------------------------------------------------
  const speciesRows = new Map<number, unknown[]>();
  for (const id of variantIds) {
    const p = pokemon.get(id)!;
    if (speciesRows.has(p.dexNum) && !p.isDefault) continue;
    speciesRows.set(p.dexNum, [
      p.dexNum,
      p.speciesNames?.eng ?? p.names.eng ?? id,
      p.gen,
      p.region ?? "unknown",
      bool(p.isLegendary),
      bool(p.isMythical),
      bool(p.isBaby),
      bool(p.isUltraBeast),
      bool(p.isParadox),
    ]);
  }
  insert(
    "INSERT INTO species (dexNum, name, generation, region, isLegendary, isMythical, isBaby, isUltraBeast, isParadox) VALUES (?,?,?,?,?,?,?,?,?)",
    [...speciesRows.entries()].sort((a, x) => a[0] - x[0]).map((e) => e[1]),
  );

  // --- variant -----------------------------------------------------------------
  insert(
    "INSERT INTO variant (id, nid, dexNum, formId, displayName, formName, type1, type2, isDefault, isForm, isCosmeticForm, isFemaleForm, isRegional, isBattleOnlyForm, baseSpeciesId, evolvesFromId, evolveCondition, shinyReleased, spriteFile) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
    variantIds.map((id) => {
      const p = pokemon.get(id)!;
      return [
        p.id,
        p.nid,
        p.dexNum,
        p.formId ?? null,
        displayNameFor(p, speciesRows.get(p.dexNum)![1] as string),
        p.formNames?.eng ?? null,
        p.type1,
        p.type2 ?? null,
        bool(p.isDefault),
        bool(p.isForm),
        bool(p.isCosmeticForm),
        bool(p.isFemaleForm),
        bool(p.isRegional),
        bool(p.isBattleOnlyForm),
        p.baseSpecies ?? null,
        p.evolvesFrom ?? null,
        p.evoFromLevel != null ? "level " + p.evoFromLevel : null,
        bool(p.shinyReleased),
        spriteFileFor(p),
      ];
    }),
  );

  // --- preset / boxes / slots --------------------------------------------------
  insert(
    "INSERT INTO dex_preset (id, name, description, sourceVersion, presetVersion, boxCount, filledSlotCount) VALUES (?,?,?,?,?,?,?)",
    [
      [
        PRESET.id,
        preset.name,
        preset.description.trim(),
        preset.source.version,
        PRESET_VERSION,
        preset.boxes.length,
        slotRows.length,
      ],
    ],
  );
  insert(
    "INSERT INTO box (presetId, boxIndex, name, slotCount, filledSlotCount) VALUES (?,?,?,?,?)",
    preset.boxes.map((box, i) => [
      PRESET.id,
      i,
      box.name,
      box.slots.length,
      box.slots.filter((s) => s !== null).length,
    ]),
  );
  insert(
    "INSERT INTO slot (presetId, boxIndex, slotIndex, variantId, copyIndex) VALUES (?,?,?,?,?)",
    slotRows.map((s) => [s.presetId, s.boxIndex, s.slotIndex, s.variantId, s.copyIndex]),
  );

  // --- games -------------------------------------------------------------------
  insert(
    "INSERT INTO game (id, name, gameSet, generation, releaseDate, region, originMark, supportsShiny, sortOrder) VALUES (?,?,?,?,?,?,?,?,?)",
    games.map((g) => [
      g.curated.id,
      g.curated.name,
      g.upstream.gameSet ?? g.curated.id,
      g.upstream.gen,
      g.upstream.releaseDate ?? "",
      g.upstream.region ?? "",
      g.upstream.originMark ?? "",
      bool(g.upstream.features?.shiny ?? true),
      g.curated.sortOrder,
    ]),
  );

  // --- availability: upstream facts, plus the curated shiny locks ---------------
  const lockIndex = new Map(
    curated.shinyLocks.map((l) => [lockKey(l.variantId, l.gameId), l]),
  );
  const availability: unknown[][] = [];
  for (const id of variantIds) {
    const p = pokemon.get(id)!;
    for (const g of games) {
      const gameId = g.curated.id;
      const obtainable = p.obtainableIn.includes(gameId);
      const storable = p.storableIn.includes(gameId);
      // No row at all when the game neither offers nor stores it: an absent row is
      // cheaper and less ambiguous than a row of five falses.
      if (!obtainable && !storable) continue;
      const lock = lockIndex.get(lockKey(id, gameId));
      availability.push([
        id,
        gameId,
        bool(obtainable),
        bool(p.eventOnlyIn.includes(gameId)),
        bool(storable),
        bool(p.transferOnlyIn.includes(gameId)),
        bool(!!lock),
        lock?.reason ?? null,
      ]);
    }
  }
  insert(
    "INSERT INTO game_availability (variantId, gameId, obtainable, eventOnly, storable, transferOnly, shinyLocked, shinyLockReason) VALUES (?,?,?,?,?,?,?,?)",
    availability.sort(compareRows),
  );

  // --- curated encounters and odds ---------------------------------------------
  insert(
    "INSERT INTO encounter_method (id, name, description) VALUES (?,?,?)",
    [...curated.encounterMethods]
      .sort((a, x) => a.id.localeCompare(x.id))
      .map((m) => [m.id, m.name, m.description]),
  );

  const ordinals = new Map<string, number>();
  insert(
    "INSERT INTO encounter (id, variantId, gameId, methodId, location, prerequisite, notes, shinyLocked, fromVariantId, sourceUrl) VALUES (?,?,?,?,?,?,?,?,?,?)",
    [...curated.encounters]
      .sort(
        (a, x) =>
          a.variantId.localeCompare(x.variantId) ||
          a.gameId.localeCompare(x.gameId) ||
          a.methodId.localeCompare(x.methodId),
      )
      .map((e) => {
        const base = e.variantId + ":" + e.gameId;
        const n = ordinals.get(base) ?? 0;
        ordinals.set(base, n + 1);
        return [
          base + ":" + n,
          e.variantId,
          e.gameId,
          e.methodId,
          e.location ?? null,
          e.prerequisite ?? null,
          e.notes ?? null,
          bool(e.shinyLocked ?? false),
          e.from ?? null,
          e.source,
        ];
      }),
  );

  // One curated row names every game and method it applies to; the table holds one row
  // per (game, method) so the app reads a method's modifiers with a single lookup.
  insert(
    "INSERT INTO odds_modifier (gameId, methodId, id, label, rollsAdded, denominator, tier, inherent, notes, sourceUrl) VALUES (?,?,?,?,?,?,?,?,?,?)",
    curated.oddsModifiers
      .flatMap((m) =>
        m.games.flatMap((gameId) => m.methods.map((methodId) => ({ ...m, gameId, methodId }))),
      )
      .sort(
        (a, x) =>
          a.gameId.localeCompare(x.gameId) ||
          a.methodId.localeCompare(x.methodId) ||
          a.id.localeCompare(x.id),
      )
      .map((m) => [
        m.gameId,
        m.methodId,
        m.id,
        m.label,
        m.rollsAdded ?? null,
        m.denominator ?? null,
        m.tier ?? null,
        bool(m.inherent ?? false),
        m.notes ?? null,
        m.source,
      ]),
  );

  // Frozen on purpose: a real build timestamp would make every rebuild differ.
  const builtAt = "1970-01-01T00:00:00Z";
  insert(
    "INSERT INTO dataset_meta (id, datasetVersion, upstreamTag, presetVersion, builtAt, contentHash) VALUES (?,?,?,?,?,?)",
    [[1, DATASET_VERSION, UPSTREAM.tag, PRESET_VERSION, builtAt, "pending"]],
  );

  const hashes = contentManifest(db);
  db.prepare("UPDATE dataset_meta SET contentHash = ? WHERE id = 1").run(hashes.overall);

  db.exec("VACUUM");
  db.close();

  writeManifest({
    datasetVersion: DATASET_VERSION,
    presetVersion: PRESET_VERSION,
    upstreamTag: UPSTREAM.tag,
    upstreamSha: upstreamSha(),
    spriteSet: SPRITES.home,
    spriteSize: SPRITES.size,
    builtAt,
    contentHash: hashes.overall,
    tables: hashes.tables,
  });

  process.stdout.write(
    "reference.db written: " +
      preset.boxes.length +
      " boxes, " +
      slotRows.length +
      " filled slots, " +
      variantIds.length +
      " variants, " +
      availability.length +
      " availability rows\n",
  );
}
