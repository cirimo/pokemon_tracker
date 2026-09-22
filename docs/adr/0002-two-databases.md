# 0002 — Reference data and user data live in separate Room databases

Status: accepted, 2026-09-22

## Context

Reference data is replaced wholesale when the dataset is regenerated. User data must
never be lost by that. Room's `createFromAsset` only re-applies the prepackaged file
during a **destructive** fallback -- when an implemented migration path exists, Room
migrates the existing file and ignores the asset entirely.

So in a single database, refreshing the dataset means dropping the catch records with it.

## Options

**A. One database, reference tables refreshed in place.** On a dataset bump, ATTACH the
new asset, delete the reference tables, copy across. Keeps SQL joins between slots and
records. Hand-written data migration code on the path that touches user data, and
`ATTACH` under WAL cannot commit atomically across files.

**B. Two databases.** reference.db from the asset with destructive fallback enabled;
user.db with real migrations and no fallback. Room cannot join across two
`RoomDatabase` instances, so the join moves into Kotlin.

## Decision

**B.** The separation is enforced by the filesystem rather than by discipline, and the
blast radius of a corrupt asset stops at data that can be regenerated.

The cross-database join is not a real cost here: the active preset is ~1394 slot rows
(about 280 KB), loaded once and combined with a `Flow<Map<CatchKey, CatchRecord>>`.
Search and filtering become in-memory operations over a list that fits easily in RAM.

## Consequences

- `ReferenceDatabase` gets `fallbackToDestructiveMigration(dropAllTables = true)`.
  `UserDatabase` must never get it. This is stated in `CLAUDE.md` as a hard rule.
- No foreign key crosses the boundary. `catch_record.originGameId` is a plain string, so
  a record outlives the reference row that explained it.
- Queries spanning both are written in Kotlin, not SQL. Accepted deliberately; the
  budget for search is 5 ms over 1394 items.
- Backup and restore touch exactly one file, which makes both simpler.
- The asset lives in `:core:data` rather than `:app`, so the module owning
  `ReferenceDatabase` owns the file it is populated from -- and its instrumentation test
  can open the asset that actually ships.
