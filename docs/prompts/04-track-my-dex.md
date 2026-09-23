# Prompt 4 — M3, "Track my dex"

> Run this in a fresh Claude Code session at the repo root, in **plan mode**.
> Read `docs/00-big-picture.md`, `docs/architecture.md`, `CLAUDE.md`,
> `docs/adr/0001-slot-identity.md`, `docs/adr/0007-backup-format.md` and, before you write
> a single line of UI, **`docs/design-usage.md`**.

---

You are the feature engineer on a **shiny living dex tracker**: an Android app for one
person (me) that mirrors my Pokémon HOME boxes and tells me what I still need. It is
sideloaded, offline, and has no backend. Treat the locked decisions in
`docs/00-big-picture.md` as constraints, and tell me directly if one of them is wrong.

M2 made the dex visible. **This session makes it mine: the records I keep, and the
guarantee that I cannot lose them.**

## Why durability comes first

On 2026-09-23 a baseline-profile generation run installed a test build under the release
id and uninstalled it afterwards, which deleted every catch record on my phone. Nothing
could be recovered. `android:allowBackup` is `false`, no export had ever been written, and
the app had never been asked to write one. The design was right on paper, since ADR 0007
defines a versioned, diffable backup file, but nothing ever produced one.

The generation mishap is fixed (`net.pokedex.profiling`, see §8 of `docs/architecture.md`).
The lesson is not about that bug. **An app whose only copy of my data lives inside its own
sandbox loses that data to any uninstall**, whether it comes from me, from a tool or from a
failed signature change. Durability is the first deliverable of M3. The screens come after.

## What already exists

Most of the data layer is built. Read it before you design anything.

- **`CatchRecord`** (`:core:model`) already carries `caught`, `originGameId`, `caughtAt`,
  `notes`, `favourite`, `priority` and `updatedAt`, keyed `(variantId, copyIndex)`, never by
  position (ADR 0001).
- **`CatchRepository`**: `observeRecords()`, `setCaught(...)`, `update(...)`, `record(key)`.
  `CaughtToggle` and `UndoBar` already work in slot detail. M2 wired them up because a
  read-only dex is useless to test with.
- **`BackupCodec`** (`:core:model`, JVM-tested) and **`BackupRepository.export/import`**
  (`:core:data`) implement ADR 0007: schema integer, refusal of a newer schema, sorted
  records, one-transaction import, merge or replace.
- **`UserSettings.autoBackupEnabled` / `autoBackupKeepCount`** exist and default to
  `true` / `10`, but **nothing reads them**. There is no worker, no backup directory, no
  settings screen and no restore UI. `PokedexNavHost` has
  `Settings -> BackupRestore (M3)` written down and nothing behind it.
- `BackupLogEntity` / `BackupLogDao` exist in `UserDatabase`.
- Progress is derived: `progressOf`, `progressByBox`, `orphanedRecords`. **Never store a
  count.**

## Scope

In priority order. If the session runs long, the first item ships complete and the last
one waits.

1. **Backups that survive an uninstall.**
   - Automatic backups written **outside the app's sandbox** after changes, keeping a
     rolling set of `autoBackupKeepCount` files.
   - Manual export and import through the system file picker.
   - A restore flow that shows what the file contains (record count, caught count, schema,
     exported-at) before touching anything. It offers merge or replace, refuses a newer
     schema with both numbers shown, and writes the pre-import snapshot ADR 0007 promises.
   - **First launch with an empty database offers to restore.** That is exactly the moment
     after a reinstall, and it is when I will least remember that a backup exists.
2. **Recording a catch properly.** Origin game, caught date and notes from slot detail,
   plus editing them later. Uncatching must not silently discard what was recorded.
3. **Progress dashboards.** Where I stand overall and by the cuts a living-dex hunter
   actually uses. Propose which cuts; do not build every possible chart.
4. **Settings.** Only what the items above need: backup location, auto-backup on/off,
   keep count, and "back up now". This is not a general preferences screen.

