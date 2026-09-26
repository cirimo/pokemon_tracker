package net.pokedex.core.model

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
