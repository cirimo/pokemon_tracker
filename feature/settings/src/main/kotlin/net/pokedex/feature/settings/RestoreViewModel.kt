package net.pokedex.feature.settings

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.pokedex.core.data.backup.BackupLocation
import net.pokedex.core.data.backup.BackupRepository
import net.pokedex.core.model.AppError
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.backup.BackupDestination
import net.pokedex.core.model.backup.BackupName
import net.pokedex.core.model.backup.ImportMode
import net.pokedex.core.model.backup.RestorePreview
import javax.inject.Inject

@Immutable
data class RestoreUiState(
    val phase: RestorePhase = RestorePhase.Loading,
    /** Opened on one file from the system picker, so there is no list to go back to. */
    val fromFile: Boolean = false,
    val confirmReplace: Boolean = false,
)

@Immutable
data class BackupItem(val name: BackupName, val title: String, val summary: String)

sealed interface RestorePhase {
    data object Loading : RestorePhase
    data class Choose(val backups: List<BackupItem>, val destination: BackupDestination) : RestorePhase
    data class Preview(val source: String, val preview: RestorePreview) : RestorePhase
    data class Refused(val error: AppError) : RestorePhase
    data object Working : RestorePhase
    data class Done(val written: Int, val snapshot: String?) : RestorePhase
}

sealed interface RestoreEvent {
    data class Open(val item: BackupItem) : RestoreEvent
    data class FolderPicked(val tree: Uri) : RestoreEvent
    data object BackToList : RestoreEvent
    data object Merge : RestoreEvent
    data object AskReplace : RestoreEvent
    data object ConfirmReplace : RestoreEvent
    data object CancelReplace : RestoreEvent
}

/**
 * A restore, in the order ADR 0007 and 0011 require: show what the file holds and what each
 * mode would do, touch nothing until the user chooses, and let the repository snapshot the
 * current records before it writes. A newer schema never reaches a button; it arrives here
 * as [RestorePhase.Refused] with both numbers.
 */
@HiltViewModel
class RestoreViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val location: BackupLocation,
    private val backups: BackupRepository,
) : ViewModel() {

    private val fileUri = savedState.toRoute<BackupRestoreRoute>().fileUri?.let(Uri::parse)
    private val _state = MutableStateFlow(RestoreUiState(fromFile = fileUri != null))
    val state: StateFlow<RestoreUiState> = _state.asStateFlow()

    /** The file being previewed. Held here, not in state: it can be a few hundred KB. */
    private var text: String? = null

    init {
        if (fileUri != null) launchStep { load(location.readDocument(fileUri), "The file you picked") } else list()
    }

    fun onEvent(event: RestoreEvent) {
        when (event) {
            is RestoreEvent.Open -> launchStep { load(location.read(event.item.name), event.item.title) }
            is RestoreEvent.FolderPicked -> launchStep {
                when (val chosen = location.choose(event.tree)) {
                    is Outcome.Err -> phase(RestorePhase.Refused(chosen.error))
                    is Outcome.Ok -> list()
                }
            }
            RestoreEvent.BackToList -> list()
            RestoreEvent.Merge -> restore(ImportMode.MERGE)
            RestoreEvent.AskReplace -> _state.update { it.copy(confirmReplace = true) }
            RestoreEvent.CancelReplace -> _state.update { it.copy(confirmReplace = false) }
            RestoreEvent.ConfirmReplace -> {
                _state.update { it.copy(confirmReplace = false) }
                restore(ImportMode.REPLACE)
            }
        }
    }

    private fun list() = launchStep {
        text = null
        val destination = location.observe().first()
        when (val found = location.restorable()) {
            is Outcome.Err -> phase(RestorePhase.Refused(found.error))
            is Outcome.Ok -> phase(
                RestorePhase.Choose(
                    backups = found.value.map { BackupItem(it, formatWhen(it.createdAt), backupLabel(it)) },
                    destination = destination,
                ),
            )
        }
    }

    private suspend fun load(read: Outcome<String>, source: String) {
        val loaded = when (read) {
            is Outcome.Err -> return phase(RestorePhase.Refused(read.error))
            is Outcome.Ok -> read.value
        }
        when (val preview = backups.preview(loaded)) {
            is Outcome.Err -> phase(RestorePhase.Refused(preview.error))
            is Outcome.Ok -> {
                text = loaded
                phase(RestorePhase.Preview(source, preview.value))
            }
        }
    }

    private fun restore(mode: ImportMode) {
        val loaded = text ?: return
        launchStep {
            when (val result = backups.import(loaded, mode)) {
                is Outcome.Err -> phase(RestorePhase.Refused(result.error))
                is Outcome.Ok -> phase(
                    RestorePhase.Done(result.value.written, result.value.snapshot?.let(::backupLabel)),
                )
            }
        }
    }

    private fun launchStep(block: suspend () -> Unit) {
        phase(if (text == null) RestorePhase.Loading else RestorePhase.Working)
        viewModelScope.launch { block() }
    }

    private fun phase(phase: RestorePhase) = _state.update { it.copy(phase = phase) }
}
