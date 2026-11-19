package net.pokedex.core.model

/**
 * What to have done before a game's hunts (prompt 11): every requirement and every build its
 * guides ask for, each ticked or not. Derived on read from the guide book and the ticks, like
 * progress; the only thing stored is the tick (ADR 0016).
 */

enum class PrepKind { Dlc, Story, Service, Item, Build }

data class PrepItem(
    val refId: String,
    val kind: PrepKind,
    val label: String,
    val detail: String?,
    /** Helps, but no guide waits on it: the Shiny Charm. */
    val optional: Boolean,
    val done: Boolean,
    /** How to get it, in the order you would walk it. */
    val farm: List<FarmSource>,
    /** How many of the family's guides ask for it. */
    val usedBy: Int,
)

/**
 * The prep list for [game], in a fixed order: what every hunt waits on first (DLC, story,
 * services), then builds, then items, optional last. The order never depends on the ticks,
 * so ticking a row does not move it out from under the finger.
 */
fun prepListOf(book: GuideBook, gameSet: String, game: GameId, ticks: Set<PrepTick>): List<PrepItem> {
    val done = ticks.filter { it.gameId == game }.mapTo(HashSet()) { it.refId }
    val family = book.guides.filter { it.gameSet == gameSet }
    val requirementUse = family.flatMap { g -> g.requirements.map { it.refId } }.groupingBy { it }.eachCount()
    val buildUse = family.flatMap { g -> g.builds.map { it.refId } }.groupingBy { it }.eachCount()

    val requirements = book.requirementsOf(gameSet).map { r ->
        PrepItem(
            refId = r.id,
            kind = when (r.kind) {
                RequirementKind.Dlc -> PrepKind.Dlc
                RequirementKind.Story -> PrepKind.Story
                RequirementKind.Service -> PrepKind.Service
                RequirementKind.Item -> PrepKind.Item
            },
            label = r.label,
            detail = r.detail,
            optional = r.optional,
            done = r.id in done,
            farm = book.farmChain(r),
            usedBy = requirementUse[r.id] ?: 0,
        )
    }
    val builds = book.buildsOf(gameSet).map { b ->
        PrepItem(
            refId = b.id,
            kind = PrepKind.Build,
            label = b.title,
            detail = b.purpose,
            optional = false,
            done = b.id in done,
            farm = book.farmChain(b),
            usedBy = buildUse[b.id] ?: 0,
        )
    }
    return (requirements + builds).sortedWith(compareBy({ it.optional }, { it.kind }, { it.label }))
}

/** Whether I said I have the Shiny Charm in [game]. Per game: Sword's is not Shield's. */
fun hasShinyCharm(ticks: Set<PrepTick>, game: GameId): Boolean =
    ticks.any { it.gameId == game && it.refId == SHINY_CHARM }

/**
 * The figure to lead with for a method, given the charm tick: with the charm when I have it and
 * it applies, the plain rate otherwise. Both are always shown somewhere; this only picks which
 * one a single-figure row (a hunt card) carries.
 */
fun leadingOdds(odds: MethodOdds, charm: Boolean): Odds = if (charm) odds.withCharm ?: odds.plain else odds.plain
