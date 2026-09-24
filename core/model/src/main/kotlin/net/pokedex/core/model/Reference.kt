package net.pokedex.core.model

/**
 * Reference-data domain types. Everything here is replaced wholesale when the dataset
 * is regenerated, and nothing here may hold a reference to user data.
 */

data class Species(
    val dexNum: Int,
    val name: String,
    val generation: Int,
    val region: String,
    val isLegendary: Boolean = false,
    val isMythical: Boolean = false,
    val isBaby: Boolean = false,
    val isUltraBeast: Boolean = false,
    val isParadox: Boolean = false,
)

/**
 * The distinction this app cares about:
 *
 *  - Species is a National Dex number. One per line in the dex.
 *  - Form is a named alternative of a species that has its own reference entry:
 *    regional (growlithe-hisui), mega, battle-only, gender-differentiated (venusaur-f),
 *    or purely cosmetic (alcremie-ruby-cream-berry).
 *  - Variant is the living-dex-addressable unit: exactly one row per thing a slot can
 *    demand. Upstream collapses species and form into a single entity, so Variant is
 *    the real table and Species is the grouping the UI hangs off.
 */
data class Variant(
    val id: VariantId,
    /** Upstream national id, e.g. "0003-f". Carried so a rename upstream is detectable. */
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
    val baseSpeciesId: VariantId?,
    val evolvesFromId: VariantId?,
    val evolveCondition: String?,
    /** False means no shiny exists for this variant at all, in any game. */
    val shinyReleased: Boolean,
    /** Asset-relative path, e.g. "sprites/0003-f.webp". */
    val spriteFile: String,
)

data class DexPreset(
    val id: PresetId,
    val name: String,
    val description: String,
    /** Upstream `source.version` from the preset JSON. */
    val sourceVersion: Int,
    /** Ours. Bumped whenever the slot layout changes; drives the review screen. */
    val presetVersion: Int,
    val boxCount: Int,
    val filledSlotCount: Int,
)

data class Box(
    val presetId: PresetId,
    val boxIndex: Int,
    val name: String,
    /** Boxes are NOT all 30 wide in grouped-balanced -- 14 of the 52 are short. */
    val slotCount: Int,
    val filledSlotCount: Int,
)

/**
 * A position in a preset and the variant it demands.
 *
 * Empty positions are simply absent rows, so [slotIndex] is not dense within a box.
 * grouped-balanced has ten interior holes.
 */
data class Slot(
    val presetId: PresetId,
    val boxIndex: Int,
    val slotIndex: Int,
    val variantId: VariantId,
    val copyIndex: Int,
) {
    val catchKey: CatchKey get() = CatchKey(variantId, copyIndex)
}

data class Game(
    val id: GameId,
    val name: String,
    val gameSet: String,
    val generation: Int,
    val releaseDate: String,
    val region: String,
    val originMark: String,
    val supportsShiny: Boolean,
    val sortOrder: Int,
)

/**
 * Is this variant obtainable in this game, can it be stored there, and is it
 * shiny-locked there.
 *
 * obtainable/storable/transferOnly/eventOnly come from upstream. shinyLocked is the
 * curated layer -- nobody publishes it machine-readably.
 */
data class GameAvailability(
    val variantId: VariantId,
    val gameId: GameId,
    val obtainable: Boolean,
    val eventOnly: Boolean,
    val storable: Boolean,
    val transferOnly: Boolean,
    val shinyLocked: Boolean,
    val shinyLockReason: String?,
)

data class EncounterMethod(
    val id: String,
    val name: String,
    val description: String,
)

/**
 * One curated way of getting a variant in a game. Absent rows mean "no method recorded
 * yet", never "not obtainable": obtainability is [GameAvailability], from upstream.
 */
data class Encounter(
    val id: String,
    val variantId: VariantId,
    val gameId: GameId,
    val methodId: String,
    val location: String?,
    val prerequisite: String?,
    val notes: String?,
    /** This encounter can never be shiny, though the variant may be huntable another way. */
    val shinyLocked: Boolean,
    /** For the `evolution` method: hunt this variant, then evolve it. */
    val fromVariantId: VariantId?,
    val sourceUrl: String,
)

/**
 * A modifier that changes shiny odds for a (game, method) pair.
 *
 * Two shapes, because the games use two mechanics: extra reroll chances
 * ([rollsAdded], as with the Shiny Charm and Masuda) and a flat replacement
 * denominator ([denominator], as with Dynamax Adventures at 1/300). How they combine is
 * [oddsFor].
 */
data class OddsModifier(
    val gameId: GameId,
    val methodId: String,
    val id: String,
    val label: String,
    val rollsAdded: Int?,
    val denominator: Int?,
    /** Rows sharing a tier are levels of one thing (outbreak 30+ or 60+); only the best applies. */
    val tier: String?,
    /** Comes with the method itself, not with anything the player sets up. */
    val inherent: Boolean,
    val notes: String?,
    val sourceUrl: String,
)

data class DatasetMeta(
    val datasetVersion: Int,
    val upstreamTag: String,
    val presetVersion: Int,
    val builtAt: String,
    val contentHash: String,
)
