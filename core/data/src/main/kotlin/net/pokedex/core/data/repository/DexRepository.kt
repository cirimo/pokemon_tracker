package net.pokedex.core.data.repository

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.pokedex.core.data.di.DefaultDispatcher
import net.pokedex.core.model.AppError
import net.pokedex.core.model.Box
import net.pokedex.core.model.Dex
import net.pokedex.core.model.DexPreset
import net.pokedex.core.model.Game
import net.pokedex.core.model.GameAvailability
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.Slot
import net.pokedex.core.model.Species
import net.pokedex.core.model.Variant
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
        // Concurrent, because on a cold start these are mostly waiting: the first touch of
        // user.db (for the preset id) and the first touch of reference.db each pay for a
        // database open, and the reference queries do not depend on each other. Measured
        // serially on the emulator, the open of user.db alone was half the load.
        val reads = coroutineScope {
            val variants = async { reference.variants() }
            val species = async { reference.species() }
            val games = async { reference.games() }
            val availability = async { reference.availability() }
            val presetId = settings.get().activePresetId
            val preset = async { reference.preset(presetId) }
            val boxes = async { reference.boxes(presetId) }
            val slots = async { reference.slots(presetId) }
            Reads(
                preset = preset.await(),
                boxes = boxes.await(),
                slots = slots.await(),
                variants = variants.await(),
                species = species.await(),
                games = games.await(),
                availability = availability.await(),
            )
        }

        reads.failure()?.let { return it }

        val assembled = withContext(default) {
            runCatching {
                Dex.assemble(
                    preset = reads.preset.okValue(),
                    boxes = reads.boxes.okValue(),
                    slots = reads.slots.okValue(),
                    variants = reads.variants.okValue(),
                    species = reads.species.okValue(),
                    games = reads.games.okValue(),
                    availability = reads.availability.okValue(),
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

    private data class Reads(
        val preset: Outcome<DexPreset>,
        val boxes: Outcome<List<Box>>,
        val slots: Outcome<List<Slot>>,
        val variants: Outcome<List<Variant>>,
        val species: Outcome<List<Species>>,
        val games: Outcome<List<Game>>,
        val availability: Outcome<List<GameAvailability>>,
    ) {
        fun failure(): Outcome.Err? =
            listOf(preset, boxes, slots, variants, species, games, availability)
                .filterIsInstance<Outcome.Err>()
                .firstOrNull()
    }

    /** Only called after [Reads.failure] has returned null. */
    private fun <T> Outcome<T>.okValue(): T = (this as Outcome.Ok).value

    private companion object {
        const val TAG = "DexRepository"
        const val NANOS_PER_MS = 1_000_000
    }
}
