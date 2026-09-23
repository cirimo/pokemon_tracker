package net.pokedex.feature.dex

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import net.pokedex.designsystem.component.SlotSpriteRendering
import net.pokedex.designsystem.component.slotSpriteDestination
import net.pokedex.designsystem.component.slotSpriteOrigin
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * A bundled shiny sprite, drawn the way the slot says.
 *
 * [rendering] is not optional and not reinterpreted: it carries the silhouette rule from
 * `:design-system`, and the only job here is loading the file. `file` is the dataset's
 * asset-relative path, e.g. "sprites/0003-f.webp".
 */
@Composable
internal fun DexSprite(
    file: String,
    rendering: SlotSpriteRendering,
    modifier: Modifier = Modifier,
) {
    val model = remember(file) { "$ASSET_ROOT$file" }
    AsyncImage(
        model = model,
        // The slot around the sprite speaks for it. A sprite with its own description would
        // be announced as a second, redundant node.
        contentDescription = null,
        colorFilter = rendering.colorFilter,
        alpha = rendering.alpha,
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxSize().padding(PokedexTheme.dimens.spaceXs),
    )
}

private const val ASSET_ROOT = "file:///android_asset/"

/**
 * The two scopes a shared element needs, handed down by the destination that owns them.
 *
 * A composition local rather than parameters because the sprite that needs them sits four
 * components deep (pager, grid, slot, sprite lambda) and none of the layers in between
 * should know shared elements exist. Null in previews and anywhere outside the nav graph,
 * where the modifiers below quietly do nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Immutable
internal class SpriteTransition(
    val shared: SharedTransitionScope,
    val animated: AnimatedVisibilityScope,
)

internal val LocalSpriteTransition = staticCompositionLocalOf<SpriteTransition?> { null }

/** The grid or search-row side of the slot-to-detail transition. [slotKey] is a CatchKey string. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.slotOrigin(slotKey: String): Modifier {
    val transition = LocalSpriteTransition.current ?: return this
    return with(transition.shared) { slotSpriteOrigin(transition.animated, slotKey, this@slotOrigin) }
}

/** The detail-hero side. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.slotDestination(slotKey: String): Modifier {
    val transition = LocalSpriteTransition.current ?: return this
    return with(transition.shared) { slotSpriteDestination(transition.animated, slotKey, this@slotDestination) }
}
