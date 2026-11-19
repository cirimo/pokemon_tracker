package net.pokedex.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.pokedex.designsystem.theme.PokedexTheme

/*
 * The collection, drawn as a collection.
 *
 * Progress used to report the collection in rows of numbers. These components show it: every
 * box as a plate of thirty dots in HOME order, a region's boxes on one shelf, the latest
 * catches as trophies, and the whole dex as one arc. Each plate is one Canvas -- thirty dots
 * are thirty draw calls, not thirty composables -- so 52 plates cost what one box costs.
 *
 * Gold here is what gold is everywhere: a caught shiny (a dot, a trophy rim) or a progress
 * graphic (the arc, a full box's rim). A regular is a filled dot in the regular silhouette's
 * grey, a needed slot is a hollow ring in the rim colour -- shape, not only colour, tells it
 * from a caught one -- and an empty position draws nothing.
 */

/** One box, as far as the collection views need it. */
@Immutable
data class BoxTally(val name: String, val caught: Int, val total: Int) {
    val complete: Boolean get() = total > 0 && caught == total
}

/**
 * One box with its slot states in position order (thirty entries, [SlotState.Empty] for the
 * preset's holes), so a plate puts each dot where the slot sits in HOME.
 */
@Immutable
data class ShelfBox(val boxIndex: Int, val tally: BoxTally, val cells: List<SlotState>)

/**
 * Every box as one bar: its height is how full the box is, a complete box is a full gold bar,
 * and the box on screen carries a rim. The whole collection at a glance, above the pager.
 *
 * [current] is read in the draw phase, so paging through boxes redraws the strip without
 * recomposing it. A tap jumps to the box under the finger; TalkBack users have the stepper
 * and "All boxes", so the strip itself is one spoken summary rather than 52 targets.
 */
@Composable
fun CollectionStrip(
    boxes: List<BoxTally>,
    current: () -> Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val complete = boxes.count { it.complete }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.touchTargetMin)
            .clearAndSetSemantics {
                contentDescription = "Every box: $complete of ${boxes.size} complete"
            }
            .pointerInput(boxes.size) {
                if (boxes.isEmpty()) return@pointerInput
                detectTapGestures { offset ->
                    val index = (offset.x / size.width * boxes.size).toInt().coerceIn(0, boxes.lastIndex)
                    onSelect(index)
                }
            }
            .padding(vertical = dimens.spaceSm)
            .drawWithCache {
                val gap = STRIP_GAP.toPx()
                val width = (size.width - gap * (boxes.size - 1)) / boxes.size
                val corner = CornerRadius(STRIP_CORNER.toPx())
                val ring = Stroke(dimens.focusRingWidth.toPx() * HALF)
                val reach = dimens.spaceXs.toPx()
                onDrawBehind {
                    // Empty for the frame before the boxes load, and on a configuration change.
                    if (boxes.isEmpty()) return@onDrawBehind
                    boxes.forEachIndexed { i, box ->
                        val x = i * (width + gap)
                        val filled = if (box.total == 0) 0f else size.height * box.caught / box.total
                        drawRoundRect(colors.progressTrack, Offset(x, 0f), Size(width, size.height), corner)
                        drawRoundRect(
                            colors.accentGraphic,
                            Offset(x, size.height - filled),
                            Size(width, filled),
                            corner
                        )
                    }
                    val x = current().coerceIn(0, boxes.lastIndex) * (width + gap)
                    drawRoundRect(
                        colors.onCase,
                        Offset(x - reach / 2, -reach),
                        Size(width + reach, size.height + reach * 2),
                        corner,
                        style = ring,
                    )
                }
            },
    )
}

