package net.pokedex.feature.dex.boxes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.flow.first
import net.pokedex.core.model.Browse
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CaughtFilter
import net.pokedex.core.model.FarmScope
import net.pokedex.core.model.NoShinyFilter
import net.pokedex.designsystem.component.EmptyState
import net.pokedex.designsystem.component.FilterChip
import net.pokedex.designsystem.component.PokedexBottomSheet
import net.pokedex.designsystem.component.SpeciesCard
import net.pokedex.designsystem.icon.PokedexIcons
import net.pokedex.designsystem.theme.PokedexTheme
import net.pokedex.feature.dex.DexSprite
import net.pokedex.feature.dex.farmScopeLabel
import net.pokedex.feature.dex.slotOrigin
import net.pokedex.feature.dex.toSlotState
import net.pokedex.feature.dex.typesOf

/**
 * Search mode: quick filters, a count, and the results.
 *
 * The two most-used refinements -- needed and caught -- sit in the row under the field
 * because they are the ones you flip mid-hunt. Games, types and the no-shiny switch live in
 * a sheet: 24 chips on screen at once would push the results off it.
 */
@Composable
internal fun SearchContent(
    search: SearchUiState,
    onEvent: (BoxesEvent) -> Unit,
    onOpenResult: (CatchKey, Browse) -> Unit,
    returnedTo: String? = null,
    onReturnHandled: () -> Unit = {},
) {
    val dimens = PokedexTheme.dimens
    val colors = PokedexTheme.colors
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    // Saveable, so the list comes back scrolled to where it was after process death.
    val listState = rememberLazyListState()

    // Back from a detail that paged through these results: bring the row it ended on into view,
    // but only if it is not already, so a list you were reading does not jump. A row that a
    // catch took out of a "needed" search is simply not found.
    LaunchedEffect(returnedTo) {
        val returned = returnedTo ?: return@LaunchedEffect
        val index = search.results.indexOfFirst { it.slotKey == returned }
        if (index >= 0) {
            val visible = snapshotFlow { listState.layoutInfo.visibleItemsInfo }.first { it.isNotEmpty() }
            if (visible.none { it.index == index && it.offset >= 0 }) listState.scrollToItem(index)
        }
        onReturnHandled()
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        FlowRow(
            modifier = Modifier.padding(horizontal = dimens.spaceLg),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
        ) {
            FilterChip(
                label = "Needed",
                selected = search.needed,
                onSelectedChange = { on ->
                    onEvent(BoxesEvent.CaughtFilterChanged(if (on) CaughtFilter.Needed else CaughtFilter.All))
                },
            )
            FilterChip(
                label = "Caught",
                selected = search.caught,
                onSelectedChange = { on ->
                    onEvent(BoxesEvent.CaughtFilterChanged(if (on) CaughtFilter.Caught else CaughtFilter.All))
                },
            )
            OutlinedButton(onClick = { sheetOpen = true }) {
                Icon(PokedexIcons.Filter, contentDescription = null)
                // What the sheet holds: every refinement but the caught chips beside this button.
                val extra = search.filter.refinementCount - if (search.filter.caught != CaughtFilter.All) 1 else 0
                Text(
                    text = if (extra == 0) "More filters" else "More filters, $extra on",
                    style = PokedexTheme.text.badgeLabel,
                    modifier = Modifier.padding(start = dimens.spaceXs),
                )
            }
        }

        Text(
            text = resultCountSentence(search.results.size),
            style = PokedexTheme.text.dexNumber,
            color = colors.onCaseMuted,
            modifier = Modifier
                .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm)
                // Announced as it changes, so a TalkBack user typing a name hears that the
                // list narrowed without having to go looking for it.
                .semantics { liveRegion = LiveRegionMode.Polite },
        )

        if (search.results.isEmpty()) {
            EmptyState(
                title = "No slots match",
                body = if (search.filter.hasRefinements) {
                    val n = search.filter.refinementCount
                    if (n == 1) "One filter is narrowing this search." else "$n filters are narrowing this search."
                } else {
                    "Nothing in the dex has that name or number."
                },
                actionLabel = if (search.filter.hasRefinements) "Clear filters" else null,
                action = { onEvent(BoxesEvent.ClearRefinements) },
                modifier = Modifier.padding(dimens.spaceLg),
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = dimens.spaceLg, end = dimens.spaceLg, bottom = dimens.spaceLg),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            ) {
                items(search.results, key = { it.slotKey }, contentType = { "result" }) { result ->
                    SpeciesCard(
                        name = result.name,
                        dexNumber = result.dexNumber,
                        types = typesOf(result.type1, result.type2),
                        state = result.status.toSlotState(),
                        // Where it sits, not the form name: the form is already in the name,
                        // and the location is what separates the two copies of a duplicate.
                        formName = result.location,
                        onClick = { onOpenResult(result.key, Browse.Search(search.filter)) },
                        sprite = { rendering ->
                            DexSprite(result.spriteFile, rendering, Modifier.slotOrigin(result.slotKey))
                        },
                    )
                }
            }
        }
    }

    if (sheetOpen) {
        FilterSheet(search = search, onEvent = onEvent, onDismiss = { sheetOpen = false })
    }
}

