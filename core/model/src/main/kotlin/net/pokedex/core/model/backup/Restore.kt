package net.pokedex.core.model.backup

import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CatchRecord
import net.pokedex.core.model.GameId
import java.time.Instant

enum class ImportMode { MERGE, REPLACE }

/**
 * What a backup file holds and what restoring it would do, shown before anything is touched.
 *
 * The counts that matter are the consequences, not the file: how many records a merge
 * would change, and how many a replace would throw away. A restore screen that shows only
 * "1394 records" invites a replace that deletes last night's catches.
 */
data class RestorePreview(
    val schema: Int,
    val exportedAt: Instant?,
    val appVersionName: String,
    val recordCount: Int,
    val caughtCount: Int,
    /** Records a merge would write: new here, or changed in the file after this device. */
    val mergeWrites: Int,
    /** Records on this device that are absent from the file, which replace would delete. */
    val replaceRemoves: Int,
    /** Caught here, and a replace would leave them uncaught or gone. */
    val replaceUncatches: Int,
    /** Records the active preset has no slot for. Kept, never dropped (ADR 0001). */
    val orphanCount: Int,
    /** Nothing here yet, so there is nothing to merge into or snapshot. */
    val localIsEmpty: Boolean,
) {
    companion object {
        fun of(
            file: BackupFile,
            local: Map<CatchKey, CatchRecord>,
            presetKeys: Set<CatchKey>,
        ): RestorePreview {
            val incoming = file.records.map { it.toCatchRecord() }
            val incomingByKey = incoming.associateBy { it.key }
            return RestorePreview(
                schema = file.schema,
                exportedAt = runCatching { Instant.parse(file.exportedAt) }.getOrNull(),
                appVersionName = file.app.versionName,
                recordCount = incoming.size,
                caughtCount = incoming.count { it.caught },
                mergeWrites = mergeRecords(local, incoming).size,
                replaceRemoves = local.keys.count { it !in incomingByKey },
                replaceUncatches = local.values.count { it.caught && incomingByKey[it.key]?.caught != true },
                orphanCount = incoming.count { it.key !in presetKeys },
                localIsEmpty = local.isEmpty(),
            )
        }
    }
}

/**
 * What an import does, decided before it does it.
 *
 * @property snapshotFirst whether the current records must be written to a pre-import
 *   snapshot first. Always, unless there are none: an empty database has nothing to lose,
 *   and after a reinstall there may be no folder to write to yet.
 * @property write the records to upsert.
 * @property clearFirst replace mode: the table is emptied in the same transaction.
 * @property myGames the games to hold afterwards, or null to leave them as they are.
 */
data class ImportPlan(
    val snapshotFirst: Boolean,
    val write: List<CatchRecord>,
    val clearFirst: Boolean,
    val myGames: Set<GameId>?,
) {
    companion object {
        fun of(
            local: Map<CatchKey, CatchRecord>,
            localGames: Set<GameId>,
            file: BackupFile,
            mode: ImportMode,
        ): ImportPlan {
            val incoming = file.records.map { it.toCatchRecord() }
            val snapshot = local.isNotEmpty() || localGames.isNotEmpty()
            return when (mode) {
                ImportMode.MERGE -> ImportPlan(
                    snapshotFirst = snapshot,
                    write = mergeRecords(local, incoming),
                    clearFirst = false,
                    myGames = gamesAfterImport(localGames, file, mode),
                )
                ImportMode.REPLACE -> ImportPlan(
                    snapshotFirst = snapshot,
                    write = incoming,
                    clearFirst = true,
                    myGames = gamesAfterImport(localGames, file, mode),
                )
            }
        }

        /**
         * A file without games -- every file from before M4 -- says nothing about them, so
         * it never clears the ones set here. Otherwise replace takes the file's set and merge
         * takes both: owning a game is not something a merge should quietly undo.
         */
        private fun gamesAfterImport(local: Set<GameId>, file: BackupFile, mode: ImportMode): Set<GameId>? {
            val incoming = file.settings.myGames.mapTo(HashSet()) { GameId(it) }
            if (incoming.isEmpty()) return null
            val after = when (mode) {
                ImportMode.MERGE -> local + incoming
                ImportMode.REPLACE -> incoming
            }
            return after.takeIf { it != local }
        }
    }
}
