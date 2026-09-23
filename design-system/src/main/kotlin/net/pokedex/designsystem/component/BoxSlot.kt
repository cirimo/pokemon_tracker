package net.pokedex.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.pokedex.designsystem.theme.PokedexColors
import net.pokedex.designsystem.theme.PokedexShapes
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * What a slot can be.
 *
 * Five states, and none of them is told apart by colour alone -- see [SlotMark] and the
 * rim width in [BoxSlot]. That constraint is not decoration: in light theme the caught and
 * needed rims sit at 1.58:1 against each other, so if colour were the only signal the grid
 * would be unreadable for a good number of people, and unreadable in sunlight for everyone.
 */
enum class SlotState {
    /** A position the preset leaves empty. grouped-balanced has ten interior holes. */
    Empty,

    /** Wanted, not yet owned. The default state of most of the dex. */
    Needed,

    /** Owned. The only state with gold in it. */
    Caught,

    /** Exists, but no game you can reach lets you get it shiny. Not your fault. */
    ShinyLocked,

    /** Obtainable, but not in a game you own. */
    Unavailable,
}

/** The non-colour half of a slot's state. */
internal enum class SlotMark { None, Pip, Cross, Dash }

internal val SlotState.mark: SlotMark
    get() = when (this) {
        SlotState.Empty, SlotState.Needed -> SlotMark.None
        SlotState.Caught -> SlotMark.Pip
        SlotState.ShinyLocked -> SlotMark.Cross
        SlotState.Unavailable -> SlotMark.Dash
    }

/**
 * How [BoxSlot] wants its sprite drawn.
 *
 * The slot decides, the caller draws. That split exists because sprite *loading* needs
 * Coil and a file path, which are a feature's business, while the silhouette rule is a
 * design-system rule no feature may reinterpret.
 */
@Immutable
data class SlotSpriteRendering(
    val colorFilter: ColorFilter?,
    val alpha: Float,
)

/**
 * A single well in the case. The most performance-sensitive component in the app: thirty
 * of these are on screen at once and they scroll.
 *
 * ## The silhouette rule
 *
 * An uncaught sprite is drawn as a flat silhouette; a caught one is drawn in full shiny
 * colour. This is the decision that makes a 1394-slot collection legible. Only shiny
 * sprites are bundled, so every tile would otherwise be a saturated 28dp thumbnail and a
 * box would read as a contact sheet. Instead a box is quiet until you fill it, colour
 * arriving *is* the progress, and you can see how a box is doing from across the room.
 * It costs one `ColorFilter.tint` -- no layer, no shader.
 *
 * ## Constraints baked in (docs/design-decisions.md)
 *
 *  - border, never elevation -- `Modifier.shadow` forces a render node per tile
 *  - no ripple -- M3's ripple on a 28dp tile is mush; the press morphs the corner instead
 *  - no shader, ever -- AGSL needs API 33 and minSdk is 26
 *  - no type colour, and no text of any kind, inside a tile
 *
 * That last one is what lets the grid survive 200% font scale: there is nothing inside a
 * tile for the system font setting to grow.
 */
@Composable
fun BoxSlot(
    state: SlotState,
    modifier: Modifier = Modifier,
    /** The Pokemon this slot demands. Null only for [SlotState.Empty]. */
    label: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    sprite: @Composable (SlotSpriteRendering) -> Unit = {},
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val motion = PokedexTheme.motion

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val active = pressed && enabled

    val radius by animateDpAsState(
        targetValue = if (active) dimens.slotRadius + PRESS_RADIUS_GAIN else dimens.slotRadius,
        animationSpec = motion.interactive(),
        label = "slotRadius",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) PRESS_SCALE else 1f,
        animationSpec = motion.interactive(),
        label = "slotScale",
    )

    // Only allocate a shape while the tile is actually moving. At rest all thirty tiles
    // share the one cached instance.
    val shape = if (radius == dimens.slotRadius) PokedexShapes.slot else RoundedCornerShape(radius)

    val celebration = remember { Animatable(CELEBRATION_DONE) }
    CatchCelebration(state, motion.reduced, motion.celebration, celebration)

    val rimColor = when (state) {
        SlotState.Caught -> colors.rimCaught
        SlotState.Empty -> Color.Transparent
        else -> colors.rim
    }
    val rimWidth = if (state == SlotState.Caught) dimens.rimCaughtWidth else dimens.rimWidth
    // Captured into a local so the smart cast survives into the modifier lambda.
    val tap = onClick.takeIf { state != SlotState.Empty }

    // Modifier order here is a contract, and two earlier versions got it wrong.
    //
    // The caller's `modifier` must come FIRST, because it is what positions this component
    // in its parent: `Modifier.weight(1f)` from the grid only works on the Row's direct
    // child. An earlier version put the caller's modifier on an inner box to protect the
    // 48dp guarantee, and silently broke weight -- one tile filled the whole row.
    //
    // So the guarantee is the one M3's own components offer, and it is stated rather than
    // pretended: the slot is at least 48dp *unless the caller explicitly sizes it smaller*.
    // Nothing in the app does -- a six-column grid on the narrowest phone we support gives
    // cells over 50dp -- and AccessibilityTest asserts the unconstrained default.
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minWidth = dimens.touchTargetMin, minHeight = dimens.touchTargetMin)
            .aspectRatio(1f)
            .scale(scale)
            .clip(shape)
            .background(if (state == SlotState.Empty) colors.slotVoid else colors.slotWell)
            .border(rimWidth, rimColor, shape)
            .catchSweep(celebration, colors.rimCaught, radius, rimWidth)
            .then(
                if (tap != null) {
                    Modifier.clickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        // Deliberately null. See the no-ripple constraint above.
                        indication = null,
                        onClick = tap,
                    )
                } else {
                    Modifier
                },
            )
            // clearAndSet, not merge: a tile's children are a sprite and a 6dp dot, and
            // TalkBack announcing "image, image" after the sentence helps nobody.
            .clearAndSetSemantics {
                contentDescription = state.describe(label)
                if (tap != null) role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        if (state != SlotState.Empty) {
            sprite(state.spriteRendering(colors))
            SlotMarkGlyph(state.mark, colors, Modifier.fillMaxSize())
        }
    }
}

