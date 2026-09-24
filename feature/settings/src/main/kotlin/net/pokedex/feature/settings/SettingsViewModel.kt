package net.pokedex.feature.settings

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.pokedex.core.data.backup.BackupLocation
import net.pokedex.core.data.backup.BackupRepository
import net.pokedex.core.data.repository.DexRepository
import net.pokedex.core.data.repository.SettingsRepository
import net.pokedex.core.model.AppError
import net.pokedex.core.model.GameId
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.backup.BackupDestination
import net.pokedex.core.model.backup.BackupWriter
import java.time.Instant
import javax.inject.Inject

@Immutable
data class SettingsUiState(
    val destination: BackupDestination? = null,
    val autoBackupEnabled: Boolean = true,
    val keepCount: Int = 10,
    val lastBackup: Instant? = null,
    /** The chosen games' names, joined; empty when none are chosen. */
    val myGames: String = "",
    val working: Boolean = false,
    /** The outcome of the last action, as a sentence. Never gold: housekeeping is not a shiny. */
    val notice: Notice? = null,
)

@Immutable
data class Notice(val title: String, val body: String, val warning: Boolean)

sealed interface SettingsEvent {
    data class FolderPicked(val tree: Uri) : SettingsEvent
    data class SetAutoBackup(val enabled: Boolean) : SettingsEvent
    data class SetKeepCount(val count: Int) : SettingsEvent
    data object BackUpNow : SettingsEvent
    data class ExportTo(val uri: Uri) : SettingsEvent
    data object DismissNotice : SettingsEvent
}

/**
 * Only what backups need: where they go, whether they are automatic, how many to keep, and
 * a way to write one now. This is not a general preferences screen (M3 scope).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val location: BackupLocation,
    private val backups: BackupRepository,
    dexRepository: DexRepository,
) : ViewModel() {

    private val transient = MutableStateFlow(Transient())

    private val gameNames = MutableStateFlow<Map<GameId, String>>(emptyMap())

    private val myGames = combine(settings.observeMyGames(), gameNames) { owned, names ->
        // In the dataset's game order, so the summary reads the way the list below it does.
        names.filterKeys { it in owned }.values.joinToString(", ")
    }

    val state: StateFlow<SettingsUiState> = combine(
        settings.observe(),
        location.observe(),
        backups.observeLastBackup(),
        transient,
        myGames,
    ) { settings, destination, last, transient, myGames ->
        SettingsUiState(
            destination = destination,
            autoBackupEnabled = settings.autoBackupEnabled,
            keepCount = settings.autoBackupKeepCount,
            lastBackup = last,
            myGames = myGames,
            working = transient.working,
            notice = transient.notice,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    init {
        viewModelScope.launch {
            (dexRepository.dex() as? Outcome.Ok)?.let { loaded ->
                gameNames.value = loaded.value.games.associate { it.id to it.name }
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.FolderPicked -> run {
                when (val chosen = location.choose(event.tree)) {
                    is Outcome.Err -> transient.value = transient.value.copy(notice = errorNotice(chosen.error))
                    // Write one straight away, so the folder is proven writable while the user
                    // is still looking at the screen that picked it.
                    is Outcome.Ok -> backUp()
                }
            }
            is SettingsEvent.SetAutoBackup -> viewModelScope.launch {
                settings.edit { it.copy(autoBackupEnabled = event.enabled) }
            }
            is SettingsEvent.SetKeepCount -> viewModelScope.launch {
                settings.edit { it.copy(autoBackupKeepCount = event.count) }
            }
            SettingsEvent.BackUpNow -> run { backUp() }
            is SettingsEvent.ExportTo -> run {
                transient.value = transient.value.copy(notice = exportNotice(backups.exportTo(event.uri)))
            }
            SettingsEvent.DismissNotice -> transient.value = Transient()
        }
    }

    private suspend fun backUp() {
        val outcome = backups.autoBackup(reason = "manual")
        // Read fresh rather than from state: a folder picked a moment ago may not have
        // reached the combined state yet, and the notice should name the folder it used.
        transient.value = transient.value.copy(notice = backupNotice(outcome, location.observe().first()))
    }

    /** Runs [block] with the working flag up, so the buttons cannot be pressed twice. */
    private fun run(block: suspend () -> Unit) {
        if (transient.value.working) return
        transient.value = transient.value.copy(working = true, notice = null)
        viewModelScope.launch {
            try {
                block()
            } finally {
                transient.value = transient.value.copy(working = false)
            }
        }
    }

    private data class Transient(val working: Boolean = false, val notice: Notice? = null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

internal fun backupNotice(outcome: Outcome<BackupWriter.AutoResult>, destination: BackupDestination?): Notice =
    when (outcome) {
        is Outcome.Err -> errorNotice(outcome.error)
        is Outcome.Ok -> when (val result = outcome.value) {
            is BackupWriter.AutoResult.Written -> Notice(
                title = "Backed up",
                body = destination?.folderName
                    ?.let { "${caughtLabel(result.name.caughtCount)}, to $it." }
                    ?: "${caughtLabel(result.name.caughtCount)}, inside the app. Pick a folder so it survives an uninstall.",
                warning = destination?.survivesUninstall != true,
            )
            BackupWriter.AutoResult.SkippedUnchanged -> Notice(
                title = "Already backed up",
                body = "The newest backup already holds exactly these records.",
                warning = false,
            )
            BackupWriter.AutoResult.SkippedEmpty -> Notice(
                title = "Nothing to back up yet",
                body = "There are no catch records. An empty backup would only push out older ones.",
                warning = false,
            )
        }
    }

internal fun errorNotice(error: AppError) = Notice(backupErrorTitle(error), backupErrorBody(error), warning = true)

internal fun exportNotice(outcome: Outcome<Int>): Notice = when (outcome) {
    is Outcome.Err -> errorNotice(outcome.error)
    is Outcome.Ok -> Notice("Exported", "${recordsLabel(outcome.value)} written to the file you chose.", warning = false)
}
