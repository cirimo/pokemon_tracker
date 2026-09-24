package net.pokedex.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pokedex.designsystem.component.ActionButton
import net.pokedex.designsystem.component.FilterChip
import net.pokedex.designsystem.component.NoticeCard
import net.pokedex.designsystem.component.ScreenScaffold
import net.pokedex.designsystem.component.ScreenSection
import net.pokedex.designsystem.component.SettingRow
import net.pokedex.designsystem.component.SettingSwitch
import net.pokedex.designsystem.theme.PokedexTheme
import java.time.LocalDate

@Composable
internal fun SettingsDestination(
    onBack: () -> Unit,
    onOpenRestore: (fileUri: Uri?) -> Unit,
    onOpenMyGames: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenRestore = onOpenRestore,
        onOpenMyGames = onOpenMyGames,
    )
}

/**
 * My games, then backups.
 *
 * The folder row comes first and says plainly when backups are not safe from an uninstall,
 * because that is the one setting whose default loses data.
 */
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit,
    onOpenRestore: (fileUri: Uri?) -> Unit,
    onOpenMyGames: () -> Unit,
) {
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree?.let { onEvent(SettingsEvent.FolderPicked(it)) }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_JSON)) { uri ->
        uri?.let { onEvent(SettingsEvent.ExportTo(it)) }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onOpenRestore)
    }

    ScreenScaffold(title = "Settings", onBack = onBack) {
        state.notice?.let { NoticeCard(title = it.title, body = it.body, warning = it.warning) }

        ScreenSection(title = "Hunting") {
            SettingRow(
                title = "My games",
                summary = state.myGames.ifEmpty { "None chosen. The hunt list needs these." },
                onClick = onOpenMyGames,
            )
        }

        ScreenSection(
            title = "Backups",
            body = "Written two minutes after your last change, when you leave the app, and once a day.",
        ) {
            FolderSetting(state = state, onPick = { pickFolder.launch(null) })
            SettingSwitch(
                title = "Automatic backups",
                summary = lastBackupSummary(state),
                checked = state.autoBackupEnabled,
                onCheckedChange = { onEvent(SettingsEvent.SetAutoBackup(it)) },
            )
            KeepCountSetting(keep = state.keepCount, onPick = { onEvent(SettingsEvent.SetKeepCount(it)) })
            ActionButton(
                label = if (state.working) "Backing up…" else "Back up now",
                onClick = { onEvent(SettingsEvent.BackUpNow) },
                enabled = !state.working,
            )
        }

        ScreenSection(title = "Restore and move", body = "A restore always saves what is here first.") {
            SettingRow(
                title = "Restore from a backup",
                summary = "Choose from the backup folder",
                onClick = { onOpenRestore(null) },
            )
            SettingRow(
                title = "Restore from a file",
                summary = "Any backup file, from anywhere on the phone",
                onClick = { importFile.launch(IMPORT_TYPES) },
            )
            SettingRow(
                title = "Export to a file",
                summary = "One file, to keep or to move to another phone",
                enabled = !state.working,
                onClick = { exportFile.launch("pokedex-export-${LocalDate.now()}.json") },
            )
        }
    }
}

@Composable
private fun FolderSetting(state: SettingsUiState, onPick: () -> Unit) {
    val destination = state.destination
    if (destination != null && !destination.survivesUninstall) {
        NoticeCard(
            title = if (destination.lostGrant) "The backup folder is gone" else "Backups stay inside the app",
            body = if (destination.lostGrant) {
                "The folder you picked can no longer be reached, so backups are going inside the app. " +
                    "Pick it again, or pick another."
            } else {
                "Uninstalling the app deletes them with everything else. Pick a folder, and they survive it."
            },
            warning = true,
            actionLabel = "Pick a folder",
            onAction = onPick,
        )
    } else {
        SettingRow(title = "Backup folder", summary = destinationSummary(destination), onClick = onPick)
    }
}

@Composable
private fun KeepCountSetting(keep: Int, onPick: (Int) -> Unit) {
    SettingRow(
        title = "Keep the newest",
        summary = "Older automatic backups are removed. The one with the most catches is always kept.",
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceSm)) {
        KEEP_CHOICES.forEach { count ->
            FilterChip(label = "$count", selected = keep == count, onSelectedChange = { onPick(count) })
        }
    }
}

private fun lastBackupSummary(state: SettingsUiState): String = when {
    !state.autoBackupEnabled -> "Off. Only backups you make yourself are written."
    state.lastBackup == null -> "On. None written yet."
    else -> "On. Last backup ${relative(state.lastBackup)}."
}

private const val MIME_JSON = "application/json"

/**
 * JSON, plus the types some providers label it with. A backup the picker greys out is
 * worse than one that opens and is then refused with a reason.
 */
internal val IMPORT_TYPES = arrayOf(MIME_JSON, "text/*", "application/octet-stream")

private val KEEP_CHOICES = listOf(5, 10, 20, 50)
