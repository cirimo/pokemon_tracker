package net.pokedex.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import net.pokedex.designsystem.component.ProgressReadout
import net.pokedex.designsystem.component.SlotTile
import net.pokedex.designsystem.theme.PokedexTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The M1 gate, wired at M0 so the design session inherits a working harness rather than
 * building one.
 *
 * Roborazzi on Robolectric runs on the JVM, so these are fast enough for every push.
 * Record with `./gradlew :design-system:recordRoborazziDebug`, verify with
 * `./gradlew :design-system:verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class GalleryScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun slotTile_emptyAndCaught() {
        composeRule.setContent {
            PokedexTheme {
                Surface {
                    Row(modifier = Modifier.padding(16.dp)) {
                        SlotTile(caught = false, modifier = Modifier.size(56.dp))
                        SlotTile(caught = true, modifier = Modifier.size(56.dp))
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/slot_tile.png")
    }

    @Test
    fun progressReadout() {
        composeRule.setContent {
            PokedexTheme {
                Surface {
                    ProgressReadout(
                        caught = 412,
                        total = 1394,
                        label = "shiny",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/progress_readout.png")
    }
}