/** A box as a plate of dots in slot order. One Canvas, whatever the box holds. */
@Composable
fun BoxPlate(
    box: ShelfBox,
    modifier: Modifier = Modifier,
    current: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val shape = RoundedCornerShape(dimens.slotRadius)
    val rim = when {
        box.tally.complete -> colors.rimCaught
        current -> colors.rimFocus
        else -> colors.rim
    }
    Column(
        modifier = modifier
            .width(PLATE_WIDTH)
            .clip(shape)
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
            )
            .clearAndSetSemantics {
                contentDescription = progressSentence(box.tally.caught, box.tally.total, box.tally.name)
            },
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(PLATE_HEIGHT)
                .clip(shape)
                .background(colors.slotWell)
                .border(if (box.tally.complete) dimens.rimCaughtWidth else dimens.rimWidth, rim, shape),
        ) {
            val pad = PLATE_PADDING.toPx()
            val cellW = (size.width - pad * 2) / BOX_COLUMNS
            val cellH = (size.height - pad * 2) / BOX_ROWS
            val radius = minOf(cellW, cellH) * DOT_SCALE
            val hollow = Stroke(width = dimens.rimWidth.toPx())
            box.cells.forEachIndexed { i, state ->
                val centre = Offset(pad + cellW * (i % BOX_COLUMNS + HALF), pad + cellH * (i / BOX_COLUMNS + HALF))
                when (state) {
                    SlotState.Empty -> Unit
                    SlotState.Caught -> drawCircle(colors.accentGraphic, radius, centre)
                    SlotState.Regular -> drawCircle(colors.silhouetteRegular, radius, centre)
                    // Hollow, not just dim: a needed dot differs from a caught one in shape too.
                    SlotState.Needed, SlotState.ShinyLocked, SlotState.Unavailable ->
                        drawCircle(colors.rim, radius - hollow.width / 2, centre, style = hollow)
                }
            }
        }
        Text(
            text = box.tally.name,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onCaseMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A region's boxes on one shelf, under its name and count. When the last box of the region
 * fills, the gold rule under the shelf draws itself once: a generation's acknowledgement,
 * the same gesture as a box's and the same size, never a parade on arrival.
 */
@Composable
fun CollectionShelf(
    title: String,
    caught: Int,
    total: Int,
    boxes: List<ShelfBox>,
    modifier: Modifier = Modifier,
    currentBox: Int? = null,
    onBoxClick: ((Int) -> Unit)? = null,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = title,
                style = PokedexTheme.text.sectionTitle,
                color = colors.onCase,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            // No label: the region's name is the heading beside it.
            ProgressReadout(caught = caught, total = total, compact = true)
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
        ) {
            boxes.forEach { box ->
                BoxPlate(
                    box = box,
                    current = box.boxIndex == currentBox,
                    onClick = onBoxClick?.let { { it(box.boxIndex) } },
                )
            }
        }
        CompletionRule(complete = total > 0 && caught == total)
    }
}

/**
 * A recent catch on its own small plinth: the slot well at trophy size, the caught rim and
 * pip, the sprite in colour, and its name and day under it.
 */
