package net.pokedex.core.data.repository

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.pokedex.core.data.di.DefaultDispatcher
import net.pokedex.core.model.AppError
import net.pokedex.core.model.Dex
import net.pokedex.core.model.Outcome
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The active preset as one in-memory [Dex], loaded once per process.
 *
 * Every M2 screen is a view over the same reference data, and that data cannot change while
 * the app runs -- a dataset bump is a new APK. So it is read once, joined once, and shared:
 * the box screen, search and both detail screens ask for it, and only the first one pays.
 * Caching it here rather than in a ViewModel is what keeps opening a slot detail from
 * re-reading 7580 availability rows.
 *
 * User records are not part of it; they change, and are joined per read by CatchKey.
 */
@Singleton
class DexRepository @Inject constructor(
    private val reference: ReferenceRepository,
    private val settings: SettingsRepository,
    @DefaultDispatcher private val default: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var cached: Dex? = null

    suspend fun dex(): Outcome<Dex> = mutex.withLock {
        cached?.let { return@withLock Outcome.Ok(it) }
        load().also { if (it is Outcome.Ok) cached = it.value }
    }

    private suspend fun load(): Outcome<Dex> {
        val started = System.nanoTime()
        val presetId = settings.get().activePresetId

        val preset = reference.preset(presetId)
        val boxes = reference.boxes(presetId)
        val slots = reference.slots(presetId)
        val variants = reference.variants()
        val species = reference.species()
        val games = reference.games()
        val availability = reference.availability()

        listOf(preset, boxes, slots, variants, species, games, availability)
            .filterIsInstance<Outcome.Err>()
            .firstOrNull()
            ?.let { return it }

        val assembled = withContext(default) {
            runCatching {
                Dex.assemble(
                    preset = (preset as Outcome.Ok).value,
                    boxes = (boxes as Outcome.Ok).value,
                    slots = (slots as Outcome.Ok).value,
                    variants = (variants as Outcome.Ok).value,
                    species = (species as Outcome.Ok).value,
                    games = (games as Outcome.Ok).value,
                    availability = (availability as Outcome.Ok).value,
                )
            }
        }
        val dex = assembled.getOrElse { e ->
            return Outcome.Err(AppError.DatasetCorrupt(e.message ?: "the preset does not assemble"))
        }

        // The count every other layer asserts. A dataset that assembles but disagrees with its
        // own preset row has lost slots somewhere, and a quietly smaller denominator is the one
        // failure a progress app must never have.
        if (dex.entries.size != dex.preset.filledSlotCount) {
            return Outcome.Err(
                AppError.DatasetCorrupt(
                    "preset declares ${dex.preset.filledSlotCount} slots, found ${dex.entries.size}",
                ),
            )
        }

        Log.i(TAG, "dex loaded: ${dex.entries.size} slots in ${(System.nanoTime() - started) / NANOS_PER_MS} ms")
        return Outcome.Ok(dex)
    }

    private companion object {
        const val TAG = "DexRepository"
        const val NANOS_PER_MS = 1_000_000
    }
}
