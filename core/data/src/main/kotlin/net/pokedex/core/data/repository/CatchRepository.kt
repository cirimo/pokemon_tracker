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
import net.pokedex.core.model.Priority
import net.pokedex.core.model.withCaught
import net.pokedex.core.model.withDetails
import net.pokedex.core.model.withPriority
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
     * Marks a slot caught or not. See [withCaught] for what is kept: unticking loses
     * nothing, and [prefill] never overrides an origin already recorded.
     */
    suspend fun setCaught(
        key: CatchKey,
        caught: Boolean,
        prefill: GameId? = null,
        now: Long = System.currentTimeMillis(),
    ) = withContext(io) {
        val existing = dao.find(key.variantId.value, key.copyIndex)?.toDomain() ?: CatchRecord.empty(key, now)
        dao.upsert(existing.withCaught(caught, now, prefill).toEntity())
    }

    /** The catch sheet's save: origin, date and notes, written as one change. */
    suspend fun setDetails(
        key: CatchKey,
        origin: GameId?,
        caughtAt: Long?,
        notes: String?,
        now: Long = System.currentTimeMillis(),
    ) = withContext(io) {
        val existing = dao.find(key.variantId.value, key.copyIndex)?.toDomain() ?: CatchRecord.empty(key, now)
        dao.upsert(existing.withDetails(origin, caughtAt, notes, now).toEntity())
    }

    /**
     * Deletes a record outright. The explicit action behind "Forget", never a side effect of
     * unticking, and the rolling backups still hold it.
     */
    /**
     * Sets the hunt priority of every slot a hunt fills, in one transaction. A record is
     * created for a slot that has none, uncaught, holding only the priority.
     */
    suspend fun setPriority(keys: Collection<CatchKey>, priority: Priority, now: Long = System.currentTimeMillis()) =
        withContext(io) {
            val changed = keys.mapNotNull { key ->
                val existing = dao.find(key.variantId.value, key.copyIndex)?.toDomain() ?: CatchRecord.empty(key, now)
                existing.withPriority(priority, now).takeIf { it !== existing }
            }
            if (changed.isNotEmpty()) dao.upsertAll(changed.map { it.toEntity() })
        }

    suspend fun forget(key: CatchKey) = withContext(io) {
        dao.delete(key.variantId.value, key.copyIndex)
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
