package net.pokedex.designsystem.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.pokedex.designsystem.component.BoxGrid
import net.pokedex.designsystem.component.BoxHeader
import net.pokedex.designsystem.component.BoxPager
import net.pokedex.designsystem.component.BoxSlot
import net.pokedex.designsystem.component.BoxSummary
import net.pokedex.designsystem.component.CaseSurface
import net.pokedex.designsystem.component.CaughtToggle
import net.pokedex.designsystem.component.ContainerSamples
import net.pokedex.designsystem.component.DemoSprite
import net.pokedex.designsystem.component.EmptyState
import net.pokedex.designsystem.component.ErrorState
import net.pokedex.designsystem.component.FilterChipRow
import net.pokedex.designsystem.component.FilterOption
import net.pokedex.designsystem.component.GameBadge
import net.pokedex.designsystem.component.LoadingState
import net.pokedex.designsystem.component.MethodBadge
import net.pokedex.designsystem.component.ProgressBar
import net.pokedex.designsystem.component.ProgressReadout
import net.pokedex.designsystem.component.ProgressRing
import net.pokedex.designsystem.component.SearchField
import net.pokedex.designsystem.component.SlotState
import net.pokedex.designsystem.component.SortControl
import net.pokedex.designsystem.component.SortOption
import net.pokedex.designsystem.component.SpeciesCard
import net.pokedex.designsystem.component.SpeciesHeader
import net.pokedex.designsystem.component.StatTile
import net.pokedex.designsystem.component.TypeBadge
import net.pokedex.designsystem.component.UndoBar
import net.pokedex.designsystem.component.demoBox
import net.pokedex.designsystem.component.demoPages
import net.pokedex.designsystem.theme.PokedexTheme
import net.pokedex.designsystem.theme.PokemonType

/**
 * Every component, in every state that means anything.
 *
 * Split out of Gallery.kt so the screen's chrome and its content are separable -- the
 * sections are also what the screenshot tests capture, one section at a time, rather than
 * one enormous unreviewable image.
 *
 * The one thing deliberately *not* here is a scrolling wall of boxes. This is a component
 * review surface, not a fake app; a fake app would tempt features into copying its layout,
 * and layout is their job.
 */
@Composable
internal fun GallerySections() {
    val dimens = PokedexTheme.dimens

    GallerySection("Box slot — every state") { SlotStatesSection() }
    GallerySection("Box grid, header and the completion moment") { BoxSection() }
    GallerySection("Box pager and overview") { PagerSection() }
    GallerySection("Caught toggle — the most-used control") { CaughtSection() }
    GallerySection("Progress") { ProgressSection() }
    GallerySection("Badges — 18 types, contrast-checked in both themes") { BadgeSection() }
    GallerySection("Search, filter, sort") { FilterSection() }
    GallerySection("Species card and detail hero") { SpeciesSection() }
    GallerySection("Stat tiles") { StatSection() }
    GallerySection("Empty, error, loading") { StateSection() }
    GallerySection("Sheets and dialogs") { ContainerSamples() }

    Box(Modifier.padding(dimens.spaceXl))
}

private val GALLERY_SLOT = 52.dp

/** Human labels for the state row. The enum names are for code, not for a design review. */
private val SlotState.galleryLabel: String
    get() = when (this) {
        SlotState.Empty -> "Empty"
        SlotState.Needed -> "Needed"
        SlotState.Caught -> "Caught"
        SlotState.ShinyLocked -> "Shiny locked"
        SlotState.Unavailable -> "Not in my games"
    }

