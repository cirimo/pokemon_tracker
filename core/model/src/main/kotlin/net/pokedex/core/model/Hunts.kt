package net.pokedex.core.model

/**
 * What to hunt next, derived on read like progress. Nothing here is stored: not a rank, not
 * a score, not a count. See docs/adr/0012-hunt-ranking.md for why the order is fixed.
 */

/**
 * [CatchRecord.priority] as the three buckets the app offers. The field stays an Int so a
 * backup written before M4 still reads, and so any positive number already means "want".
 */
enum class Priority(val value: Int) {
    Want(1),
    Normal(0),
    Later(-1),
    ;

    companion object {
        fun of(value: Int): Priority = when {
            value > 0 -> Want
            value < 0 -> Later
            else -> Normal
        }
    }
}

/**
 * The curated "how": encounters and odds, indexed for lookup. Built once per dataset; the
 * reference data does not change while the app runs.
 */
class HuntGuide(
    encounters: List<Encounter>,
    modifiers: List<OddsModifier>,
    methods: List<EncounterMethod>,
) {
    private val byVariantGame = encounters.groupBy { it.variantId to it.gameId }
    private val oddsByMethod: Map<Pair<GameId, String>, MethodOdds?> = modifiers
        .groupBy { it.gameId to it.methodId }
        .mapValues { (_, rows) -> oddsFor(rows) }
    private val methodsById = methods.associateBy { it.id }

    fun encounters(variant: VariantId, game: GameId): List<Encounter> = byVariantGame[variant to game].orEmpty()

    fun method(id: String): EncounterMethod? = methodsById[id]

    fun odds(game: GameId, methodId: String): MethodOdds? = oddsByMethod[game to methodId]

    /**
     * The best recorded way to get [variant] shiny in any of [games]: the unlocked encounter
     * with the best odds, then the one with odds recorded at all, then game order.
     *
     * An evolution row takes the odds of the best way to get what it evolves from, in the
     * same game, because that is the hunt you will actually be doing.
     */
    fun bestWay(variant: VariantId, games: List<GameId>): Way? =
        games.flatMapIndexed { order, game -> waysIn(variant, game, depth = 0).map { it to order } }
            .sortedWith(
                compareBy<Pair<Way, Int>> { (way, _) -> way.odds == null }
                    .thenBy { (way, _) -> way.odds?.best?.oneIn ?: Double.MAX_VALUE }
                    .thenBy { (_, order) -> order },
            )
            .firstOrNull()
            ?.first

    private fun waysIn(variant: VariantId, game: GameId, depth: Int): List<Way> =
        encounters(variant, game).filterNot { it.shinyLocked }.mapNotNull { encounter ->
            val from = encounter.fromVariantId
            if (from == null) {
                Way(game, encounter, odds(game, encounter.methodId), via = null)
            } else if (depth < MAX_EVOLUTION_DEPTH) {
                // Evolving is a way only if the thing it evolves from can be found shiny.
                val source = waysIn(from, game, depth + 1)
                    .minByOrNull { it.odds?.best?.oneIn ?: Double.MAX_VALUE }
                source?.let { Way(game, encounter, it.odds, via = it) }
            } else {
                null
            }
        }

    private companion object {
        /** Pichu to Pikachu to Raichu is two; nothing in the games needs more. */
        const val MAX_EVOLUTION_DEPTH = 3
    }
}

/**
 * One way to get a variant shiny in one game.
 *
 * @property via for an evolution: the way to get what it evolves from.
 * @property odds null when nothing is curated for this method's odds.
 */
data class Way(
    val game: GameId,
    val encounter: Encounter,
    val odds: MethodOdds?,
    val via: Way?,
)

/**
 * The needed slots one hunt fills: a species, with each regional form its own hunt. A
 * Galarian Meowth is not found where a Kantonian one is, so they are two trips; the male
 * and female Meowth, or the 28 Unown, come from the same place.
 */
data class HuntKey(val dexNum: Int, val regionalForm: String?)

