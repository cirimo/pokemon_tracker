package net.pokedex.core.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.pokedex.core.data.di.IoDispatcher
import net.pokedex.core.data.reference.ReferenceDatabase
import net.pokedex.core.model.GuideBook
import net.pokedex.core.model.Outcome
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The guide layer as one indexed [GuideBook], loaded once per process and only when a guide
 * or a game page asks for it. Like [CurationRepository], never on the cold-start path.
 */
@Singleton
class GuideBookRepository @Inject constructor(
    private val database: ReferenceDatabase,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var cached: GuideBook? = null

    suspend fun guideBook(): Outcome<GuideBook> = mutex.withLock {
        cached?.let { return@withLock Outcome.Ok(it) }
        guardDataset(io) {
            val dao = database.referenceDao()
            GuideTables(
                guides = dao.guides(),
                targets = dao.guideTargets(),
                steps = dao.guideSteps(),
                builds = dao.builds(),
                parts = dao.buildParts(),
                links = dao.guideLinks(),
                advice = dao.guideAdvice(),
                requirements = dao.requirements(),
                locations = dao.locations(),
                farm = dao.farmSources(),
            ).toDomain()
        }.also { if (it is Outcome.Ok) cached = it.value }
    }
}
