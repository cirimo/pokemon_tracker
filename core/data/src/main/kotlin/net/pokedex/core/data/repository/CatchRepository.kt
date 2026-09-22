package net.pokedex.core.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.pokedex.core.data.di.IoDispatcher
import net.pokedex.core.data.user.CatchRecordDao
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CatchRecord
import net.pokedex.core.model.GameId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user data. Read as a whole-table stream keyed by [CatchKey], because that is the
 * shape every caller wants: progress, the box grid and search all resolve slots to
 * records by key.
 */
@Singleton
class CatchRepository @Inject constructor(
    private val dao: CatchRecordDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeRecords(): Flow<Map<CatchKey, CatchRecord>> =
        dao.observeAll().map { rows -> rows.associate { it.toDomain().let { r -> r.key to r } } }

    suspend fun record(key: CatchKey): CatchRecord? = withContext(io) {
        dao.find(key.variantId.value, key.copyIndex)?.toDomain()
    }

    /**
     * Marks a slot caught or not.
     *
     * Unmarking keeps the row rather than deleting it, so notes and origin survive an
     * accidental tap. Deleting a record is a separate, explicit action.
     */
    suspend fun setCaught(
        key: CatchKey,
        caught: Boolean,
        originGameId: GameId? = null,
        now: Long = System.currentTimeMillis(),
    ) = withContext(io) {
        val existing = dao.find(key.variantId.value, key.copyIndex)?.toDomain()
            ?: CatchRecord.empty(key, now)
        val updated = existing.copy(
            caught = caught,
            originGameId = originGameId ?: existing.originGameId,
            caughtAt = if (caught) existing.caughtAt ?: now else existing.caughtAt,
            updatedAt = now,
        )
        dao.upsert(updated.toEntity())
    }

    suspend fun update(record: CatchRecord) = withContext(io) {
        dao.upsert(record.toEntity())
    }

    suspend fun allRecords(): List<CatchRecord> = withContext(io) {
        dao.all().map { it.toDomain() }
    }

    /** Merge import: incoming records win, untouched existing rows are left alone. */
    suspend fun merge(records: List<CatchRecord>) = withContext(io) {
        dao.upsertAll(records.map { it.toEntity() })
    }

    /** Replace import: the entire table is swapped inside one transaction. */
    suspend fun replaceAll(records: List<CatchRecord>) = withContext(io) {
        dao.replaceAll(records.map { it.toEntity() })
    }
}
