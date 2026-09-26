package net.pokedex.feature.dex.boxes

import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.pokedex.core.data.di.DefaultDispatcher
import net.pokedex.core.data.repository.CatchRepository
import net.pokedex.core.data.repository.DexRepository
import net.pokedex.core.data.repository.GuideRepository
import net.pokedex.core.data.repository.SettingsRepository
import net.pokedex.core.model.AppError
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CatchRecord
import net.pokedex.core.model.Dex
import net.pokedex.core.model.DexEntry
import net.pokedex.core.model.DexFilter
import net.pokedex.core.model.FarmFilter
import net.pokedex.core.model.FarmPlan
import net.pokedex.core.model.FarmScope
import net.pokedex.core.model.GameId
import net.pokedex.core.model.HuntGuide
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.Progress
import net.pokedex.core.model.SlotStatus
import net.pokedex.core.model.farmOrderOf
import net.pokedex.core.model.huntPlan
import net.pokedex.core.model.progressOf
import net.pokedex.core.model.searchDex
import net.pokedex.core.model.statusOf
import net.pokedex.designsystem.component.BoxPage
import net.pokedex.designsystem.component.BoxSlotItem
import net.pokedex.designsystem.component.SlotState
import net.pokedex.feature.dex.gameSetLabel
import net.pokedex.feature.dex.locationOf
import net.pokedex.feature.dex.oddsLabel
import net.pokedex.feature.dex.toSlotState
import net.pokedex.feature.dex.typeLabel
import javax.inject.Inject