@Composable
private fun SlotStatesSection() {
    val dimens = PokedexTheme.dimens
    Column(
        modifier = Modifier.padding(horizontal = dimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
            SlotState.entries.forEach { state ->
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    BoxSlot(
                        state = state,
                        label = "Bulbasaur",
                        onClick = {},
                        modifier = Modifier.size(GALLERY_SLOT),
                        sprite = { DemoSprite(it) },
                    )
                    Text(
                        text = state.galleryLabel,
                        style = PokedexTheme.text.badgeLabel,
                        color = PokedexTheme.colors.onCaseMuted,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                BoxSlot(
                    state = SlotState.Needed,
                    label = "Bulbasaur",
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.size(GALLERY_SLOT),
                    sprite = { DemoSprite(it) },
                )
                Text(
                    text = "Disabled",
                    style = PokedexTheme.text.badgeLabel,
                    color = PokedexTheme.colors.onCaseMuted,
                )
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                BoxSlot(state = SlotState.Needed, modifier = Modifier.size(GALLERY_SLOT))
                Text(
                    text = "No sprite",
                    style = PokedexTheme.text.badgeLabel,
                    color = PokedexTheme.colors.onCaseMuted,
                )
            }
        }
        Text(
            text = "Press and hold a slot to see the corner morph. Tapping a needed slot in the " +
                "grid below runs the catch sweep.",
            style = PokedexTheme.text.dexNumber,
            color = PokedexTheme.colors.onCaseMuted,
        )
    }
}

/**
 * The box, wired live.
 *
 * Tapping toggles a slot, which means the gallery exercises the two signature moments --
 * the catch sweep and, once the last slot lands, the completion rule -- rather than just
 * showing their end states.
 */
@Composable
private fun BoxSection() {
    val dimens = PokedexTheme.dimens
    var caught by remember { mutableStateOf(demoCaughtSet()) }

    // Built from the same demoBox the previews and screenshots use, with the caught set
    // swapped in -- so tapping in the gallery cannot drift from what the tests capture.
    val slots = demoBox().mapIndexed { i, item ->
        val state = when {
            item.state == SlotState.Empty -> SlotState.Empty
            item.state == SlotState.ShinyLocked -> SlotState.ShinyLocked
            item.state == SlotState.Unavailable -> SlotState.Unavailable
            i in caught -> SlotState.Caught
            else -> SlotState.Needed
        }
        item.copy(state = state, key = "gallery-$i")
    }
    val filled = slots.count { it.state != SlotState.Empty }
    val caughtCount = slots.count { it.state == SlotState.Caught }

    Box(Modifier.padding(horizontal = dimens.spaceLg)) {
        CaseSurface {
            BoxHeader(name = "Kanto 1", caught = caughtCount, total = filled)
            Box(Modifier.padding(top = dimens.spaceMd)) {
                BoxGrid(
                    slots = slots,
                    onSlotClick = { index ->
                        caught = if (index in caught) caught - index else caught + index
                    },
                    sprite = { _, rendering -> DemoSprite(rendering) },
                )
            }
        }
    }
}

private const val GALLERY_CAUGHT = 21
private val GALLERY_NOT_CAUGHT = setOf(7, 15)

private fun demoCaughtSet(): Set<Int> = (0 until GALLERY_CAUGHT).toSet() - GALLERY_NOT_CAUGHT

@Composable
private fun PagerSection() {
    val dimens = PokedexTheme.dimens
    val pages = demoPages()
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceMd)) {
        BoxPager(pages = pages, onSlotClick = { _, _ -> }, sprite = { _, r -> DemoSprite(r) })
        Column(
            modifier = Modifier.padding(horizontal = dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
        ) {
            pages.forEach { page ->
                BoxSummary(name = page.name, caught = page.caught, total = page.filled, onClick = {})
            }
        }
    }
}

@Composable
private fun CaughtSection() {
    val dimens = PokedexTheme.dimens
    var caught by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(horizontal = dimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        CaughtToggle(caught = caught, onCaughtChange = { caught = it })
        CaughtToggle(caught = true, onCaughtChange = {})
        CaughtToggle(caught = false, onCaughtChange = {}, enabled = false)
        UndoBar(message = "Bulbasaur marked caught", onUndo = {})
    }
}