private const val PRESS_SCALE = 0.94f
private val PRESS_RADIUS_GAIN = 6.dp
private const val CELEBRATION_DONE = 1f
private const val FULL_TURN = 360f
private const val SWEEP_START = 0.00f
private const val SWEEP_PEAK = 0.06f
private const val SWEEP_END = 0.14f
private const val SWEEP_WRAP = 1.00f
private const val SWEEP_STROKE_MULTIPLE = 2f

/**
 * The catch celebration: one sweep of light around the rim, and nothing else.
 *
 * The hard constraint is that this runs four hundred times over the life of the app, so it
 * is built out of what it does *not* do. No overlay, no particles, no sound, no blocked
 * input, nothing outside the tile's own bounds. It is under 300ms and it is interruptible.
 * A celebration you can trigger twice in a second without wincing is the only kind worth
 * having.
 *
 * This is also the single shimmer permitted anywhere in the app. It is a `Brush`, not a
 * shader: `Brush.sweepGradient` is allocated once inside `drawWithCache` and then only
 * rotated, so the per-frame cost is a rotate and one stroked outline.
 */
@Composable
private fun CatchCelebration(
    state: SlotState,
    reduced: Boolean,
    spec: AnimationSpec<Float>,
    celebration: Animatable<Float, *>,
) {
    LaunchedEffect(state, reduced) {
        if (state == SlotState.Caught && !reduced) {
            celebration.snapTo(0f)
            celebration.animateTo(CELEBRATION_DONE, spec)
        } else {
            celebration.snapTo(CELEBRATION_DONE)
        }
    }
}

private fun Modifier.catchSweep(
    celebration: Animatable<Float, *>,
    gold: Color,
    radius: Dp,
    rimWidth: Dp,
) = this.drawWithCache {
    // A short bright arc on an otherwise transparent sweep: the highlight is roughly a
    // fourteenth of the perimeter, which is short enough to read as a glint travelling
    // round the rim rather than as the whole rim lighting up.
    val brush = Brush.sweepGradient(
        SWEEP_START to Color.Transparent,
        SWEEP_PEAK to gold,
        SWEEP_END to Color.Transparent,
        SWEEP_WRAP to Color.Transparent,
    )
    val corner = radius.toPx().let { CornerRadius(it, it) }
    val stroke = Stroke(width = rimWidth.toPx() * SWEEP_STROKE_MULTIPLE)
    onDrawBehind {
        // Read inside the draw lambda, so the animation never invalidates composition.
        val progress = celebration.value
        if (progress < CELEBRATION_DONE) {
            rotate(progress * FULL_TURN) {
                drawRoundRect(brush, cornerRadius = corner, style = stroke, alpha = 1f - progress)
            }
        }
    }
}

/**
 * The corner mark.
 *
 * Drawn rather than iconified on purpose: these are three, four and two vector operations
 * respectively, they need to stay crisp at 6dp, and pulling an icon font into the single
 * hottest component in the app to draw a dash would be a poor trade.
 */
