package net.pokedex.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.pokedex.designsystem.icon.PokedexIcons
import net.pokedex.designsystem.theme.PokedexShapes
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * Empty, error and loading.
 *
 * ## Skeletons, not spinners
 *
 * A spinner says "something is happening"; a skeleton says "a box is arriving, and it will
 * be this shape". Since the app has exactly one layout it ever loads into, the skeleton can
 * be the real thing with the content removed -- which means the screen does not jump when
 * the data lands. A spinner would guarantee it does.
 *
 * The pulse honours reduce-motion by construction: [PokedexTheme.motion] reports a skeleton
 * duration of zero, and [SkeletonBox] renders a flat surface instead of animating.
 */

/** One shimmerless skeleton block. The pulse is opacity, not a moving gradient. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier) {
    val colors = PokedexTheme.colors
    val duration = PokedexTheme.motion.skeletonDurationMillis

    val alpha = if (duration == 0) {
        SKELETON_REST
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val animated by transition.animateFloat(
            initialValue = SKELETON_LOW,
            targetValue = SKELETON_HIGH,
            animationSpec = infiniteRepeatable(tween(duration), RepeatMode.Reverse),
            label = "skeletonAlpha",
        )
        animated
    }

    Box(
        modifier = modifier
            .clip(PokedexShapes.slot)
            .alpha(alpha)
            .background(colors.caseSurfaceHigh),
    )
}

private const val SKELETON_LOW = 0.35f
private const val SKELETON_HIGH = 0.75f
private const val SKELETON_REST = 0.5f

/**
 * A box-shaped loading state.
 *
 * Deliberately the same 6x5 geometry as [BoxGrid], so the skeleton and the thing it stands
 * in for occupy identical space.
 */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val dimens = PokedexTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(dimens.slotGutter),
    ) {
        SkeletonBox(Modifier.width(SKELETON_TITLE_WIDTH).height(SKELETON_TITLE_HEIGHT))
        repeat(BOX_ROWS) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.slotGutter)) {
                repeat(BOX_COLUMNS) {
                    SkeletonBox(Modifier.size(dimens.slotMinSize))
                }
            }
        }
    }
}

private val SKELETON_TITLE_WIDTH = 140.dp
private val SKELETON_TITLE_HEIGHT = 20.dp

/**
 * Nothing here.
 *
 * Empty states in this app are almost always the result of a filter, not of an unpopulated
 * database -- the dataset ships with the app. So the default message assumes the user did
 * something, and [action] is how they undo it.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    action: (() -> Unit)? = null,
) {
    StateBlock(
        modifier = modifier,
        icon = PokedexIcons.Search,
        tint = PokedexTheme.colors.onCaseMuted,
        title = title,
        body = body,
        actionLabel = actionLabel,
        action = action,
    )
}

/**
 * Something failed.
 *
 * There is no network in this app, so an error here is a corrupt backup, a failed import
 * or an unreadable dataset -- all of which are worth naming exactly. A generic "Something
 * went wrong" would be a lie about how much we know.
 */
@Composable
fun ErrorState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = "Try again",
    action: (() -> Unit)? = null,
) {
    StateBlock(
        modifier = modifier,
        icon = PokedexIcons.Error,
        tint = PokedexTheme.colors.errorGraphic,
        title = title,
        body = body,
        actionLabel = actionLabel,
        action = action,
    )
}

@Composable
private fun StateBlock(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    body: String?,
    actionLabel: String?,
    action: (() -> Unit)?,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PokedexShapes.card)
            .background(colors.caseSurface)
            .border(dimens.rimWidth, colors.rim, PokedexShapes.card)
            .padding(dimens.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(dimens.iconLg),
        )
        Text(
            text = title,
            style = PokedexTheme.text.statValue,
            color = colors.onCase,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                text = body,
                style = PokedexTheme.text.dexNumber,
                color = colors.onCaseMuted,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && action != null) {
            OutlinedButton(onClick = action, modifier = Modifier.padding(top = dimens.spaceSm)) {
                Text(actionLabel, style = PokedexTheme.text.badgeLabel)
            }
        }
    }
}

@Composable
private fun StateSamples() {
    val dimens = PokedexTheme.dimens
    Column(
        modifier = Modifier.padding(dimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceLg),
    ) {
        EmptyState(
            title = "No slots match",
            body = "Three filters are active. Clearing the game filter would show 412 more.",
            actionLabel = "Clear filters",
            action = {},
        )
        ErrorState(
            title = "That backup is from a newer version",
            body = "It was written by schema 4; this build reads up to 3.",
            action = {},
        )
        LoadingState()
    }
}

@Preview(name = "States dark", widthDp = 360, heightDp = 720)
@Composable
private fun StatesDarkPreview() {
    PokedexTheme(darkTheme = true) { StateSamples() }
}

@Preview(name = "States light", widthDp = 360, heightDp = 720)
@Composable
private fun StatesLightPreview() {
    PokedexTheme(darkTheme = false) { StateSamples() }
}
