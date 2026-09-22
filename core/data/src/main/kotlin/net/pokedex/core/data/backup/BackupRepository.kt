package net.pokedex.core.data.backup

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.pokedex.core.data.di.IoDispatcher
import net.pokedex.core.data.repository.CatchRepository
import net.pokedex.core.data.repository.SettingsRepository
import net.pokedex.core.model.AppError
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CatchRecord
import net.pokedex.core.model.GameId
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.VariantId
import net.pokedex.core.model.backup.AppInfo
import net.pokedex.core.model.backup.BackupCodec
import net.pokedex.core.model.backup.BackupFile
import net.pokedex.core.model.backup.DatasetInfo
import net.pokedex.core.model.backup.RecordInfo
import net.pokedex.core.model.backup.SettingsInfo
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
                    .map(::toRecordInfo),
            )
            Outcome.Ok(BackupCodec.encode(file))
        } catch (e: Exception) {
            Outcome.Err(AppError.StorageFailure(e.message ?: "export failed"))
        }
    }

    /**
     * Imports a backup. Refuses outright rather than importing part of a file it does
     * not fully understand -- see BackupCodec.
     */
    suspend fun import(
        text: String,
        mode: ImportMode,
        now: Long = System.currentTimeMillis(),
    ): Outcome<Int> = withContext(io) {
        when (val parsed = BackupCodec.decode(text)) {
            is Outcome.Err -> parsed
            is Outcome.Ok -> try {
                val records = parsed.value.records.map { toRecord(it, now) }
                when (mode) {
                    ImportMode.MERGE -> catchRepository.merge(records)
                    ImportMode.REPLACE -> catchRepository.replaceAll(records)
                }
                Outcome.Ok(records.size)
            } catch (e: Exception) {
                Outcome.Err(AppError.StorageFailure(e.message ?: "import failed"))
            }
        }
    }

    private fun toRecordInfo(record: CatchRecord) = RecordInfo(
        variantId = record.key.variantId.value,
        copyIndex = record.key.copyIndex,
        caught = record.caught,
        originGameId = record.originGameId?.value,
        caughtAt = record.caughtAt?.let { DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(it)) },
        notes = record.notes,
        favourite = record.favourite,
        priority = record.priority,
    )

    private fun toRecord(info: RecordInfo, now: Long) = CatchRecord(
        key = CatchKey(VariantId(info.variantId), info.copyIndex),
        caught = info.caught,
        originGameId = info.originGameId?.let(::GameId),
        caughtAt = info.caughtAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
        notes = info.notes,
        favourite = info.favourite,
        priority = info.priority,
        updatedAt = now,
    )
}
