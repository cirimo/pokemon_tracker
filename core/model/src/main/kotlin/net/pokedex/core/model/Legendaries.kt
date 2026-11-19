package net.pokedex.core.model

/**
 * Which legendaries one game lets me hunt shiny, ranked by how easy they really are (prompt 11).
 *
 * The order is fixed and lexicographic, like the hunt list's (ADR 0012), so every row can say
 * in one line why it is where it is:
 *
 *  1. Only what this game offers unlocked is a hunt here. Locked ones are listed apart.
 *  2. Better odds per attempt first. A Dynamax Adventure at 1/100 beats a reset at 1/4096 by a
 *     factor that no difference in time per attempt makes up, so odds lead. At equal odds, one
 *     this game reaches on its own comes before one that needs another player's game online.
 *  3. Where the game stands in my farm order for it: the only one of my games, then the first,
 *     then a game an earlier one already covers.
 *  4. More slots from one hunt.
 *  5. Preset order.
 *
 * Time per attempt and the risk of losing a shiny are said on the guide, not ranked: no source
 * gives the first, and the second is low for every method here.
 */

/** Where a game stands for one hunt, against my farm order. */
enum class FarmStanding { OnlyHere, HereFirst, EarlierElsewhere }

data class LegendaryHunt(
    val key: HuntKey,
    /** The hunt's needed slots this game offers unlocked, in preset order. */
    val slots: List<DexEntry>,
    /** The best recorded way in this game, or null: "no method recorded yet". */
    val way: Way?,
    val standing: FarmStanding,
    /** Reached only through another player's game online: a Crown Tundra legendary of the other version. */
    val onlineOnly: Boolean,
) {
    val lead: DexEntry get() = slots.first()
}

data class LegendaryPlan(
    val hunts: List<LegendaryHunt>,
    /** Needed legendaries this game has but can never give shiny. */
    val locked: List<DexEntry>,
)

fun isLegendaryEntry(dex: Dex, entry: DexEntry): Boolean =
    dex.species(entry.variant.dexNum)?.let { it.isLegendary || it.isMythical || it.isUltraBeast } == true

fun legendaryPlan(
    dex: Dex,
    records: Map<CatchKey, CatchRecord>,
    farmOrder: List<GameId>,
    curation: Curation,
    game: GameId,
): LegendaryPlan {
    val myGames = farmOrder.toSet() + game
    val needed = dex.entries.filter {
        isLegendaryEntry(dex, it) && it.variant.shinyReleased && records[it.key]?.caught != true
    }
    val inGame = needed.mapNotNull { entry ->
        dex.availability(entry.variant.id).firstOrNull { it.gameId == game && it.obtainable }?.let { entry to it }
    }
    val (lockedRows, open) = inGame.partition { (_, availability) -> availability.shinyLocked }
    val hunts = open
        .filter { (entry, _) -> statusOf(entry, records, myGames) == SlotStatus.Needed }
        .groupBy({ (entry, _) -> huntKeyOf(entry.variant) }, { it })
        .map { (key, rows) ->
            val slots = rows.map { it.first }.sortedBy { it.order }
            val mine = farmOrder.filter { g -> slots.any { g in it.shinyGames } }
            LegendaryHunt(
                key = key,
                slots = slots,
                way = slots.mapNotNull { curation.bestWay(it.variant.id, listOf(game)) }
                    .minByOrNull { it.odds?.best?.oneIn ?: Double.MAX_VALUE },
                standing = when {
                    mine.isEmpty() || mine == listOf(game) -> FarmStanding.OnlyHere
                    mine.first() == game -> FarmStanding.HereFirst
                    else -> FarmStanding.EarlierElsewhere
                },
                onlineOnly = rows.all { it.second.exclusiveTo != null },
            )
        }
        .sortedWith(
            compareBy<LegendaryHunt>(
                { it.way == null },
                { it.way?.odds?.best?.oneIn ?: Double.MAX_VALUE },
                { it.onlineOnly },
                { it.standing },
                { -it.slots.size },
                { it.lead.order },
            ),
        )
    return LegendaryPlan(hunts = hunts, locked = lockedRows.map { it.first }.sortedBy { it.order })
}
