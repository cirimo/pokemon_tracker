package net.pokedex.feature.dex.game

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import net.pokedex.designsystem.component.InfoRow
import net.pokedex.designsystem.component.InfoTable
import net.pokedex.designsystem.theme.PokedexTheme

/**
 * The prep checklist as a table, one ticking row per item, on the game page and on a guide.
 * The tick is per game and stored (ADR 0016); ticking the Shiny Charm here changes which odds
 * every hunt in the game leads with.
 */
@Composable
internal fun PrepTable(rows: List<PrepRowUi>, onToggle: (String, Boolean) -> Unit, showFarm: Boolean = true) {
    InfoTable {
        rows.forEachIndexed { index, row ->
            InfoRow(
                title = row.title,
                value = row.value,
                first = index == 0,
                emphasis = !row.done,
                checked = row.done,
                onCheckedChange = { onToggle(row.refId, it) },
                details = if (row.lines.isEmpty() && (!showFarm || row.farm.isEmpty())) {
                    null
                } else {
                    {
                        row.lines.forEach { MutedLine(it) }
                        // Numbered because the chain is walked in order: Ralts, then Kirlia, then the stone.
                        if (showFarm) row.farm.forEachIndexed { i, step -> MutedLine("${i + 1}. $step") }
                    }
                },
            )
        }
    }
}

@Composable
internal fun MutedLine(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = PokedexTheme.colors.onCaseMuted)
}
