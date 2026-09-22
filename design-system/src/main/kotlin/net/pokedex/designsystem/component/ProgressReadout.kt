package net.pokedex.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.pokedex.designsystem.theme.CaseColors
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * "412 / 1394". One of the three places gold is allowed.
 *
 * Takes two integers rather than a fraction so the exact counts are always shown --
 * "29.6%" is not what you want to read when deciding what to hunt next.
 */
@Composable
fun ProgressReadout(
    caught: Int,
    total: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val dimens = PokedexTheme.dimens
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        Text(
            text = caught.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = CaseColors.Gold,
        )
        Text(
            text = "/ $total",
            style = MaterialTheme.typography.labelLarge,
            color = CaseColors.OnCaseMuted,
        )
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = CaseColors.OnCaseMuted,
            )
        }
    }
}

@Preview
@Composable
private fun ProgressReadoutPreview() {
    PokedexTheme { ProgressReadout(caught = 412, total = 1394, label = "shiny") }
}
