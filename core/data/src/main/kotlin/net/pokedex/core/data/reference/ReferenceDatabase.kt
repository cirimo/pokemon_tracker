package net.pokedex.core.data.reference

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The bundled, read-only reference dataset.
 *
 * Separate from UserDatabase on purpose, and the separation is physical rather than a
 * convention: Room only re-applies a createFromAsset prepackaged database during a
 * DESTRUCTIVE fallback. If catch records shared this file, refreshing the dataset would
 * mean dropping them. Two files means the reference side can be thrown away and
 * recopied freely while user.db is migrated normally and never destroyed.
 *
 * See docs/adr/0002-two-databases.md.
 *
 * When this schema changes, bump [VERSION] and regenerate the asset -- the pipeline
 * reads the exported schema JSON in core/data/schemas to emit matching DDL and the
 * room_master_table identity hash Room validates the asset against.
 */
@Database(
    entities = [
        SpeciesEntity::class,
        VariantEntity::class,
        DexPresetEntity::class,
        BoxEntity::class,
        SlotEntity::class,
        GameEntity::class,
        GameAvailabilityEntity::class,
        EncounterMethodEntity::class,
        EncounterEntity::class,
        OddsModifierEntity::class,
        DatasetMetaEntity::class,
    ],
    version = ReferenceDatabase.VERSION,
    exportSchema = true,
)
abstract class ReferenceDatabase : RoomDatabase() {

    abstract fun referenceDao(): ReferenceDao

    companion object {
        const val VERSION = 2

        /** Path inside assets/, produced by tools/dataset-pipeline. */
        const val ASSET_PATH = "dataset/reference.db"
    }
}
