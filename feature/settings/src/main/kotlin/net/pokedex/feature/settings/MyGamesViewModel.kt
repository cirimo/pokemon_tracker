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
import net.pokedex.core.model.farmOrderOf
import net.pokedex.core.model.moveInOrder
import net.pokedex.core.model.statusOf
import javax.inject.Inject

@Immutable
data class MyGamesUiState(
    val loading: Boolean = true,
    val sets: List<GameSetChoice> = emptyList(),
    /** My games, first to farm first. */
    val order: List<OrderedGame> = emptyList(),
)

@Immutable
data class OrderedGame(val id: String, val name: String)

/** One pair of versions, e.g. Scarlet and Violet, as the screen groups them. */
@Immutable
data class GameSetChoice(val title: String, val games: List<GameChoice>)

@Immutable
data class GameChoice(
    val id: String,
    val name: String,
    val owned: Boolean,
    /** Slots still needed that this game offers shiny. */
    val neededHere: Int,
    /**
     * For a game I do not own, while I own some: needed slots it offers shiny that none of
     * mine does. The "which one extra game" answer. Null otherwise.
     */
    val newlyInReach: Int? = null,
)

sealed interface MyGamesEvent {
    data class Toggle(val gameId: String, val owned: Boolean) : MyGamesEvent

    /** [by] places, later for positive. */
    data class Move(val gameId: String, val by: Int) : MyGamesEvent
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
        settings.observeFarmRanks(),
    ) { dex, records, ranks ->
        if (dex == null) {
            MyGamesUiState()
        } else {
            val order = farmOrderOf(dex.games, ranks)
            MyGamesUiState(
                loading = false,
                sets = setsOf(dex, records, ranks.keys),
                order = order.mapNotNull { id -> dex.games.firstOrNull { it.id == id }?.let { OrderedGame(id.value, it.name) } },
            )
        }
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
            // The whole order is written, not a swap: before anyone has moved a game every
            // rank is 0, and only the resolved order says which two games are trading places.
            is MyGamesEvent.Move -> viewModelScope.launch {
                val order = state.value.order.map { GameId(it.id) }
                settings.setFarmOrder(moveInOrder(order, GameId(event.gameId), event.by))
            }
        }
    }

    private fun setsOf(dex: Dex, records: Map<CatchKey, CatchRecord>, owned: Set<GameId>): List<GameSetChoice> {
        // Needed regardless of games: this screen is where games are chosen, so it counts
        // against all of them.
        val needed = dex.entries.filter { statusOf(it, records) == SlotStatus.Needed }
        val outOfReach = if (owned.isEmpty()) emptyList() else needed.filter { e -> e.shinyGames.none { it in owned } }
        return dex.gameSets.map { set ->
            GameSetChoice(
                title = set.games.joinToString(" and ") { it.name },
                games = set.games.map { game ->
                    GameChoice(
                        id = game.id.value,
                        name = game.name,
                        owned = game.id in owned,
                        neededHere = needed.count { game.id in it.shinyGames },
                        newlyInReach = if (owned.isEmpty() || game.id in owned) {
                            null
                        } else {
                            outOfReach.count { game.id in it.shinyGames }
                        },
                    )
                },
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
