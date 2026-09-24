package net.pokedex.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.pokedex.core.data.backup.BackupLocation
import net.pokedex.core.data.repository.CatchRepository
import net.pokedex.core.data.repository.SettingsRepository
import net.pokedex.designsystem.component.ActionButton
import net.pokedex.designsystem.component.PokedexBottomSheet
import net.pokedex.designsystem.theme.PokedexTheme
import javax.inject.Inject

/**
 * The first-launch offer to restore.
 *
 * An empty database on launch is what a reinstall looks like, and it is the moment the
 * user is least likely to remember that a backup exists. So the app asks. It asks only
 * while there are no records at all and the user has not said "start fresh", and an
 * uninstall resets that answer along with everything else.
 *
 * This shows over whatever destination is current, which is why :app places it beside the
 * NavHost rather than inside a route.
 */
// PokedexBottomSheet's signature carries M3's experimental SheetState, so every caller opts in.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreOffer(
    onRestoreFromFolder: () -> Unit,
    onRestoreFromFile: (Uri) -> Unit,
    viewModel: RestoreOfferViewModel = hiltViewModel(),
) {
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) viewModel.adoptFolder(tree, then = onRestoreFromFolder)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.handled()
            onRestoreFromFile(uri)
        }
    }
    if (!visible) return

    PokedexBottomSheet(onDismissRequest = viewModel::handled, title = "Restore your catches?") {
        val dimens = PokedexTheme.dimens
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = dimens.spaceXl),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
        ) {
            Text(
                text = "This install has no catch records. If you had the app before, its backups are in the " +
                    "folder you picked for them, usually Documents/Pokedex. Picking it again also " +
                    "turns automatic backups back on there.",
                style = MaterialTheme.typography.bodyMedium,
                color = PokedexTheme.colors.onCaseMuted,
            )
            ActionButton(
                label = "Pick the backup folder",
                onClick = { pickFolder.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            )
            ActionButton(
                label = "Pick a backup file",
                onClick = { pickFile.launch(IMPORT_TYPES) },
                primary = false,
                modifier = Modifier.fillMaxWidth(),
            )
            ActionButton(
                label = "Start fresh",
                onClick = viewModel::startFresh,
                primary = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@HiltViewModel
class RestoreOfferViewModel @Inject constructor(
    catches: CatchRepository,
    private val settings: SettingsRepository,
    private val location: BackupLocation,
) : ViewModel() {

    /** Dismissed for this process: the user is on their way to restoring, or closed the sheet. */
    private val handled = MutableStateFlow(false)

    val visible: StateFlow<Boolean> = combine(catches.observeRecords(), settings.observe(), handled) { records, s, h ->
        records.isEmpty() && !s.restoreOfferDismissed && !h
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    fun handled() {
        handled.value = true
    }

    fun startFresh() {
        handled()
        viewModelScope.launch { settings.edit { it.copy(restoreOfferDismissed = true) } }
    }

    /** Adopts the folder for automatic backups too, then opens the list of what is in it. */
    fun adoptFolder(tree: Uri, then: () -> Unit) {
        handled()
        viewModelScope.launch {
            // The result is not checked on purpose. If the grant fails, the restore screen still
            // opens on the in-app folder, and its own notice offers to pick again. That beats
            // a sheet that closes and does nothing.
            location.choose(tree)
            then()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
