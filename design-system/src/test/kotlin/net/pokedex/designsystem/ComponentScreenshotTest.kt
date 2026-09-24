package net.pokedex.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import net.pokedex.designsystem.component.BoxGrid
import net.pokedex.designsystem.component.BoxHeader
import net.pokedex.designsystem.component.BoxSlot
import net.pokedex.designsystem.component.BoxSlotItem
import net.pokedex.designsystem.component.BoxSummary
import net.pokedex.designsystem.component.CaseSurface
import net.pokedex.designsystem.component.CaughtToggle
import net.pokedex.designsystem.component.DemoSprite
import net.pokedex.designsystem.component.EmptyState
import net.pokedex.designsystem.component.ErrorState
import net.pokedex.designsystem.component.FilterChip
import net.pokedex.designsystem.component.InputSamples
import net.pokedex.designsystem.component.ProgressBar
import net.pokedex.designsystem.component.ProgressReadout
import net.pokedex.designsystem.component.ProgressRing
import net.pokedex.designsystem.component.SearchField
import net.pokedex.designsystem.component.SettingsSamples
import net.pokedex.designsystem.component.SlotState
import net.pokedex.designsystem.component.SortControl
import net.pokedex.designsystem.component.SortOption
import net.pokedex.designsystem.component.SpeciesCard
import net.pokedex.designsystem.component.StatTile
import net.pokedex.designsystem.component.TypeBadge
import net.pokedex.designsystem.theme.PokedexTheme
import net.pokedex.designsystem.theme.PokemonType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the core components, across the two axes that break design systems.
 *
 * Every component is captured in **both themes**, and the ones that contain text are also
 * captured at **200% font scale**. Those are the three requirements from the brief that a
 * human reviewer reliably forgets to re-check after a small change, so they are the ones
 * worth pinning to an image.
 *
 * What is deliberately not captured: anything mid-animation. Roborazzi would happily snap a
 * frame of the catch sweep, and the test would then fail on timing rather than on
 * appearance. Motion is reviewed in the gallery, by hand, which is the only honest way.
 *
 * Record with `./gradlew :design-system:recordRoborazziDebug`,
 * verify with `./gradlew :design-system:verifyRoborazziDebug`,
 * or run the whole gate with `./gradlew :design-system:designCheck`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1600dp-xhdpi")
class ComponentScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun boxSlotStates() = captureBothThemes("box_slot_states") { SlotStates() }

    @Test
    fun boxGrid() = captureBothThemes("box_grid") { GridSample(caught = 21) }

    @Test
    fun boxGridComplete() = captureBothThemes("box_grid_complete") { GridSample(caught = 30) }

    @Test
    fun caughtToggle() = captureBothThemes("caught_toggle", alsoLargeFont = true) { Toggles() }

    @Test
    fun progress() = captureBothThemes("progress", alsoLargeFont = true) { Progress() }

    @Test
    fun typeBadges() = captureBothThemes("type_badges", alsoLargeFont = true) { Badges() }

    @Test
    fun filters() = captureBothThemes("filters", alsoLargeFont = true) { Filters() }

    @Test
    fun speciesCards() = captureBothThemes("species_cards", alsoLargeFont = true) { Species() }

    @Test
    fun boxOverview() = captureBothThemes("box_overview", alsoLargeFont = true) { Overview() }

    @Test
    fun statesAndSkeletons() = captureBothThemes("states") { States() }

    @Test
    fun settings() = captureBothThemes("settings", alsoLargeFont = true) { SettingsSamples() }

    @Test
    fun inputs() = captureBothThemes("inputs", alsoLargeFont = true) { InputSamples() }

    /**
     * One helper, so adding a component to the suite is one line.
     *
     * `setContent` may only be called once per test, so theme and font scale are driven by
     * state that the captures then change between shots -- rather than by re-setting the
     * content, which throws. That also makes the shots honest about recomposition: the same
     * composition really does have to survive a theme flip.
     *
     * The font scale is provided through `LocalDensity` rather than through Robolectric
     * qualifiers because that is exactly how the gallery does it -- the screenshot and the
     * thing you review on the device are then the same code path.
     */
    private fun captureBothThemes(
        name: String,
        alsoLargeFont: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        var dark by mutableStateOf(true)
        var fontScale by mutableStateOf(1f)

        composeRule.setContent {
            PokedexTheme(darkTheme = dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale),
                ) {
                    Box(
                        modifier = Modifier
                            .width(SHOT_WIDTH)
                            .background(PokedexTheme.colors.case)
                            .padding(PokedexTheme.dimens.spaceLg),
                    ) {
                        content()
                    }
                }
            }
        }

        shoot("${name}_dark")
        dark = false
        shoot("${name}_light")
        if (alsoLargeFont) {
            dark = true
            fontScale = LARGE_FONT
            shoot("${name}_dark_200")
        }
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        val SHOT_WIDTH = 380.dp
        const val LARGE_FONT = 2f
    }
}

