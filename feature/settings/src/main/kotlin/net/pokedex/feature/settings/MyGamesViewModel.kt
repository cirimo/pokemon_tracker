package net.pokedex.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.pokedex.core.data.repository.CatchRepository
import net.pokedex.core.data.repository.DexRepository
import net.pokedex.core.data.repository.SettingsRepository
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CatchRecord
import net.pokedex.core.model.Dex
import net.pokedex.core.model.GameId
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.SlotStatus
import net.pokedex.core.model.statusOf
import javax.inject.Inject

@Immutable
data class MyGamesUiState(
    val loading: Boolean = true,
    val sets: List<GameSetChoice> = emptyList(),
)

/** One pair of versions, e.g. Scarlet and Violet, as the screen groups them. */
@Immutable
data class GameSetChoice(val title: String, val games: List<GameChoice>)

@Immutable
data class GameChoice(
    val id: String,
    val name: String,
    val owned: Boolean,
    /** Slots still needed that this game offers shiny. What owning it would put in reach. */
    val neededHere: Int,
)

sealed interface MyGamesEvent {
    data class Toggle(val gameId: String, val owned: Boolean) : MyGamesEvent
}

/**
 * The games the user owns and plays. They decide which needed slots are out of reach, and
 * they are what the hunt list is filtered by.
 *
 * Per game rather than per pair: a pair's versions differ in exclusives and shiny locks, so
 * owning Scarlet says nothing about a Violet exclusive. The screen groups by pair only so
 * that ticking both is quick.
 */
@HiltViewModel
class MyGamesViewModel @Inject constructor(
    dexRepository: DexRepository,
    catches: CatchRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val dex = MutableStateFlow<Dex?>(null)

    val state: StateFlow<MyGamesUiState> = combine(
        dex,
        catches.observeRecords(),
        settings.observeMyGames(),
    ) { dex, records, owned ->
        if (dex == null) MyGamesUiState() else MyGamesUiState(loading = false, sets = setsOf(dex, records, owned))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MyGamesUiState())

    init {
        viewModelScope.launch {
            (dexRepository.dex() as? Outcome.Ok)?.let { dex.value = it.value }
        }
    }

    fun onEvent(event: MyGamesEvent) {
        when (event) {
            is MyGamesEvent.Toggle -> viewModelScope.launch {
                settings.setMyGame(GameId(event.gameId), event.owned)
            }
        }
    }

    private fun setsOf(dex: Dex, records: Map<CatchKey, CatchRecord>, owned: Set<GameId>): List<GameSetChoice> {
        // Needed regardless of games: this screen is where games are chosen, so it counts
        // against all of them.
        val needed = dex.entries.filter { statusOf(it, records) == SlotStatus.Needed }
        return dex.gameSets.map { set ->
            GameSetChoice(
                title = set.games.joinToString(" and ") { it.name },
                games = set.games.map { game ->
                    GameChoice(
                        id = game.id.value,
                        name = game.name,
                        owned = game.id in owned,
                        neededHere = needed.count { game.id in it.shinyGames },
                    )
                },
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
