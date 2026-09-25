package net.pokedex.feature.dex

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import net.pokedex.core.model.Browse
import net.pokedex.core.model.CatchKey
import net.pokedex.feature.dex.boxes.BoxesDestination
import net.pokedex.feature.dex.detail.SlotDetailDestination
import net.pokedex.feature.dex.detail.VariantDetailDestination
import net.pokedex.feature.dex.hunt.HuntDestination
import net.pokedex.feature.dex.progress.ProgressDestination

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
 *
 * [browse] is the list it was opened from, encoded by [Browse.encode]: not the list, the
 * question it answered, which the detail asks again to page through its neighbours. Null for
 * a slot opened on its own. docs/adr/0013-browse-context.md.
 */
@Serializable
data class SlotDetailRoute(val variantId: String, val copyIndex: Int, val browse: String? = null) {
    constructor(key: CatchKey, browse: Browse? = null) :
        this(key.variantId.value, key.copyIndex, browse?.let(Browse::encode))
}

@Serializable
data class VariantDetailRoute(val variantId: String)

/**
 * What to hunt next. [gameId] preselects one of my games; the list otherwise draws on all
 * of them.
 */
@Serializable
data class HuntRoute(val gameId: String? = null)

/** The dashboards: overall, by region, by game, and recent catches. */
@Serializable
data object ProgressRoute

/** A result a detail screen leaves on the box destination's back stack entry. */
private const val SHOW_BOX_KEY = "showBox"

/** The game pair Progress asked to see the needed slots of; the box view opens search on it. */
private const val SHOW_NEEDED_KEY = "showNeeded"

/**
 * The slot a browsing detail came to rest on, as a CatchKey string, left on the entry that
 * opened it. The list reads it on the way back so it lands on that slot and the sprite flies
 * home to it rather than to the one first tapped.
 */
private const val BROWSED_TO_KEY = "browsedTo"

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.dexGraph(
    navController: NavController,
    sharedTransitionScope: SharedTransitionScope,
    onOpenSettings: () -> Unit,
    onOpenMyGames: () -> Unit,
) {
    val openSlot: (CatchKey) -> Unit = { navController.navigate(SlotDetailRoute(it)) }
    val browseSlot: (CatchKey, Browse) -> Unit = { key, browse -> navController.navigate(SlotDetailRoute(key, browse)) }
    val openVariant: (String) -> Unit = { navController.navigate(VariantDetailRoute(it)) }
    val back: () -> Unit = { navController.popBackStack() }

    // Leave the request on the box destination's back stack entry, then pop back to it.
    // Popping rather than navigating keeps one box screen on the stack however deep the
    // trail went.
    fun returnToBoxes(key: String, value: Any) {
        navController.getBackStackEntry<BoxesRoute>().savedStateHandle[key] = value
        navController.popBackStack<BoxesRoute>(inclusive = false)
    }

    composable<BoxesRoute> { entry ->
        CompositionLocalProvider(LocalSpriteTransition provides SpriteTransition(sharedTransitionScope, this)) {
            BoxesDestination(
                onOpenSlot = browseSlot,
                onOpenSettings = onOpenSettings,
                onOpenProgress = { navController.navigate(ProgressRoute) },
                onOpenHunt = { navController.navigate(HuntRoute()) },
                jumpRequests = entry.savedStateHandle.getStateFlow<Int?>(SHOW_BOX_KEY, null),
                onJumpForwarded = { entry.savedStateHandle[SHOW_BOX_KEY] = null },
                neededRequests = entry.savedStateHandle.getStateFlow<String?>(SHOW_NEEDED_KEY, null),
                onNeededForwarded = { entry.savedStateHandle[SHOW_NEEDED_KEY] = null },
                browsedTo = entry.savedStateHandle.getStateFlow<String?>(BROWSED_TO_KEY, null),
                onBrowsedToHandled = { entry.savedStateHandle[BROWSED_TO_KEY] = null },
            )
        }
    }
    composable<SlotDetailRoute> { entry ->
        val browsing = entry.toRoute<SlotDetailRoute>().browse != null
        CompositionLocalProvider(LocalSpriteTransition provides SpriteTransition(sharedTransitionScope, this)) {
            SlotDetailDestination(
                onBack = back,
                onOpenSlot = openSlot,
                onOpenVariant = openVariant,
                onShowInBox = { boxIndex -> returnToBoxes(SHOW_BOX_KEY, boxIndex) },
                onOpenMyGames = onOpenMyGames,
                // Only while this detail is on top: during its exit transition the entry below
                // is already current, and writing then would land on the wrong entry.
                onBrowsed = { slotKey ->
                    if (browsing && navController.currentBackStackEntry == entry) {
                        navController.previousBackStackEntry?.savedStateHandle?.set(BROWSED_TO_KEY, slotKey)
                    }
                },
            )
        }
    }
    composable<HuntRoute> {
        HuntDestination(
            onBack = back,
            onOpenSlot = openSlot,
            onOpenMyGames = onOpenMyGames,
            onOpenProgress = { navController.navigate(ProgressRoute) },
        )
    }
    composable<ProgressRoute> {
        ProgressDestination(
            onBack = back,
            onOpenHunt = { navController.navigate(HuntRoute()) },
            onShowBox = { boxIndex -> returnToBoxes(SHOW_BOX_KEY, boxIndex) },
            onShowNeededIn = { gameSetId -> returnToBoxes(SHOW_NEEDED_KEY, gameSetId) },
            onOpenSlot = openSlot,
        )
    }
    composable<VariantDetailRoute> {
        VariantDetailDestination(onBack = back, onOpenSlot = openSlot, onOpenVariant = openVariant)
    }
}