@Composable
private fun SlotStates() {
    Row(horizontalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.slotGutter)) {
        SlotState.entries.forEach { state ->
            BoxSlot(
                state = state,
                label = "Bulbasaur",
                onClick = {},
                modifier = Modifier.size(SHOT_SLOT),
                sprite = { DemoSprite(it) },
            )
        }
    }
}

private val SHOT_SLOT = 52.dp

@Composable
private fun GridSample(caught: Int) {
    val slots = List(THIRTY) { i ->
        val state = when {
            i == 29 -> SlotState.Empty
            i == 7 || i == 22 -> SlotState.ShinyLocked
            i == 15 -> SlotState.Unavailable
            i < caught -> SlotState.Caught
            else -> SlotState.Needed
        }
        BoxSlotItem(state = state, label = "Slot ${i + 1}", key = "shot-$i")
    }
    val filled = slots.count { it.state != SlotState.Empty }
    CaseSurface {
        BoxHeader(
            name = "Kanto 1",
            caught = slots.count { it.state == SlotState.Caught },
            total = filled,
        )
        Box(Modifier.padding(top = PokedexTheme.dimens.spaceMd)) {
            BoxGrid(slots = slots, sprite = { _, r -> DemoSprite(r) })
        }
    }
}

private const val THIRTY = 30

@Composable
private fun Overview() {
    Column(verticalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceSm)) {
        BoxSummary(name = "Kanto 1", caught = 29, total = 29, onClick = {})
        BoxSummary(name = "Kanto 2", caught = 21, total = 29, onClick = {})
        BoxSummary(name = "Johto 1", caught = 0, total = 30, onClick = {})
    }
}

@Composable
private fun Toggles() {
    Column(verticalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceMd)) {
        CaughtToggle(caught = false, onCaughtChange = {})
        CaughtToggle(caught = true, onCaughtChange = {})
        CaughtToggle(caught = false, onCaughtChange = {}, enabled = false)
    }
}

@Composable
private fun Progress() {
    Column(verticalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceMd)) {
        ProgressReadout(caught = 412, total = 1394, label = "shiny")
        Row(horizontalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceMd)) {
            ProgressRing(caught = 0, total = 29)
            ProgressRing(caught = 21, total = 29)
            ProgressRing(caught = 29, total = 29)
        }
        ProgressBar(caught = 412, total = 1394)
    }
}

@Composable
private fun Badges() {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceSm),
        verticalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceSm),
    ) {
        PokemonType.entries.forEach { TypeBadge(it) }
    }
}

@Composable
private fun Filters() {
    Column(verticalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceMd)) {
        SearchField(value = "charmander", onValueChange = {})
        Row(horizontalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceSm)) {
            FilterChip("Needed", selected = true, onSelectedChange = {}, count = 982)
            FilterChip("Caught", selected = false, onSelectedChange = {}, count = 412)
        }
        SortControl(
            options = listOf(
                SortOption("box", "Box order"),
                SortOption("dex", "Dex no."),
                SortOption("name", "Name"),
            ),
            selectedId = "box",
            onSelect = {},
        )
    }
}

@Composable
private fun Species() {
    Column(verticalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceMd)) {
        SpeciesCard(
            name = "Growlithe",
            dexNumber = 58,
            formName = "Hisuian",
            types = listOf(PokemonType.Fire, PokemonType.Rock),
            state = SlotState.Needed,
            sprite = { DemoSprite(it) },
        )
        SpeciesCard(
            name = "Zacian",
            dexNumber = 888,
            types = listOf(PokemonType.Fairy),
            state = SlotState.ShinyLocked,
            sprite = { DemoSprite(it) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceSm),
        ) {
            StatTile("Collection", "412", caption = "of 1394", accent = true, modifier = Modifier.weight(1f))
            StatTile("Boxes done", "9", caption = "of 52", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun States() {
    Column(verticalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceMd)) {
        EmptyState(
            title = "No slots match",
            body = "Three filters are active.",
            actionLabel = "Clear filters",
            action = {},
        )
        ErrorState(
            title = "That backup is from a newer version",
            body = "It was written by schema 4; this build reads up to 3.",
            action = {},
        )
    }
}
