# 0011 — Backups live outside the sandbox; device transfer carries user.db

Status: accepted, 2026-09-24. Amends `0007-backup-format.md`, which it does not replace:
the file format is unchanged. This decides where the files go, when they are written,
and what the platform's own backup may carry.

## Context

On 2026-09-23 a baseline-profile run installed a test build under the release id and
uninstalled it afterwards. Every catch record on the phone went with it. Nothing could be
recovered: `allowBackup` was `false`, no export had ever been written, and the app had
never been asked to write one.

ADR 0007 got the format right and said nothing about where the files go. The only copy of
the data lived inside the app's sandbox, and anything that uninstalls the app deletes it
from there: the user, a tool, or a failed signature change.

## Where automatic backups live

| | Uninstall | Factory reset | Phone swap | API 26 vs 35 |
|---|---|---|---|---|
| **SAF folder** (picked once, persisted grant) | survives | if the folder is synced or on an SD card | if the folder is copied or synced | the same code on both |
| MediaStore Downloads | the file survives, but after a reinstall the app no longer owns it: it cannot prune it, and on 33+ cannot read it without SAF anyway | no | if copied | API 29+ only; 26–28 needs a storage permission and raw paths |
| App-private + Google cloud backup | only through restore-on-install, which `adb install` and `installRelease` do not trigger | if Google backup is on | yes | silent, about daily, not testable here |

**Decision: a folder the user picks once through the Storage Access Framework.** It is the
only option that would have saved the records on 2026-09-23, and it is one code path from
API 26 to 35. The files are ordinary JSON the user can see, copy and open.

The cost is that the persisted grant dies with the app, so after a reinstall the folder has
to be picked again. The first-launch restore offer makes that the same tap as "restore".

Until a folder is picked, or if its grant is lost, backups go to a folder inside the app.
That protects against a bad import but not an uninstall, and settings says so plainly.

A SAF folder can be backed by anything that syncs (Syncthing, a OneDrive or Samsung folder,
any provider that supports tree picking), which adds factory-reset and phone-swap survival
without the app gaining `INTERNET`. That is the system's business, not ours. Google Drive's
own provider does not support tree picking, so the app does not suggest it.

## When they are written

Debounced two minutes after the last change, flushed when the app goes to the background,
and once a day as a net, all through WorkManager. Thirty catches in one sitting produce one
file. The writer skips an empty database and unchanged records, so the daily run costs
nothing on a quiet day. The rules that keep a rolling set from destroying what it protects
(a high-water mark that is never pruned, a separate pool for pre-import snapshots, pruning
scoped to the build's own prefix) are in `BackupWriter`, with JVM tests.

## allowBackup

ADR 0007 turned Android backup off because it is silent, unversioned and not inspectable.
Reconsidered on the evidence:

- **Cloud backup would not have saved this loss.** Restore-on-install does not run for `adb`
  installs, and the data was hours old. The objection still stands: a restore path that
  cannot be tested or inspected is not one to rely on. **Cloud backup stays off.**
- **Device-to-device transfer is a different thing.** It happens once, at a phone swap the
  user starts, into an empty install. It is exactly the phone-swap case, and it was being
  excluded twice: by `allowBackup="false"` on API 30 and below, and by
  `<device-transfer><exclude domain="root"/>` on 31 and above.

**Decision: `allowBackup="true"`, with rules that carry `user.db` on device transfer and
nothing to the cloud.** `reference.db` is excluded, since it is rebuilt from the asset.
On API 31+ `dataExtractionRules` governs this. On API 28–30, `fullBackupContent` includes
`user.db` only with `requireFlags="deviceToDeviceTransfer"`. On 26–27 that flag does not
exist, so nothing is included, which is acceptable.

Verified on the API 36 emulator through the local transport
(`settings put secure backup_local_transport_parameters is_device_transfer=true`, then
`bmgr backupnow --non-incremental net.pokedex.debug`). Device transfer carried exactly
`databases/user.db` and `databases/user.db-wal`. With `is_device_transfer=false` the
preflight measured 0 bytes and the transport had nothing to take.

The trade-off is a second way for records to arrive. It is safe because it only ever fills
an empty install, and the first-launch restore offer only appears when the database is
empty.

## Consequences

- Durability no longer depends on the user remembering to export. It still depends on them
  picking a folder once, which the first launch and settings both ask for.
- `backup_log` lives in `user.db` and disappears with it, so it is a status line for
  settings ("last backup 5 minutes ago"), never the restore list. The restore list is read
  from the folder.
- A debug build and a release build can share a folder. Neither prunes the other's files.
