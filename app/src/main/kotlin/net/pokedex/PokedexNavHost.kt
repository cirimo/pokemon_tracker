package net.pokedex

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import net.pokedex.feature.dex.SmokeRouteKey
import net.pokedex.feature.dex.dexGraph

/**
 * The navigation graph.
 *
 * :app is the only module that knows the whole graph. Each feature contributes its own
 * destinations through a NavGraphBuilder extension, which is what lets two features
 * link to each other without depending on each other.
 *
 * The shape this grows into (docs/architecture.md):
 *   Boxes (start) -> SlotDetail(key) -> VariantDetail(variantId)
 *   Search
 *   Settings -> BackupRestore
 */
@Composable
fun PokedexNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = SmokeRouteKey) {
        dexGraph()
    }
}
