package net.pokedex.core.model

/**
 * The active preset, joined against the reference data it points into, in memory.
 *
 * This is the "loaded once, joined in memory" half of docs/architecture.md section 3. The
 * reference database is immutable at runtime, so everything here is computed once when the
 * dex loads and then only read: the box screen, search, and both detail screens are views
 * over one instance. User records are deliberately absent -- they change, this does not,
 * and they are joined per read by [CatchKey] (see [statusOf] and [searchDex]).
 */
class Dex(
    val preset: DexPreset,
    /** In box order. */
    val boxes: List<Box>,
    /** Every filled slot, in preset order. 1394 for grouped-balanced. */
    val entries: List<DexEntry>,
    /** In the dataset's sort order. */
    val games: List<Game>,
    private val species: Map<Int, Species>,
    private val variants: Map<VariantId, Variant>,
    private val availability: Map<VariantId, List<GameAvailability>>,
) {
    private val byKey: Map<CatchKey, DexEntry> = entries.associateBy { it.key }
    private val byVariant: Map<VariantId, List<DexEntry>> = entries.groupBy { it.variant.id }
    private val byBox: Map<Int, List<DexEntry>> = entries.groupBy { it.slot.boxIndex }
    private val byDexNum: Map<Int, List<Variant>> =
        variants.values.groupBy { it.dexNum }.mapValues { (_, v) -> v.sortedBy { order(it.id) } }
    private val evolvesInto: Map<VariantId, List<Variant>> = variants.values
        .mapNotNull { v -> v.evolvesFromId?.let { from -> from to v } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, v) -> v.sortedBy { order(it.id) } }

    /**
     * The game pairs a filter chip stands for, in release order. HOME is not one: it is where
     * the collection lives, not somewhere you hunt.
     */
    val gameSets: List<GameSet> = games
        .filter { it.gameSet != HOME_GAME_SET }
        .groupBy { it.gameSet }
        .map { (id, members) -> GameSet(id, members.sortedBy { it.sortOrder }) }
        .sortedBy { set -> set.games.minOf { it.sortOrder } }

    /** Distinct types across the dex, in first-seen dex order. */
    val types: List<String> = variants.values
        .sortedBy { it.dexNum }
        .flatMap { listOfNotNull(it.type1, it.type2) }
        .distinct()

    fun entry(key: CatchKey): DexEntry? = byKey[key]

    fun variant(id: VariantId): Variant? = variants[id]

    fun species(dexNum: Int): Species? = species[dexNum]

    /**
     * Every slot demanding [id], in preset order. One for almost everything; two for the
     * seven variants grouped-balanced asks for twice.
     */
    fun copiesOf(id: VariantId): List<DexEntry> = byVariant[id].orEmpty()

    /**
     * A box as positions, holes included: index `i` is slot `i`, and null is a position the
     * preset leaves empty. The grid is drawn from this, so a hole stays a hole instead of
     * the slots after it sliding left -- which is what a HOME box does.
     */
    fun layout(boxIndex: Int): List<DexEntry?> {
        val box = boxes.firstOrNull { it.boxIndex == boxIndex } ?: return emptyList()
        val filled = byBox[boxIndex].orEmpty().associateBy { it.slot.slotIndex }
        return List(box.slotCount) { filled[it] }
    }

    /** Every variant sharing [dexNum], i.e. the species and all of its forms, in preset order. */
    fun forms(dexNum: Int): List<Variant> = byDexNum[dexNum].orEmpty()

    fun evolvesInto(id: VariantId): List<Variant> = evolvesInto[id].orEmpty()

    /** Per-game availability for [id], in game order. */
    fun availability(id: VariantId): List<GameAvailability> = availability[id].orEmpty()

    private fun order(id: VariantId): Int = byVariant[id]?.firstOrNull()?.order ?: Int.MAX_VALUE

    companion object {
        const val HOME_GAME_SET = "home"

        /**
         * Joins the reference rows into a [Dex].
         *
         * A slot whose variant or box is missing is a corrupt dataset rather than something
         * to skip: dropping it would silently shrink the denominator, and 1394 is asserted
         * everywhere else precisely so that cannot happen quietly.
         */
        fun assemble(
            preset: DexPreset,
            boxes: List<Box>,
            slots: List<Slot>,
            variants: List<Variant>,
            species: List<Species>,
            games: List<Game>,
            availability: List<GameAvailability>,
        ): Dex {
            val variantsById = variants.associateBy { it.id }
            val boxesByIndex = boxes.associateBy { it.boxIndex }
            val gameOrder = games.associate { it.id to it.sortOrder }
            val gameSetOf = games.associate { it.id to it.gameSet }
            val availabilityByVariant = availability
                .groupBy { it.variantId }
                .mapValues { (_, rows) -> rows.sortedBy { gameOrder[it.gameId] ?: Int.MAX_VALUE } }

            val entries = slots
                .sortedWith(compareBy({ it.boxIndex }, { it.slotIndex }))
                .mapIndexed { order, slot ->
                    val variant = requireNotNull(variantsById[slot.variantId]) {
                        "slot ${slot.boxIndex}:${slot.slotIndex} demands unknown variant ${slot.variantId}"
                    }
                    val box = requireNotNull(boxesByIndex[slot.boxIndex]) {
                        "slot ${slot.boxIndex}:${slot.slotIndex} is in a box that does not exist"
                    }
                    DexEntry(
                        slot = slot,
                        variant = variant,
                        boxName = box.name,
                        order = order,
                        shinyGames = availabilityByVariant[variant.id].orEmpty()
                            .filter { it.obtainable && !it.shinyLocked && gameSetOf[it.gameId] != HOME_GAME_SET }
                            .mapTo(HashSet()) { it.gameId },
                        shinyGameSets = availabilityByVariant[variant.id].orEmpty()
                            .filter { it.obtainable && !it.shinyLocked }
                            .mapNotNullTo(HashSet()) { gameSetOf[it.gameId] }
                            .apply { remove(HOME_GAME_SET) },
                        searchText = normalizeForSearch(
                            "${variant.displayName} ${variant.formName.orEmpty()} ${variant.id.value}",
                        ),
                        nameText = normalizeForSearch(variant.displayName),
                    )
                }

            return Dex(
                preset = preset,
                boxes = boxes.sortedBy { it.boxIndex },
                entries = entries,
                games = games.sortedBy { it.sortOrder },
                species = species.associateBy { it.dexNum },
                variants = variantsById,
                availability = availabilityByVariant,
            )
        }
    }
}

