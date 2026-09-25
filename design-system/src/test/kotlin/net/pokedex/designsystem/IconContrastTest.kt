package net.pokedex.designsystem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertWithMessage
import net.pokedex.designsystem.gallery.GallerySections
import net.pokedex.designsystem.theme.ContrastKind
import net.pokedex.designsystem.theme.PokedexTheme
import net.pokedex.designsystem.theme.contrastRatio
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every icon button in the gallery draws a glyph that stands out from what is behind it.
 *
 * `ContrastTest` checks the token pairs we declare. It cannot see a component that never
 * asks for a token: an untinted `Icon` inside an `IconButton` takes `LocalContentColor`, and
 * until `PokedexTheme` provided one that was Material's default black -- invisible on the
 * dark case, indistinguishable from `onCase` in light, and unnoticed for two milestones.
 * So this test checks what is drawn, not what is declared.
 *
 * An icon button is found by its shape in the semantics tree: a node with `Role.Button`
 * whose unmerged child is an `Icon` with a description (`Role.Image`). That skips text
 * buttons, whose glyphs are decorative, and box slots, whose sprites describe nothing.
 * Each one is captured, the most common pixel is taken as its background, and the pixel
 * that contrasts most with it as the glyph; the ratio must reach WCAG's 3:1 for graphics.
 * Disabled buttons are exempt, as WCAG exempts inactive controls.
 *
 * Not a pixel comparison, so unlike the goldens it is valid on any workstation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class IconContrastTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var view: View

    @Test
    fun everyIconButtonInTheGalleryIsVisibleInBothThemes() {
        var dark by mutableStateOf(true)
        composeRule.setContent {
            view = LocalView.current
            PokedexTheme(darkTheme = dark) {
                Column(Modifier.verticalScroll(rememberScrollState())) { GallerySections() }
            }
        }

        val failures = checkIcons("dark") + run {
            dark = false
            checkIcons("light")
        }
        assertWithMessage("icon buttons below 3:1 against their background").that(failures).isEmpty()
    }

    private fun checkIcons(theme: String): List<String> {
        composeRule.waitForIdle()
        val icons = composeRule.onAllNodes(iconInsideAButton, useUnmergedTree = true)
        val count = icons.fetchSemanticsNodes().size
        assertWithMessage("icon buttons found in the $theme gallery").that(count).isGreaterThan(0)

        return (0 until count).mapNotNull { i ->
            val icon = icons[i]
            val name = icon.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
            val button = icon.onParent()
            if (SemanticsProperties.Disabled in button.fetchSemanticsNode().config) return@mapNotNull null
            button.performScrollTo()
            composeRule.waitForIdle()
            val ratio = glyphContrast(pixelsOf(button.fetchSemanticsNode().boundsInRoot))
            if (ratio < ContrastKind.Graphical.minimumRatio) "$theme: \"$name\" at %.2f:1".format(ratio) else null
        }
    }

    /**
     * The window drawn into a bitmap and cropped to [bounds]. `captureToImage` waits for a
     * redraw that Robolectric's paused looper never delivers; drawing the view directly is
     * what Roborazzi does too.
     */
    private fun pixelsOf(bounds: Rect): IntArray {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val left = bounds.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = bounds.top.toInt().coerceIn(0, bitmap.height - 1)
        val width = bounds.width.toInt().coerceIn(1, bitmap.width - left)
        val height = bounds.height.toInt().coerceIn(1, bitmap.height - top)
        return IntArray(width * height).also { bitmap.getPixels(it, 0, width, left, top, width, height) }
    }

    private fun glyphContrast(pixels: IntArray): Double {
        val background = pixels.toList().groupingBy { it }.eachCount().maxBy { it.value }.key
        val ground = Color(background)
        return pixels.distinct().maxOf { contrastRatio(Color(it), ground) }
    }

    private companion object {
        val iconInsideAButton: SemanticsMatcher =
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image) and
                hasContentDescription("", substring = true) and
                hasParent(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }
}