@Composable
private fun ProgressSection() {
    val dimens = PokedexTheme.dimens
    Column(
        modifier = Modifier.padding(horizontal = dimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        ProgressReadout(caught = 412, total = 1394, label = "shiny")
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            ProgressRing(caught = 0, total = 29, label = "Paldea 4")
            ProgressRing(caught = 21, total = 29, label = "Kanto 1")
            ProgressRing(caught = 29, total = 29, label = "Kanto 2")
            ProgressReadout(caught = 21, total = 29, compact = true)
        }
        ProgressBar(caught = 412, total = 1394, label = "Collection")
        ProgressBar(caught = 0, total = 1394, label = "Nothing yet")
        ProgressBar(caught = 1394, total = 1394, label = "Complete")
    }
}

@Composable
private fun BadgeSection() {
    val dimens = PokedexTheme.dimens
    Column(
        modifier = Modifier.padding(horizontal = dimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
        ) {
            PokemonType.entries.forEach { TypeBadge(it) }
            TypeBadge(type = null, fallbackLabel = "unknown")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
            GameBadge("Scarlet")
            GameBadge("Legends: Arceus", shinyLocked = true)
            MethodBadge("Mass outbreak")
        }
    }
}

@Composable
private fun FilterSection() {
    val dimens = PokedexTheme.dimens
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf("needed")) }
    var sort by remember { mutableStateOf("box") }

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceMd)) {
        Box(Modifier.padding(horizontal = dimens.spaceLg)) {
            SearchField(value = query, onValueChange = { query = it })
        }
        FilterChipRow(
            filters = listOf(
                FilterOption("needed", "Needed", "needed" in selected, count = 982),
                FilterOption("caught", "Caught", "caught" in selected, count = 412),
                FilterOption("locked", "Shiny locked", "locked" in selected, count = 43),
                FilterOption("sv", "Scarlet / Violet", "sv" in selected, count = 700),
                FilterOption("event", "Event only", false, enabled = false),
            ),
            onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
        )
        Box(Modifier.padding(horizontal = dimens.spaceLg)) {
            SortControl(
                options = listOf(
                    SortOption("box", "Box order"),
                    SortOption("dex", "Dex no."),
                    SortOption("name", "Name"),
                    SortOption("odds", "Odds"),
                ),
                selectedId = sort,
                onSelect = { sort = it },
            )
        }
    }
}

@Composable
private fun SpeciesSection() {
    val dimens = PokedexTheme.dimens
    Column(
        modifier = Modifier.padding(horizontal = dimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        SpeciesHeader(
            name = "Charizard",
            dexNumber = 6,
            types = listOf(PokemonType.Fire, PokemonType.Flying),
            caught = true,
            sprite = { DemoSprite(it) },
        )
        SpeciesCard(
            name = "Growlithe",
            dexNumber = 58,
            formName = "Hisuian",
            types = listOf(PokemonType.Fire, PokemonType.Rock),
            state = SlotState.Needed,
            onClick = {},
            sprite = { DemoSprite(it) },
        )
        SpeciesCard(
            name = "Zacian",
            dexNumber = 888,
            types = listOf(PokemonType.Fairy),
            state = SlotState.ShinyLocked,
            onClick = {},
            sprite = { DemoSprite(it) },
        )
    }
}

@Composable
private fun StatSection() {
    val dimens = PokedexTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spaceLg),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        StatTile("Collection", "412", caption = "of 1394", accent = true, modifier = Modifier.weight(1f))
        StatTile("Boxes done", "9", caption = "of 52", modifier = Modifier.weight(1f))
        StatTile("Locked", "43", caption = "unreachable", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StateSection() {
    val dimens = PokedexTheme.dimens
    Column(
        modifier = Modifier.padding(horizontal = dimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        EmptyState(
            title = "No slots match",
            body = "Three filters are active. Clearing the game filter would show 412 more.",
            actionLabel = "Clear filters",
            action = {},
        )
        ErrorState(
            title = "That backup is from a newer version",
            body = "It was written by schema 4; this build reads up to 3.",
            action = {},
        )
        LoadingState()
    }
}
