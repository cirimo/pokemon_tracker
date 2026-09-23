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
import net.pokedex.core.model.AppError
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CatchRecord
import net.pokedex.core.model.Dex
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.SlotStatus
import net.pokedex.core.model.Variant
import net.pokedex.core.model.VariantId
import net.pokedex.core.model.variantStatusOf
import net.pokedex.feature.dex.VariantDetailRoute
import javax.inject.Inject

@Immutable
data class VariantDetailUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val variant: VariantUi? = null,
    /** "Generation 2, Johto". Null when the species row is missing. */
    val origin: String? = null,
    /** Where this variant sits in the preset -- one row per copy. */
    val slots: List<CopyUi> = emptyList(),
    val evolvesFrom: RelatedUi? = null,
    val evolvesInto: List<RelatedUi> = emptyList(),
    /** Every variant of this species, this one included. */
    val forms: List<RelatedUi> = emptyList(),
)

@Immutable
data class VariantUi(
    val id: String,
    val name: String,
    val formName: String?,
    val dexNumber: Int,
    val type1: String,
    val type2: String?,
    val spriteFile: String,
    val status: SlotStatus,
)

/** Another variant, shown as a row that opens its own page. */
@Immutable
data class RelatedUi(
    val id: String,
    val name: String,
    val dexNumber: Int,
    val type1: String,
    val type2: String?,
    val spriteFile: String,
    val status: SlotStatus,
    /** Evolution condition ("level 32"), or null for a form. */
    val note: String?,
    val isCurrent: Boolean = false,
)

sealed interface VariantDetailEvent {
    data object Retry : VariantDetailEvent
}

/**
 * The species and form reference: what this variant is, what it evolves from and into and
 * under what condition, its sibling forms, and where it sits in the dex.
 *
 * Read-only by design. Catching happens on a slot, because a catch is a fact about a
 * (variant, copy) and this screen is about the variant.
 */
@HiltViewModel
class VariantDetailViewModel @Inject constructor(
    private val dexRepository: DexRepository,
    catches: CatchRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val id = VariantId(savedState.toRoute<VariantDetailRoute>().variantId)
    private val dex = MutableStateFlow<Outcome<Dex>?>(null)

    val state: StateFlow<VariantDetailUiState> = combine(dex, catches.observeRecords()) { dex, records ->
        when (dex) {
            null -> VariantDetailUiState()
            is Outcome.Err -> VariantDetailUiState(loading = false, error = dex.error)
            is Outcome.Ok -> build(dex.value, records)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VariantDetailUiState())

    init {
        load()
    }

    fun onEvent(event: VariantDetailEvent) {
        when (event) {
            VariantDetailEvent.Retry -> load()
        }
    }

    private fun load() {
        viewModelScope.launch { dex.value = dexRepository.dex() }
    }

    private fun build(dex: Dex, records: Map<CatchKey, CatchRecord>): VariantDetailUiState {
        val variant = dex.variant(id) ?: return VariantDetailUiState(
            loading = false,
            error = AppError.Unexpected("${id.value} is not in this dataset"),
        )
        fun Variant.related(note: String? = null) = RelatedUi(
            id = this.id.value,
            name = displayName,
            dexNumber = dexNum,
            type1 = type1,
            type2 = type2,
            spriteFile = spriteFile,
            status = variantStatusOf(dex.copiesOf(this.id), records),
            note = note,
            isCurrent = this.id == id,
        )

        val species = dex.species(variant.dexNum)
        return VariantDetailUiState(
            loading = false,
            variant = VariantUi(
                id = variant.id.value,
                name = variant.displayName,
                formName = variant.formName,
                dexNumber = variant.dexNum,
                type1 = variant.type1,
                type2 = variant.type2,
                spriteFile = variant.spriteFile,
                status = variantStatusOf(dex.copiesOf(variant.id), records),
            ),
            origin = species?.let { "Generation ${it.generation}, ${it.region.replaceFirstChar(Char::uppercase)}" },
            slots = dex.copiesOf(variant.id).map { it.toCopyUi(records) },
            evolvesFrom = variant.evolvesFromId
                ?.let(dex::variant)
                ?.related(note = variant.evolveCondition),
            evolvesInto = dex.evolvesInto(variant.id).map { it.related(note = it.evolveCondition) },
            forms = dex.forms(variant.dexNum).map { it.related() },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