/**
 * A row of the hunt list, with everything it needs to say why it is where it is.
 *
 * @property slots the needed slots it fills that one of my games offers shiny, in preset
 *   order. A duplicated variant appears twice: owning one Unown-A fills one slot.
 * @property games my games that offer at least one of those slots shiny, in game order.
 * @property way the best recorded method, or null: "no method recorded yet".
 * @property slotsWithWay how many of [slots] have a recorded method of their own. Fewer than
 *   all when a hunt spans forms the method does not reach (an outbreak gives plain Rotom, not
 *   its appliance forms), and the row has to say so rather than imply the method fills them.
 * @property box the box that is closest to done among the boxes these slots are in.
 */
data class Hunt(
    val key: HuntKey,
    val slots: List<DexEntry>,
    val priority: Priority,
    val games: List<GameId>,
    val way: Way?,
    val slotsWithWay: Int,
    val box: BoxNeed,
) {
    val lead: DexEntry get() = slots.first()
}

/**
 * How close a box is: [remaining] needed slots in it overall, [filledHere] of them by this hunt.
 * When the two are equal, this hunt finishes the box.
 */
data class BoxNeed(val boxIndex: Int, val name: String, val remaining: Int, val filledHere: Int) {
    val finishes: Boolean get() = remaining == filledHere
}

/**
 * A needed slot none of my games offers shiny, and why. The list shows these last rather
 * than dropping them: they are still gaps, they just need something other than a hunt.
 */
data class OutOfReach(val entry: DexEntry, val reason: Reason, val games: List<GameId>) {
    enum class Reason {
        /** Shiny in games I have not chosen. [games] names them. */
        OtherGames,

        /** Obtainable, but shiny-locked in every game that has it. */
        ShinyLocked,

        /** Only ever an event distribution in the games that have it. */
        EventOnly,

        /** No Switch game offers it shiny at all; it arrives through HOME from elsewhere. */
        TransferOnly,
    }
}

/**
 * Where a variant stands in one game, as slot detail says it. Every case but [Shiny] is a
 * reason the game is not a place to hunt it.
 */
enum class Standing {
    /** Obtainable and not shiny-locked. The only one a hunt can use. */
    Shiny,

    /** Obtainable, but never shiny there. */
    ShinyLocked,

    /** Only through an event distribution. */
    EventOnly,

    /** Can live in the game's boxes, but only arrives through HOME. */
    TransferOnly,

    /** Obtainable, but no shiny of it has been released anywhere. */
    NoShinyYet,

    /** The game does not have it. */
    Absent,
}

fun standingOf(dex: Dex, variant: VariantId, game: GameId): Standing {
    val row = dex.availability(variant).firstOrNull { it.gameId == game } ?: return Standing.Absent
    val shinyExists = dex.variant(variant)?.shinyReleased ?: false
    return when {
        row.obtainable && !shinyExists -> Standing.NoShinyYet
        row.obtainable && row.shinyLocked -> Standing.ShinyLocked
        row.obtainable -> Standing.Shiny
        row.eventOnly -> Standing.EventOnly
        row.transferOnly -> Standing.TransferOnly
        else -> Standing.Absent
    }
}

data class HuntPlan(val hunts: List<Hunt>, val outOfReach: List<OutOfReach>)

/**
 * The ranked hunt list.
 *
 * Only needed slots with a shiny in existence are considered; caught and no-shiny slots are
 * not hunts. Order, fixed and explained on every row:
 *
 *  1. Priority bucket, want first. The one input that is the user's own judgement.
 *  2. A method recorded for one of my games. A hunt you know how to do is actionable
 *     tonight; one with no method yet needs research first.
 *  3. More slots filled by the one hunt.
 *  4. Fewer needed slots left in its box, so a nearly finished box gets finished.
 *  5. Preset order, so the list is stable and reads like the boxes.
 *
 * @param myGames the chosen games. Empty is handled by the caller, which asks the user to
 *   choose; here it simply makes every slot out of reach.
 * @param include which reachable slots to hunt at all; the rest are left out, not listed as
 *   out of reach, because they are reachable, just not asked about. Box nearness still counts
 *   them: a box is as close to done as it is.
 */
