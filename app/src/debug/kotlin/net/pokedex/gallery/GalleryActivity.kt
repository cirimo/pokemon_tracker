package net.pokedex.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.pokedex.designsystem.gallery.DesignSystemGallery

/**
 * The design-system gallery host, required by the M1 contract.
 *
 * Lives in the debug source set, so it exists on the device as a second launcher icon
 * during development and is absent from release entirely -- there is no flag to get it
 * wrong. No Hilt here on purpose: the gallery must render with no app state at all.
 */
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { DesignSystemGallery() }
    }
}
