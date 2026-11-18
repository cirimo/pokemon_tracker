package net.pokedex.core.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.pokedex.core.data.di.IoDispatcher
import net.pokedex.core.data.user.PrepTickDao
import net.pokedex.core.data.user.PrepTickEntity
import net.pokedex.core.model.GameId
import net.pokedex.core.model.PrepTick
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The preparation ticked per game: builds made, the Shiny Charm, DLC owned (prompt 11).
 * The ids are the guide layer's, from the other database; nothing here checks them, because a
 * tick must outlive a dataset that renames or drops what it named (ADR 0002).
 */
@Singleton
class PrepRepository @Inject constructor(
    private val dao: PrepTickDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    fun observe(): Flow<Set<PrepTick>> =
        dao.observe().map { rows -> rows.mapTo(HashSet()) { it.toDomain() } }.distinctUntilChanged()

    suspend fun all(): Set<PrepTick> = withContext(io) { dao.all().mapTo(HashSet()) { it.toDomain() } }

    suspend fun setDone(game: GameId, refId: String, done: Boolean, now: Long = System.currentTimeMillis()) =
        withContext(io) {
            if (done) dao.upsert(PrepTickEntity(game.value, refId, now)) else dao.delete(game.value, refId)
        }

    /** An import's ticks, replacing what is here; call inside the import's transaction. */
    suspend fun replaceAll(ticks: Set<PrepTick>) =
        dao.replaceAll(ticks.map { PrepTickEntity(it.gameId.value, it.refId, it.doneAt) })

    private fun PrepTickEntity.toDomain() = PrepTick(GameId(gameId), refId, doneAt)
}
