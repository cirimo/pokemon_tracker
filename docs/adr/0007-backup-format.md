# 0007 — Versioned JSON backup, refusing anything from a newer schema

Status: accepted, 2026-09-22. Where the files go, and the `allowBackup` position below, are
amended by `0011-backups-outside-the-sandbox.md`.

## Context

The requirement is surviving a lost phone. There is no backend and no account, so the
only durability story is a file the user exports and can put somewhere safe. Android
cloud backup is off deliberately -- it is silent, unversioned and not inspectable. (Device
transfer of `user.db` was turned on in M3; see ADR 0011.)

The file may be restored years later, possibly into a newer build.

## Decision

One JSON file. Pretty-printed, stable key order, records sorted by `(variantId,
copyIndex)` so two exports of the same data are byte-identical and diffable.

```json
{
  "schema": 1,
  "exportedAt": "2026-09-22T18:04:11Z",
  "app": { "versionName": "0.1.0", "versionCode": 1 },
  "dataset": { "presetId": "grouped-balanced", "presetVersion": 1, "datasetVersion": 1 },
  "settings": { "activePresetId": "grouped-balanced", "autoBackupEnabled": true },
  "records": [
    { "variantId": "venusaur-f", "copyIndex": 0, "caught": true,
      "originGameId": "sv-s", "caughtAt": "2026-03-04T21:10:00Z",
      "notes": "sandwich, 412 resets", "favourite": false, "priority": 0,
      "updatedAt": "2026-03-04T21:10:00Z" }
  ]
}
```

### Compatibility rules

- `schema` is a single integer, bumped **only** for a breaking change.
- `schema > CURRENT` is **refused outright**, with both numbers shown to the user. A
  partial import of a shape we do not understand is how records get silently lost.
- `schema < CURRENT` runs a chain of pure upgrade functions.
- Unknown object keys are ignored, so a newer *minor* export still restores its records.
  That is precisely why additive changes must not bump `schema`.
- `copyIndex` defaults to 0, so a hand-edited file can omit it for the 1387 variants
  where it is always zero.
- Timestamps are ISO-8601 UTC strings rather than epoch millis, because a backup you may
  have to trust in five years should be readable in a text editor.

- `updatedAt` per record was added in M3, additively, so `schema` stayed 1. A merge keeps,
  per key, whichever side changed the record last; a record without it counts as older
  than anything local. Without it, restoring last week's file would untick tonight's
  catches.

Import is one transaction, merge by default with an explicit replace option, and always
writes a pre-import snapshot to the rolling backup directory first.

## Consequences

- The backup DTOs are kept from obfuscation by a ProGuard rule. Obfuscating their field
  names would make a recovered file unreadable by a future build.
- `BackupCodec` lives in `:core:model` -- pure, Android-free, and unit-tested on the JVM
  rather than through an instrumentation test.
- Records reference `variantId` and `copyIndex` only. A backup does not depend on a
  dataset version and restores cleanly into a newer dataset; anything the new preset no
  longer has becomes an orphaned record, which is kept and surfaced (ADR 0001).
