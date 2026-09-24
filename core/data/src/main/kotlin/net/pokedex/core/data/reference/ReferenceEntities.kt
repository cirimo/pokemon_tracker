package net.pokedex.core.data.reference

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Reference-data tables. These live in reference.db, which is shipped as a prebuilt
 * asset and REPLACED WHOLESALE when the dataset is regenerated.
 *
 * Nothing in this file may reference user data, and no entity here may escape
 * :core:data -- repositories map to the domain types in :core:model.
 */

@Entity(tableName = "species")
data class SpeciesEntity(
    @PrimaryKey val dexNum: Int,
    val name: String,
    val generation: Int,
    val region: String,
    val isLegendary: Boolean,
    val isMythical: Boolean,
    val isBaby: Boolean,
    val isUltraBeast: Boolean,
    val isParadox: Boolean,
)

@Entity(
    tableName = "variant",
    foreignKeys = [
        ForeignKey(
            entity = SpeciesEntity::class,
            parentColumns = ["dexNum"],
            childColumns = ["dexNum"],
        ),
    ],
    indices = [
        Index(value = ["dexNum"]),
        Index(value = ["nid"], unique = true),
    ],
)
data class VariantEntity(
    @PrimaryKey val id: String,
    val nid: String,
    val dexNum: Int,
    val formId: String?,
    val displayName: String,
    val formName: String?,
    val type1: String,
    val type2: String?,
    val isDefault: Boolean,
    val isForm: Boolean,
    val isCosmeticForm: Boolean,
    val isFemaleForm: Boolean,
    val isRegional: Boolean,
    val isBattleOnlyForm: Boolean,
    val baseSpeciesId: String?,
    val evolvesFromId: String?,
    val evolveCondition: String?,
    val shinyReleased: Boolean,
    val spriteFile: String,
)

@Entity(tableName = "dex_preset")
data class DexPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val sourceVersion: Int,
    val presetVersion: Int,
    val boxCount: Int,
    val filledSlotCount: Int,
)

@Entity(
    tableName = "box",
    primaryKeys = ["presetId", "boxIndex"],
    foreignKeys = [
        ForeignKey(
            entity = DexPresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
        ),
    ],
)
data class BoxEntity(
    val presetId: String,
    val boxIndex: Int,
    val name: String,
    /** Boxes are not all 30 wide. Fourteen of the 52 in grouped-balanced are short. */
    val slotCount: Int,
    val filledSlotCount: Int,
)

/**
 * A position in a preset and the variant it demands.
 *
 * Empty positions are absent rows rather than rows with a null variant, so slotIndex
 * is not dense within a box. copyIndex is computed by the dataset pipeline and is what
 * lets a slot resolve to a catch record without the record knowing about positions.
 */
@Entity(
    tableName = "slot",
    primaryKeys = ["presetId", "boxIndex", "slotIndex"],
    foreignKeys = [
        ForeignKey(
            entity = VariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variantId"],
        ),
    ],
    indices = [Index(value = ["variantId", "copyIndex"])],
)
data class SlotEntity(
    val presetId: String,
    val boxIndex: Int,
    val slotIndex: Int,
    val variantId: String,
    val copyIndex: Int,
)

@Entity(tableName = "game")
data class GameEntity(
    @PrimaryKey val id: String,
    val name: String,
    val gameSet: String,
    val generation: Int,
    val releaseDate: String,
    val region: String,
    val originMark: String,
    val supportsShiny: Boolean,
    val sortOrder: Int,
)

@Entity(
    tableName = "game_availability",
    primaryKeys = ["variantId", "gameId"],
    indices = [Index(value = ["gameId"])],
)
data class GameAvailabilityEntity(
    val variantId: String,
    val gameId: String,
    val obtainable: Boolean,
    val eventOnly: Boolean,
    val storable: Boolean,
    val transferOnly: Boolean,
    /** Curated. Nobody publishes shiny locks machine-readably. */
    val shinyLocked: Boolean,
    val shinyLockReason: String?,
)

@Entity(tableName = "encounter_method")
data class EncounterMethodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
)

@Entity(
    tableName = "encounter",
    indices = [Index(value = ["variantId", "gameId"]), Index(value = ["gameId"])],
)
data class EncounterEntity(
    /** Deterministic, built as variantId:gameId:ordinal so rebuilds are stable. */
    @PrimaryKey val id: String,
    val variantId: String,
    val gameId: String,
    val methodId: String,
    val location: String?,
    val prerequisite: String?,
    val notes: String?,
    /** This encounter can never be shiny; the variant may still be huntable another way. */
    val shinyLocked: Boolean,
    /** For the `evolution` method: the variant to hunt and then evolve. */
    val fromVariantId: String?,
    val sourceUrl: String,
)

@Entity(tableName = "odds_modifier", primaryKeys = ["gameId", "methodId", "id"])
data class OddsModifierEntity(
    val gameId: String,
    val methodId: String,
    val id: String,
    val label: String,
    val rollsAdded: Int?,
    val denominator: Int?,
    /** Rows sharing a tier are levels of one thing; only the best applies. */
    val tier: String?,
    /** Comes with the method itself rather than with anything the player sets up. */
    val inherent: Boolean,
    val notes: String?,
    val sourceUrl: String,
)

@Entity(tableName = "dataset_meta")
data class DatasetMetaEntity(
    @PrimaryKey val id: Int,
    val datasetVersion: Int,
    val upstreamTag: String,
    val presetVersion: Int,
    val builtAt: String,
    val contentHash: String,
)
