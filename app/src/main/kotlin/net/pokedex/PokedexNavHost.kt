package net.pokedex

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import net.pokedex.designsystem.theme.PokedexTheme
import net.pokedex.feature.dex.BoxesRoute
import net.pokedex.feature.dex.dexGraph
import net.pokedex.feature.settings.BackupRestoreRoute
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
    // The NavHost's own default is a 700ms tween, which ignores the system's animation
    // setting. PokedexTheme.motion is already resolved for reduce-motion, so routing the
    // screen fade through it is what makes navigation obey that setting too.
    val fade = PokedexTheme.motion.container
    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = BoxesRoute,
            enterTransition = { fadeIn(fade) },
            exitTransition = { fadeOut(fade) },
            popEnterTransition = { fadeIn(fade) },
            popExitTransition = { fadeOut(fade) },
        ) {
            dexGraph(
                navController = navController,
                sharedTransitionScope = this@SharedTransitionLayout,
                onOpenSettings = { navController.navigate(SettingsRoute) },
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
