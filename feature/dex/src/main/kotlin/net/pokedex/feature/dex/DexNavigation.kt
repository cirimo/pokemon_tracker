package net.pokedex.feature.dex

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/**
 * Type-safe routes, Navigation Compose 2.10 style.
 *
 * Each feature owns its own destinations and exposes one extension on NavGraphBuilder,
 * so :app assembles the graph without knowing any feature internals -- that is what
 * keeps features from having to depend on each other to link to one another.
 */
@Serializable
data object SmokeRouteKey

fun NavGraphBuilder.dexGraph() {
    composable<SmokeRouteKey> { SmokeRoute() }
}
