package net.pokedex.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.pokedex.designsystem.theme.PokedexShapes
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * Sheets and dialogs -- the only two places elevation is allowed.
 *
 * Both wrap the M3 components rather than replacing them. A modal sheet is predictive-back,
 * drag-to-dismiss, window-inset and accessibility behaviour that is genuinely hard to get
 * right and that M3 already has; what we override is the shape, the surface and the handle.
 * Rebuilding them from scratch would be the kind of custom identity that costs more than
 * it shows.
 *
 * This is where the "borders, not elevation" rule stops applying, and deliberately so: a
 * sheet needs to read as floating above the case, and there is exactly one of them on
 * screen, so the render node it costs is affordable in a way thirty tiles' worth is not.
 */

/** A modal bottom sheet in the case's own colours. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokedexBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    title: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = PokedexShapes.sheet,
        containerColor = colors.caseSurface,
        contentColor = colors.onCase,
        scrimColor = colors.scrim,
        tonalElevation = dimens.spaceXxs,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm)) {
            if (title != null) {
                Text(
                    text = title,
                    style = PokedexTheme.text.boxTitle,
                    color = colors.onCase,
                    modifier = Modifier.padding(bottom = dimens.spaceMd),
                )
            }
            content()
        }
    }
}

/**
 * The drag handle.
 *
 * M3's default is a pill in `onSurfaceVariant`. Ours is the same rim colour the whole app
 * draws boundaries in, because a sheet arriving should look like a part of the case sliding
 * out, not like a different widget set.
 */
@Composable
internal fun SheetHandle(modifier: Modifier = Modifier) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dimens.spaceMd),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(HANDLE_WIDTH)
                .height(HANDLE_HEIGHT)
                .clip(PokedexShapes.badge)
                .background(colors.rim),
        )
    }
}

private val HANDLE_WIDTH = 32.dp
private val HANDLE_HEIGHT = 4.dp

/**
 * A dialog.
 *
 * Only for decisions that cannot be undone -- a destructive restore, a preset change that
 * strands records. Everything reversible gets a sheet or just happens, because a dialog for
 * a reversible action trains you to dismiss dialogs without reading them.
 *
 * [destructive] colours the confirm action with the error role, which is the one place
 * error colour appears on a button.
 */
@Composable
fun PokedexDialog(
    onDismissRequest: () -> Unit,
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
) {
    val colors = PokedexTheme.colors
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = PokedexShapes.dialog,
        containerColor = colors.caseSurfaceHigh,
        titleContentColor = colors.onCase,
        textContentColor = colors.onCaseMuted,
        title = { Text(title, style = PokedexTheme.text.boxTitle) },
        text = { Text(body, style = PokedexTheme.text.dexNumber) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    style = PokedexTheme.text.badgeLabel,
                    color = if (destructive) colors.errorText else colors.accentText,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(dismissLabel, style = PokedexTheme.text.badgeLabel, color = colors.onCaseMuted)
            }
        },
    )
}

/**
 * Sheet and dialog *contents*, rendered inline.
 *
 * A real `ModalBottomSheet` cannot be screenshot-tested or shown in a scrolling gallery --
 * it is a window. So the gallery shows what the surfaces look like, and the behaviour is
 * the platform's.
 */
@Composable
internal fun ContainerSamples() {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    Column(
        modifier = Modifier.padding(dimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceLg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PokedexShapes.sheet)
                .background(colors.caseSurface),
        ) {
            SheetHandle()
            Column(modifier = Modifier.padding(dimens.spaceLg)) {
                Text("Where did you catch it?", style = PokedexTheme.text.boxTitle, color = colors.onCase)
                Row(
                    modifier = Modifier.padding(top = dimens.spaceMd),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
                ) {
                    GameBadge("Scarlet")
                    GameBadge("Violet")
                    GameBadge("Legends: Arceus", shinyLocked = true)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PokedexShapes.dialog)
                .background(colors.caseSurfaceHigh)
                .padding(dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
        ) {
            Text("Restore this backup?", style = PokedexTheme.text.boxTitle, color = colors.onCase)
            Text(
                "It will replace all 412 catch records with the 389 in the file.",
                style = PokedexTheme.text.dexNumber,
                color = colors.onCaseMuted,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {}) {
                    Text("Cancel", style = PokedexTheme.text.badgeLabel, color = colors.onCaseMuted)
                }
                TextButton(onClick = {}) {
                    Text("Restore", style = PokedexTheme.text.badgeLabel, color = colors.errorText)
                }
            }
        }
    }
}

@Preview(name = "Containers dark", widthDp = 380)
@Composable
private fun ContainersDarkPreview() {
    PokedexTheme(darkTheme = true) { ContainerSamples() }
}

@Preview(name = "Containers light", widthDp = 380)
@Composable
private fun ContainersLightPreview() {
    PokedexTheme(darkTheme = false) { ContainerSamples() }
}
