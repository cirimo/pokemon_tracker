package net.pokedex

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import net.pokedex.designsystem.theme.PokedexTheme
import net.pokedex.feature.dex.BoxesRoute
import net.pokedex.feature.dex.dexGraph
import net.pokedex.feature.settings.BackupRestoreRoute
import net.pokedex.feature.settings.MyGamesRoute
import net.pokedex.feature.settings.RestoreOffer
import net.pokedex.feature.settings.SettingsRoute
import net.pokedex.feature.settings.settingsGraph

/**
 * The navigation graph.
 *
 * :app is the only module that knows the whole graph. Each feature contributes its own
 * destinations through a NavGraphBuilder extension, which is what lets two features
 * link to each other without depending on each other.
 *
 * Shape (docs/architecture.md):
 *   Boxes (start, with search as a mode) -> SlotDetail(key) -> VariantDetail(variantId)
 *   Boxes -> Settings -> BackupRestore
 *   RestoreOffer (a sheet over the first screen, on an empty database) -> BackupRestore
 *
 * The SharedTransitionLayout is here because both sides of the slot-to-detail shared
 * element must sit inside the same one, and the NavHost is the only thing both are inside.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PokedexNavHost() {
    val navController = rememberNavController()
    // A shared axis: forward moves the new screen in from the end and the old one out to
    // the start, back reverses it, both while fading. One space you move through rather than
    // cards swapping in place. The NavHost's own default is a 700ms tween that ignores the
    // system's animation setting; PokedexTheme.motion is already resolved for reduce-motion,
    // so routing through it is what makes navigation obey that setting too.
    val motion = PokedexTheme.motion
    val shift = with(LocalDensity.current) { PokedexTheme.dimens.screenShift.roundToPx() }
    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = BoxesRoute,
            enterTransition = { slideInHorizontally(motion.screen()) { shift } + fadeIn(motion.screen()) },
            exitTransition = { slideOutHorizontally(motion.screen()) { -shift } + fadeOut(motion.screen()) },
            popEnterTransition = { slideInHorizontally(motion.screen()) { -shift } + fadeIn(motion.screen()) },
            popExitTransition = { slideOutHorizontally(motion.screen()) { shift } + fadeOut(motion.screen()) },
        ) {
            dexGraph(
                navController = navController,
                sharedTransitionScope = this@SharedTransitionLayout,
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenMyGames = { navController.navigate(MyGamesRoute) },
            )
            settingsGraph(
                navController = navController,
                onRestored = { navController.popBackStack<BoxesRoute>(inclusive = false) },
            )
        }
    }
    // Beside the NavHost, not in it: the offer shows over whatever the first screen is, and
    // only while the database is empty (docs/adr/0011-backups-outside-the-sandbox.md).
    RestoreOffer(
        onRestoreFromFolder = { navController.navigate(BackupRestoreRoute()) },
        onRestoreFromFile = { navController.navigate(BackupRestoreRoute(it.toString())) },
    )
}
