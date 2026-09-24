package net.pokedex.core.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
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
        myGameDao.observe().map { ids -> ids.mapTo(LinkedHashSet()) { GameId(it) } }

    suspend fun myGames(): Set<GameId> = withContext(io) {
        myGameDao.all().mapTo(LinkedHashSet()) { GameId(it) }
    }

    suspend fun setMyGame(gameId: GameId, owned: Boolean) = withContext(io) {
        if (owned) myGameDao.insert(MyGameEntity(gameId.value)) else myGameDao.delete(gameId.value)
    }

    suspend fun setLastBox(boxIndex: Int) = edit { it.copy(lastBoxIndex = boxIndex) }

    /** Read, change, write back only if something changed. There is one writer: the user. */
    suspend fun edit(transform: (UserSettings) -> UserSettings) = withContext(io) {
        val current = dao.get()?.toDomain() ?: UserSettings.DEFAULT
        val next = transform(current)
        if (next != current) dao.upsert(next.toEntity())
    }
}
