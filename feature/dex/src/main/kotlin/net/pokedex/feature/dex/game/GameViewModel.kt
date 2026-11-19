package net.pokedex.feature.dex.game

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.pokedex.core.data.di.DefaultDispatcher
import net.pokedex.core.data.repository.CatchRepository
import net.pokedex.core.data.repository.CurationRepository
import net.pokedex.core.data.repository.DexRepository
import net.pokedex.core.data.repository.GuideBookRepository
import net.pokedex.core.data.repository.PrepRepository
import net.pokedex.core.data.repository.SettingsRepository
import net.pokedex.core.model.AppError
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CatchRecord
import net.pokedex.core.model.Curation
import net.pokedex.core.model.Dex
import net.pokedex.core.model.FarmPlan
import net.pokedex.core.model.GameId
import net.pokedex.core.model.GuideBook
import net.pokedex.core.model.LegendaryHunt
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.PrepItem
import net.pokedex.core.model.PrepTick
import net.pokedex.core.model.farmCounts
import net.pokedex.core.model.farmOrderOf
import net.pokedex.core.model.hasShinyCharm
import net.pokedex.core.model.leadingOdds
import net.pokedex.core.model.legendaryPlan
import net.pokedex.core.model.prepListOf
import net.pokedex.feature.dex.GameRoute
import net.pokedex.feature.dex.oddsLabel
import net.pokedex.feature.dex.prepValue
import net.pokedex.feature.dex.standingLine
import net.pokedex.feature.dex.usedBySentence
import javax.inject.Inject

@Immutable
data class GameUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val gameId: String = "",
    val name: String = "",
    /** Not one of my games: the page still shows what it has, and says so. */
    val mine: Boolean = false,
    val onlyHere: Int = 0,
    val hereFirst: Int = 0,
    val prep: List<PrepRowUi> = emptyList(),
    val legendaries: List<LegendaryRowUi> = emptyList(),
    val locked: List<LockedRowUi> = emptyList(),
    val showLocked: Boolean = false,
    val charm: Boolean = false,
)

/** A prep item as a checklist row; shared with the guide screen. */
@Immutable
data class PrepRowUi(
    val refId: String,
    val title: String,
    val value: String,
    val done: Boolean,
    /** What it is, then how many guides ask for it. */
    val lines: List<String>,
    /** How to get it, in order. Empty for a DLC or a story step. */
    val farm: List<String>,
)

@Immutable
data class LegendaryRowUi(
    val key: String,
    val lead: CatchKey,
    val name: String,
    val type1: String,
    val type2: String?,
    val spriteFile: String,
    val lines: List<String>,
    val odds: String?,
    val oddsLabel: String,
    /** Opens the guide when there is one, the slot otherwise. */
    val guideId: String?,
)

@Immutable
data class LockedRowUi(val key: CatchKey, val name: String, val reason: String?)

sealed interface GameEvent {
    data class TogglePrep(val refId: String, val done: Boolean) : GameEvent
    data class ShowLocked(val show: Boolean) : GameEvent
    data object Retry : GameEvent
}

