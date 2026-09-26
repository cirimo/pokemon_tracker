package net.pokedex.feature.dex.hunt

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import net.pokedex.core.data.repository.DexRepository
import net.pokedex.core.data.repository.GuideRepository
import net.pokedex.core.data.repository.SettingsRepository
import net.pokedex.core.model.AppError
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CatchRecord
import net.pokedex.core.model.Dex
import net.pokedex.core.model.FarmScope
import net.pokedex.core.model.GameId
import net.pokedex.core.model.Hunt
import net.pokedex.core.model.HuntGuide
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.Priority
import net.pokedex.core.model.farmOrderOf
import net.pokedex.core.model.huntPlanFor
import net.pokedex.feature.dex.locationOf
import net.pokedex.feature.dex.oddsLabel
import net.pokedex.feature.dex.outOfReachSentence
import javax.inject.Inject

@Immutable
data class HuntUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    /** No games chosen: the list cannot be built, and the screen asks for them instead. */
    val needsGames: Boolean = false,
    /** My games, as filter choices. Empty or one game shows no chips. */
    val games: List<GameChoice> = emptyList(),
    val selectedGame: String? = null,
    /** For one game: null for everything it offers, or which share of it. */
    val scope: FarmScope? = null,
    val sections: List<HuntSection> = emptyList(),
    val huntCount: Int = 0,
    val slotCount: Int = 0,
    val outOfReach: List<OutOfReachRow> = emptyList(),
    val showOutOfReach: Boolean = false,
)

@Immutable
data class GameChoice(val id: String, val name: String)

/**
 * A run of rows under one header: a priority bucket, split into hunts with a recorded method
 * and hunts without. The split is the header, so "no method recorded yet" is said once per
 * section rather than on every row.
 */
@Immutable
data class HuntSection(val key: String, val title: String, val body: String?, val rows: List<HuntRowUi>)

@Immutable
data class HuntRowUi(
    val key: String,
    val lead: CatchKey,
    val slots: List<CatchKey>,
    val name: String,
    val dexNumber: Int,
    val type1: String,
    val type2: String?,
    val spriteFile: String,
    val where: String,
    /** Why it is here, one line each: the method or the games, then slots and box. */
    val reasons: List<String>,
)

@Immutable
data class OutOfReachRow(
    val key: CatchKey,
    val name: String,
    val dexNumber: Int,
    val type1: String,
    val type2: String?,
    val spriteFile: String,
    val where: String,
    val reason: String,
)

sealed interface HuntEvent {
    data class SelectGame(val gameId: String?) : HuntEvent

    /** Null for everything the selected game offers. */
    data class SelectScope(val scope: FarmScope?) : HuntEvent
    data class ShowOutOfReach(val show: Boolean) : HuntEvent
    data object Retry : HuntEvent
}

/**
 * What to hunt next. The ranking is `huntPlan` in :core:model (docs/adr/0012); this only
 * turns it into rows and sentences. Rebuilt off the main thread whenever a record or my games
 * change, so ticking a catch on slot detail removes it here on the way back.
 */
