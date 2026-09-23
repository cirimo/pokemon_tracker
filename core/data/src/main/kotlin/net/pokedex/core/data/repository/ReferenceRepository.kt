package net.pokedex.core.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.pokedex.core.data.di.IoDispatcher
import net.pokedex.core.data.reference.ReferenceDatabase
import net.pokedex.core.model.AppError
import net.pokedex.core.model.Box
import net.pokedex.core.model.DatasetMeta
import net.pokedex.core.model.DexPreset
import net.pokedex.core.model.Game
import net.pokedex.core.model.GameAvailability
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.PresetId
import net.pokedex.core.model.Slot
import net.pokedex.core.model.Species
import net.pokedex.core.model.Variant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the bundled dataset.
 *
 * Every entry point returns [Outcome] because "the asset is corrupt or missing" is a
 * real state on a real device (a truncated install, a failed copy), and the app must
 * show a dedicated screen rather than crash into a stack trace.
 */
@Singleton
class ReferenceRepository @Inject constructor(
    private val database: ReferenceDatabase,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dao get() = database.referenceDao()

    suspend fun datasetMeta(): Outcome<DatasetMeta> = guardDataset(io) {
        dao.datasetMeta()?.toDomain() ?: error("dataset_meta row is missing")
    }

    suspend fun presets(): Outcome<List<DexPreset>> = guardDataset(io) {
        dao.presets().map { it.toDomain() }
    }

    suspend fun preset(id: PresetId): Outcome<DexPreset> = guardDataset(io) {
        dao.preset(id.value)?.toDomain() ?: error("preset ${id.value} is not in the dataset")
    }

    suspend fun boxes(presetId: PresetId): Outcome<List<Box>> = guardDataset(io) {
        dao.boxes(presetId.value).map { it.toDomain() }
    }

    /** The full ordered slot list for a preset. ~1394 rows for grouped-balanced. */
    suspend fun slots(presetId: PresetId): Outcome<List<Slot>> = guardDataset(io) {
        dao.slots(presetId.value).map { it.toDomain() }
    }

    suspend fun variants(): Outcome<List<Variant>> = guardDataset(io) {
        dao.variants().map { it.toDomain() }
    }

    suspend fun games(): Outcome<List<Game>> = guardDataset(io) {
        dao.games().map { it.toDomain() }
    }

    suspend fun species(): Outcome<List<Species>> = guardDataset(io) {
        dao.species().map { it.toDomain() }
    }

    /** Every (variant, game) row. The source of "where can I get it shiny". */
    suspend fun availability(): Outcome<List<GameAvailability>> = guardDataset(io) {
        dao.allAvailability().map { it.toDomain() }
    }

    /**
     * Cheap structural check that the asset is the one we expect.
     *
     * Room already validates the schema identity hash; this validates the CONTENT, which
     * is the part a bad pipeline run can get wrong without Room noticing.
     */
    suspend fun integrity(presetId: PresetId): Outcome<Integrity> = guardDataset(io) {
        Integrity(
            boxCount = dao.boxCount(presetId.value),
            filledSlotCount = dao.slotCount(presetId.value),
            distinctVariantCount = dao.distinctVariantCount(presetId.value),
        )
    }

    data class Integrity(
        val boxCount: Int,
        val filledSlotCount: Int,
        val distinctVariantCount: Int,
    )
}

/**
 * Any exception reading the bundled dataset is a corrupt dataset. Top-level rather than a
 * member so the repository's function count measures its API, not its plumbing.
 */
@Suppress("TooGenericExceptionCaught") // Room, SQLite and the mappers all throw differently.
internal suspend inline fun <T> guardDataset(
    io: CoroutineDispatcher,
    crossinline block: suspend () -> T,
): Outcome<T> = withContext(io) {
    try {
        Outcome.Ok(block())
    } catch (e: Exception) {
        Outcome.Err(AppError.DatasetCorrupt(e.message ?: e::class.java.simpleName))
    }
}
