package net.pokedex.feature.dex.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.pokedex.core.data.repository.CatchRepository
import net.pokedex.core.data.repository.DexRepository
import net.pokedex.core.data.repository.GuideRepository
import net.pokedex.core.data.repository.SettingsRepository
import net.pokedex.core.model.AppError
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CatchRecord
import net.pokedex.core.model.Dex
import net.pokedex.core.model.DexEntry
import net.pokedex.core.model.Encounter
import net.pokedex.core.model.GameId
import net.pokedex.core.model.HuntGuide
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.Priority
import net.pokedex.core.model.SlotStatus
import net.pokedex.core.model.Standing
import net.pokedex.core.model.VariantId
import net.pokedex.core.model.hasDetails
import net.pokedex.core.model.prefillOrigin
import net.pokedex.core.model.standingOf
import net.pokedex.core.model.statusOf
import net.pokedex.feature.dex.SlotDetailRoute
import net.pokedex.feature.dex.locationOf
import net.pokedex.feature.dex.oddsSentence
import net.pokedex.feature.dex.sourceLabel
import net.pokedex.feature.dex.standingSentence
import javax.inject.Inject

@Immutable
data class SlotDetailUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val slot: SlotUi? = null,
    /** 1-based: "copy 2 of 2". Only meaningful when [copies] has more than one entry. */
    val copyNumber: Int = 1,
    /** Every slot demanding this variant, this one included, in preset order. */
    val copies: List<CopyUi> = emptyList(),
    val games: List<GameUi> = emptyList(),
    val shinyReleased: Boolean = true,
    /** What is recorded for this slot, caught or not. Null when nothing ever was. */
    val record: RecordUi? = null,
    /** Every game the origin can be set to, shiny-obtainable ones first. */
    val origins: List<OriginUi> = emptyList(),
    val priority: Priority = Priority.Normal,
    /** How to get it in each of my games; every Switch game while none are chosen. */
    val hunting: List<GameHuntUi> = emptyList(),
    val gamesChosen: Boolean = false,
)

/** One game, for this slot: whether it can be shiny there, and every recorded way. */
@Immutable
data class GameHuntUi(
    val name: String,
    val standing: String,
    val huntable: Boolean,
    /** Empty for a huntable game means "no method recorded yet", and the screen says so. */
    val ways: List<WayUi>,
)

@Immutable
data class WayUi(
    val method: String,
    val location: String?,
    val prerequisite: String?,
    val notes: String?,
    /** "Evolve a shiny Dunsparce, found by: Wild encounter". Null unless this is an evolution. */
    val via: String?,
    /** Null when no odds are curated for the method. */
    val odds: String?,
    val locked: Boolean,
    val source: String,
)

/**
 * The record's details as the catch row and sheet show them. [caughtAt] stays an instant;
 * the screen formats it, because only the screen knows the locale.
 */
@Immutable
data class RecordUi(
    val originGameId: String?,
    val originName: String?,
    val caughtAt: Long?,
    val notes: String?,
    val hasDetails: Boolean,
)

/** A game the origin can be set to. [shinyHere] sorts it first and marks it in the picker. */
@Immutable
data class OriginUi(val id: String, val name: String, val shinyHere: Boolean)

/** The slot being shown. */
@Immutable
data class SlotUi(
    val key: CatchKey,
    val slotKey: String,
    val variantId: String,
    val name: String,
    val formName: String?,
    val dexNumber: Int,
    val type1: String,
    val type2: String?,
    val spriteFile: String,
    val status: SlotStatus,
    val location: String,
    val boxIndex: Int,
)

/** One slot demanding a variant, as a row: where it is and whether it is filled. */
@Immutable
data class CopyUi(
    val key: CatchKey,
    val slotKey: String,
    val name: String,
    val dexNumber: Int,
    val type1: String,
    val type2: String?,
    val spriteFile: String,
    val status: SlotStatus,
    val location: String,
)

@Immutable
data class GameUi(
    val name: String,
    val shinyLocked: Boolean,
    val eventOnly: Boolean,
    val lockReason: String?,
)

