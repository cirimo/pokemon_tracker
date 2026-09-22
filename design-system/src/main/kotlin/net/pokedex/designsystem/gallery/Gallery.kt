package net.pokedex.designsystem.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.pokedex.designsystem.component.ProgressReadout
import net.pokedex.designsystem.component.SlotTile
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * The live component gallery.
 *
 * Lives in the library rather than in :app so components and their examples move
 * together. It is only ever REFERENCED from the app debug source set, so R8 strips it
 * from release -- that is how the M1 contract requirement of "reachable in debug,
 * absent from release" is met without a separate module.
 *
 * M1 fills this out. M0 proves the host works.
 */
@Composable
fun DesignSystemGallery(modifier: Modifier = Modifier) {
    PokedexTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(PokedexTheme.dimens.spaceLg),
                verticalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceXl),
            ) {
                Text("Design system", style = MaterialTheme.typography.headlineMedium)

                GallerySection("Slot tile") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SlotTile(caught = false, modifier = Modifier.size(56.dp))
                        SlotTile(caught = true, modifier = Modifier.size(56.dp))
                    }
                }

                GallerySection("Progress readout") {
                    ProgressReadout(caught = 412, total = 1394, label = "shiny")
                }
            }
        }
    }
}

@Composable
internal fun GallerySection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceSm)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