@HiltViewModel
class HuntViewModel @Inject constructor(
    private val dexRepository: DexRepository,
    private val guides: GuideRepository,
    catches: CatchRepository,
    settings: SettingsRepository,
    private val savedState: SavedStateHandle,
    @DefaultDispatcher default: CoroutineDispatcher,
) : ViewModel() {

    private val loaded = MutableStateFlow<Outcome<Pair<Dex, HuntGuide>>?>(null)

    private val view = combine(
        savedState.getStateFlow<String?>(KEY_GAME, null),
        savedState.getStateFlow(KEY_SCOPE, DEFAULT_SCOPE),
        savedState.getStateFlow(KEY_OUT_OF_REACH, false),
    ) { game, scope, showOut -> View(game, FarmScope.entries.firstOrNull { it.name == scope }, showOut) }

    val state: StateFlow<HuntUiState> = combine(
        loaded,
        catches.observeRecords(),
        settings.observeFarmRanks(),
        view,
    ) { loaded, records, ranks, view ->
        when (loaded) {
            null -> HuntUiState()
            is Outcome.Err -> HuntUiState(loading = false, error = loaded.error)
            is Outcome.Ok -> build(loaded.value.first, loaded.value.second, records, ranks, view)
        }
    }.flowOn(default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HuntUiState())

    init {
        load()
    }

    fun onEvent(event: HuntEvent) {
        when (event) {
            is HuntEvent.SelectGame -> savedState[KEY_GAME] = event.gameId
            is HuntEvent.SelectScope -> savedState[KEY_SCOPE] = event.scope?.name ?: EVERYTHING
            is HuntEvent.ShowOutOfReach -> savedState[KEY_OUT_OF_REACH] = event.show
            HuntEvent.Retry -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            loaded.value = null
            val dex = dexRepository.dex()
            val guide = guides.guide()
            loaded.value = when {
                dex is Outcome.Err -> dex
                guide is Outcome.Err -> guide
                else -> Outcome.Ok((dex as Outcome.Ok).value to (guide as Outcome.Ok).value)
            }
        }
    }

    /** What the user chose to look at: a game or all, its share, and the folded list. */
    private class View(val game: String?, val scope: FarmScope?, val showOutOfReach: Boolean)

    private fun build(
        dex: Dex,
        guide: HuntGuide,
        records: Map<CatchKey, CatchRecord>,
        ranks: Map<GameId, Int>,
        view: View,
    ): HuntUiState {
        if (ranks.isEmpty()) return HuntUiState(loading = false, needsGames = true)
        val names = dex.games.associate { it.id to it.name }
        val order = farmOrderOf(dex.games, ranks)
        // A filter left over from a game since unticked is dropped rather than showing nothing.
        val game = view.game?.let(::GameId)?.takeIf { it in ranks }
        val plan = huntPlanFor(dex, records, order, game, view.scope, guide)
        val showOutOfReach = view.showOutOfReach

        var explained = false
        val sections = Priority.entries.flatMap { bucket ->
            val (known, unknown) = plan.hunts.filter { it.priority == bucket }.partition { it.way != null }
            listOfNotNull(
                known.takeIf { it.isNotEmpty() }?.let {
                    HuntSection(
                        key = "${bucket.name}-known",
                        title = titleOf(bucket, "Method recorded"),
                        body = null,
                        rows = it.map { h -> row(dex, h, guide, names) },
                    )
                },
                unknown.takeIf { it.isNotEmpty() }?.let {
                    // Said once, under the first such header, not under every bucket's.
                    val body = if (explained) null else NOT_CURATED
                    explained = true
                    HuntSection(
                        key = "${bucket.name}-unknown",
                        title = titleOf(bucket, "No method recorded yet"),
                        body = body,
                        rows = it.map { h -> row(dex, h, guide, names) },
                    )
                },
            )
        }

        return HuntUiState(
            loading = false,
            // In farm order, so the chips read the way I work through my games.
            games = order.map { GameChoice(it.value, names[it] ?: it.value) },
            selectedGame = game?.value,
            scope = view.scope,
            sections = sections,
            huntCount = plan.hunts.size,
            slotCount = plan.hunts.sumOf { it.slots.size },
            outOfReach = plan.outOfReach.map { out ->
                val v = out.entry.variant
                OutOfReachRow(
                    key = out.entry.key,
                    name = v.displayName,
                    dexNumber = v.dexNum,
                    type1 = v.type1,
                    type2 = v.type2,
                    spriteFile = v.spriteFile,
                    where = locationOf(out.entry),
                    reason = outOfReachSentence(out.reason, out.games.map { names[it] ?: it.value }),
                )
            },
            showOutOfReach = showOutOfReach,
        )
    }

    private fun titleOf(bucket: Priority, split: String): String = when (bucket) {
        Priority.Want -> "Want: ${split.lowercase()}"
        Priority.Normal -> split
        Priority.Later -> "Later: ${split.lowercase()}"
    }

    private fun row(dex: Dex, hunt: Hunt, guide: HuntGuide, names: Map<GameId, String>): HuntRowUi {
        val lead = hunt.lead
        val variant = lead.variant
        val way = hunt.way
        val how = if (way == null) {
            "Shiny in " + hunt.games.joinToString(", ") { names[it] ?: it.value }
        } else {
            val method = guide.method(way.encounter.methodId)?.name ?: way.encounter.methodId
            val odds = way.odds?.let { ", ${oddsLabel(it.best)}" }.orEmpty()
            "$method in ${names[way.game] ?: way.game.value}$odds"
        }
        val box = hunt.box
        val boxLine = if (box.finishes) "Finishes ${box.name}" else "${box.name}: ${box.remaining} to go"
        val fills = when {
            hunt.slots.size == 1 -> ""
            way != null && hunt.slotsWithWay < hunt.slots.size ->
                "Fills ${hunt.slots.size} slots, a method for ${hunt.slotsWithWay}, "
            else -> "Fills ${hunt.slots.size} slots, "
        }
        // A regional hunt is named for its form; any other is the species, since its slots
        // span genders and cosmetic forms.
        val species = dex.species(variant.dexNum)?.name
        val name = if (hunt.key.regionalForm != null || species == null) variant.displayName else species
        return HuntRowUi(
            key = "${hunt.key.dexNum}:${hunt.key.regionalForm.orEmpty()}",
            lead = lead.key,
            slots = hunt.slots.map { it.key },
            name = name,
            dexNumber = variant.dexNum,
            type1 = variant.type1,
            type2 = variant.type2,
            spriteFile = variant.spriteFile,
            where = if (hunt.slots.size == 1) {
                locationOf(lead)
            } else {
                "${locationOf(lead)} and ${hunt.slots.size - 1} more"
            },
            reasons = listOf(how, fills + boxLine),
        )
    }

    private companion object {
        const val NOT_CURATED =
            "Shiny in your games, but how to find them is not curated yet. Slot detail says what is known."

        /** The same name as [net.pokedex.feature.dex.HuntRoute]'s argument, so a route can preselect it. */
        const val KEY_GAME = "gameId"
        const val KEY_OUT_OF_REACH = "outOfReach"
        const val KEY_SCOPE = "scope"

        /** Stored for "everything the game offers", since a saved-state null reads as unset. */
        const val EVERYTHING = "Everything"

        /** A game picked from the chips shows what it is first for: the question I farm by. */
        val DEFAULT_SCOPE = FarmScope.HereFirst.name
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