sealed interface SlotDetailEvent {
    data class SetCaught(val caught: Boolean) : SlotDetailEvent
    data class SaveDetails(val originGameId: String?, val caughtAt: Long?, val notes: String?) : SlotDetailEvent
    data object Forget : SlotDetailEvent
    data class SetPriority(val priority: Priority) : SlotDetailEvent
    data object Retry : SlotDetailEvent
}

/**
 * One slot: which variant it demands, where you can get that shiny, and the caught toggle.
 *
 * The toggle writes through CatchRepository and the screen re-renders from the records
 * stream rather than from a local flag, so what the toggle shows is always what the
 * database holds -- the same value the grid behind this screen will show on the way back.
 */
@HiltViewModel
class SlotDetailViewModel @Inject constructor(
    private val dexRepository: DexRepository,
    private val guides: GuideRepository,
    private val catches: CatchRepository,
    private val settings: SettingsRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val key = savedState.toRoute<SlotDetailRoute>().let { CatchKey(VariantId(it.variantId), it.copyIndex) }
    private val dex = MutableStateFlow<Outcome<Dex>?>(null)

    // A guide that fails to load costs the "how" section, not the screen: the rest of the
    // slot comes from the dex, which is the part that must work.
    private val guide = MutableStateFlow<HuntGuide?>(null)

    val state: StateFlow<SlotDetailUiState> = combine(
        dex,
        catches.observeRecords(),
        settings.observeMyGames(),
        guide,
    ) { dex, records, myGames, guide ->
        when (dex) {
            null -> SlotDetailUiState()
            is Outcome.Err -> SlotDetailUiState(loading = false, error = dex.error)
            is Outcome.Ok -> build(dex.value, records, myGames, guide)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SlotDetailUiState())

    init {
        load()
    }

    fun onEvent(event: SlotDetailEvent) {
        when (event) {
            is SlotDetailEvent.SetCaught -> viewModelScope.launch {
                // The prefill is decided here, where the dex is loaded, and only offered: the
                // record keeps an origin it already has (see withCaught).
                val availability = (dex.value as? Outcome.Ok)?.value?.availability(key.variantId).orEmpty()
                val prefill = if (event.caught) prefillOrigin(settings.get().lastOriginGameId, availability) else null
                catches.setCaught(key, event.caught, prefill)
            }
            is SlotDetailEvent.SaveDetails -> viewModelScope.launch {
                val origin = event.originGameId?.let(::GameId)
                catches.setDetails(key, origin, event.caughtAt, event.notes)
                // A game chosen by hand is what the next catch prefills.
                if (origin != null) settings.edit { it.copy(lastOriginGameId = origin) }
            }
            SlotDetailEvent.Forget -> viewModelScope.launch { catches.forget(key) }
            is SlotDetailEvent.SetPriority -> viewModelScope.launch { catches.setPriority(listOf(key), event.priority) }
            SlotDetailEvent.Retry -> load()
        }
    }

    private fun load() {
        viewModelScope.launch { dex.value = dexRepository.dex() }
        viewModelScope.launch { (guides.guide() as? Outcome.Ok)?.let { guide.value = it.value } }
    }

    private fun build(
        dex: Dex,
        records: Map<CatchKey, CatchRecord>,
        myGames: Set<GameId>,
        guide: HuntGuide?,
    ): SlotDetailUiState {
        // A key the dataset no longer has is a link from an older screen state -- say so
        // rather than crash. The record itself, if any, is untouched.
        val entry = dex.entry(key) ?: return SlotDetailUiState(
            loading = false,
            error = AppError.Unexpected("$key is not a slot in this preset"),
        )
        val variant = entry.variant
        val copies = dex.copiesOf(variant.id)
        val gameNames = dex.games.associate { it.id to it.name }
        return SlotDetailUiState(
            loading = false,
            slot = SlotUi(
                key = entry.key,
                slotKey = entry.key.toString(),
                variantId = variant.id.value,
                name = variant.displayName,
                formName = variant.formName,
                dexNumber = variant.dexNum,
                type1 = variant.type1,
                type2 = variant.type2,
                spriteFile = variant.spriteFile,
                status = statusOf(entry, records, myGames),
                location = locationOf(entry),
                boxIndex = entry.slot.boxIndex,
            ),
            copyNumber = copies.indexOf(entry) + 1,
            copies = copies.map { it.toCopyUi(records) },
            games = dex.availability(variant.id)
                .filter { it.obtainable && it.gameId.value != Dex.HOME_GAME_SET }
                .map {
                    GameUi(
                        name = gameNames[it.gameId] ?: it.gameId.value,
                        shinyLocked = it.shinyLocked,
                        eventOnly = it.eventOnly,
                        lockReason = it.shinyLockReason,
                    )
                },
            shinyReleased = variant.shinyReleased,
            record = records[key]?.let { record ->
                RecordUi(
                    originGameId = record.originGameId?.value,
                    // A game a later dataset dropped still shows, by its id.
                    originName = record.originGameId?.let { gameNames[it] ?: it.value },
                    caughtAt = record.caughtAt,
                    notes = record.notes,
                    hasDetails = record.hasDetails,
                )
            },
            origins = originsFor(dex, variant.id),
            priority = Priority.of(records[key]?.priority ?: 0),
            hunting = huntingFor(dex, variant.id, myGames, guide),
            gamesChosen = myGames.isNotEmpty(),
        )
    }

    /**
     * Per game: where it stands, then every recorded encounter, locked ones included so a
     * locked gift is seen as locked rather than missing. With no games chosen, every game
     * the variant appears in, so the screen is still useful before setup.
     */
    private fun huntingFor(dex: Dex, variant: VariantId, myGames: Set<GameId>, guide: HuntGuide?): List<GameHuntUi> {
        val games = dex.games.filter { it.gameSet != Dex.HOME_GAME_SET }.filter { game ->
            if (myGames.isEmpty()) standingOf(dex, variant, game.id) != Standing.Absent else game.id in myGames
        }
        val lockReasons = dex.availability(variant).associate { it.gameId to it.shinyLockReason }
        return games.map { game ->
            val standing = standingOf(dex, variant, game.id)
            GameHuntUi(
                name = game.name,
                standing = standingSentence(standing, lockReasons[game.id]),
                huntable = standing == Standing.Shiny,
                ways = guide?.let { g -> g.encounters(variant, game.id).map { wayUi(dex, g, it) } }.orEmpty(),
            )
        }
    }

    private fun wayUi(dex: Dex, guide: HuntGuide, encounter: Encounter): WayUi {
        val from = encounter.fromVariantId
        val source = from?.let { guide.bestWay(it, listOf(encounter.gameId)) }
        val odds = if (from == null) guide.odds(encounter.gameId, encounter.methodId) else source?.odds
        return WayUi(
            method = guide.method(encounter.methodId)?.name ?: encounter.methodId,
            location = encounter.location,
            prerequisite = encounter.prerequisite,
            notes = encounter.notes,
            via = from?.let { id ->
                val name = dex.variant(id)?.displayName ?: id.value
                val how = source?.let { guide.method(it.encounter.methodId)?.name ?: it.encounter.methodId }
                if (how == null) {
                    "Evolve a shiny $name. No way to find one is recorded yet."
                } else {
                    "Evolve a shiny $name, found by: $how"
                }
            },
            odds = odds?.let(::oddsSentence).takeUnless { encounter.shinyLocked },
            locked = encounter.shinyLocked,
            source = sourceLabel(encounter.sourceUrl),
        )
    }

    private fun originsFor(dex: Dex, variant: VariantId): List<OriginUi> {
        val shiny = dex.availability(variant).filter { it.obtainable && !it.shinyLocked }.mapTo(HashSet()) { it.gameId }
        return dex.games
            .map { OriginUi(id = it.id.value, name = it.name, shinyHere = it.id in shiny) }
            .sortedByDescending { it.shinyHere } // stable, so release order holds within each group
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

internal fun DexEntry.toCopyUi(records: Map<CatchKey, CatchRecord>) = CopyUi(
    key = key,
    slotKey = key.toString(),
    name = variant.displayName,
    dexNumber = variant.dexNum,
    type1 = variant.type1,
    type2 = variant.type2,
    spriteFile = variant.spriteFile,
    status = statusOf(this, records),
    location = locationOf(this),
)