private fun resultCountSentence(count: Int): String = when (count) {
    0 -> "No slots"
    1 -> "1 slot"
    else -> "$count slots"
}

/**
 * Everything else you can narrow by. Every section is "any of these"; sections combine.
 * The copy says so, because a filter whose logic you have to guess is a filter you stop
 * trusting.
 */
// PokedexBottomSheet's signature carries M3's experimental SheetState, so every caller opts in.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(search: SearchUiState, onEvent: (BoxesEvent) -> Unit, onDismiss: () -> Unit) {
    val dimens = PokedexTheme.dimens
    PokedexBottomSheet(onDismissRequest = onDismiss, title = "Filters") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceLg),
        ) {
            if (search.farmGames.isNotEmpty()) FarmSection(search, onEvent)
            FilterSection(
                title = "Get it shiny in",
                hint = "Any of these. A game where it is shiny-locked does not count.",
            ) {
                search.gameSets.forEach { set ->
                    FilterChip(
                        label = set.label,
                        selected = set.id in search.filter.gameSets,
                        onSelectedChange = { onEvent(BoxesEvent.GameSetToggled(set.id)) },
                    )
                }
            }
            FilterSection(title = "Type", hint = "Any of these. A dual type matches either.") {
                search.types.forEach { type ->
                    FilterChip(
                        label = type.label,
                        selected = type.id in search.filter.types,
                        onSelectedChange = { onEvent(BoxesEvent.TypeToggled(type.id)) },
                    )
                }
            }
            FilterSection(
                title = "No shiny released",
                hint = "Slots whose shiny no game has released yet.",
            ) {
                FilterChip(
                    label = "Hide them",
                    selected = search.hideNoShiny,
                    onSelectedChange = { on ->
                        onEvent(BoxesEvent.NoShinyChanged(if (on) NoShinyFilter.Hide else NoShinyFilter.Any))
                    },
                )
                FilterChip(
                    label = "Only them",
                    selected = search.onlyNoShiny,
                    onSelectedChange = { on ->
                        onEvent(BoxesEvent.NoShinyChanged(if (on) NoShinyFilter.Only else NoShinyFilter.Any))
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = dimens.spaceLg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = resultCountSentence(search.results.size),
                    style = PokedexTheme.text.dexNumber,
                    color = PokedexTheme.colors.onCaseMuted,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { onEvent(BoxesEvent.ClearRefinements) },
                    enabled = search.filter.hasRefinements,
                ) {
                    Text("Clear filters", style = PokedexTheme.text.badgeLabel)
                }
            }
        }
    }
}

/**
 * "Farm in": one of my games, and which half of the question. First in the sheet, because
 * it is the question asked most while working through my games one at a time.
 */
@Composable
private fun FarmSection(search: SearchUiState, onEvent: (BoxesEvent) -> Unit) {
    val farm = search.filter.farm
    Column(verticalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceSm)) {
        FilterSection(
            title = "Farm in",
            hint = "One of your games, in your farm order. Here first: the first of your games that has " +
                "it shiny. Only here: none of your other games has it shiny.",
        ) {
            search.farmGames.forEach { game ->
                FilterChip(
                    label = game.label,
                    selected = farm?.gameId == game.id,
                    onSelectedChange = { onEvent(BoxesEvent.FarmGameToggled(game.id)) },
                )
            }
        }
        if (farm != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceSm)) {
                FarmScope.entries.forEach { scope ->
                    FilterChip(
                        label = farmScopeLabel(scope),
                        selected = farm.scope == scope,
                        // A scope is always chosen while a game is: tapping the chosen one again
                        // keeps it rather than leaving the filter half-set.
                        onSelectedChange = { onEvent(BoxesEvent.FarmScopeChanged(scope)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSection(title: String, hint: String, chips: @Composable () -> Unit) {
    val dimens = PokedexTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = PokedexTheme.colors.onCase,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = hint, style = PokedexTheme.text.dexNumber, color = PokedexTheme.colors.onCaseMuted)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
        ) {
            chips()
        }
    }
}
