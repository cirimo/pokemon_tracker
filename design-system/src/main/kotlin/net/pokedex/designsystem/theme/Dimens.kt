package net.pokedex.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing, radii and the grid metrics.
 *
 * Exposed as a plain immutable object rather than through MaterialTheme because these
 * are ours, not M3 roles, and shadowing M3 tokens with different meanings is how a
 * design system becomes unreadable.
 */
@Immutable
data class PokedexDimens(
    val spaceXs: Dp = 4.dp,
    val spaceSm: Dp = 8.dp,
    val spaceMd: Dp = 12.dp,
    val spaceLg: Dp = 16.dp,
    val spaceXl: Dp = 24.dp,

    /** Slots are square-ish at 8dp, not pill-shaped. A departure from Expressive. */
    val slotRadius: Dp = 8.dp,
    val cardRadius: Dp = 12.dp,
    val sheetRadius: Dp = 20.dp,

    /** Hairline. Borders carry the grid, because per-tile shadow is the frame killer. */
    val rimWidth: Dp = 1.dp,
    val slotMinSize: Dp = 44.dp,
)

/** Named object, not an object expression: a public `val x = object {}` types as Any. */
object PokedexShapes {
    val slot = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(12.dp)
    val sheet = RoundedCornerShape(20.dp)
}
