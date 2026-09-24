package net.pokedex.feature.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

/** The games the user owns and plays. Public so the hunt list can send a first-time user here. */
@Serializable
data object MyGamesRoute

/**
 * The restore flow. With [fileUri], it opens on that one file from the system picker;
 * without, it lists the backup folder.
 */
@Serializable
data class BackupRestoreRoute(val fileUri: String? = null)

/**
 * Settings, my games and restore.
 *
 * @param onRestored where "Done" goes after a restore. :app decides, because the answer is
 *   the box view, which this feature may not name.
 */
fun NavGraphBuilder.settingsGraph(navController: NavController, onRestored: () -> Unit) {
    val back: () -> Unit = { navController.popBackStack() }
    composable<SettingsRoute> {
        SettingsDestination(
            onBack = back,
            onOpenRestore = { uri -> navController.navigate(BackupRestoreRoute(uri?.toString())) },
            onOpenMyGames = { navController.navigate(MyGamesRoute) },
        )
    }
    composable<MyGamesRoute> {
        MyGamesDestination(onBack = back)
    }
    composable<BackupRestoreRoute> {
        RestoreDestination(
            onBack = back,
            onDone = onRestored,
            // A file picked from the list screen replaces it, so back does not return to a
            // list the user already walked away from.
            onPickFile = { uri ->
                navController.navigate(BackupRestoreRoute(uri.toString())) {
                    popUpTo<BackupRestoreRoute> { inclusive = true }
                }
            },
        )
    }
}
