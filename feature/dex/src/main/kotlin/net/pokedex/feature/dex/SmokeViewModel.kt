package net.pokedex.feature.dex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.pokedex.core.data.repository.CatchRepository
import net.pokedex.core.data.repository.ReferenceRepository
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.PresetId
import net.pokedex.core.model.progressOf
import javax.inject.Inject

/**
 * Proves the whole stack in one screen: Hilt to repository to Room asset, and a write
 * to the user database that comes back out.
 *
 * It deliberately touches BOTH databases and joins them in memory, because that is the
 * architecture decision most likely to be wrong, and M0 is the cheapest place to find
 * out that it is not.
 */
@HiltViewModel
class SmokeViewModel @Inject constructor(
    private val reference: ReferenceRepository,
    private val catches: CatchRepository,
) : ViewModel() {

    private val presetId = PresetId("grouped-balanced")

    private val _state = MutableStateFlow<SmokeUiState>(SmokeUiState.Loading)
    val state: StateFlow<SmokeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: SmokeEvent) {
        when (event) {
            SmokeEvent.Reload -> load()
            SmokeEvent.ToggleFirstSlot -> toggleFirstSlot()
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = SmokeUiState.Loading

            val meta = reference.datasetMeta()
            val preset = reference.preset(presetId)
            val slots = reference.slots(presetId)
            val integrity = reference.integrity(presetId)

            val failure = listOf(meta, preset, slots, integrity)
                .filterIsInstance<Outcome.Err>()
                .firstOrNull()
            if (failure != null) {
                _state.value = SmokeUiState.Broken(failure.error)
                return@launch
            }

            meta as Outcome.Ok
            preset as Outcome.Ok
            slots as Outcome.Ok
            integrity as Outcome.Ok

            // The in-memory join: reference slots on one side, user records on the
            // other, resolved by CatchKey. The two databases never meet in SQL.
            val records = catches.observeRecords().first()
            val progress = progressOf(slots.value, records)

            _state.value = SmokeUiState.Ready(
                presetName = preset.value.name,
                boxCount = integrity.value.boxCount,
                filledSlotCount = integrity.value.filledSlotCount,
                distinctVariantCount = integrity.value.distinctVariantCount,
                duplicateSlotCount = slots.value.count { it.copyIndex > 0 },
                caughtCount = progress.caught,
                datasetVersion = meta.value.datasetVersion,
                upstreamTag = meta.value.upstreamTag,
            )
        }
    }

    private fun toggleFirstSlot() {
        viewModelScope.launch {
            val slots = reference.slots(presetId)
            if (slots !is Outcome.Ok) return@launch
            val first = slots.value.firstOrNull() ?: return@launch
            val current = catches.record(first.catchKey)?.caught ?: false
            catches.setCaught(first.catchKey, caught = !current)
            load()
        }
    }
}