fun huntPlan(
    dex: Dex,
    records: Map<CatchKey, CatchRecord>,
    myGames: Set<GameId>,
    guide: HuntGuide,
    include: (DexEntry) -> Boolean = { true },
): HuntPlan {
    val needed = dex.entries.filter { statusOf(it, records) == SlotStatus.Needed }
    val myGamesInOrder = dex.games.map { it.id }.filter { it in myGames }
    val remainingByBox = needed.groupingBy { it.slot.boxIndex }.eachCount()
    val boxNames = dex.boxes.associate { it.boxIndex to it.name }

    val (reachable, unreachable) = needed.partition { entry -> entry.shinyGames.any { it in myGames } }

    val hunts = reachable
        .filter(include)
        .groupBy { HuntKey(it.variant.dexNum, regionalFormOf(it.variant)) }
        .map { (key, slots) ->
            val sorted = slots.sortedBy { it.order }
            val ways = sorted.map { it.variant.id }.distinct()
                .associateWith { guide.bestWay(it, myGamesInOrder) }
            Hunt(
                key = key,
                slots = sorted,
                priority = Priority.of(sorted.maxOf { records[it.key]?.priority ?: 0 }),
                games = myGamesInOrder.filter { game -> sorted.any { game in it.shinyGames } },
                way = ways.values.filterNotNull()
                    .minWithOrNull(compareBy({ it.odds == null }, { it.odds?.best?.oneIn ?: Double.MAX_VALUE })),
                slotsWithWay = sorted.count { ways[it.variant.id] != null },
                box = sorted.groupBy { it.slot.boxIndex }
                    .map { (box, here) ->
                        BoxNeed(box, boxNames[box].orEmpty(), remainingByBox.getValue(box), here.size)
                    }
                    .minWith(compareBy({ it.remaining - it.filledHere }, { it.boxIndex })),
            )
        }
        .sortedWith(
            compareByDescending<Hunt> { it.priority.value }
                .thenBy { it.way == null }
                .thenByDescending { it.slots.size }
                .thenBy { it.box.remaining - it.box.filledHere }
                .thenBy { it.lead.order },
        )

    return HuntPlan(hunts = hunts, outOfReach = unreachable.map { outOfReach(it, dex) })
}

/**
 * The hunt list as the hunt screen asks for it: all my games, or one of them, and for one
 * game, which of its slots. The screen and browsing both call this, so the list you swipe
 * through is the list you were looking at.
 *
 * @param farmOrder my games, first to farm first.
 * @param selected one game to narrow to. Dropped if it is no longer one of mine, rather than
 *   showing nothing.
 * @param scope for one game: null for everything it offers shiny, otherwise only the slots
 *   that are farmed there (see [FarmPlan]). Ignored for all my games, where every slot is
 *   in exactly one game's share anyway.
 */
fun huntPlanFor(
    dex: Dex,
    records: Map<CatchKey, CatchRecord>,
    farmOrder: List<GameId>,
    selected: GameId?,
    scope: FarmScope?,
    guide: HuntGuide,
): HuntPlan {
    val myGames = farmOrder.toSet()
    val game = selected?.takeIf { it in myGames }
    val share: ((DexEntry) -> Boolean)? = if (game != null && scope != null) {
        FarmPlan(dex, farmOrder).let { plan -> { entry -> plan.matches(entry, game, scope) } }
    } else {
        null
    }
    return huntPlan(dex, records, huntGames(myGames, game), guide, include = share ?: { true })
}

/**
 * The region a regional variant belongs to, or null. A female regional form ("hisui-f", as
 * upstream spells Hisuian Sneasel's) is the same trip as the male, so the gender is dropped.
 */
private fun regionalFormOf(variant: Variant): String? =
    variant.formId?.takeIf { variant.isRegional }?.removeSuffix(FEMALE_SUFFIX)

private const val FEMALE_SUFFIX = "-f"

private fun outOfReach(entry: DexEntry, dex: Dex): OutOfReach {
    val otherGames = dex.games.map { it.id }.filter { it in entry.shinyGames }
    val availability = dex.availability(entry.variant.id)
    return when {
        otherGames.isNotEmpty() -> OutOfReach(entry, OutOfReach.Reason.OtherGames, otherGames)
        availability.any { it.obtainable && it.shinyLocked } ->
            OutOfReach(entry, OutOfReach.Reason.ShinyLocked, emptyList())
        availability.any { it.eventOnly } -> OutOfReach(entry, OutOfReach.Reason.EventOnly, emptyList())
        else -> OutOfReach(entry, OutOfReach.Reason.TransferOnly, emptyList())
    }
}