/**
 * The box pager and its search mode.
 *
 * Two derivations run off the same records stream, deliberately separately: the pages are
 * rebuilt when a record changes, the search results when a record OR the filter changes.
 * Folding them into one would rebuild 1394 tiles on every keystroke for nothing.
 *
 * Everything that must survive process death lives in [savedState] -- search mode, the
 * whole filter -- and the pager's own page is restored by `rememberPagerState`. What
 * survives a cold start is only the last box, from settings; a filter is a question you
 * were asking, not a place you were.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class BoxesViewModel @Inject constructor(
    private val dexRepository: DexRepository,
    private val guides: GuideRepository,
    catches: CatchRepository,
    private val settings: SettingsRepository,
    private val savedState: SavedStateHandle,
    @DefaultDispatcher private val default: CoroutineDispatcher,
) : ViewModel() {

    private val loaded = MutableStateFlow<Loaded?>(null)
    private val error = MutableStateFlow<AppError?>(null)
    private val settledBox = MutableStateFlow<Int?>(null)

    private val records = catches.observeRecords()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    // Shared for the same reason as records: several derivations read it, and it should be
    // one Room observer, not several.
    private val farmRanks = settings.observeFarmRanks()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    // The set alone, so a reorder -- which changes no slot's status -- does not rebuild the
    // pager's 1394 tiles.
    private val myGames = farmRanks.map { it.keys }.distinctUntilChanged()

    private val searching = savedState.getStateFlow(KEY_SEARCHING, false)
    private val filter = savedState.getStateFlow(KEY_FILTER, "").map(::decodeFilter)
    private val jump = savedState.getStateFlow(KEY_JUMP_TO_BOX, NO_JUMP)

    // Nullable rather than filterNotNull: if the dex fails to load these must still emit, or
    // the combine below would wait on them forever and the error would never be shown.
    private val boxes = combine(loaded, records, myGames) { loaded, records, games ->
        loaded?.let { boxesOf(it, records, games) }
    }.flowOn(default)

    // Where each slot is farmed. Built when my games or their order change, so a keystroke
    // pays an array read per slot for the farm filter rather than a walk of my games.
    private val farmPlan = combine(loaded, farmRanks) { loaded, ranks ->
        loaded?.let { FarmPlan(it.dex, farmOrderOf(it.dex.games, ranks)) }
    }.flowOn(default)

    private val search = combine(
        loaded,
        records,
        farmPlan,
        searching,
        filter,
    ) { loaded, records, plan, active, filter ->
        if (loaded == null || plan == null) SearchUiState() else searchOf(loaded, records, plan, active, filter)
    }.flowOn(default)

    private val guide = MutableStateFlow<HuntGuide?>(null)

    /**
     * The top of the hunt list, as one line. Derived from the same ranking as the hunt screen,
     * so the two cannot disagree about what is next.
     */
    val nextHunt: StateFlow<NextHuntUi?> = combine(
        loaded,
        guide,
        records,
        myGames,
    ) { loaded, guide, records, games ->
        if (loaded == null || guide == null) null else nextHuntOf(loaded.dex, guide, records, games)
    }.flowOn(default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val state: StateFlow<BoxesUiState> =
        combine(loaded, error, boxes, search, jump) { loaded, error, boxes, search, jump ->
            BoxesUiState(
                loading = loaded == null && error == null,
                error = error,
                pages = boxes?.pages.orEmpty(),
                overall = boxes?.overall ?: Progress.ZERO,
                noShinyRemaining = boxes?.noShinyRemaining ?: 0,
                slots = loaded?.slots ?: SlotIndex.EMPTY,
                startBox = loaded?.startBox ?: 0,
                jumpToBox = jump.takeIf { it != NO_JUMP },
                search = search,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BoxesUiState())

    init {
        load()
        // The last box is written as the pager settles, debounced, so a fast run through
        // twenty boxes is one write rather than twenty. The first value is the restore itself
        // and is not worth writing back.
        settledBox.filterNotNull()
            .distinctUntilChanged()
            .drop(1)
            .debounce(LAST_BOX_DEBOUNCE_MS)
            .onEach { settings.setLastBox(it) }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: BoxesEvent) {
        when (event) {
            is BoxesEvent.BoxSettled -> settledBox.value = event.index
            is BoxesEvent.JumpRequested -> {
                // Search closes now, not after the pager moves: the pager is only laid out
                // while search is closed, and a scroll on a pager that is not laid out waits
                // for a layout that never comes.
                closeSearch()
                savedState[KEY_JUMP_TO_BOX] = event.boxIndex
            }
            BoxesEvent.JumpHandled -> savedState[KEY_JUMP_TO_BOX] = NO_JUMP
            is BoxesEvent.ShowSearch -> {
                savedState[KEY_FILTER] = event.encodedFilter
                savedState[KEY_SEARCHING] = true
            }
            BoxesEvent.OpenSearch -> savedState[KEY_SEARCHING] = true
            BoxesEvent.CloseSearch -> closeSearch()
            is BoxesEvent.FilterEdit -> editFilter { edited(event) }
            BoxesEvent.Retry -> load()
        }
    }

    private fun DexFilter.edited(event: BoxesEvent.FilterEdit): DexFilter = when (event) {
        is BoxesEvent.QueryChanged -> copy(query = event.query)
        is BoxesEvent.CaughtFilterChanged -> copy(caught = event.value)
        is BoxesEvent.GameSetToggled -> copy(gameSets = gameSets.toggle(event.id))
        is BoxesEvent.TypeToggled -> copy(types = types.toggle(event.id))
        is BoxesEvent.NoShinyChanged -> copy(noShiny = event.value)
        // Switching game keeps the scope: "only here" in Arceus, then "only here" in Violet.
        is BoxesEvent.FarmGameToggled -> {
            val scope = farm?.scope ?: FarmScope.HereFirst
            copy(farm = if (farm?.gameId == event.gameId) null else FarmFilter(event.gameId, scope))
        }
        is BoxesEvent.FarmScopeChanged -> copy(farm = farm?.copy(scope = event.scope))
        BoxesEvent.ClearRefinements -> DexFilter(query = query)
    }

    private fun load() {
        viewModelScope.launch {
            error.value = null
            val started = System.nanoTime()
            when (val outcome = dexRepository.dex()) {
                is Outcome.Err -> error.value = outcome.error
                is Outcome.Ok -> {
                    val dex = outcome.value
                    val lastBox = settings.get().lastBoxIndex.coerceIn(0, dex.boxes.lastIndex)
                    loaded.value = Loaded.of(dex, lastBox)
                    // After the dex, never before it: the guide is not needed to draw a box.
                    launch { (guides.guide() as? Outcome.Ok)?.let { guide.value = it.value } }
                    // The number the cold-start budget is about: process start to a box you can
                    // read, not just to the first frame (which is a skeleton).
                    val sinceStart = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
                    val sinceAsked = (System.nanoTime() - started) / NANOS_PER_MS
                    Log.i(TAG, "box view usable $sinceStart ms after process start")
                    Log.i(TAG, "dex ready $sinceAsked ms after asking")
                }
            }
        }
    }

    private fun closeSearch() {
        savedState[KEY_SEARCHING] = false
        savedState[KEY_FILTER] = ""
    }

    private inline fun editFilter(change: DexFilter.() -> DexFilter) {
        val current = decodeFilter(savedState[KEY_FILTER] ?: "")
        savedState[KEY_FILTER] = Json.encodeToString(current.change())
    }

    // A filter saved by an older build that no longer decodes is dropped, not crashed on:
    // it is a question the user was asking, and asking it again costs two taps.
    private fun decodeFilter(json: String): DexFilter =
        if (json.isEmpty()) {
            DexFilter()
        } else {
            runCatching { Json.decodeFromString<DexFilter>(json) }.getOrDefault(DexFilter())
        }

    private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

    /** Everything about the dex that does not change while the app runs, computed once. */
    private class Loaded(
        val dex: Dex,
        val layouts: List<List<DexEntry?>>,
        val slots: SlotIndex,
        val startBox: Int,
        val gameSets: List<FilterChoice>,
        val types: List<FilterChoice>,
    ) {
        companion object {
            fun of(dex: Dex, startBox: Int) = Loaded(
                dex = dex,
                layouts = dex.boxes.map { dex.layout(it.boxIndex) },
                slots = SlotIndex(
                    keys = dex.entries.associate { it.key.toString() to it.key },
                    sprites = dex.entries.associate { it.key.toString() to it.variant.spriteFile },
                ),
                startBox = startBox,
                gameSets = dex.gameSets.map { FilterChoice(it.id, gameSetLabel(it)) },
                types = dex.types.map { FilterChoice(it, typeLabel(it)) },
            )
        }
    }

    private class Boxes(val pages: List<BoxPage>, val overall: Progress, val noShinyRemaining: Int)

    private fun boxesOf(loaded: Loaded, records: Map<CatchKey, CatchRecord>, myGames: Set<GameId>): Boxes {
        val dex = loaded.dex
        val pages = dex.boxes.mapIndexed { i, box ->
            BoxPage(
                index = box.boxIndex,
                name = box.name,
                slots = loaded.layouts[i].mapIndexed { position, entry ->
                    if (entry == null) {
                        BoxSlotItem(state = SlotState.Empty, key = "hole-${box.boxIndex}-$position")
                    } else {
                        BoxSlotItem(
                            state = statusOf(entry, records, myGames).toSlotState(),
                            label = entry.variant.displayName,
                            key = entry.key.toString(),
                        )
                    }
                },
            )
        }
        return Boxes(
            pages = pages,
            overall = progressOf(dex.entries.map { it.slot }, records),
            noShinyRemaining = dex.entries.count { statusOf(it, records) == SlotStatus.NoShinyExists },
        )
    }

    private fun nextHuntOf(
        dex: Dex,
        guide: HuntGuide,
        records: Map<CatchKey, CatchRecord>,
        myGames: Set<GameId>,
    ): NextHuntUi {
        if (myGames.isEmpty()) return NextHuntUi("Choose your games to see what to hunt next")
        val top = huntPlan(dex, records, myGames, guide).hunts.firstOrNull()
            ?: return NextHuntUi("Nothing left to hunt in your games")
        val names = dex.games.associate { it.id to it.name }
        val way = top.way
        val name = dex.species(top.lead.variant.dexNum)?.name.takeIf { top.key.regionalForm == null }
            ?: top.lead.variant.displayName
        val how = if (way == null) {
            "shiny in " + top.games.joinToString(", ") { names[it] ?: it.value }
        } else {
            val method = guide.method(way.encounter.methodId)?.name ?: way.encounter.methodId
            "$method in ${names[way.game] ?: way.game.value}" + way.odds?.let { ", ${oddsLabel(it.best)}" }.orEmpty()
        }
        return NextHuntUi("$name, $how")
    }

    private fun searchOf(
        loaded: Loaded,
        records: Map<CatchKey, CatchRecord>,
        plan: FarmPlan,
        active: Boolean,
        filter: DexFilter,
    ): SearchUiState {
        val names = loaded.dex.games.associate { it.id to it.name }
        val farmGames = plan.order.map { FilterChoice(it.value, names[it] ?: it.value) }
        if (!active) return SearchUiState(gameSets = loaded.gameSets, types = loaded.types, farmGames = farmGames)
        val myGames = plan.games
        val results = searchDex(loaded.dex, records, filter, farm = plan).map { entry ->
            SearchResult(
                slotKey = entry.key.toString(),
                key = entry.key,
                name = entry.variant.displayName,
                dexNumber = entry.variant.dexNum,
                type1 = entry.variant.type1,
                type2 = entry.variant.type2,
                status = statusOf(entry, records, myGames),
                location = locationOf(entry),
                spriteFile = entry.variant.spriteFile,
            )
        }
        return SearchUiState(
            active = true,
            filter = filter,
            results = results,
            gameSets = loaded.gameSets,
            types = loaded.types,
            farmGames = farmGames,
        )
    }

    private companion object {
        /** Kept in this ViewModel's own handle, so a pending jump survives process death. */
        const val KEY_JUMP_TO_BOX = "jumpToBox"
        const val KEY_SEARCHING = "searching"
        const val KEY_FILTER = "filter"
        const val NO_JUMP = -1
        const val STOP_TIMEOUT_MS = 5_000L
        const val LAST_BOX_DEBOUNCE_MS = 500L
        const val NANOS_PER_MS = 1_000_000
        const val TAG = "BoxesViewModel"
    }
}