**Out of scope:** the priority queue and "what should I hunt next" are M4, even though
`priority` is already a field. The hunt engine is M5. `favourite` is your call. Tell me
whether it earns a place in M3 or waits for M4's queue.

## Think about these before you propose anything

Give me a recommendation and the trade-off for each one. I approve recommendations quickly
and override only the ones I care about.

1. **Where do automatic backups live?** Candidates: a folder I pick once through the
   Storage Access Framework, with a persisted URI permission; `MediaStore` Downloads; or
   app-private storage plus flipping `allowBackup`. Say which of them survives an uninstall,
   a factory reset and a phone swap, and what each costs on API 26 versus 35. A SAF folder
   can be backed by a cloud provider without the app gaining `INTERNET`. Say whether that
   matters.
2. **When is a backup written?** Debounced after each change, daily through WorkManager,
   or both? What bounds the number of writes when I mark 30 slots in one sitting?
3. **Does ADR 0007's `allowBackup="false"` still stand?** It was chosen so durability would
   be explicit rather than silent. It was also never tested against a real loss. Reconsider
   it on the evidence, and write an ADR if the answer changes.
4. **Catch flow friction.** Must origin game be chosen at the moment of catching, or can it
   be left blank and filled in later? Should the last-used game be the default? I usually
   log several catches from the same game in a row.
5. **Uncatch.** What happens to origin, date and notes when I untick a slot by accident,
   and how long does undo last?
6. **Orphaned records** (ADR 0001): records the current preset no longer has a slot for.
   Where are they surfaced, and what can I do with them?
7. **The dashboards.** Which three or four views would you build first, and why those?

Ask me these **before** you build, then stop.

## Constraints that are not negotiable

- **`UserDatabase` changes get a `Migration` and a `MigrationTestHelper` test in the same
  commit.** Never `fallbackToDestructiveMigration`. This milestone is about not losing
  data. Losing it to a migration would be absurd.
- **Test the durability path, not only the codec.** Round-trip, merge, replace, refusal of
  a newer schema, the pre-import snapshot, and restore into an empty database. Put the
  logic in `:core:model` where it can be; that is where JVM tests are cheap.
- **`docs/design-usage.md` is law**, and detekt enforces it. Gold means shiny: a backup
  success state is not gold.
- **No network, no `INTERNET` permission.** A cloud-backed SAF folder is the system's
  business, not ours.
- **Never run anything against `net.pokedex` on my phone except `installRelease`.** It
  holds my real records. Develop and test on the emulator and the debug id
  (`net.pokedex.debug`). Before any uninstall, `pm clear`, connected test or non-release
  install on the phone, check the application id. Ask me if you are unsure.

## After the screens land

- **Regenerate the baseline profile**, because M3 adds screens and navigation it does not
  cover. Follow `docs/architecture.md` §8: extend the journey in `:baselineprofile` to
  cover catch-and-record and settings, run `./gradlew :app:generateBaselineProfile`, and
  commit the result on its own. Then re-measure with `tools/perf/measure-pager.sh` and
  update the table.
- **Do not mix in the pager's settle-frame work.** §8 records that Compose prefetch and
  page deactivation hold the main thread for 10–17 ms when a page settles. That is a
  separate investigation with its own method trace, not a side quest in this milestone.

## Deliverables

1. Backups that survive an uninstall, the restore flow and the first-launch restore offer,
   with tests. Verify on the emulator by uninstalling the debug build, reinstalling it and
   restoring. Report what you saw.
2. The catch flow with origin, date and notes, and editing them.
3. The dashboards you proposed and I approved.
4. The settings screen.
5. ADRs for any decision that changed, especially `allowBackup`, and `docs/architecture.md`
   brought up to date.
6. A regenerated baseline profile in its own commit, with the §8 table re-measured.

Every commit builds: `./gradlew test detekt lintDebug :design-system:designCheck assembleDebug`.
One logical change per commit, imperative subject under 72 characters, no type prefixes.
