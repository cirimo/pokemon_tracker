package net.pokedex.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import net.pokedex.designsystem.component.BoxGrid
import net.pokedex.designsystem.component.BoxSlot
import net.pokedex.designsystem.component.BoxSlotItem
import net.pokedex.designsystem.component.CaughtToggle
import net.pokedex.designsystem.component.FilterChip
import net.pokedex.designsystem.component.ProgressRing
import net.pokedex.designsystem.component.SlotState
import net.pokedex.designsystem.component.describe
import net.pokedex.designsystem.theme.PokedexTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Touch targets and TalkBack semantics, checked rather than claimed.
 *
 * Runs on Robolectric so it needs no device and can gate every push alongside the contrast
 * tests. Between this and `ContrastTest`, the four accessibility promises in the brief --
 * contrast, non-colour state, semantics and 48dp targets -- are all enforced by the build.
 * Font scale and reduce-motion are covered by `ComponentScreenshotTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class AccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * An unconstrained slot is at least 48dp in both directions.
     *
     * This is the contract BoxSlot actually offers, and it is the one every real caller
     * gets: the grid sizes tiles by weight, never by an explicit `.size()`. A caller that
     * *does* pass a smaller explicit size wins, exactly as it does for M3's own Checkbox --
     * that is stated in BoxSlot's KDoc rather than pretended away, because the alternative
     * (an inner box protecting the minimum) breaks `Modifier.weight` and was the cause of a
     * real layout bug.
     */
    @Test
    fun anUnconstrainedBoxSlotMeetsTheMinimumTouchTarget() {
        composeRule.setContent {
            PokedexTheme {
                BoxSlot(state = SlotState.Needed, label = "Bulbasaur", onClick = {})
            }
        }
        composeRule.onNodeWithContentDescription("Bulbasaur, not yet caught")
            .assertWidthIsAtLeast(MIN_TARGET)
            .assertHeightIsAtLeast(MIN_TARGET)
    }

    /** And a tile sized by the grid's own weight is comfortably past it. */
    @Test
    fun aGridSizedBoxSlotMeetsTheMinimumTouchTarget() {
        composeRule.setContent {
            PokedexTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    BoxGrid(
                        slots = List(THIRTY) {
                            BoxSlotItem(SlotState.Needed, "Slot ${it + 1}", "target-$it")
                        },
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription("Slot 1, not yet caught")
            .assertWidthIsAtLeast(MIN_TARGET)
            .assertHeightIsAtLeast(MIN_TARGET)
    }

    @Test
    fun caughtToggleMeetsTheMinimumTouchTarget() {
        composeRule.setContent {
            PokedexTheme { CaughtToggle(caught = false, onCaughtChange = {}) }
        }
        composeRule.onNodeWithText("Mark caught").assertHeightIsAtLeast(MIN_TARGET)
    }

    @Test
    fun filterChipMeetsTheMinimumTouchTarget() {
        composeRule.setContent {
            PokedexTheme {
                Column { FilterChip(label = "Needed", selected = false, onSelectedChange = {}) }
            }
        }
        composeRule.onNodeWithText("Needed").assertHeightIsAtLeast(MIN_TARGET)
    }

    /** Every slot state announces a sentence, and no two states announce the same one. */
    @Test
    fun everySlotStateHasADistinctSpokenDescription() {
        val spoken = SlotState.entries.map { it.describe("Bulbasaur") }
        assertThat(spoken).containsNoDuplicates()
        assertThat(spoken).hasSize(SlotState.entries.size)
        spoken.forEach { assertThat(it).isNotEmpty() }
    }

    /**
     * The compound state names must not leak into speech.
     *
     * "ShinyLocked" and "Unavailable" are things the codebase calls them; "shiny locked in
     * every game you have" and "not available in your games" are what they mean. Those two
     * are the ones worth asserting -- "Empty slot" legitimately contains the word Empty,
     * and pretending otherwise would be a test written to be passed rather than to be true.
     */
    @Test
    fun compoundStateNamesDoNotLeakIntoSpeech() {
        listOf(SlotState.ShinyLocked, SlotState.Unavailable).forEach { state ->
            assertThat(state.describe("Bulbasaur")).doesNotContain(state.name)
        }
    }

    @Test
    fun boxSlotIsAnnouncedWithItsStateInBothDirections() {
        composeRule.setContent {
            PokedexTheme {
                Column {
                    BoxSlot(state = SlotState.Caught, label = "Charizard", onClick = {})
                    BoxSlot(state = SlotState.ShinyLocked, label = "Zacian", onClick = {})
                    BoxSlot(state = SlotState.Empty)
                }
            }
        }
        composeRule.onNodeWithContentDescription("Charizard, shiny caught").assertExists()
        composeRule.onNodeWithContentDescription("Zacian, shiny locked in every game you have")
            .assertExists()
        composeRule.onNodeWithContentDescription("Empty slot").assertExists()
    }

    /**
     * Progress is spoken as a sentence that leads with what is left.
     *
     * A screen reader user should get the same answer from the announcement that a sighted
     * user gets from the length of the arc, which is "how much further", not "29.6%".
     */
    @Test
    fun progressIsAnnouncedAsRemainingWork() {
        composeRule.setContent {
            PokedexTheme { ProgressRing(caught = 21, total = 29, label = "Kanto 1") }
        }
        composeRule.onNodeWithContentDescription("Kanto 1: 21 of 29 caught, 8 to go").assertExists()
    }

    @Test
    fun completeProgressSaysSo() {
        composeRule.setContent {
            PokedexTheme { ProgressRing(caught = 29, total = 29, label = "Kanto 2") }
        }
        composeRule.onNodeWithContentDescription("Kanto 2: complete, all 29 caught").assertExists()
    }

    private companion object {
        val MIN_TARGET = 48.dp
        const val THIRTY = 30
    }
}
