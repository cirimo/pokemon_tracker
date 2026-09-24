package net.pokedex.feature.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pokedex.core.model.backup.RestorePreview
import net.pokedex.designsystem.component.ActionButton
import net.pokedex.designsystem.component.EmptyState
import net.pokedex.designsystem.component.ErrorState
import net.pokedex.designsystem.component.NoticeCard
import net.pokedex.designsystem.component.PokedexDialog
import net.pokedex.designsystem.component.ScreenScaffold
import net.pokedex.designsystem.component.ScreenSection
import net.pokedex.designsystem.component.SettingRow
import net.pokedex.designsystem.component.SkeletonBox
import net.pokedex.designsystem.theme.PokedexTheme

@Composable
internal fun RestoreDestination(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onPickFile: (Uri) -> Unit,
    viewModel: RestoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RestoreScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, onDone = onDone, onPickFile = onPickFile)
}

@Composable
internal fun RestoreScreen(
    state: RestoreUiState,
    onEvent: (RestoreEvent) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onPickFile: (Uri) -> Unit,
) {
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree?.let { onEvent(RestoreEvent.FolderPicked(it)) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPickFile)
    }
    // From a preview, back returns to the list it was picked from rather than leaving.
    val backToList = state.phase is RestorePhase.Preview && !state.fromFile
    BackHandler(enabled = backToList) { onEvent(RestoreEvent.BackToList) }

    ScreenScaffold(
        title = "Restore",
        onBack = { if (backToList) onEvent(RestoreEvent.BackToList) else onBack() },
    ) {
        when (val phase = state.phase) {
            RestorePhase.Loading, RestorePhase.Working -> RowsSkeleton()
            is RestorePhase.Choose -> ChooseBackup(
                phase = phase,
                onEvent = onEvent,
                onPickFolder = { pickFolder.launch(null) },
                onPickFile = { pickFile.launch(IMPORT_TYPES) },
            )
            is RestorePhase.Preview -> PreviewContent(phase = phase, onEvent = onEvent)
            is RestorePhase.Refused -> ErrorState(
                title = backupErrorTitle(phase.error),
                body = backupErrorBody(phase.error),
                actionLabel = if (state.fromFile) "Back" else "Back to the list",
                action = if (state.fromFile) onBack else ({ onEvent(RestoreEvent.BackToList) }),
            )
            is RestorePhase.Done -> DoneContent(phase = phase, onDone = onDone)
        }
    }

    if (state.confirmReplace) {
        val preview = (state.phase as? RestorePhase.Preview)?.preview
        PokedexDialog(
            onDismissRequest = { onEvent(RestoreEvent.CancelReplace) },
            title = "Replace every record?",
            body = preview?.let(::replaceConsequence).orEmpty() +
                " What is here now is saved to a snapshot first, so this can be undone by restoring it.",
            confirmLabel = "Replace",
            onConfirm = { onEvent(RestoreEvent.ConfirmReplace) },
            destructive = true,
        )
    }
}

@Composable
private fun ColumnScope.ChooseBackup(
    phase: RestorePhase.Choose,
    onEvent: (RestoreEvent) -> Unit,
    onPickFolder: () -> Unit,
    onPickFile: () -> Unit,
) {
    if (!phase.destination.survivesUninstall) {
        NoticeCard(
            title = "Looking inside the app",
            body = "After a reinstall, your backups are in the folder you picked before. Pick it again to see them.",
            actionLabel = "Pick the backup folder",
            onAction = onPickFolder,
        )
    }
    if (phase.backups.isEmpty()) {
        EmptyState(
            title = "No backups here",
            body = "This folder has no backups this app wrote. A backup file from elsewhere can still be restored.",
            actionLabel = "Pick a file",
            action = onPickFile,
        )
        return
    }
    ScreenSection(title = "Newest first", body = "Nothing changes until you choose how to restore.") {
        phase.backups.forEach { item ->
            SettingRow(title = item.title, summary = item.summary, onClick = { onEvent(RestoreEvent.Open(item)) })
        }
    }
    ActionButton(label = "Pick a file instead", onClick = onPickFile, primary = false)
}

@Composable
private fun PreviewContent(phase: RestorePhase.Preview, onEvent: (RestoreEvent) -> Unit) {
    val preview = phase.preview
    ScreenSection(title = phase.source) {
        SettingRow(title = recordsLabel(preview.recordCount), summary = caughtLabel(preview.caughtCount))
        SettingRow(title = "Exported", summary = preview.exportedAt?.let(::formatWhen) ?: "Unknown")
        SettingRow(
            title = "Written by",
            summary = "App ${preview.appVersionName}, backup schema ${preview.schema}",
        )
    }
    if (preview.orphanCount > 0) {
        NoticeCard(
            title = "${recordsLabel(preview.orphanCount)} without a slot",
            body = "The current preset has no slot for them. They are restored and kept, not dropped.",
        )
    }
    if (preview.localIsEmpty) {
        ScreenSection(title = "Restore", body = "This install has no records yet, so everything in the file is added.") {
            ActionButton(label = "Restore ${recordsLabel(preview.recordCount)}", onClick = { onEvent(RestoreEvent.Merge) })
        }
        return
    }
    ScreenSection(title = "Merge", body = mergeConsequence(preview)) {
        ActionButton(label = "Merge", onClick = { onEvent(RestoreEvent.Merge) }, enabled = preview.mergeWrites > 0)
    }
    ScreenSection(title = "Replace", body = replaceConsequence(preview)) {
        ActionButton(
            label = "Replace everything",
            onClick = { onEvent(RestoreEvent.AskReplace) },
            primary = false,
            destructive = true,
        )
    }
}

@Composable
private fun DoneContent(phase: RestorePhase.Done, onDone: () -> Unit) {
    NoticeCard(
        title = "Restored",
        body = recordsLabel(phase.written) + " written." +
            (phase.snapshot?.let { " What was here before is saved as \"$it\"." } ?: ""),
    )
    ActionButton(label = "Done", onClick = onDone)
}

/** The list's own shape with the text taken out, so the screen does not jump when it lands. */
@Composable
private fun RowsSkeleton() {
    repeat(SKELETON_ROWS) {
        SkeletonBox(Modifier.fillMaxWidth().height(PokedexTheme.dimens.touchTargetMin))
    }
}

private const val SKELETON_ROWS = 4

private fun mergeConsequence(preview: RestorePreview): String = when (preview.mergeWrites) {
    0 -> "Nothing would change: every record here is as new as the file's, or newer."
    else -> "Writes ${recordsLabel(preview.mergeWrites)}: new to this phone, or changed in the file " +
        "after they were changed here. Nothing here is removed."
}

private fun replaceConsequence(preview: RestorePreview): String {
    val removes = if (preview.replaceRemoves > 0) "removes ${recordsLabel(preview.replaceRemoves)}" else null
    val uncatches = if (preview.replaceUncatches > 0) "un-catches ${preview.replaceUncatches}" else null
    val cost = listOfNotNull(removes, uncatches).joinToString(" and ")
    return if (cost.isEmpty()) {
        "Makes this phone match the file exactly. Nothing here would be lost."
    } else {
        "Makes this phone match the file exactly, which ${cost}."
    }
}
