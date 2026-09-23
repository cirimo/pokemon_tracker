package net.pokedex.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.pokedex.designsystem.icon.PokedexIcons
import net.pokedex.designsystem.theme.PokedexShapes
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * The single most-used control in the app, and the one that gets disproportionate care.
 *
 * ## Why it is a button and not a Switch
 *
 * A switch says "a setting changed". This says "I caught one", which is an event, and
 * events want a target you can hit without looking. So: a full-width pill at 56dp with the
 * whole pill tappable, rather than a 32dp thumb on the right-hand edge of a row. The
 * difference matters at 2am on the four hundredth reset.
 *
 * ## The undo path
 *
 * There isn't a snackbar, because the control *is* the undo: tapping again reverses it,
 * immediately, in the same place your finger already is, with no timeout to race. A
 * snackbar undo for a one-tap reversible action is a second mechanism that can only be
 * slower than the first. [UndoBar] exists for the case this cannot cover -- a mis-tap in
 * the grid, where the slot you changed may have scrolled away.
 *
 * ## Haptics
 *
 * One confirm-weight tick on the way to caught, a lighter one on the way back. Asymmetric
 * on purpose: catching is the thing worth feeling, un-catching is a correction.
 */
@Composable
fun CaughtToggle(
    caught: Boolean,
    onCaughtChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val motion = PokedexTheme.motion
    val haptics = LocalHapticFeedback.current

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESS_SCALE else 1f,
        animationSpec = motion.interactive(),
        label = "toggleScale",
    )
    val border by animateColorAsState(
        targetValue = when {
            !enabled -> colors.rim
            caught -> colors.rimCaught
            else -> colors.rim
        },
        animationSpec = motion.stateChange(),
        label = "toggleBorder",
    )

    val contentColor = when {
        !enabled -> colors.onCaseMuted
        caught -> colors.accentText
        else -> colors.onCase
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .heightIn(min = TOGGLE_HEIGHT)
            .clip(PokedexShapes.field)
            .background(colors.caseSurface)
            .border(
                width = if (caught) dimens.rimCaughtWidth else dimens.rimWidth,
                color = border,
                shape = PokedexShapes.field,
            )
            .toggleable(
                value = caught,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = { next ->
                    haptics.performHapticFeedback(
                        if (next) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove,
                    )
                    onCaughtChange(next)
                },
            )
            .semantics {
                // The label is the action; the state description is what changed. TalkBack
                // reads "Caught, switch, on" rather than making you infer it from a tick.
                stateDescription = if (caught) "Caught" else "Not caught"
            }
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CaughtIndicator(caught = caught, enabled = enabled)
        Text(
            text = if (caught) "Caught" else "Mark caught",
            style = PokedexTheme.text.statValue,
            color = contentColor,
        )
    }
}

/**
 * The tick, and its box.
 *
 * Gold fill plus a tick glyph, which is two signals rather than one: the same reason the
 * slot marks exist. The box is always drawn, so the control does not change size when it
 * changes state and the label never shifts under your thumb.
 */
@Composable
private fun CaughtIndicator(caught: Boolean, enabled: Boolean) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val motion = PokedexTheme.motion

    val fill by animateColorAsState(
        targetValue = if (caught && enabled) colors.rimCaught else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = motion.stateChange(),
        label = "indicatorFill",
    )
    val tickScale by animateFloatAsState(
        targetValue = if (caught) 1f else 0f,
        animationSpec = motion.stateChange(),
        label = "tickScale",
    )

    Box(
        modifier = Modifier
            .size(INDICATOR_SIZE)
            .clip(INDICATOR_SHAPE)
            .background(fill)
            .border(dimens.rimWidth, if (caught) colors.rimCaught else colors.rim, INDICATOR_SHAPE),
        contentAlignment = Alignment.Center,
    ) {
        if (tickScale > 0f) {
            Icon(
                imageVector = PokedexIcons.Check,
                contentDescription = null,
                tint = colors.case,
                modifier = Modifier
                    .size(dimens.iconMd)
                    .scale(tickScale),
            )
        }
    }
}

/**
 * The undo the toggle cannot provide.
 *
 * Only for a change made somewhere you can no longer see -- a mis-tap in the grid that has
 * since scrolled away. It carries the name of what changed, because "Undo" with no subject
 * is a gamble.
 */
@Composable
fun UndoBar(
    message: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PokedexShapes.field)
            .background(colors.caseSurfaceHigh)
            .border(dimens.rimWidth, colors.rim, PokedexShapes.field)
            .padding(start = dimens.spaceLg, end = dimens.spaceSm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = message, style = PokedexTheme.text.dexNumber, color = colors.onCase)
        TextButton(onClick = onUndo) {
            Text("Undo", style = PokedexTheme.text.badgeLabel, color = colors.accentText)
        }
    }
}

private const val PRESS_SCALE = 0.97f
private val TOGGLE_HEIGHT = 56.dp
private val INDICATOR_SIZE = 28.dp
private val INDICATOR_SHAPE = RoundedCornerShape(8.dp)

@Composable
private fun ToggleSamples() {
    val dimens = PokedexTheme.dimens
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.padding(dimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        CaughtToggle(caught = false, onCaughtChange = {})
        CaughtToggle(caught = true, onCaughtChange = {})
        CaughtToggle(caught = false, onCaughtChange = {}, enabled = false)
        UndoBar(message = "Bulbasaur marked caught", onUndo = {})
    }
}

@Preview(name = "CaughtToggle dark", widthDp = 360)
@Composable
private fun CaughtToggleDarkPreview() {
    PokedexTheme(darkTheme = true) { ToggleSamples() }
}

@Preview(name = "CaughtToggle light", widthDp = 360)
@Composable
private fun CaughtToggleLightPreview() {
    PokedexTheme(darkTheme = false) { ToggleSamples() }
}

@Preview(name = "CaughtToggle 200% font", widthDp = 360, fontScale = 2f)
@Composable
private fun CaughtToggleLargeFontPreview() {
    PokedexTheme(darkTheme = true) { ToggleSamples() }
}
