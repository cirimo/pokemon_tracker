package net.pokedex.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pokedex.designsystem.component.LoadingState
import net.pokedex.designsystem.component.ScreenScaffold
import net.pokedex.designsystem.component.ScreenSection
import net.pokedex.designsystem.component.SettingSwitch

@Composable
internal fun MyGamesDestination(onBack: () -> Unit, viewModel: MyGamesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MyGamesScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

/**
 * One switch per game, grouped by pair. Each says what owning it puts in reach, in slots
 * still needed, because that is the question the choice answers.
 */
@Composable
internal fun MyGamesScreen(state: MyGamesUiState, onEvent: (MyGamesEvent) -> Unit, onBack: () -> Unit) {
    ScreenScaffold(title = "My games", onBack = onBack) {
        ScreenSection(
            title = "Games you own and play",
            body = "The hunt list only suggests what these games can give you shiny, and the boxes " +
                "mark what none of them can. With none chosen, nothing is marked out of reach.",
        )
        if (state.loading) {
            LoadingState()
        } else {
            state.sets.forEach { set ->
                ScreenSection(title = set.title) {
                    set.games.forEach { game ->
                        SettingSwitch(
                            title = game.name,
                            summary = neededSummary(game.neededHere),
                            checked = game.owned,
                            onCheckedChange = { onEvent(MyGamesEvent.Toggle(game.id, it)) },
                        )
                    }
                }
            }
        }
    }
}

private fun neededSummary(count: Int): String = when (count) {
    0 -> "Nothing you still need is shiny here"
    1 -> "Shiny for 1 slot you still need"
    else -> "Shiny for $count slots you still need"
}