/**
 * One filled slot and everything a screen needs to draw it, minus the user's record.
 *
 * [shinyGameSets] and the two normalised strings are precomputed here, once, so that a
 * search keystroke is 1394 set lookups and substring checks rather than 1394 joins.
 */
data class DexEntry(
    val slot: Slot,
    val variant: Variant,
    val boxName: String,
    /** Position in preset order, 0-based. The tiebreak for every sort. */
    val order: Int,
    /**
     * Games, never HOME, where this variant can be obtained AND is not shiny-locked. The
     * per-game counterpart of [shinyGameSets], which is what "my games" is checked against:
     * owning Scarlet does not make a Violet exclusive reachable.
     */
    val shinyGames: Set<GameId>,
    /**
     * Game pairs where this variant can be obtained AND is not shiny-locked. Never HOME: a
     * HOME gift is not a hunt, and "available in HOME" would make every filter match it.
     */
    val shinyGameSets: Set<String>,
    internal val searchText: String,
    internal val nameText: String,
) {
    val key: CatchKey get() = slot.catchKey
}

/** A pair of versions sold together, e.g. Scarlet and Violet. What a game filter selects. */
data class GameSet(val id: String, val games: List<Game>)

/**
 * The state of one slot, resolved against the user's records.
 *
 * Resolution is by [CatchKey], never by variant: the two Unown-A slots are two different
 * facts, and catching one says nothing about the other.
 */
enum class SlotStatus {
    Caught,
    Needed,

    /** Not caught, and no shiny of this variant has been released in any game. */
    NoShinyExists,

    /**
     * Not caught, a shiny exists, but none of the user's games offers it shiny. Only ever
     * produced when the user has chosen games: with none chosen, nothing is out of reach.
     */
    Unavailable,
}

/**
 * @param myGames the games the user owns and plays. Empty means "not chosen yet", which
 *   must not paint 1394 slots as out of reach, so it produces no [SlotStatus.Unavailable].
 *   Progress and the dashboards leave it empty on purpose: what is needed does not depend
 *   on which games you own, only whether you can get it now does.
 */
fun statusOf(
    entry: DexEntry,
    records: Map<CatchKey, CatchRecord>,
    myGames: Set<GameId> = emptySet(),
): SlotStatus = when {
    // Caught wins over "no shiny exists": the dataset can lag behind a real distribution,
    // and a record the user made is a fact the app must not contradict.
    records[entry.key]?.caught == true -> SlotStatus.Caught
    !entry.variant.shinyReleased -> SlotStatus.NoShinyExists
    myGames.isNotEmpty() && entry.shinyGames.none { it in myGames } -> SlotStatus.Unavailable
    else -> SlotStatus.Needed
}

/**
 * The state of a variant across every slot demanding it -- what a species page or a form
 * list shows, where there is one row per variant rather than one per slot.
 *
 * Caught only when EVERY copy is caught. For the seven duplicates, owning one Unown-A while
 * the preset demands two is not done, and a form list that showed it in full colour would
 * hide the one gap the living dex is for.
 */
fun variantStatusOf(copies: List<DexEntry>, records: Map<CatchKey, CatchRecord>): SlotStatus {
    val statuses = copies.map { statusOf(it, records) }
    return when {
        statuses.isNotEmpty() && statuses.all { it == SlotStatus.Caught } -> SlotStatus.Caught
        statuses.any { it == SlotStatus.NoShinyExists } -> SlotStatus.NoShinyExists
        else -> SlotStatus.Needed
    }
}
