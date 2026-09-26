package net.pokedex.feature.dex.hunt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import net.pokedex.core.model.Browse
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.FarmScope
import net.pokedex.designsystem.component.EmptyState
import net.pokedex.designsystem.component.ErrorState
import net.pokedex.designsystem.component.FilterChip
import net.pokedex.designsystem.component.LoadingState
import net.pokedex.designsystem.component.ScreenScaffold
import net.pokedex.designsystem.component.ScreenSection
import net.pokedex.designsystem.component.SettingRow
import net.pokedex.designsystem.component.SlotState
import net.pokedex.designsystem.component.SpeciesCard
import net.pokedex.designsystem.theme.PokedexTheme
import net.pokedex.feature.dex.DexSprite
import net.pokedex.feature.dex.errorBody
import net.pokedex.feature.dex.errorTitle
import net.pokedex.feature.dex.farmScopeLabel
import net.pokedex.feature.dex.typesOf

/**
 * [browsedTo] is the hunt a detail paged to before coming back, left on this entry's own
 * SavedStateHandle; the list scrolls it into view and clears it through [onBrowsedToHandled].
 */
@Suppress("LongParameterList") // The screen's links out, plus one result from the back stack.
@Composable
internal fun HuntDestination(
    onBack: () -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
    onBrowseSlot: (CatchKey, Browse) -> Unit,
    onOpenMyGames: () -> Unit,
    onOpenProgress: () -> Unit,
    browsedTo: StateFlow<String?>,
    onBrowsedToHandled: () -> Unit,
    viewModel: HuntViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val returnedTo by browsedTo.collectAsStateWithLifecycle()
    HuntScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenSlot = onOpenSlot,
        onBrowseSlot = onBrowseSlot,
        onOpenMyGames = onOpenMyGames,
        onOpenProgress = onOpenProgress,
        returnedTo = returnedTo,
        onReturnHandled = onBrowsedToHandled,
    )
}

/**
 * "What should I hunt next?" -- one row per hunt, ranked as docs/adr/0012 says, each row
 * saying why it is where it is.
 *
 * A lazy list, not the scaffold's scrolling column: an uncurated game is hundreds of rows.
 * Nothing on the list is gold. The top pick is where it is because of its position and its
 * reasons, not because it glows, and gold is reserved for shiny things.
 */
@Suppress("LongParameterList") // Stateless screen: every input and every way out is a parameter.
@Composable
internal fun HuntScreen(
    state: HuntUiState,
    onEvent: (HuntEvent) -> Unit,
    onBack: () -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
    onOpenMyGames: () -> Unit,
    onOpenProgress: () -> Unit,
    onBrowseSlot: (CatchKey, Browse) -> Unit = { key, _ -> onOpenSlot(key) },
    returnedTo: String? = null,
    onReturnHandled: () -> Unit = {},
) {
    ScreenScaffold(title = "Hunt next", onBack = onBack, scrollable = false) {
        when {
            state.error != null -> ErrorState(
                title = errorTitle(state.error),
                body = errorBody(state.error),
                action = { onEvent(HuntEvent.Retry) },
            )
            state.loading -> LoadingState()
            state.needsGames -> EmptyState(
                title = "Choose your games first",
                body = "The hunt list suggests only what the games you own can give you shiny.",
                actionLabel = "Choose my games",
                action = onOpenMyGames,
            )
            else -> HuntList(
                state = state,
                onEvent = onEvent,
                onOpenSlot = onOpenSlot,
                // The hunts in the order shown, under the game filter shown. Out-of-reach rows are
                // not hunts, so they open a slot on its own.
                onOpenHunt = { lead ->
                    val scope = state.scope.takeIf { state.selectedGame != null }
                    onBrowseSlot(lead, Browse.Hunt(state.selectedGame, scope))
                },
                onOpenMyGames = onOpenMyGames,
                onOpenProgress = onOpenProgress,
                returnedTo = returnedTo,
                onReturnHandled = onReturnHandled,
            )
        }
    }
}

