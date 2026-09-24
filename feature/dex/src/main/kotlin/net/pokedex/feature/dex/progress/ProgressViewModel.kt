package net.pokedex.feature.dex.progress

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
import net.pokedex.core.model.AppError
import net.pokedex.core.model.BoxProgress
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CatchRecord
import net.pokedex.core.model.Dex
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.Progress
import net.pokedex.core.model.dashboardOf
import net.pokedex.feature.dex.gameSetLabel
import javax.inject.Inject

@Immutable
data class ProgressUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val overall: Progress = Progress.ZERO,
    val noShinyYet: Int = 0,
    val closest: List<BoxRow> = emptyList(),
    val regions: List<RegionRow> = emptyList(),
    val games: List<GameRow> = emptyList(),
    val recent: List<RecentRow> = emptyList(),
    val orphans: List<OrphanRow> = emptyList(),
)

@Immutable
data class BoxRow(val boxIndex: Int, val name: String, val caught: Int, val total: Int)

@Immutable
data class RegionRow(val name: String, val caught: Int, val total: Int, val boxes: List<BoxRow>)

@Immutable
data class GameRow(val gameSetId: String, val label: String, val needed: Int)

@Immutable
data class RecentRow(
    val key: CatchKey,
    val name: String,
    val dexNumber: Int,
    val type1: String,
    val type2: String?,
    val spriteFile: String,
    val originName: String?,
    val caughtAt: Long,
)

/** A record with no slot in this preset. [name] falls back to the raw id when the dataset dropped the variant. */
@Immutable
data class OrphanRow(
    val key: CatchKey,
    val name: String,
    val originName: String?,
    val caughtAt: Long?,
    val notes: String?,
)

sealed interface ProgressEvent {
    data class ForgetOrphan(val key: CatchKey) : ProgressEvent
    data object Retry : ProgressEvent
}

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val dexRepository: DexRepository,
    private val catches: CatchRepository,
) : ViewModel() {

    private val dex = MutableStateFlow<Outcome<Dex>?>(null)

    val state: StateFlow<ProgressUiState> = combine(dex, catches.observeRecords()) { dex, records ->
        when (dex) {
            null -> ProgressUiState()
            is Outcome.Err -> ProgressUiState(loading = false, error = dex.error)
            is Outcome.Ok -> build(dex.value, records)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProgressUiState())

    init {
        load()
    }

    fun onEvent(event: ProgressEvent) {
        when (event) {
            is ProgressEvent.ForgetOrphan -> viewModelScope.launch { catches.forget(event.key) }
            ProgressEvent.Retry -> load()
        }
    }

    private fun load() {
        viewModelScope.launch { dex.value = dexRepository.dex() }
    }

    private fun build(dex: Dex, records: Map<CatchKey, CatchRecord>): ProgressUiState {
        val board = dashboardOf(dex, records)
        val gameNames = dex.games.associate { it.id to it.name }
        fun box(b: BoxProgress) = BoxRow(b.boxIndex, b.name, b.progress.caught, b.progress.total)
        return ProgressUiState(
            loading = false,
            overall = board.overall,
            noShinyYet = board.noShinyYet,
            closest = board.closest.map(::box),
            regions = board.regions.map { r ->
                RegionRow(r.name, r.progress.caught, r.progress.total, r.boxes.map(::box))
            },
            games = board.neededByGame.map { GameRow(it.gameSet.id, gameSetLabel(it.gameSet), it.needed) },
            recent = board.recent.map { (entry, record) ->
                RecentRow(
                    key = entry.key,
                    name = entry.variant.displayName,
                    dexNumber = entry.variant.dexNum,
                    type1 = entry.variant.type1,
                    type2 = entry.variant.type2,
                    spriteFile = entry.variant.spriteFile,
                    originName = record.originGameId?.let { gameNames[it] ?: it.value },
                    caughtAt = requireNotNull(record.caughtAt),
                )
            },
            orphans = board.orphans.map { record ->
                OrphanRow(
                    key = record.key,
                    name = dex.variant(record.key.variantId)?.displayName ?: record.key.toString(),
                    originName = record.originGameId?.let { gameNames[it] ?: it.value },
                    caughtAt = record.caughtAt,
                    notes = record.notes,
                )
            },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
