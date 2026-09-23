package net.pokedex.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.pokedex.designsystem.theme.PokedexShapes
import net.pokedex.designsystem.theme.PokedexTheme

/** One position in a box. Deliberately free of any domain type -- see the module rule. */
@Immutable
data class BoxSlotItem(
    val state: SlotState,
    val label: String? = null,
    /** Stable identity for the lazy grid and for the shared element. */
    val key: String,
)

/**
 * The 6x5 box.
 *
 * ## Why this is NOT a lazy grid
 *
 * It was a `LazyVerticalGrid` briefly, and that was a mistake worth recording.
 *
 * A box is thirty tiles, so laziness buys nothing -- all thirty are on screen at once, and
 * the app never puts 1394 tiles in one scroller anyway; it puts thirty in a pager. What
 * laziness *costs* is composability: a lazy layout throws `IllegalStateException:
 * Vertically scrollable component was measured with an infinity maximum height constraints`
 * the moment it is placed inside a `Column(Modifier.verticalScroll())`.
 *
 * That is exactly where a feature screen wants to put it -- header, grid and notes
 * scrolling together -- and it is where the gallery put it, which is how the crash was
 * found. The screenshot tests missed it because they handed the grid bounded height;
 * `BoxGridScrollingParentTest` now covers the case they did not.
 *
 * A plain Column of Rows has none of that. It composes all thirty tiles eagerly, which was
 * going to happen regardless, and it drops into any parent.
 *
 * ## Fixed columns, on purpose
 *
 * Six, never adaptive. A HOME box *is* six across; a grid that reflows to five columns on a
 * narrow screen stops being a mirror of the boxes, which is the whole premise of the app
 * (docs/00-big-picture.md). Tiles shrink instead, down to `slotMinSize`.
 *
 * The grid never scales with font size, because tiles contain no text. That is what makes
 * the 200% font-scale requirement survivable.
 */
@Composable
fun BoxGrid(
    slots: List<BoxSlotItem>,
    modifier: Modifier = Modifier,
    onSlotClick: ((Int) -> Unit)? = null,
    sprite: @Composable (BoxSlotItem, SlotSpriteRendering) -> Unit = { _, _ -> },
) {
    val dimens = PokedexTheme.dimens
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.slotGutter),
    ) {
        slots.chunked(BOX_COLUMNS).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.slotGutter)) {
                row.forEachIndexed { columnIndex, item ->
                    val index = rowIndex * BOX_COLUMNS + columnIndex
                    // Keyed so a tile's catch animation stays attached to its slot when the
                    // pager moves -- the one thing the lazy version did give us for free.
                    key(item.key) {
                        BoxSlot(
                            state = item.state,
                            label = item.label,
                            onClick = onSlotClick?.let { { it(index) } },
                            modifier = Modifier.weight(1f),
                            sprite = { rendering -> sprite(item, rendering) },
                        )
                    }
                }
                // Keeps a short last row's tiles the same width as every other row's.
                repeat(BOX_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

const val BOX_COLUMNS = 6
const val BOX_ROWS = 5

/**
 * The box header: name, count, and the completion moment.
 *
 * The header is where the craft budget goes. Thirty tiles decorated individually is a
 * contact sheet; thirty plain tiles under a header that knows how the box is doing is an
 * object. That is the whole trick, and it is also why the tiles stayed cheap.
 */
@Composable
fun BoxHeader(
    name: String,
    caught: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val complete = total > 0 && caught == total

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = PokedexTheme.text.boxTitle,
                color = colors.onCase,
                modifier = Modifier.clearAndSetSemantics { contentDescription = name },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProgressReadout(caught = caught, total = total, compact = true)
                ProgressRing(caught = caught, total = total, label = name)
            }
        }
        CompletionRule(complete = complete)
    }
}

/**
 * The second signature moment: a box completing.
 *
 * A gold rule draws itself left to right, once, over half a second, and then simply stays.
 * It fires only on the *transition* into complete, never on arriving at an already-complete
 * box -- otherwise every swipe through a finished region would be a parade.
 *
 * Like the catch sweep it is a draw-phase animation: the width is read inside the draw
 * lambda, so completing a box recomposes nothing.
 */