/**
 * One game's page (prompt 11): what it is for in my farm order, what to have ready before its
 * hunts, and its legendaries ranked as `legendaryPlan` says. Generic over games; a game with
 * no curated guides simply has an empty prep list.
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val dexRepository: DexRepository,
    private val curations: CurationRepository,
    private val guideBooks: GuideBookRepository,
    private val prepRepository: PrepRepository,
    catches: CatchRepository,
    settings: SettingsRepository,
    private val savedState: SavedStateHandle,
    @DefaultDispatcher default: CoroutineDispatcher,
) : ViewModel() {

    private val game = GameId(savedState.toRoute<GameRoute>().gameId)
    private val loaded = MutableStateFlow<Outcome<Loaded>?>(null)

    val state: StateFlow<GameUiState> = combine(
        loaded,
        catches.observeRecords(),
        settings.observeFarmRanks(),
        prepRepository.observe(),
        savedState.getStateFlow(KEY_SHOW_LOCKED, false),
    ) { loaded, records, ranks, ticks, showLocked ->
        when (loaded) {
            null -> GameUiState()
            is Outcome.Err -> GameUiState(loading = false, error = loaded.error)
            is Outcome.Ok -> build(loaded.value, records, ranks, ticks, showLocked)
        }
    }.flowOn(default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GameUiState())

    init {
        load()
    }

    fun onEvent(event: GameEvent) {
        when (event) {
            is GameEvent.TogglePrep -> viewModelScope.launch { prepRepository.setDone(game, event.refId, event.done) }
            is GameEvent.ShowLocked -> savedState[KEY_SHOW_LOCKED] = event.show
            GameEvent.Retry -> load()
        }
    }

    private class Loaded(val dex: Dex, val curation: Curation, val book: GuideBook)

    private fun load() {
        viewModelScope.launch {
            loaded.value = null
            val dex = dexRepository.dex()
            val curation = curations.curation()
            val book = guideBooks.guideBook()
            loaded.value = when {
                dex is Outcome.Err -> dex
                curation is Outcome.Err -> curation
                book is Outcome.Err -> book
                else -> Outcome.Ok(
                    Loaded((dex as Outcome.Ok).value, (curation as Outcome.Ok).value, (book as Outcome.Ok).value),
                )
            }
        }
    }

    private fun build(
        loaded: Loaded,
        records: Map<CatchKey, CatchRecord>,
        ranks: Map<GameId, Int>,
        ticks: Set<PrepTick>,
        showLocked: Boolean,
    ): GameUiState {
        val dex = loaded.dex
        val game = dex.games.firstOrNull { it.id == this.game }
            ?: return GameUiState(
                loading = false,
                error = AppError.Unexpected("no game ${this.game.value} in the dataset"),
            )
        val order = farmOrderOf(dex.games, ranks)
        val share = farmCounts(dex, records, FarmPlan(dex, order)).firstOrNull { it.game == game.id }
        val plan = legendaryPlan(dex, records, order, loaded.curation, game.id)
        val charm = hasShinyCharm(ticks, game.id)
        return GameUiState(
            loading = false,
            gameId = game.id.value,
            name = game.name,
            mine = game.id in ranks,
            onlyHere = share?.onlyHere ?: 0,
            hereFirst = share?.hereFirst ?: 0,
            prep = prepListOf(loaded.book, game.gameSet, game.id, ticks).map { prepRow(it) },
            legendaries = plan.hunts.map { legendaryRow(loaded, it, game.name, game.gameSet, charm) },
            locked = plan.locked.map { entry ->
                LockedRowUi(
                    key = entry.key,
                    name = entry.variant.displayName,
                    reason = dex.availability(entry.variant.id).firstOrNull { it.gameId == game.id }?.shinyLockReason,
                )
            },
            showLocked = showLocked,
            charm = charm,
        )
    }

    private fun legendaryRow(
        loaded: Loaded,
        hunt: LegendaryHunt,
        gameName: String,
        gameSet: String,
        charm: Boolean,
    ): LegendaryRowUi {
        val variant = hunt.lead.variant
        val species = loaded.dex.species(variant.dexNum)?.name
        val way = hunt.way
        val guide = loaded.book.guidesFor(hunt.key, hunt.slots.map { it.variant }, gameSet).firstOrNull()
        val method = way?.let { loaded.curation.method(it.encounter.methodId)?.name ?: it.encounter.methodId }
        val charmLeads = charm && way?.odds?.withCharm != null
        return LegendaryRowUi(
            key = "${hunt.key.dexNum}:${hunt.key.regionalForm.orEmpty()}",
            lead = hunt.lead.key,
            name = if (hunt.key.regionalForm != null || species == null) variant.displayName else species,
            type1 = variant.type1,
            type2 = variant.type2,
            spriteFile = variant.spriteFile,
            lines = listOfNotNull(
                method ?: "No method recorded yet",
                standingLine(hunt.standing, gameName),
                slotsLine(hunt.slots.size, guide?.oneInSave == true),
                if (hunt.onlineOnly) "Only through another player's game, online" else null,
            ),
            odds = way?.odds?.let { oddsLabel(leadingOdds(it, charm)) },
            oddsLabel = if (charmLeads) "with the charm" else "odds",
            guideId = guide?.id,
        )
    }

    /**
     * One per save means a save fills one of a hunt's slots, however many it has: Tornadus
     * Incarnate or Therian, never both from one Sword.
     */
    private fun slotsLine(slots: Int, oneInSave: Boolean): String? = when {
        oneInSave && slots > 1 -> "One per save: fills 1 of its $slots slots"
        oneInSave -> "One per save: keep only a shiny one"
        slots > 1 -> "Fills $slots slots"
        else -> null
    }

    private companion object {
        const val KEY_SHOW_LOCKED = "showLocked"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

internal fun prepRow(item: PrepItem) = PrepRowUi(
    refId = item.refId,
    title = item.label,
    value = prepValue(item),
    done = item.done,
    lines = listOfNotNull(item.detail, usedBySentence(item.usedBy)),
    farm = item.farm.map { it.detail },
)
