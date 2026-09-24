package net.pokedex.core.data.backup

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.pokedex.core.data.di.IoDispatcher
import net.pokedex.core.data.repository.CatchRepository
import net.pokedex.core.data.repository.SettingsRepository
import net.pokedex.core.model.AppError
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.backup.AppInfo
import net.pokedex.core.model.backup.BackupCodec
import net.pokedex.core.model.backup.BackupFile
import net.pokedex.core.model.backup.DatasetInfo
import net.pokedex.core.model.backup.SettingsInfo
import net.pokedex.core.model.backup.mergeRecords
import net.pokedex.core.model.backup.toCatchRecord
import net.pokedex.core.model.backup.toRecordInfo
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export and import of user data.
 *
 * The only thing standing between a lost phone and a lost collection, so it is written
 * to be boring: whole-file, one transaction, no partial states.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val catchRepository: CatchRepository,
    private val settingsRepository: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    enum class ImportMode { MERGE, REPLACE }

    suspend fun export(
        appVersionName: String,
        appVersionCode: Int,
        presetVersion: Int,
        datasetVersion: Int,
        now: Instant = Instant.now(),
    ): Outcome<String> = withContext(io) {
        try {
            val settings = settingsRepository.get()
            val records = catchRepository.allRecords()
            val file = BackupFile(
                schema = BackupFile.CURRENT_SCHEMA,
                exportedAt = DateTimeFormatter.ISO_INSTANT.format(now),
                app = AppInfo(appVersionName, appVersionCode),
                dataset = DatasetInfo(
                    presetId = settings.activePresetId.value,
                    presetVersion = presetVersion,
                    datasetVersion = datasetVersion,
                ),
                settings = SettingsInfo(
                    activePresetId = settings.activePresetId.value,
                    autoBackupEnabled = settings.autoBackupEnabled,
                    autoBackupKeepCount = settings.autoBackupKeepCount,
                ),
                // Sorted so two exports of the same data are byte-identical and diffable.
                records = records
                    .sortedWith(compareBy({ it.key.variantId.value }, { it.key.copyIndex }))
                    .map { it.toRecordInfo() },
            )
            Outcome.Ok(BackupCodec.encode(file))
        } catch (e: Exception) {
            Outcome.Err(AppError.StorageFailure(e.message ?: "export failed"))
        }
    }

    /**
     * Imports a backup. Refuses outright rather than importing part of a file it does
     * not fully understand -- see BackupCodec.
     *
     * Merge writes only the records the file changed more recently than this device did
     * (see [mergeRecords]). The count returned is the number of records written.
     */
    suspend fun import(text: String, mode: ImportMode): Outcome<Int> = withContext(io) {
        when (val parsed = BackupCodec.decode(text)) {
            is Outcome.Err -> parsed
            is Outcome.Ok -> try {
                val incoming = parsed.value.records.map { it.toCatchRecord() }
                val written = when (mode) {
                    ImportMode.MERGE -> {
                        val local = catchRepository.allRecords().associateBy { it.key }
                        mergeRecords(local, incoming).also { catchRepository.merge(it) }
                    }
                    ImportMode.REPLACE -> incoming.also { catchRepository.replaceAll(it) }
                }
                Outcome.Ok(written.size)
            } catch (e: Exception) {
                Outcome.Err(AppError.StorageFailure(e.message ?: "import failed"))
            }
        }
    }
}
