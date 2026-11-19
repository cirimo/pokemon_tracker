package net.pokedex.feature.dex.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CaughtFilter
import net.pokedex.core.model.DexFilter
import net.pokedex.core.model.FarmFilter
import net.pokedex.core.model.FarmScope
import net.pokedex.designsystem.component.ErrorState
import net.pokedex.designsystem.component.HuntCard
import net.pokedex.designsystem.component.InfoRow
import net.pokedex.designsystem.component.InfoTable
import net.pokedex.designsystem.component.LoadingState
import net.pokedex.designsystem.component.NoticeCard
import net.pokedex.designsystem.component.ScreenScaffold
import net.pokedex.designsystem.component.ScreenSection
import net.pokedex.designsystem.component.SettingRow
import net.pokedex.designsystem.component.StatBlock
import net.pokedex.designsystem.component.StatRow
import net.pokedex.designsystem.icon.PokedexIcons
import net.pokedex.feature.dex.DexSprite
import net.pokedex.feature.dex.errorBody
import net.pokedex.feature.dex.errorTitle
import net.pokedex.feature.dex.typesOf

@Suppress("LongParameterList") // The page's links out: guides, slots, search, the hunt list, my games.
@Composable
internal fun GameDestination(
    onBack: () -> Unit,
    onOpenGuide: (guideId: String, gameId: String) -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
    onShowSearch: (DexFilter) -> Unit,
    onOpenHunt: (gameId: String) -> Unit,
    onOpenMyGames: () -> Unit,
    viewModel: GameViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GameScreen(state, viewModel::onEvent, onBack, onOpenGuide, onOpenSlot, onShowSearch, onOpenHunt, onOpenMyGames)
}

/**
 * One game: what it is for in my farm order, what to have ready, and its legendaries ranked.
 *
 * The first legendary is the one thing the page features, so it alone is lit; the rest are
 * plain cards, because a list of lit cards is a list of cards again. Nothing here is gold:
 * the figures are odds and counts of what is still needed, not shinies.
 */
@Suppress("LongParameterList", "LongMethod") // Stateless screen: every input and every way out is a parameter.
@Composable
internal fun GameScreen(
    state: GameUiState,
    onEvent: (GameEvent) -> Unit,
    onBack: () -> Unit,
    onOpenGuide: (String, String) -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
    onShowSearch: (DexFilter) -> Unit,
    onOpenHunt: (String) -> Unit,
    onOpenMyGames: () -> Unit,
) {
    ScreenScaffold(title = state.name.ifEmpty { "Game" }, onBack = onBack) {
        when {
            state.error != null -> ErrorState(
                title = errorTitle(state.error),
                body = errorBody(state.error),
                action = { onEvent(GameEvent.Retry) },
            )
            state.loading -> LoadingState()
            else -> {
                if (state.mine) {
                    StatRow {
                        StatBlock(value = state.onlyHere.toString(), label = "only here")
                        StatBlock(value = state.hereFirst.toString(), label = "here first")
                        if (state.prep.isNotEmpty()) {
                            StatBlock(
                                value = "${state.prep.count { it.done }} of ${state.prep.size}",
                                label = "prep done"
                            )
                        }
                    }
                } else {
                    NoticeCard(
                        title = "Not one of your games",
                        body = "Its legendaries are ranked as if it were. Add it to see what it is first for.",
                        actionLabel = "Choose my games",
                        onAction = onOpenMyGames,
                    )
                }

                if (state.prep.isNotEmpty()) {
                    ScreenSection(
                        title = "Prep",
                        body = "What the guides for ${state.name} ask for. Tick what you have; the Shiny Charm " +
                            "changes the odds every hunt here leads with. How to get each is on its guides.",
                    ) {
                        // No farm chains here: at a large text size they push the legendaries
                        // screens down, and each guide shows the chain beside the build it is for.
                        PrepTable(
                            state.prep,
                            onToggle = { refId, done -> onEvent(GameEvent.TogglePrep(refId, done)) },
                            showFarm = false,
                        )
                    }
                }

                ScreenSection(
                    title = "Legendaries",
                    body = when {
                        state.legendaries.isEmpty() -> "None left that ${state.name} can give you shiny."
                        else -> "Best odds first, then where ${state.name} stands in your farm order."
                    },
                ) {
                    state.legendaries.forEachIndexed { index, row ->
                        HuntCard(
                            name = row.name,
                            lines = row.lines,
                            odds = row.odds,
                            oddsLabel = row.oddsLabel,
                            types = typesOf(row.type1, row.type2),
                            featured = index == 0,
                            onClick = {
                                if (row.guideId != null) {
                                    onOpenGuide(
                                        row.guideId,
                                        state.gameId
                                    )
                                } else {
                                    onOpenSlot(row.lead)
                                }
                            },
                            sprite = { rendering -> DexSprite(row.spriteFile, rendering) },
                        )
                    }
                }

                if (state.locked.isNotEmpty()) Locked(state, onEvent, onOpenSlot)

                ScreenSection(title = "Elsewhere") {
                    if (state.mine) {
                        SettingRow(
                            title = "Only in ${state.name}",
                            summary = "In the boxes: what you still need that none of your other games has shiny",
                            onClick = {
                                onShowSearch(
                                    DexFilter(
                                        caught = CaughtFilter.Needed,
                                        farm = FarmFilter(state.gameId, FarmScope.OnlyHere),
                                    ),
                                )
                            },
                        )
                    }
                    SettingRow(
                        title = "Hunt list for ${state.name}",
                        summary = "Every hunt here, ranked, not only the legendaries",
                        onClick = { onOpenHunt(state.gameId) },
                    )
                }
            }
        }
    }
}

/** Legendaries this game has but never gives shiny: folded, and never dropped. */
@Composable
private fun Locked(state: GameUiState, onEvent: (GameEvent) -> Unit, onOpenSlot: (CatchKey) -> Unit) {
    ScreenSection(title = "Shiny-locked here") {
        SettingRow(
            title = "${state.locked.size} legendaries you still need",
            summary = if (state.showLocked) "Tap to hide." else "Tap to show.",
            onClick = { onEvent(GameEvent.ShowLocked(!state.showLocked)) },
        )
        if (state.showLocked) {
            InfoTable {
                state.locked.forEachIndexed { index, row ->
                    InfoRow(
                        title = row.name,
                        value = "Shiny-locked",
                        first = index == 0,
                        icon = PokedexIcons.Locked,
                        onClick = { onOpenSlot(row.key) },
                        details = row.reason?.let { reason -> { MutedLine(reason) } },
                    )
                }
            }
        }
    }
}