@Composable
private fun CompletionRule(complete: Boolean) {
    val colors = PokedexTheme.colors
    val motion = PokedexTheme.motion
    val progress = remember { Animatable(if (complete) 1f else 0f) }

    LaunchedEffect(complete) {
        if (complete) {
            if (progress.value < 1f) progress.animateTo(1f, motion.flourish) else progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
        }
    }

    val gold = colors.accentGraphic
    val rim = colors.rim
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PokedexTheme.dimens.spaceSm)
            .height(RULE_HEIGHT)
            .drawWithCache {
                onDrawBehind {
                    drawRect(rim, size = Size(size.width, size.height))
                    drawRect(gold, size = Size(size.width * progress.value, size.height))
                }
            },
    )
}

private val RULE_HEIGHT = 2.dp

/**
 * The case edge.
 *
 * A box sits inside this rather than floating on the background. It is one rounded border
 * and one surface -- no shadow, because the grid rule applies to whatever contains the
 * grid too -- and it is what makes a screen of tiles read as a thing you are holding.
 */
@Composable
fun CaseSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    Column(
        modifier = modifier
            .clip(PokedexShapes.card)
            .background(colors.caseSurface)
            .border(dimens.rimWidth, colors.rim, PokedexShapes.card)
            .padding(dimens.spaceMd),
    ) {
        content()
    }
}

private const val DEMO_CAUGHT = 21
private const val DEMO_HOLE = 29
private const val DEMO_UNAVAILABLE = 15
private val DEMO_LOCKED = setOf(7, 22)

/** A demo box: 29 filled positions and one interior hole, the shape grouped-balanced has. */
internal fun demoBox(caughtCount: Int = DEMO_CAUGHT): List<BoxSlotItem> = List(BOX_COLUMNS * BOX_ROWS) { i ->
    val state = when {
        i == DEMO_HOLE -> SlotState.Empty
        i in DEMO_LOCKED -> SlotState.ShinyLocked
        i == DEMO_UNAVAILABLE -> SlotState.Unavailable
        i < caughtCount -> SlotState.Caught
        else -> SlotState.Needed
    }
    BoxSlotItem(state = state, label = "Slot ${i + 1}", key = "demo-$i")
}

@Composable
private fun BoxSample(caught: Int) {
    val dimens = PokedexTheme.dimens
    val slots = demoBox(caught)
    val filled = slots.count { it.state != SlotState.Empty }
    Box(modifier = Modifier.padding(dimens.spaceLg)) {
        CaseSurface {
            BoxHeader(name = "Kanto 1", caught = slots.count { it.state == SlotState.Caught }, total = filled)
            Box(modifier = Modifier.padding(top = dimens.spaceMd)) {
                BoxGrid(slots = slots, onSlotClick = {}, sprite = { _, r -> DemoSprite(r) })
            }
        }
    }
}

@Preview(name = "BoxGrid dark", widthDp = 380, heightDp = 400)
@Composable
private fun BoxGridDarkPreview() {
    PokedexTheme(darkTheme = true) { BoxSample(caught = DEMO_CAUGHT) }
}

@Preview(name = "BoxGrid light", widthDp = 380, heightDp = 400)
@Composable
private fun BoxGridLightPreview() {
    PokedexTheme(darkTheme = false) { BoxSample(caught = DEMO_CAUGHT) }
}

@Preview(name = "BoxGrid complete", widthDp = 380, heightDp = 400)
@Composable
private fun BoxGridCompletePreview() {
    PokedexTheme(darkTheme = true) { BoxSample(caught = BOX_COLUMNS * BOX_ROWS) }
}

@Preview(name = "BoxGrid 200% font", widthDp = 380, heightDp = 460, fontScale = 2f)
@Composable
private fun BoxGridLargeFontPreview() {
    PokedexTheme(darkTheme = true) { BoxSample(caught = DEMO_CAUGHT) }
}
