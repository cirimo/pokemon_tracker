package net.pokedex.core.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.pokedex.core.data.di.IoDispatcher
import net.pokedex.core.data.reference.ReferenceDatabase
import net.pokedex.core.model.HuntGuide
import net.pokedex.core.model.Outcome
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The curated layer -- encounters, methods and odds -- as one indexed [HuntGuide], loaded
 * once per process.
 *
 * Apart from [DexRepository] on purpose: the dex is on the cold-start path and this is not.
 * Only the hunt list and slot detail ask for it, so the box view never pays for it.
 */
@Singleton
class GuideRepository @Inject constructor(
    private val database: ReferenceDatabase,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var cached: HuntGuide? = null

    suspend fun guide(): Outcome<HuntGuide> = mutex.withLock {
        cached?.let { return@withLock Outcome.Ok(it) }
        guardDataset(io) {
            val dao = database.referenceDao()
            HuntGuide(
                encounters = dao.encounters().map { it.toDomain() },
                modifiers = dao.oddsModifiers().map { it.toDomain() },
                methods = dao.encounterMethods().map { it.toDomain() },
            )
        }.also { if (it is Outcome.Ok) cached = it.value }
    }
}
