package net.pokedex.feature.dex.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import net.pokedex.designsystem.theme.PokedexTheme

/** Badges that wrap onto as many lines as the text scale needs. */
@Composable
internal fun ChipFlow(content: @Composable () -> Unit) {
    val dimens = PokedexTheme.dimens
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        content()
    }
}
