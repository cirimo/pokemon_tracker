import fs from 'node:fs';
import path from 'node:path';
import { paths } from './config.js';

/**
 * Reads the schema Room exported from the compiled entities.
 *
 * This is the mechanism that makes a hand-built SQLite file acceptable to Room. Room
 * refuses any prepackaged database whose room_master_table identity hash does not match
 * the hash of the compiled entities, and that hash is undocumented to compute. So we do
 * not compute it -- we read it, along with the exact CREATE statements, out of the JSON
 * Room already wrote. Schema drift becomes a build failure here instead of a crash on
 * the device.
 */

export interface RoomField {
  fieldPath: string;
  columnName: string;
  affinity: string;
  notNull: boolean;
}

export interface RoomEntity {
  tableName: string;
  createSql: string;
  fields: RoomField[];
  primaryKey: { columnNames: string[] };
  indices?: { name: string; createSql: string }[];
}

export interface RoomSchema {
  version: number;
  identityHash: string;
  entities: RoomEntity[];
}

export function loadRoomSchema(version?: number): RoomSchema {
  if (!fs.existsSync(paths.roomSchema)) {
    throw new Error(
      `Room schema directory not found at ${paths.roomSchema}.\n` +
        'Run `./gradlew :core:data:assembleDebug` first -- the pipeline needs the ' +
        'exported schema to emit matching DDL.',
    );
  }
  const files = fs
    .readdirSync(paths.roomSchema)
    .filter((f) => f.endsWith('.json'))
    .map((f) => ({ file: f, version: Number.parseInt(f, 10) }))
    .filter((f) => Number.isFinite(f.version))
    .sort((a, b) => a.version - b.version);

  const chosen = version
    ? files.find((f) => f.version === version)
    : files[files.length - 1];

  if (!chosen) throw new Error(`No exported Room schema for version ${version ?? 'latest'}`);

  const raw = JSON.parse(
    fs.readFileSync(path.join(paths.roomSchema, chosen.file), 'utf8'),
  ) as { database: RoomSchema };

  return raw.database;
}

/**
 * DDL for the whole reference database, in a deterministic order.
 *
 * `${TABLE_NAME}` is Room's placeholder in the exported createSql.
 */
export function emitDdl(schema: RoomSchema): string[] {
  const statements: string[] = [];
  const entities = [...schema.entities].sort((a, b) =>
    a.tableName.localeCompare(b.tableName),
  );
  for (const entity of entities) {
    statements.push(entity.createSql.replace(/\$\{TABLE_NAME\}/g, entity.tableName));
    for (const index of [...(entity.indices ?? [])].sort((a, b) =>
      a.name.localeCompare(b.name),
    )) {
      statements.push(index.createSql.replace(/\$\{TABLE_NAME\}/g, entity.tableName));
    }
  }
  return statements;
}

/**
 * The two rows Room looks for when it opens a prepackaged database.
 *
 * 42 is Room's own magic id for the identity row. Getting either of these wrong makes
 * Room throw IllegalStateException on first open, on the device, with no hint.
 */
export function emitRoomMasterTable(schema: RoomSchema): string[] {
  return [
    'CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)',
    `INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '${schema.identityHash}')`,
  ];
}