@Composable
fun TrophyTile(
    name: String,
    caption: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    sprite: @Composable (SlotSpriteRendering) -> Unit = {},
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val shape = RoundedCornerShape(dimens.cardRadius)
    // No clip on the column: the corner radius would bite the first letter off a caption's
    // last line. The well carries the shape; the ripple is the tile's rectangle.
    Column(
        modifier = modifier
            .width(TROPHY_SIZE)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .clearAndSetSemantics { contentDescription = "$name, caught $caption" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXxs),
    ) {
        Box(
            Modifier
                .size(TROPHY_SIZE)
                .clip(shape)
                .drawWithCache {
                    val stand = Brush.verticalGradient(0f to colors.slotWell, 1f to colors.caseSurfaceHigh)
                    onDrawBehind { drawRect(stand) }
                }
                .border(dimens.rimCaughtWidth, colors.rimCaught, shape),
        ) {
            Box(Modifier.fillMaxSize().padding(dimens.spaceSm)) { sprite(SlotState.Caught.spriteRendering(colors)) }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(dimens.spaceSm)
                    .size(PIP_SIZE)
                    .clip(CircleShape)
                    .background(colors.accentGraphic),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onCase,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onCaseMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The whole collection as one trophy: a three-quarter arc with the count inside it, in the
 * largest type in the app. The arc animates in the draw phase.
 */
@Composable
fun CollectionTrophy(
    caught: Int,
    total: Int,
    modifier: Modifier = Modifier,
    label: String = "shiny",
    /** One muted line under the count -- the living dex, never a second gold figure. */
    detail: String? = null,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val target = if (total == 0) 0f else caught.toFloat() / total
    val fraction by animateFloatAsState(target, PokedexTheme.motion.stateChange(), label = "trophy")
    // The count and its label sit inside the arc, so the arc grows with the text rather than
    // the text spilling over it at a large font scale, up to the width it is given.
    val grow = LocalDensity.current.fontScale.coerceIn(1f, TROPHY_MAX_GROW)
    Box(
        modifier = modifier
            .widthIn(max = TROPHY_ARC_SIZE * grow)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(progressSentence(caught, total, label), detail).joinToString(". ")
            }
            .drawWithCache {
                val stroke = Stroke(width = TROPHY_STROKE.toPx(), cap = StrokeCap.Round)
                val inset = stroke.width / 2
                val topLeft = Offset(inset, inset)
                val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                onDrawBehind {
                    drawArc(colors.progressTrack, ARC_FROM, ARC_SWEEP, false, topLeft, arcSize, style = stroke)
                    drawArc(
                        colors.accentGraphic,
                        ARC_FROM,
                        ARC_SWEEP * fraction,
                        false,
                        topLeft,
                        arcSize,
                        style = stroke
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = caught.toString(), style = PokedexTheme.text.heroNumerals, color = colors.accentText)
            Text(text = "of $total $label", style = MaterialTheme.typography.titleMedium, color = colors.onCaseMuted)
            if (detail != null) {
                Spacer(Modifier.height(dimens.spaceXxs))
                Text(text = detail, style = MaterialTheme.typography.labelMedium, color = colors.onCaseMuted)
            }
        }
    }
}

private val STRIP_GAP = 2.dp
private val STRIP_CORNER = 1.5.dp
private val PLATE_WIDTH = 64.dp
private val PLATE_HEIGHT = 54.dp
private val PLATE_PADDING = 6.dp
private val TROPHY_SIZE = 96.dp
private val PIP_SIZE = 8.dp
private val TROPHY_ARC_SIZE = 232.dp
private val TROPHY_STROKE = 10.dp
private const val TROPHY_MAX_GROW = 2f
private const val DOT_SCALE = 0.32f
private const val HALF = 0.5f
private const val ARC_FROM = 135f
private const val ARC_SWEEP = 270f

/** A demo region: two full boxes, one a slot short, a short box part-filled, an empty one. */
private val DEMO_CAUGHT = listOf(30, 30, 29, 20, 0)
private const val DEMO_SHORT_BOX = 3
private const val DEMO_SHORT_TOTAL = 24

internal fun demoShelf(): List<ShelfBox> = DEMO_CAUGHT.mapIndexed { i, caught ->
    val total = if (i == DEMO_SHORT_BOX) DEMO_SHORT_TOTAL else BOX_COLUMNS * BOX_ROWS
    ShelfBox(
        boxIndex = i,
        tally = BoxTally("Kanto ${i + 1}", caught, total),
        cells = List(BOX_COLUMNS * BOX_ROWS) { cell ->
            when {
                cell >= total -> SlotState.Empty
                cell < caught -> SlotState.Caught
                cell == caught -> SlotState.Regular
                else -> SlotState.Needed
            }
        },
    )
}

@Composable
internal fun CollectionSamples() {
    val dimens = PokedexTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceLg)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CollectionTrophy(caught = 766, total = 1394, detail = "Living dex 768")
        }
        CollectionStrip(
            boxes = List(52) { i -> BoxTally("Box ${i + 1}", (i * 7) % 31, 30) },
            current = { 48 },
            onSelect = {},
        )
        val shelf = demoShelf()
        CollectionShelf(
            title = "Kanto",
            caught = shelf.sumOf { it.tally.caught },
            total = shelf.sumOf { it.tally.total },
            boxes = shelf,
            currentBox = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
            TrophyTile(name = "Unown D", caption = "15 Nov") { DemoSprite(it) }
            TrophyTile(name = "Probopass", caption = "15 Nov") { DemoSprite(it) }
        }
    }
}

@Preview(name = "Collection dark", widthDp = 360, heightDp = 900)
@Composable
private fun CollectionDarkPreview() {
    PokedexTheme(darkTheme = true) { CollectionSamples() }
}

@Preview(name = "Collection light", widthDp = 360, heightDp = 900)
@Composable
private fun CollectionLightPreview() {
    PokedexTheme(darkTheme = false) { CollectionSamples() }
}
