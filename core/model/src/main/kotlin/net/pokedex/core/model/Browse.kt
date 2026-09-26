package net.pokedex.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The list a slot detail was opened from, small enough to travel in a route.
 *
 * Not the list itself: 1394 keys do not belong in a navigation argument. It is the question
 * the list answered -- this box, this search, this hunt filter -- and [browseKeys] asks it
 * again with the same function the list was drawn with. Because the question travels in the
 * route, it survives process death with the rest of the back stack, which an in-memory copy
 * of the list would not. docs/adr/0013-browse-context.md.
 */
@Serializable
sealed interface Browse {
    /** The filled slots of one box, holes skipped, in grid order. */
    @Serializable
    @SerialName("box")
    data class Box(val boxIndex: Int) : Browse

    /** The search results for [filter], in the order the search list shows them. */
    @Serializable
    @SerialName("search")
    data class Search(val filter: DexFilter) : Browse

    /** The hunt list, one page per hunt on its lead slot, narrowed to [gameId] if set. */
    @Serializable
    @SerialName("hunt")
    data class Hunt(val gameId: String? = null) : Browse

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(browse: Browse): String = json.encodeToString(serializer(), browse)

        /**
         * Null for a context an older build wrote that this one cannot read. The detail then
         * opens on its slot alone, which is what it did before browsing existed.
         */
        fun decode(encoded: String): Browse? = runCatching { json.decodeFromString(serializer(), encoded) }.getOrNull()
    }
}

/**
 * The games the hunt list draws on: the one it is filtered to, if that is still one of my
 * games, otherwise all of them. The hunt screen and browsing both call this, so the list you
 * swipe through is the list you were looking at.
 */
fun huntGames(myGames: Set<GameId>, selected: GameId?): Set<GameId> =
    selected?.takeIf { it in myGames }?.let(::setOf) ?: myGames

/**
 * The keys [browse] lists, in order, always including [keep].
 *
 * [keep] is the slot on screen. A detail freezes its list when it opens, so a slot caught
 * while browsing a "needed" search stays where it was. After process death the list is asked
 * for again, and by then [keep] may have been caught out of it; this puts it back in the
 * place it would have had, so the page on screen is never the one that disappears. If even
 * that cannot place it (a newer dataset dropped it), the list is [keep] alone.
 *
 * @param farmOrder my games, first to farm first ([farmOrderOf]).
 */
fun browseKeys(
    browse: Browse,
    dex: Dex,
    records: Map<CatchKey, CatchRecord>,
    farmOrder: List<GameId>,
    guide: HuntGuide?,
    keep: CatchKey,
): List<CatchKey> {
    val myGames = farmOrder.toSet()
    val keys = when (browse) {
        is Browse.Box -> dex.layout(browse.boxIndex).mapNotNull { it?.key }
        is Browse.Search -> {
            val farm = browse.filter.farm?.let { FarmPlan(dex, farmOrder) }
            searchDex(dex, records, browse.filter, always = keep, farm = farm).map { it.key }
        }
        is Browse.Hunt -> {
            if (guide == null) return listOf(keep)
            // Ranked as if [keep] were still needed, with its own priority intact: a hunt caught
            // while browsing keeps the place it had rather than falling to the end.
            val asNeeded = records[keep]?.let { records + (keep to it.copy(caught = false)) } ?: records
            huntPlan(dex, asNeeded, huntGames(myGames, browse.gameId?.let(::GameId)), guide).hunts.map { it.lead.key }
        }
    }
    return if (keep in keys) keys else listOf(keep)
}