@Composable
internal fun SlotMarkGlyph(mark: SlotMark, colors: PokedexColors, modifier: Modifier = Modifier) {
    if (mark == SlotMark.None) return
    val pip = colors.rimCaught
    val muted = colors.onCaseMuted
    Box(
        modifier = modifier.drawWithCache {
            val inset = size.minDimension * MARK_INSET
            val r = size.minDimension * MARK_RADIUS
            val centre = Offset(size.width - inset - r, inset + r)
            onDrawBehind {
                when (mark) {
                    SlotMark.Pip -> drawCircle(pip, radius = r, center = centre)
                    SlotMark.Cross -> drawCross(centre, r, muted)
                    SlotMark.Dash -> drawLine(
                        color = muted,
                        start = Offset(centre.x - r, centre.y),
                        end = Offset(centre.x + r, centre.y),
                        strokeWidth = r * MARK_STROKE,
                        cap = StrokeCap.Round,
                    )
                    SlotMark.None -> Unit
                }
            }
        },
    )
}

private fun DrawScope.drawCross(centre: Offset, r: Float, color: Color) {
    val w = r * MARK_STROKE
    val topLeft = Offset(centre.x - r, centre.y - r)
    val bottomRight = Offset(centre.x + r, centre.y + r)
    val topRight = Offset(centre.x + r, centre.y - r)
    val bottomLeft = Offset(centre.x - r, centre.y + r)
    drawLine(color, topLeft, bottomRight, w, StrokeCap.Round)
    drawLine(color, topRight, bottomLeft, w, StrokeCap.Round)
}

private const val MARK_INSET = 0.08f
private const val MARK_RADIUS = 0.085f
private const val MARK_STROKE = 0.7f

private fun SlotState.spriteRendering(colors: PokedexColors) = when (this) {
    SlotState.Caught -> SlotSpriteRendering(colorFilter = null, alpha = 1f)
    SlotState.Unavailable -> SlotSpriteRendering(ColorFilter.tint(colors.silhouette), alpha = 0.45f)
    else -> SlotSpriteRendering(ColorFilter.tint(colors.silhouette), alpha = 1f)
}

/**
 * The sentence TalkBack reads.
 *
 * A sentence, not a state name: "Bulbasaur, not yet caught" tells you what you need
 * without requiring you to have learned the app's vocabulary first.
 */
internal fun SlotState.describe(label: String?): String {
    val name = label ?: "Slot"
    return when (this) {
        SlotState.Empty -> "Empty slot"
        SlotState.Needed -> "$name, not yet caught"
        SlotState.Caught -> "$name, shiny caught"
        SlotState.ShinyLocked -> "$name, shiny locked in every game you have"
        SlotState.Unavailable -> "$name, not available in your games"
    }
}

/**
 * A stand-in sprite for previews, the gallery and screenshot tests.
 *
 * Deliberately obeys [SlotSpriteRendering] rather than ignoring it, so the gallery shows
 * the real silhouette-versus-colour behaviour and a screenshot diff would catch it
 * regressing. Real sprites arrive through Coil in a feature module.
 */
@Composable
internal fun DemoSprite(rendering: SlotSpriteRendering, modifier: Modifier = Modifier) {
    val colors = PokedexTheme.colors
    val silhouette = colors.silhouette
    val shiny = if (colors.isDark) DemoShinyDark else DemoShinyLight
    val filtered = rendering.colorFilter != null
    val alpha = rendering.alpha
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(DEMO_SPRITE_INSET)
            .drawWithCache {
                val body = if (filtered) silhouette else shiny
                onDrawBehind {
                    drawCircle(body, radius = size.minDimension / 2f, alpha = alpha)
                }
            },
    )
}

private val DEMO_SPRITE_INSET = 8.dp
private val DemoShinyDark = Color(0xFF7FC8A9)
private val DemoShinyLight = Color(0xFF2E7D5B)

@Composable
private fun SlotStateRow() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.slotGutter),
        modifier = Modifier.padding(PokedexTheme.dimens.spaceMd),
    ) {
        SlotState.entries.forEach { state ->
            BoxSlot(
                state = state,
                label = "Bulbasaur",
                onClick = {},
                modifier = Modifier.size(PREVIEW_SLOT),
                sprite = { DemoSprite(it) },
            )
        }
    }
}

private val PREVIEW_SLOT = 48.dp

@Preview(name = "BoxSlot dark", widthDp = 300, heightDp = 80)
@Composable
private fun BoxSlotDarkPreview() {
    PokedexTheme(darkTheme = true) { SlotStateRow() }
}

@Preview(name = "BoxSlot light", widthDp = 300, heightDp = 80)
@Composable
private fun BoxSlotLightPreview() {
    PokedexTheme(darkTheme = false) { SlotStateRow() }
}
