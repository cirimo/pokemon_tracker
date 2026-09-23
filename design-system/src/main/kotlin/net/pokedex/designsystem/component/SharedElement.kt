package net.pokedex.designsystem.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * The third signature moment: a slot opening into its detail view.
 *
 * ## Why this lives here and not in a feature
 *
 * A shared element needs the *same key* on both sides of a navigation. If each feature
 * invented its own key format, the transition would silently do nothing the first time two
 * screens disagreed -- and "silently does nothing" is the worst possible failure mode for
 * an animation, because it looks like it was never built. So the key scheme is owned here,
 * takes a slot key, and there is no other way to spell it.
 *
 * ## Why a slot key and not a variant id
 *
 * It took a variant id until M2 wired it to a real screen. Seven variants are demanded
 * twice in grouped-balanced, so a search listing both Unown-A slots put two sprites with
 * the same key in one transition scope -- which the API does not reject, it just animates
 * the wrong one. The key is now `:core:model`'s `CatchKey.toString()`, which is unique per
 * slot by construction (`"unown"` and `"unown#1"`).
 *
 * ## Exactly one
 *
 * Compose's own guidance is that shared elements are costly and that too many of them hurt
 * frame rate on low-end devices; the API is still experimental. So: one shared element per
 * navigation, on the sprite, and nothing else. The name, the types and the badges are not
 * worth a render node each to slide four hundred pixels.
 *
 * ## Reduce motion
 *
 * When the user has animations off, [PokedexTheme.motion] reports `reduced` and these
 * modifiers become no-ops -- the detail screen simply appears. A shared element that
 * ignores the system setting is worse than no shared element.
 */

/** The one key format. [slotKey] is `:core:model`'s `CatchKey.toString()`. */
fun slotSharedElementKey(slotKey: String): String = "slot-sprite-$slotKey"

/**
 * The origin: a sprite inside a [BoxSlot] in the grid.
 *
 * Call with the slot's key -- `CatchKey.toString()`, never the bare variant id. Both sides must be inside the same
 * `SharedTransitionLayout`, which the app's `NavHost` provides.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.slotSpriteOrigin(
    animatedVisibilityScope: AnimatedVisibilityScope,
    slotKey: String,
    modifier: Modifier = Modifier,
): Modifier = if (PokedexTheme.motion.reduced) {
    modifier
} else {
    modifier.sharedElement(
        sharedContentState = rememberSharedContentState(key = slotSharedElementKey(slotKey)),
        animatedVisibilityScope = animatedVisibilityScope,
    )
}

/** The destination: the same sprite, large, in [SpeciesHeader]. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.slotSpriteDestination(
    animatedVisibilityScope: AnimatedVisibilityScope,
    slotKey: String,
    modifier: Modifier = Modifier,
): Modifier = slotSpriteOrigin(animatedVisibilityScope, slotKey, modifier)
