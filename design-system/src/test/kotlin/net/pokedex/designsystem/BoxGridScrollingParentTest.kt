package net.pokedex.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import net.pokedex.designsystem.component.BoxGrid
import net.pokedex.designsystem.component.BoxHeader
import net.pokedex.designsystem.component.BoxPage
import net.pokedex.designsystem.component.BoxPager
import net.pokedex.designsystem.component.BoxSlotItem
import net.pokedex.designsystem.component.CaseSurface
import net.pokedex.designsystem.component.SlotState
import net.pokedex.designsystem.theme.PokedexTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The test that should have existed before the gallery crashed.
 *
 * `BoxGrid` was a `LazyVerticalGrid`. Every screenshot test placed it inside a `Box` with
 * bounded height, so every screenshot test passed -- and the component threw
 * `IllegalStateException: Vertically scrollable component was measured with an infinity
 * maximum height constraints` the first time it went into a real screen, because a real
 * screen scrolls.
 *
 * The lesson is not "add a test for lazy grids". It is that a component's *container
 * contract* is part of its API: "works when given bounded height" and "works anywhere" are
 * different components, and only the second one is any use to a feature session. So these
 * tests assert placement, not appearance, in the containers features will actually use.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class BoxGridScrollingParentTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** [tag] keeps labels unique when a test stacks two boxes -- semantics must not collide. */
    private fun box(tag: String = "a") = List(THIRTY) { i ->
        BoxSlotItem(
            state = if (i == 0) SlotState.Caught else SlotState.Needed,
            label = "$tag slot ${i + 1}",
            key = "$tag-$i",
        )
    }

    /** The case the crash came from: header, grid and more content scrolling together. */
    @Test
    fun boxGridSurvivesAVerticallyScrollingParent() {
        composeRule.setContent {
            PokedexTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    CaseSurface {
                        BoxHeader(name = "Kanto 1", caught = 1, total = THIRTY)
                        BoxGrid(slots = box("a"))
                    }
                }
            }
        }
        composeRule.onNodeWithContentDescription("a slot 1, shiny caught").assertExists()
    }

    /** Unbounded height with no scroll container at all. */
    @Test
    fun boxGridSurvivesUnboundedHeight() {
        composeRule.setContent {
            PokedexTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    BoxGrid(slots = box("a"))
                    BoxGrid(slots = box("b"))
                }
            }
        }
        composeRule.onNodeWithContentDescription("a slot 1, shiny caught").assertExists()
    }

    /** The pager stacks a grid inside a horizontal scroller inside a vertical one. */
    @Test
    fun boxPagerSurvivesAVerticallyScrollingParent() {
        composeRule.setContent {
            PokedexTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    BoxPager(
                        pages = listOf(
                            BoxPage(0, "Kanto 1", box("a")),
                            BoxPage(1, "Kanto 2", box("b")),
                        ),
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("a slot 1, shiny caught").assertExists()
    }

    /**
     * A short last row must not stretch its tiles.
     *
     * The Column-of-Rows rewrite pads short rows with weighted spacers. Without them a box
     * whose final row holds two slots would render those two at three times the width of
     * every other tile, which is the kind of thing that looks like a rendering bug and is
     * actually a layout one.
     */
    @Test
    fun aShortLastRowKeepsTileWidth() {
        val short = List(SHORT_BOX) { i ->
            BoxSlotItem(state = SlotState.Needed, label = "short slot ${i + 1}", key = "short-$i")
        }
        composeRule.setContent {
            PokedexTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    BoxGrid(slots = short)
                }
            }
        }
        val first = composeRule.onNodeWithContentDescription("short slot 1, not yet caught")
            .fetchSemanticsNode().size.width
        val last = composeRule.onNodeWithContentDescription("short slot $SHORT_BOX, not yet caught")
            .fetchSemanticsNode().size.width
        // Within a pixel: weighted distribution can leave a rounding remainder.
        assert(kotlin.math.abs(first - last) <= 1) {
            "short last row stretched its tiles: first=$first last=$last"
        }
    }

    private companion object {
        const val THIRTY = 30
        const val SHORT_BOX = 14
    }
}
