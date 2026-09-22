package net.pokedex.core.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.pokedex.core.data.di.IoDispatcher
import net.pokedex.core.data.user.UserSettingsDao
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
}
