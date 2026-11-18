package net.pokedex.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import net.pokedex.designsystem.theme.PokedexColors
import net.pokedex.designsystem.theme.PokedexShapes
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * A surface lit from above: the case's tone falls off from its top edge, and a hairline of
 * light catches that edge and fades out towards both corners.
 *
 * This is the "real depth and light" around the grid, done without elevation. A shadow would
 * be a render node and an offset; this is two brushes allocated once in `drawWithCache` and
 * drawn behind the content, so a pager page wrapped in it costs two rect draws, not a layer.
 *
 * Reserved for the things that frame or feature the collection -- the box case, the next
 * hunt, a trophy -- not for every row. A screen of lit surfaces is a screen of cards again.
 */
@Composable
fun LitSurface(
    modifier: Modifier = Modifier,
    shape: Shape = PokedexShapes.card,
    contentPadding: Dp = PokedexTheme.dimens.spaceLg,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .litBackground(colors, dimens.rimWidth)
            .border(dimens.rimWidth, colors.rim, shape)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * The fall-off and the edge light, as a modifier, for a component that owns its own layout
 * (the box case is a Column). Clip before this; the brushes fill the bounds they are given.
 */
fun Modifier.litBackground(colors: PokedexColors, edge: Dp): Modifier = drawWithCache {
    val fall = Brush.verticalGradient(
        0f to colors.caseSurfaceHigh,
        FALL_OFF to colors.caseSurface,
        1f to colors.caseSurface,
    )
    val light = Brush.horizontalGradient(
        0f to Color.Transparent,
        HALF to colors.caseHighlight,
        1f to Color.Transparent,
    )
    val edgePx = edge.toPx()
    onDrawBehind {
        drawRect(fall)
        drawRect(light, size = Size(size.width, edgePx))
    }
}

private const val FALL_OFF = 0.4f
private const val HALF = 0.5f

@Preview(name = "LitSurface dark", widthDp = 360)
@Composable
private fun LitSurfaceDarkPreview() {
    PokedexTheme(darkTheme = true) { SurfaceSample() }
}

@Preview(name = "LitSurface light", widthDp = 360)
@Composable
private fun LitSurfaceLightPreview() {
    PokedexTheme(darkTheme = false) { SurfaceSample() }
}

@Composable
private fun SurfaceSample() {
    Box(Modifier.padding(PokedexTheme.dimens.spaceLg)) {
        LitSurface { BoxHeader(name = "Paldea 2", caught = 17, total = 30) }
    }
}
