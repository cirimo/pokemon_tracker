package net.pokedex.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import net.pokedex.designsystem.theme.PokedexShapes
import net.pokedex.designsystem.theme.PokedexTheme

/** One box, as the pager and the overview need to know it. */
@Immutable
data class BoxPage(
    val index: Int,
    val name: String,
    val slots: List<BoxSlotItem>,
) {
    val filled: Int get() = slots.count { it.state != SlotState.Empty }
    val caught: Int get() = slots.count { it.state == SlotState.Caught }
}

/**
 * The 52 boxes, one swipe apart.
 *
 * ## Why a pager and not a long scroll
 *
 * 52 boxes is 1394 tiles. A single vertical scroller would be virtualised fine by Compose,
 * but it would be *useless*: you would have no idea which box you were in, and "which box"
 * is the unit the app is organised around because it is the unit HOME is organised around
 * (docs/00-big-picture.md). A pager makes the box the thing you are looking at rather than
 * a region you happen to be scrolled to.
 *
 * It also caps the work: at most two boxes are composed at a time, so the 1400-tile problem
 * never becomes a 1400-tile *render*.
 *
 * `beyondViewportPageCount` stays at its default of 0, and that was measured rather than
 * assumed. Bringing a page in is not free: on the S21 Ultra it is about 10 ms of main-thread
 * work in total, spread by the lazy layout's prefetch over the idle time between frames.
 * Taking one out adds about 1.5 ms more. Pre-composing a neighbour does not remove that work.
 * It moves it to the moment `currentPage` changes, mid-swipe, and triples the live tile
 * count. Tried at 1, it measured worse on janky frames and P90 (docs/architecture.md §8).
 *
 * What made pages expensive until 2026-09-24 was a shared-element origin on every sprite,
 * not the tiles. Only the slot being opened may carry one. Anything else per tile that
 * registers with a scope beyond the page costs its add and its remove on every swipe.
 */
@Composable
fun BoxPager(
    pages: List<BoxPage>,
    modifier: Modifier = Modifier,
    state: PagerState = rememberPagerState { pages.size },
    onSlotClick: ((boxIndex: Int, slotIndex: Int) -> Unit)? = null,
    sprite: @Composable (BoxSlotItem, SlotSpriteRendering) -> Unit = { _, _ -> },
) {
    val dimens = PokedexTheme.dimens
    HorizontalPager(
        state = state,
        modifier = modifier,
        pageSpacing = dimens.spaceMd,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = dimens.spaceLg),
    ) { page ->
        val box = pages[page]
        CaseSurface {
            BoxHeader(name = box.name, caught = box.caught, total = box.filled)
            Box(modifier = Modifier.padding(top = dimens.spaceMd)) {
                BoxGrid(
                    slots = box.slots,
                    onSlotClick = onSlotClick?.let { click -> { slot -> click(box.index, slot) } },
                    sprite = sprite,
                )
            }
        }
    }
}

/**
 * One box in the overview: name, counts, ring.
 *
 * No tiles. An overview of 52 boxes rendered as 52 miniature grids is 1394 tiles on one
 * screen -- the exact thing the pager exists to avoid -- and at that size the tiles carry no
 * information a ring does not carry better. The overview answers "which box should I open",
 * and the ring plus the fraction answers it.
 */
@Composable
fun BoxSummary(
    name: String,
    caught: Int,
    total: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val complete = total > 0 && caught == total

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.touchTargetMin)
            .clip(PokedexShapes.card)
            .background(colors.caseSurface)
            .border(
                // A completed box carries the gold rim, the same signal a completed slot
                // carries. One vocabulary at both scales.
                width = if (complete) dimens.rimCaughtWidth else dimens.rimWidth,
                color = if (complete) colors.rimCaught else colors.rim,
                shape = PokedexShapes.card,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = dimens.spaceMd, vertical = dimens.spaceSm)
            .clearAndSetSemantics {
                contentDescription = progressSentence(caught, total, name)
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = name, style = PokedexTheme.text.boxTitle, color = colors.onCase)
            if (complete) {
                Text(
                    text = "Complete",
                    style = PokedexTheme.text.badgeLabel,
                    color = colors.accentText,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressReadout(caught = caught, total = total, compact = true)
            ProgressRing(caught = caught, total = total, label = name)
        }
    }
}

internal fun demoPages(): List<BoxPage> = listOf(
    BoxPage(0, "Kanto 1", demoBox(caughtCount = BOX_COLUMNS * BOX_ROWS)),
    BoxPage(1, "Kanto 2", demoBox(caughtCount = 21)),
    BoxPage(2, "Johto 1", demoBox(caughtCount = 0)),
)

@Composable
private fun PagerSamples() {
    val dimens = PokedexTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceLg)) {
        BoxPager(pages = demoPages(), onSlotClick = { _, _ -> }, sprite = { _, r -> DemoSprite(r) })
        Column(
            modifier = Modifier.padding(horizontal = dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
        ) {
            demoPages().forEach { page ->
                BoxSummary(
                    name = page.name,
                    caught = page.caught,
                    total = page.filled,
                    onClick = {},
                )
            }
        }
    }
}

@Preview(name = "BoxPager dark", widthDp = 380, heightDp = 700)
@Composable
private fun BoxPagerDarkPreview() {
    PokedexTheme(darkTheme = true) { PagerSamples() }
}

@Preview(name = "BoxPager light", widthDp = 380, heightDp = 700)
@Composable
private fun BoxPagerLightPreview() {
    PokedexTheme(darkTheme = false) { PagerSamples() }
}
