package net.pokedex.feature.dex.boxes

import androidx.compose.runtime.Immutable
import net.pokedex.core.model.AppError
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CaughtFilter
import net.pokedex.core.model.DexFilter
import net.pokedex.core.model.FarmScope
import net.pokedex.core.model.NoShinyFilter
import net.pokedex.core.model.Ownership
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
    /** The living dex, regular or shiny. The second number: never the headline, never gold. */
    val living: Progress = Progress.ZERO,
    /** Uncaught slots whose variant has no released shiny. Counted in [overall], never hidden. */
    val noShinyRemaining: Int = 0,
    val slots: SlotIndex = SlotIndex.EMPTY,
    /** Where the pager opens on a cold start: the box it was on last time. */
    val startBox: Int = 0,
    /** A one-shot request to page to a box, from "Show in box" on a detail screen. */
    val jumpToBox: Int? = null,
    val search: SearchUiState = SearchUiState(),
    val mark: MarkUi = MarkUi(),
)

/**
 * Mark mode: entering regulars from HOME, box by box. A tap marks or unmarks the regular one
 * instead of opening the slot, and is written at once. [changed] is how many slots this
 * session touched, all of which Undo puts back.
 */
@Immutable
data class MarkUi(val active: Boolean = false, val changed: Int = 0)

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
    val regular: Boolean get() = filter.caught == CaughtFilter.Regular
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
    val ownership: Ownership,
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

    /**
     * From Progress: open search on this filter, e.g. the needed slots Arceus is first for.
     * Encoded, as it travelled; one that no longer decodes opens an unfiltered search.
     */
    data class ShowSearch(val encodedFilter: String) : BoxesEvent
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

    /** Into mark mode, or out of it ("Done"). Out keeps every mark and ends the session's undo. */
    data class Marking(val on: Boolean) : BoxesEvent

    /** A write in mark mode. */
    sealed interface MarkEdit : BoxesEvent

    /** The regular one in this slot, on if it was off and off if it was on. */
    data class MarkToggled(val key: CatchKey) : MarkEdit

    /** Every slot in this box holding nothing, marked regular. */
    data class MarkBox(val boxIndex: Int) : MarkEdit

    /** Everything this mark session changed, put back. */
    data object UndoMarks : MarkEdit
}
