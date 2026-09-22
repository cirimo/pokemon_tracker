package net.pokedex.feature.dex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pokedex.designsystem.component.ProgressReadout
import net.pokedex.designsystem.component.SlotTile
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * The M0 smoke screen. Not a feature -- a proof that the stack is wired end to end.
 *
 * Stateful composable delegates to a stateless one that takes state and an event sink.
 * That split is the convention for every screen in this app: it is what makes a screen
 * previewable and testable without Hilt.
 */
@Composable
fun SmokeRoute(modifier: Modifier = Modifier, viewModel: SmokeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SmokeScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
fun SmokeScreen(
    state: SmokeUiState,
    onEvent: (SmokeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = PokedexTheme.dimens
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
        ) {
            Text("Shiny living dex", style = MaterialTheme.typography.headlineMedium)

            when (state) {
                SmokeUiState.Loading -> CircularProgressIndicator()

                is SmokeUiState.Broken -> {
                    Text(
                        "The bundled dataset could not be read.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(state.error.toString(), style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Reinstalling the app restores it. Your catch records are in a " +
                            "separate database and are not affected.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                is SmokeUiState.Ready -> {
                    Text(state.presetName, style = MaterialTheme.typography.titleMedium)

                    ProgressReadout(
                        caught = state.caughtCount,
                        total = state.filledSlotCount,
                        label = "shiny",
                    )

                    Fact("boxes", state.boxCount)
                    Fact("filled slots", state.filledSlotCount)
                    Fact("distinct variants", state.distinctVariantCount)
                    Fact("slots needing a second copy", state.duplicateSlotCount)
                    Fact("dataset version", state.datasetVersion)
                    Text(
                        "upstream pokepc/dataset ${state.upstreamTag}",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
                        SlotTile(caught = state.caughtCount > 0, modifier = Modifier.size(56.dp))
                        SlotTile(caught = false, modifier = Modifier.size(56.dp))
                    }

                    Button(onClick = { onEvent(SmokeEvent.ToggleFirstSlot) }) {
                        Text("Toggle the first slot")
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: Int) {
    Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
}
