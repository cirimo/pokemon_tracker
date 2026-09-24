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
import net.pokedex.core.data.repository.SettingsRepository
import net.pokedex.core.model.AppError
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CatchRecord
import net.pokedex.core.model.Dex
import net.pokedex.core.model.DexEntry
import net.pokedex.core.model.GameId
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.SlotStatus
import net.pokedex.core.model.VariantId
import net.pokedex.core.model.hasDetails
import net.pokedex.core.model.prefillOrigin
import net.pokedex.core.model.statusOf
import net.pokedex.feature.dex.SlotDetailRoute
import net.pokedex.feature.dex.locationOf
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
    private val catches: CatchRepository,
    private val settings: SettingsRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val key = savedState.toRoute<SlotDetailRoute>().let { CatchKey(VariantId(it.variantId), it.copyIndex) }
    private val dex = MutableStateFlow<Outcome<Dex>?>(null)

    val state: StateFlow<SlotDetailUiState> = combine(dex, catches.observeRecords()) { dex, records ->
        when (dex) {
            null -> SlotDetailUiState()
            is Outcome.Err -> SlotDetailUiState(loading = false, error = dex.error)
            is Outcome.Ok -> build(dex.value, records)
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
            SlotDetailEvent.Retry -> load()
        }
    }

    private fun load() {
        viewModelScope.launch { dex.value = dexRepository.dex() }
    }

    private fun build(dex: Dex, records: Map<CatchKey, CatchRecord>): SlotDetailUiState {
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
                status = statusOf(entry, records),
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
