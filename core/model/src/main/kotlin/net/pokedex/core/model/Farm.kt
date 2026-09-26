package net.pokedex.core.model

import kotlinx.serialization.Serializable

/**
 * Where to farm what: my games in the order I work through them, and which of them each
 * needed slot belongs to. Derived on read, like progress and the hunt list. The only thing
 * stored is the order itself. docs/adr/0014-game-order.md.
 */

/**
 * My games, first to farm first.
 *
 * [ranks] is the stored order, which may tie: every game chosen before the order existed
 * has 0. A tie falls back to [games]' order, which is release order, so an order nobody has
 * set yet reads the way the game lists always have.
 */
fun farmOrderOf(games: List<Game>, ranks: Map<GameId, Int>): List<GameId> =
    games.filter { it.id in ranks }
        .sortedWith(compareBy({ ranks.getValue(it.id) }, { it.sortOrder }))
        .map { it.id }

/**
 * [order] with [game] moved [by] places, later for positive. Clamped at both ends, so the
 * first game's "move earlier" does nothing rather than wrapping round.
 */
fun moveInOrder(order: List<GameId>, game: GameId, by: Int): List<GameId> {
    val from = order.indexOf(game)
    if (from < 0) return order
    val to = (from + by).coerceIn(0, order.lastIndex)
    return order.toMutableList().apply { add(to, removeAt(from)) }
}

/** Which half of "where do I farm it" a filter asks. */
@Serializable
enum class FarmScope {
    /** The slots only this one of my games can give me shiny. */
    OnlyHere,

    /** The slots this game is the first of my games, in my order, to give me shiny. */
    HereFirst,
}

/**
 * Where a slot belongs: [game] is the first of my games, in my order, that offers it shiny,
 * and [onlyHere] says no other game of mine does.
 */
data class FarmPlace(val game: GameId, val onlyHere: Boolean)

/**
 * Every slot's [FarmPlace], for one set of my games in one order.
 *
 * It depends on the dex and the order and on nothing I record: catching a slot does not
 * move it to another game, it only stops it being needed. So it is built once when my games
 * or their order change, and a search keystroke pays one array read per slot for it, not a
 * walk of my games. Whether a slot is still needed is the caller's question, answered by
 * [statusOf] as everywhere else.
 *
 * Evolutions need no special case. Upstream marks an evolved form obtainable in the games
 * where it can be evolved, and in no other (Kingambit is Scarlet and Violet only, by evolving
 * Bisharp; Wyrdeer is transfer-only in Scarlet and Violet, where Stantler cannot evolve), so
 * [DexEntry.shinyGames] already means "farm it, or what it evolves from, here".
 */
class FarmPlan(dex: Dex, val order: List<GameId>) {
    /** My games, for [statusOf]. */
    val games: Set<GameId> = order.toHashSet()

    private val places: Array<FarmPlace?> = arrayOfNulls<FarmPlace>(dex.entries.size).also { places ->
        for (entry in dex.entries) {
            // A variant no game has released a shiny of can still be "obtainable and not
            // locked" in the data. It is not a farm job anywhere.
            if (!entry.variant.shinyReleased) continue
            val mine = order.filter { it in entry.shinyGames }
            if (mine.isNotEmpty()) places[entry.order] = FarmPlace(mine.first(), onlyHere = mine.size == 1)
        }
    }

    fun placeOf(entry: DexEntry): FarmPlace? = places.getOrNull(entry.order)

    fun matches(entry: DexEntry, game: GameId, scope: FarmScope): Boolean {
        val place = placeOf(entry) ?: return false
        return when (scope) {
            FarmScope.OnlyHere -> place.onlyHere && place.game == game
            FarmScope.HereFirst -> place.game == game
        }
    }
}

/** One game's share of what I still need, as Progress shows it. */
data class GameFarm(val game: GameId, val onlyHere: Int, val hereFirst: Int) {
    /** Nothing left that this game is first for: everything it can give me first is caught. */
    val done: Boolean get() = hereFirst == 0
}

/** Per game of mine, in my order: the needed slots only it offers, and the ones it offers first. */
fun farmCounts(dex: Dex, records: Map<CatchKey, CatchRecord>, plan: FarmPlan): List<GameFarm> {
    val only = HashMap<GameId, Int>()
    val first = HashMap<GameId, Int>()
    for (entry in dex.entries) {
        val place = plan.placeOf(entry)?.takeIf { statusOf(entry, records) == SlotStatus.Needed } ?: continue
        first.merge(place.game, 1, Int::plus)
        if (place.onlyHere) only.merge(place.game, 1, Int::plus)
    }
    return plan.order.map { GameFarm(it, only[it] ?: 0, first[it] ?: 0) }
}
