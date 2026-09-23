package net.pokedex.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing, radii, borders and the grid metrics.
 *
 * Exposed as a plain immutable object rather than through MaterialTheme because these are
 * ours, not M3 roles, and shadowing M3 tokens with different meanings is how a design
 * system becomes unreadable.
 *
 * Every dp in the app comes from here. `config/detekt/feature-rules.yml` makes that a
 * build failure rather than a request.
 */
@Immutable
data class PokedexDimens(
    val spaceXxs: Dp = 2.dp,
    val spaceXs: Dp = 4.dp,
    val spaceSm: Dp = 8.dp,
    val spaceMd: Dp = 12.dp,
    val spaceLg: Dp = 16.dp,
    val spaceXl: Dp = 24.dp,
    val spaceXxl: Dp = 32.dp,

    /** Slots are square-ish at 8dp, not pill-shaped. A departure from M3 Expressive. */
    val slotRadius: Dp = 8.dp,
    val badgeRadius: Dp = 6.dp,
    val cardRadius: Dp = 12.dp,
    val sheetRadius: Dp = 20.dp,
    val fieldRadius: Dp = 10.dp,

    /** Hairline. Borders carry the grid, because per-tile shadow is the frame killer. */
    val rimWidth: Dp = 1.dp,

    /**
     * The caught rim is thicker, not just gold.
     *
     * In light theme the caught and needed rims sit at 1.58:1 against each other, so
     * width is doing real work here -- it is one of the three non-colour signals that
     * distinguish a filled slot.
     */
    val rimCaughtWidth: Dp = 2.dp,
    val focusRingWidth: Dp = 2.dp,

    /**
     * The smallest a slot may be drawn.
     *
     * Six columns at 44dp plus gutters is 296dp, which fits the narrowest phone we
     * support. The grid never scales with font size -- see [PokedexDimens] usage in
     * BoxGrid -- so this number is what guarantees the 6x5 box survives 200% text.
     */
    val slotMinSize: Dp = 44.dp,
    val slotGutter: Dp = 4.dp,

    /** Android's minimum, and non-negotiable for anything tappable. */
    val touchTargetMin: Dp = 48.dp,

    val progressRingStroke: Dp = 4.dp,
    val progressBarHeight: Dp = 6.dp,
    val iconSm: Dp = 16.dp,
    val iconMd: Dp = 20.dp,
    val iconLg: Dp = 24.dp,
)

/** Named object, not an object expression: a public `val x = object {}` types as Any. */
object PokedexShapes {
    val slot = RoundedCornerShape(8.dp)
    val badge = RoundedCornerShape(6.dp)
    val card = RoundedCornerShape(12.dp)
    val field = RoundedCornerShape(10.dp)
    val sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    val dialog = RoundedCornerShape(16.dp)

    /**
     * A slot at rest and a slot held down.
     *
     * The press state morphs the corner rather than dimming the tile -- M3 Expressive's
     * one genuinely good idea, and the reason the grid needs no ripple.
     */
    val slotPressed = RoundedCornerShape(14.dp)
}

/**
 * Elevation, and the short list of places it is allowed.
 *
 * `Modifier.shadow` forces a `graphicsLayer`, which means a render node per element. At
 * thirty visible tiles that is the frame budget gone, which is why the grid draws borders
 * instead. Nothing in this object is sized for a tile, and that is deliberate: there is no
 * token here a grid component could reach for.
 */
@Immutable
data class PokedexElevation(
    val sheet: Dp = 8.dp,
    val dialog: Dp = 12.dp,
    val menu: Dp = 6.dp,
)
