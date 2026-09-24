package net.pokedex.core.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.pokedex.core.data.di.IoDispatcher
import net.pokedex.core.data.repository.CatchRepository
import net.pokedex.core.data.repository.DexRepository
import net.pokedex.core.data.repository.ReferenceRepository
import net.pokedex.core.data.repository.SettingsRepository
import net.pokedex.core.data.user.BackupLogDao
import net.pokedex.core.data.user.BackupLogEntity
import net.pokedex.core.data.user.UserDatabase
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.backup.AppInfo
import net.pokedex.core.model.backup.BackupCodec
import net.pokedex.core.model.backup.BackupFile
import net.pokedex.core.model.backup.BackupName
import net.pokedex.core.model.backup.BackupWriter
import net.pokedex.core.model.backup.DatasetInfo
import net.pokedex.core.model.backup.ImportMode
import net.pokedex.core.model.backup.ImportPlan
import net.pokedex.core.model.backup.RestorePreview
import net.pokedex.core.model.backup.SettingsInfo
import net.pokedex.core.model.backup.toRecordInfo
import net.pokedex.core.model.getOrNull
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export, import and the backup folder.
 *
 * The only thing standing between a lost phone and a lost collection, so it is written
 * to be boring: whole-file, one transaction, no partial states. The decisions (what to
 * write, what to prune, what a merge keeps) are made in :core:model and tested there;
 * this class carries them out against Room and the file system.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: UserDatabase,
    private val catchRepository: CatchRepository,
    private val settingsRepository: SettingsRepository,
    private val referenceRepository: ReferenceRepository,
    private val dexRepository: DexRepository,
    private val location: BackupLocation,
    private val log: BackupLogDao,
    private val appInfo: AppInfo,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val writer = BackupWriter(location.prefix)

    /** When the newest backup this install wrote was made, or null if it has made none. */
    fun observeLastBackup(): Flow<Instant?> =
        log.observe().map { rows -> rows.firstOrNull()?.let { Instant.ofEpochMilli(it.createdAt) } }

    /** The current records as a backup file. */
    suspend fun currentFile(now: Instant = Instant.now()): BackupFile = withContext(io) {
        val settings = settingsRepository.get()
        val meta = referenceRepository.datasetMeta().getOrNull()
        BackupFile(
            schema = BackupFile.CURRENT_SCHEMA,
            exportedAt = DateTimeFormatter.ISO_INSTANT.format(now),
            app = appInfo,
            dataset = DatasetInfo(
                presetId = settings.activePresetId.value,
                presetVersion = meta?.presetVersion ?: 0,
                datasetVersion = meta?.datasetVersion ?: 0,
            ),
            settings = SettingsInfo(
                activePresetId = settings.activePresetId.value,
                autoBackupEnabled = settings.autoBackupEnabled,
                autoBackupKeepCount = settings.autoBackupKeepCount,
            ),
            // Sorted so two exports of the same data are byte-identical and diffable.
            records = catchRepository.allRecords()
                .sortedWith(compareBy({ it.key.variantId.value }, { it.key.copyIndex }))
                .map { it.toRecordInfo() },
        )
    }

    /**
     * An automatic backup into the current folder. Skips an empty database and unchanged
     * records; see [BackupWriter] for why each rule exists.
     */
    suspend fun autoBackup(reason: String, now: Instant = Instant.now()): Outcome<BackupWriter.AutoResult> =
        storage("backup failed") {
            val keep = settingsRepository.get().autoBackupKeepCount
            val file = currentFile(now)
            val result = writer.writeAuto(location.current(), file, now, keep)
            if (result is BackupWriter.AutoResult.Written) record(result.name, file, reason)
            result
        }

    /** Manual export to a document the user created with the system picker. */
    suspend fun exportTo(uri: Uri, now: Instant = Instant.now()): Outcome<Int> = storage("export failed") {
        val file = currentFile(now)
        val text = BackupCodec.encode(file)
        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(text.encodeToByteArray()) }
            ?: error("could not open the file for writing")
        file.records.size
    }

    /**
     * What restoring [text] would do. Refuses a newer schema here, before any restore
     * screen offers a button, with both numbers in the error.
     */
    suspend fun preview(text: String): Outcome<RestorePreview> = withContext(io) {
        when (val parsed = BackupCodec.decode(text)) {
            is Outcome.Err -> parsed
            is Outcome.Ok -> when (val dex = dexRepository.dex()) {
                is Outcome.Err -> dex
                is Outcome.Ok -> Outcome.Ok(
                    RestorePreview.of(
                        file = parsed.value,
                        local = catchRepository.allRecords().associateBy { it.key },
                        presetKeys = dex.value.entries.mapTo(HashSet()) { it.key },
                    ),
                )
            }
        }
    }

    /**
     * Imports a backup, all or nothing.
     *
     * Everything happens in one transaction, snapshot included: the current records are read,
     * written to a pre-import snapshot, and only then changed. If the snapshot cannot be
     * written, the transaction rolls back and nothing is imported, because an import without
     * its way back is exactly what ADR 0007 rules out.
     */
    suspend fun import(text: String, mode: ImportMode, now: Instant = Instant.now()): Outcome<ImportResult> =
        withContext(io) {
            when (val parsed = BackupCodec.decode(text)) {
                is Outcome.Err -> parsed
                is Outcome.Ok -> storage("import failed") {
                    val folder = location.current()
                    db.withTransaction {
                        val local = catchRepository.allRecords().associateBy { it.key }
                        val plan = ImportPlan.of(local, parsed.value, mode)
                        val snapshot = if (plan.snapshotFirst) {
                            val current = currentFile(now)
                            writer.writeSnapshot(folder, current, now).also { record(it, current, "pre-import") }
                        } else {
                            null
                        }
                        if (plan.clearFirst) {
                            catchRepository.replaceAll(plan.write)
                        } else {
                            catchRepository.merge(plan.write)
                        }
                        ImportResult(written = plan.write.size, snapshot = snapshot)
                    }
                }
            }
        }

    data class ImportResult(val written: Int, val snapshot: BackupName?)

    private suspend fun record(name: BackupName, file: BackupFile, reason: String) {
        log.insert(
            BackupLogEntity(
                fileName = name.fileName,
                createdAt = name.createdAt.toEpochMilli(),
                recordCount = file.records.size,
                sizeBytes = BackupCodec.encode(file).length.toLong(),
                reason = reason,
            ),
        )
        log.trim(LOG_ROWS)
    }

    private suspend fun <T> storage(what: String, block: suspend () -> T): Outcome<T> = storage(io, what, block)

    private companion object {
        /** backup_log is a status line for settings, not the restore list, so it stays short. */
        const val LOG_ROWS = 50
    }
}