@Suppress("LongParameterList") // The list's rows link out three ways.
@Composable
private fun HuntList(
    state: HuntUiState,
    onEvent: (HuntEvent) -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
    onOpenHunt: (CatchKey) -> Unit,
    onOpenMyGames: () -> Unit,
    onOpenProgress: () -> Unit,
    returnedTo: String?,
    onReturnHandled: () -> Unit,
) {
    val dimens = PokedexTheme.dimens
    val listState = rememberLazyListState()

    // Back from a detail that paged to another hunt: bring that hunt's row into view, unless it
    // already is. A hunt caught meanwhile has left the list and is simply not found.
    LaunchedEffect(returnedTo) {
        val returned = returnedTo ?: return@LaunchedEffect
        val index = rowIndexOf(state, returned)
        if (index >= 0) {
            val visible = snapshotFlow { listState.layoutInfo.visibleItemsInfo }.first { it.isNotEmpty() }
            if (visible.none { it.index == index && it.offset >= 0 }) listState.scrollToItem(index)
        }
        onReturnHandled()
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = dimens.spaceXl),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        item(key = "summary", contentType = "summary") { Summary(state, onEvent) }
        state.sections.forEach { section ->
            item(key = section.key, contentType = "header") {
                ScreenSection(title = section.title, body = section.body)
            }
            items(section.rows, key = { it.key }, contentType = { "hunt" }) { row ->
                SpeciesCard(
                    name = row.name,
                    dexNumber = row.dexNumber,
                    types = typesOf(row.type1, row.type2),
                    state = SlotState.Needed,
                    formName = row.where,
                    details = row.reasons,
                    onClick = { onOpenHunt(row.lead) },
                    sprite = { rendering -> DexSprite(row.spriteFile, rendering) },
                )
            }
        }
        if (state.outOfReach.isNotEmpty()) outOfReach(state, onEvent, onOpenSlot)
        item(key = "links", contentType = "links") {
            ScreenSection(title = "Elsewhere") {
                SettingRow(
                    title = "Progress",
                    summary = "Where you stand, by box, region and game",
                    onClick = onOpenProgress,
                )
                SettingRow(
                    title = "My games",
                    summary = "Change which games the list draws on",
                    onClick = onOpenMyGames,
                )
            }
        }
    }
}

/** The lazy-list index of the hunt led by [slotKey]: after the summary, each section's header then its rows. */
private fun rowIndexOf(state: HuntUiState, slotKey: String): Int {
    var index = 1
    for (section in state.sections) {
        index++
        val at = section.rows.indexOfFirst { it.lead.toString() == slotKey }
        if (at >= 0) return index + at
        index += section.rows.size
    }
    return -1
}

@Composable
private fun Summary(state: HuntUiState, onEvent: (HuntEvent) -> Unit) {
    val dimens = PokedexTheme.dimens
    ScreenSection(title = huntCountSentence(state.huntCount, state.slotCount)) {
        if (state.games.size > 1) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            ) {
                FilterChip(
                    label = "All my games",
                    selected = state.selectedGame == null,
                    onSelectedChange = { onEvent(HuntEvent.SelectGame(null)) },
                )
                state.games.forEach { game ->
                    FilterChip(
                        label = game.name,
                        selected = state.selectedGame == game.id,
                        onSelectedChange = { onEvent(HuntEvent.SelectGame(game.id)) },
                    )
                }
            }
        }
        if (state.selectedGame != null && state.games.size > 1) {
            // Which of the game's slots: the ones it is first for in my order (the default),
            // the ones only it has, or everything it offers shiny.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            ) {
                listOf(FarmScope.HereFirst, FarmScope.OnlyHere, null).forEach { scope ->
                    FilterChip(
                        label = scope?.let(::farmScopeLabel) ?: "Everything here",
                        selected = state.scope == scope,
                        onSelectedChange = { onEvent(HuntEvent.SelectScope(scope)) },
                    )
                }
            }
        }
    }
}

/** Last, folded, and never dropped: still gaps, just not ones a hunt in these games fills. */
private fun LazyListScope.outOfReach(
    state: HuntUiState,
    onEvent: (HuntEvent) -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
) {
    item(key = "out-header", contentType = "header") {
        SettingRow(
            title = if (state.selectedGame == null) "Not in your games" else "Not in this game",
            summary = "${state.outOfReach.size} needed " +
                (if (state.outOfReach.size == 1) "slot" else "slots") +
                if (state.showOutOfReach) ". Tap to hide." else ". Tap to show.",
            onClick = { onEvent(HuntEvent.ShowOutOfReach(!state.showOutOfReach)) },
        )
    }
    if (state.showOutOfReach) {
        items(state.outOfReach, key = { "out-${it.key}" }, contentType = { "out" }) { row ->
            SpeciesCard(
                name = row.name,
                dexNumber = row.dexNumber,
                types = typesOf(row.type1, row.type2),
                state = SlotState.Unavailable,
                formName = row.where,
                details = listOf(row.reason),
                onClick = { onOpenSlot(row.key) },
                sprite = { rendering -> DexSprite(row.spriteFile, rendering) },
            )
        }
    }
}

private fun huntCountSentence(hunts: Int, slots: Int): String = when (hunts) {
    0 -> "Nothing left to hunt here"
    1 -> "1 hunt, $slots ${if (slots == 1) "slot" else "slots"}"
    else -> "$hunts hunts, $slots slots"
}
