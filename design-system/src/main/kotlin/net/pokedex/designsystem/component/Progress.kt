package net.pokedex.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * Collection progress, at three scales.
 *
 * All three take two integers rather than a fraction. "29.6% complete" is not what you
 * want to read when you are deciding what to hunt next -- "412 / 1394" is, and the
 * remaining count is the number you actually act on. The fraction is derived here and
 * never stored (see `progressOf` in :core:model, and CLAUDE.md on why a stored counter is
 * a second source of truth).
 *
 * This is one of exactly three places gold is allowed.
 */

/** "412 / 1394". The headline readout. */
@Composable
fun ProgressReadout(
    caught: Int,
    total: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    compact: Boolean = false,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val text = PokedexTheme.text
    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = progressSentence(caught, total, label)
        },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        Text(
            text = caught.toString(),
            style = if (compact) text.titleNumerals else text.displayNumerals,
            color = colors.accentText,
        )
        Text(
            text = "/ $total",
            style = text.dexNumber,
            color = colors.onCaseMuted,
            modifier = Modifier.padding(bottom = if (compact) dimens.spaceXxs else dimens.spaceSm),
        )
        if (label != null) {
            Text(
                text = label,
                style = text.dexNumber,
                color = colors.onCaseMuted,
                modifier = Modifier.padding(bottom = if (compact) dimens.spaceXxs else dimens.spaceSm),
            )
        }
    }
}

/**
 * A ring, for a box or for the whole collection.
 *
 * Drawn with `drawWithCache` rather than M3's `CircularProgressIndicator` because the
 * indicator is an indeterminate-first component with its own motion and its own colour
 * roles, and because this one must sit at 24dp in a box header without turning to mush.
 * The fraction is read inside the draw lambda, so animating it never touches composition.
 */
@Composable
fun ProgressRing(
    caught: Int,
    total: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val target = if (total == 0) 0f else caught.toFloat() / total
    val fraction by animateFloatAsState(target, PokedexTheme.motion.stateChange(), label = "ring")

    val track = colors.progressTrack
    val arc = colors.accentGraphic
    Box(
        modifier = modifier
            .size(dimens.iconLg)
            .clearAndSetSemantics { contentDescription = progressSentence(caught, total, label) }
            .drawWithCache {
                val stroke = Stroke(width = dimens.progressRingStroke.toPx(), cap = StrokeCap.Round)
                val inset = stroke.width / 2f
                val topLeft = Offset(inset, inset)
                val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                onDrawBehind {
                    drawArc(track, 0f, FULL_TURN, false, topLeft, arcSize, style = stroke)
                    drawArc(arc, ARC_START, FULL_TURN * fraction, false, topLeft, arcSize, style = stroke)
                }
            },
    )
}

/** A bar, for a list row or a sheet header. */
@Composable
fun ProgressBar(
    caught: Int,
    total: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val target = if (total == 0) 0f else caught.toFloat() / total
    val fraction by animateFloatAsState(target, PokedexTheme.motion.stateChange(), label = "bar")

    val track = colors.progressTrack
    val fill = colors.accentGraphic
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.progressBarHeight)
            .clearAndSetSemantics { contentDescription = progressSentence(caught, total, label) }
            .drawWithCache {
                val radius = size.height / 2f
                onDrawBehind {
                    drawRoundRect(track, cornerRadius = cornerRadius(radius))
                    drawRoundRect(
                        color = fill,
                        size = Size(size.width * fraction, size.height),
                        cornerRadius = cornerRadius(radius),
                    )
                }
            },
    )
}

private fun cornerRadius(r: Float) = CornerRadius(r, r)

private const val FULL_TURN = 360f
private const val ARC_START = -90f

/**
 * The sentence TalkBack reads for any progress component.
 *
 * Leads with what is left rather than what is done, because "982 to go" is the thing the
 * number is for. A screen reader user gets the same answer a sighted user gets from the
 * length of the arc.
 */
internal fun progressSentence(caught: Int, total: Int, label: String?): String {
    val what = label?.let { "$it: " } ?: ""
    if (total == 0) return "${what}nothing to collect"
    val remaining = total - caught
    if (remaining == 0) return "${what}complete, all $total caught"
    return "$what$caught of $total caught, $remaining to go"
}

@Composable
private fun ProgressSamples() {
    Column(
        modifier = Modifier.padding(PokedexTheme.dimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceLg),
    ) {
        ProgressReadout(caught = 412, total = 1394, label = "shiny")
        Row(
            horizontalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(caught = 21, total = 29, label = "Kanto 1")
            ProgressRing(caught = 29, total = 29, label = "Kanto 2")
            ProgressRing(caught = 0, total = 29, label = "Paldea 4")
            ProgressReadout(caught = 21, total = 29, compact = true)
        }
        ProgressBar(caught = 412, total = 1394, label = "Collection")
        Text(
            text = "0 / 0",
            style = PokedexTheme.text.dexNumber,
            color = PokedexTheme.colors.onCaseMuted,
            textAlign = TextAlign.Start,
        )
        ProgressBar(caught = 0, total = 0, label = "Empty preset")
    }
}

@Preview(name = "Progress dark", widthDp = 320)
@Composable
private fun ProgressDarkPreview() {
    PokedexTheme(darkTheme = true) { ProgressSamples() }
}

@Preview(name = "Progress light", widthDp = 320)
@Composable
private fun ProgressLightPreview() {
    PokedexTheme(darkTheme = false) { ProgressSamples() }
}

@Preview(name = "Progress 200% font", widthDp = 320, fontScale = 2f)
@Composable
private fun ProgressLargeFontPreview() {
    PokedexTheme(darkTheme = true) { ProgressSamples() }
}
