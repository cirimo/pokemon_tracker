package net.pokedex.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import net.pokedex.designsystem.theme.CaseColors
import net.pokedex.designsystem.theme.PokedexShapes
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * A single well in the case. The most performance-sensitive component in the app:
 * thirty of these are on screen at once and they scroll.
 *
 * Constraints baked in, from docs/design-decisions.md:
 *  - border, never elevation -- per-tile shadow is the frame-budget killer
 *  - no ripple -- M3 ripple on a small tile is mush
 *  - no shader, ever -- AGSL needs API 33 and minSdk is 26
 *  - no type colour in the grid
 *
 * M0 ships the empty well and the caught rim. M1 adds the sprite, the pip and the
 * press-state shape morph.
 */
@Composable
fun SlotTile(
    caught: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable () -> Unit = {},
) {
    val dimens = PokedexTheme.dimens
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = dimens.slotMinSize, minHeight = dimens.slotMinSize)
            .aspectRatio(1f)
            .clip(PokedexShapes.slot)
            .background(CaseColors.SlotWell)
            .border(
                width = dimens.rimWidth,
                // The hairline gold rim is the single signal that a slot is filled.
                color = if (caught) CaseColors.Gold else CaseColors.SlotRim,
                shape = PokedexShapes.slot,
            )
            .semantics { contentDescription?.let { this.contentDescription = it } },
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Preview
@Composable
private fun SlotTileEmptyPreview() {
    PokedexTheme { SlotTile(caught = false) }
}

@Preview
@Composable
private fun SlotTileCaughtPreview() {
    PokedexTheme { SlotTile(caught = true) }
}
