package net.pokedex.feature.dex.boxes

import androidx.compose.runtime.Immutable
import net.pokedex.core.model.AppError
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CaughtFilter
import net.pokedex.core.model.DexFilter
import net.pokedex.core.model.FarmScope
import net.pokedex.core.model.NoShinyFilter
import net.pokedex.core.model.Progress
import net.pokedex.core.model.SlotStatus
import net.pokedex.designsystem.component.BoxPage

/**
 * The box screen, including its search mode.
 *
 * Search is a mode of this screen rather than its own destination (docs/architecture.md,
 * "Navigation"), so one state class carries both: the pager underneath is still there,
 * still on the same box, when search closes.
 */
@Immutable
data class BoxesUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val pages: List<BoxPage> = emptyList(),
    val overall: Progress = Progress.ZERO,
    /** Uncaught slots whose variant has no released shiny. Counted in [overall], never hidden. */
    val noShinyRemaining: Int = 0,
    val slots: SlotIndex = SlotIndex.EMPTY,
    /** Where the pager opens on a cold start: the box it was on last time. */
    val startBox: Int = 0,
    /** A one-shot request to page to a box, from "Show in box" on a detail screen. */
    val jumpToBox: Int? = null,
    val search: SearchUiState = SearchUiState(),
)

/**
 * The one-line answer to "what next", under the pager. Its own state, not a field of
 * [BoxesUiState]: it changes when a record does, and the pager should not recompose for it.
 */
@Immutable
data class NextHuntUi(val summary: String)

@Immutable
data class SearchUiState(
    val active: Boolean = false,
    val filter: DexFilter = DexFilter(),
    val results: List<SearchResult> = emptyList(),
    val gameSets: List<FilterChoice> = emptyList(),
    val types: List<FilterChoice> = emptyList(),
    /** My games, first to farm first: the "Farm in" choices. Empty until games are chosen. */
    val farmGames: List<FilterChoice> = emptyList(),
) {
    val needed: Boolean get() = filter.caught == CaughtFilter.Needed
    val caught: Boolean get() = filter.caught == CaughtFilter.Caught
    val hideNoShiny: Boolean get() = filter.noShiny == NoShinyFilter.Hide
    val onlyNoShiny: Boolean get() = filter.noShiny == NoShinyFilter.Only
}

@Immutable
data class FilterChoice(val id: String, val label: String)

@Immutable
data class SearchResult(
    /** `CatchKey.toString()`. Unique per slot, so it is both the list key and the shared-element key. */
    val slotKey: String,
    val key: CatchKey,
    val name: String,
    val dexNumber: Int,
    val type1: String,
    val type2: String?,
    val status: SlotStatus,
    /** "Kanto 1, slot 3". What tells the two copies of a duplicated variant apart. */
    val location: String,
    val spriteFile: String,
)

/**
 * Lookups from a grid tile's key to what the tile needs but `BoxSlotItem` cannot carry: the
 * record key to navigate with and the sprite file to draw. Built once per dex.
 *
 * A plain class, so state equality is a reference check rather than a walk over 1394 map
 * entries every time a record changes.
 */
@Immutable
class SlotIndex(
    private val keys: Map<String, CatchKey>,
    private val sprites: Map<String, String>,
) {
    fun key(slotKey: String): CatchKey? = keys[slotKey]

    fun sprite(slotKey: String): String? = sprites[slotKey]

    companion object {
        val EMPTY = SlotIndex(emptyMap(), emptyMap())
    }
}

sealed interface BoxesEvent {
    data class BoxSettled(val index: Int) : BoxesEvent

    /** "Show in box" from a detail screen: page to this box, leaving search if it is open. */
    data class JumpRequested(val boxIndex: Int) : BoxesEvent
    data object JumpHandled : BoxesEvent

    /** From Progress: open search on the slots still needed that are shiny in this game pair. */
    data class ShowNeededIn(val gameSetId: String) : BoxesEvent
    data object OpenSearch : BoxesEvent
    data object CloseSearch : BoxesEvent

    /** A change to the search filter, and nothing else. */
    sealed interface FilterEdit : BoxesEvent
    data class QueryChanged(val query: String) : FilterEdit
    data class CaughtFilterChanged(val value: CaughtFilter) : FilterEdit
    data class GameSetToggled(val id: String) : FilterEdit
    data class TypeToggled(val id: String) : FilterEdit
    data class NoShinyChanged(val value: NoShinyFilter) : FilterEdit

    /** Pick a game to farm in, or put the picked one down. One at a time. */
    data class FarmGameToggled(val gameId: String) : FilterEdit
    data class FarmScopeChanged(val scope: FarmScope) : FilterEdit
    data object ClearRefinements : FilterEdit
    data object Retry : BoxesEvent
}
