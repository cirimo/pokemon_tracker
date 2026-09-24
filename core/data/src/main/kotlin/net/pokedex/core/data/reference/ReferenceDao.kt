package net.pokedex.core.data.reference

import androidx.room.Dao
import androidx.room.Query

/**
 * Read-only access to the bundled reference dataset. There are deliberately no insert,
 * update or delete methods: this database is replaced by a new asset, never mutated.
 */
@Dao
interface ReferenceDao {

    @Query("SELECT * FROM dataset_meta WHERE id = 1")
    suspend fun datasetMeta(): DatasetMetaEntity?

    @Query("SELECT * FROM dex_preset ORDER BY id")
    suspend fun presets(): List<DexPresetEntity>

    @Query("SELECT * FROM dex_preset WHERE id = :presetId")
    suspend fun preset(presetId: String): DexPresetEntity?

    @Query("SELECT * FROM box WHERE presetId = :presetId ORDER BY boxIndex")
    suspend fun boxes(presetId: String): List<BoxEntity>

    @Query("SELECT * FROM slot WHERE presetId = :presetId ORDER BY boxIndex, slotIndex")
    suspend fun slots(presetId: String): List<SlotEntity>

    @Query("SELECT * FROM variant ORDER BY dexNum, id")
    suspend fun variants(): List<VariantEntity>

    @Query("SELECT * FROM variant WHERE id = :variantId")
    suspend fun variant(variantId: String): VariantEntity?

    @Query("SELECT * FROM species ORDER BY dexNum")
    suspend fun species(): List<SpeciesEntity>

    @Query("SELECT * FROM game ORDER BY sortOrder")
    suspend fun games(): List<GameEntity>

    /** All 7580 rows. Read once, for the in-memory game filter; see DexRepository. */
    @Query("SELECT * FROM game_availability")
    suspend fun allAvailability(): List<GameAvailabilityEntity>

    @Query("SELECT * FROM game_availability WHERE variantId = :variantId")
    suspend fun availability(variantId: String): List<GameAvailabilityEntity>

    /** Every curated encounter. Small, and read once for the hunt guide; see DexRepository. */
    @Query("SELECT * FROM encounter ORDER BY id")
    suspend fun encounters(): List<EncounterEntity>

    @Query("SELECT * FROM encounter_method ORDER BY id")
    suspend fun encounterMethods(): List<EncounterMethodEntity>

    @Query("SELECT * FROM odds_modifier ORDER BY gameId, methodId, id")
    suspend fun oddsModifiers(): List<OddsModifierEntity>

    // Counts used by the asset integrity check and by the M0 smoke screen.
    @Query("SELECT COUNT(*) FROM slot WHERE presetId = :presetId")
    suspend fun slotCount(presetId: String): Int

    @Query("SELECT COUNT(*) FROM box WHERE presetId = :presetId")
    suspend fun boxCount(presetId: String): Int

    @Query("SELECT COUNT(DISTINCT variantId) FROM slot WHERE presetId = :presetId")
    suspend fun distinctVariantCount(presetId: String): Int
}
