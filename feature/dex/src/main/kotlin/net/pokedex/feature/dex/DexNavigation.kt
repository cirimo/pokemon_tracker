package net.pokedex.feature.dex

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import net.pokedex.core.model.CatchKey
import net.pokedex.feature.dex.boxes.BoxesDestination
import net.pokedex.feature.dex.detail.SlotDetailDestination
import net.pokedex.feature.dex.detail.VariantDetailDestination

/**
 * Type-safe routes, Navigation Compose 2.9 style.
 *
 * Each feature owns its own destinations and exposes one extension on NavGraphBuilder,
 * so :app assembles the graph without knowing any feature internals -- that is what
 * keeps features from having to depend on each other to link to one another.
 *
 * Search is not a route. It is a mode of [BoxesRoute], so the box you were on is still
 * underneath when you close it; see docs/architecture.md, "Navigation".
 */
@Serializable
data object BoxesRoute

/**
 * A slot, by the key its record is stored under. Two primitives rather than a CatchKey,
 * because a custom route type needs a hand-written NavType and these two fields are the
 * whole of it.
 */
@Serializable
data class SlotDetailRoute(val variantId: String, val copyIndex: Int) {
    constructor(key: CatchKey) : this(key.variantId.value, key.copyIndex)
}

@Serializable
data class VariantDetailRoute(val variantId: String)

/** A result a detail screen leaves on the box destination's back stack entry. */
private const val SHOW_BOX_KEY = "showBox"

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.dexGraph(
    navController: NavController,
    sharedTransitionScope: SharedTransitionScope,
    onOpenSettings: () -> Unit,
) {
    val openSlot: (CatchKey) -> Unit = { navController.navigate(SlotDetailRoute(it)) }
    val openVariant: (String) -> Unit = { navController.navigate(VariantDetailRoute(it)) }
    val back: () -> Unit = { navController.popBackStack() }

    composable<BoxesRoute> { entry ->
        CompositionLocalProvider(LocalSpriteTransition provides SpriteTransition(sharedTransitionScope, this)) {
            BoxesDestination(
                onOpenSlot = openSlot,
                onOpenSettings = onOpenSettings,
                jumpRequests = entry.savedStateHandle.getStateFlow<Int?>(SHOW_BOX_KEY, null),
                onJumpForwarded = { entry.savedStateHandle[SHOW_BOX_KEY] = null },
            )
        }
    }
    composable<SlotDetailRoute> {
        CompositionLocalProvider(LocalSpriteTransition provides SpriteTransition(sharedTransitionScope, this)) {
            SlotDetailDestination(
                onBack = back,
                onOpenSlot = openSlot,
                onOpenVariant = openVariant,
                onShowInBox = { boxIndex ->
                    // Leave the request on the box destination's back stack entry, then pop back
                    // to it. Popping rather than navigating keeps one box screen on the stack
                    // however deep the detail trail went.
                    navController.getBackStackEntry<BoxesRoute>().savedStateHandle[SHOW_BOX_KEY] = boxIndex
                    navController.popBackStack<BoxesRoute>(inclusive = false)
                },
            )
        }
    }
    composable<VariantDetailRoute> {
        VariantDetailDestination(onBack = back, onOpenSlot = openSlot, onOpenVariant = openVariant)
    }
}
