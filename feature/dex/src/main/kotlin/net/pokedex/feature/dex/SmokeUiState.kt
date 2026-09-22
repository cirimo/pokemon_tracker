package net.pokedex.feature.dex

import androidx.compose.runtime.Immutable
import net.pokedex.core.model.AppError

/**
 * UI state for the M0 smoke screen.
 *
 * Shape to copy in later features: one @Immutable state class per screen, a sealed
 * hierarchy for the states the screen genuinely has, and errors modelled rather than
 * flattened to a string. @Immutable is not decoration -- it is what lets Compose skip
 * recomposition when the instance is unchanged.
 */
@Immutable
sealed interface SmokeUiState {

    data object Loading : SmokeUiState

    /**
     * Everything worked end to end: reference.db opened and returned real counts, and
     * user.db accepted a write and read it back.
     */
    @Immutable
    data class Ready(
        val presetName: String,
        val boxCount: Int,
        val filledSlotCount: Int,
        val distinctVariantCount: Int,
        val duplicateSlotCount: Int,
        val caughtCount: Int,
        val datasetVersion: Int,
        val upstreamTag: String,
    ) : SmokeUiState

    /**
     * The reference dataset is unusable. Separate from a recoverable error because the
     * only honest answer is "reinstall the app" -- there is nothing the user can retry.
     */
    @Immutable
    data class Broken(val error: AppError) : SmokeUiState
}

sealed interface SmokeEvent {
    data object ToggleFirstSlot : SmokeEvent
    data object Reload : SmokeEvent
}
