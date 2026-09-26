package net.pokedex.core.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.pokedex.core.data.di.IoDispatcher
import net.pokedex.core.data.user.MyGameDao
import net.pokedex.core.data.user.MyGameEntity
import net.pokedex.core.data.user.UserSettingsDao
import net.pokedex.core.model.GameId
import net.pokedex.core.model.UserSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings live in user.db rather than DataStore so that one export/import round trip
 * covers everything the user chose, and so a restore is one transaction.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dao: UserSettingsDao,
    private val myGameDao: MyGameDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observe(): Flow<UserSettings> =
        dao.observe().map { it?.toDomain() ?: UserSettings.DEFAULT }

    suspend fun get(): UserSettings = withContext(io) {
        dao.get()?.toDomain() ?: UserSettings.DEFAULT
    }

    suspend fun update(settings: UserSettings) = withContext(io) {
        dao.upsert(settings.toEntity())
    }

    /**
     * The games the user owns and plays. Empty means "not chosen yet", and every reader
     * treats that as nothing being out of reach rather than everything.
     */
    fun observeMyGames(): Flow<Set<GameId>> =
        // Set equality ignores order, so a reorder does not re-emit: the box grid, which
        // only cares what is owned, is not redrawn because the farm order changed.
        myGameDao.observe().map { rows -> rows.mapTo(HashSet()) { GameId(it.gameId) } }.distinctUntilChanged()

    /**
     * My games with their stored farm order. Ties are expected (every game chosen before the
     * order existed is 0) and are resolved in release order by `farmOrderOf`, which knows the
     * games; this layer does not.
     */
    fun observeFarmRanks(): Flow<Map<GameId, Int>> =
        myGameDao.observe().map { rows -> rows.toRanks() }.distinctUntilChanged()

    suspend fun farmRanks(): Map<GameId, Int> = withContext(io) { myGameDao.all().toRanks() }

    suspend fun setMyGame(gameId: GameId, owned: Boolean) = withContext(io) {
        if (owned) myGameDao.insertLast(gameId.value) else myGameDao.delete(gameId.value)
    }

    /** [order] is every game of mine, first to farm first. */
    suspend fun setFarmOrder(order: List<GameId>) = withContext(io) {
        myGameDao.reorder(order.map { it.value })
    }

    suspend fun setLastBox(boxIndex: Int) = edit { it.copy(lastBoxIndex = boxIndex) }

    /** Read, change, write back only if something changed. There is one writer: the user. */
    suspend fun edit(transform: (UserSettings) -> UserSettings) = withContext(io) {
        val current = dao.get()?.toDomain() ?: UserSettings.DEFAULT
        val next = transform(current)
        if (next != current) dao.upsert(next.toEntity())
    }
}

private fun List<MyGameEntity>.toRanks(): Map<GameId, Int> = associate { GameId(it.gameId) to it.farmOrder }
