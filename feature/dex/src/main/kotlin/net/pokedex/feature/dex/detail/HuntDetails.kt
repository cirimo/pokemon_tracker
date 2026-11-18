package net.pokedex.feature.dex.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import net.pokedex.core.model.Priority
import net.pokedex.designsystem.component.ActionButton
import net.pokedex.designsystem.component.FilterChip
import net.pokedex.designsystem.component.InfoRow
import net.pokedex.designsystem.component.InfoTable
import net.pokedex.designsystem.component.MethodBadge
import net.pokedex.designsystem.component.ScreenSection
import net.pokedex.designsystem.icon.PokedexIcons
import net.pokedex.designsystem.theme.PokedexTheme
import net.pokedex.feature.dex.label

/**
 * Where this slot sits in the hunt list. Three buckets rather than a number, because "these
 * first" and "not yet" are the only orders a person actually holds (docs/adr/0012).
 */
@Composable
internal fun PrioritySection(priority: Priority, onPick: (Priority) -> Unit) {
    ScreenSection(title = "Hunt priority", body = "Want goes to the top of the hunt list, later to the bottom.") {
        ChipFlow {
            Priority.entries.forEach { bucket ->
                FilterChip(label = bucket.label, selected = priority == bucket, onSelectedChange = { onPick(bucket) })
            }
        }
    }
}

/**
 * "How do I catch it?", per game, as a table: the game, and a word for where it stands.
 * Under a row go only the things a word cannot say -- a recorded method, a lock's reason, how
 * a Pokemon arrives through HOME. A huntable game with no method says so in its cell rather
 * than in a paragraph repeated under every game.
 */
@Composable
internal fun HuntingSection(games: List<GameHuntUi>, gamesChosen: Boolean, onOpenMyGames: () -> Unit) {
    ScreenSection(
        title = if (gamesChosen) "In your games" else "How to catch it",
        body = when {
            !gamesChosen -> "Showing every game that has it. Choose your games to see only those."
            games.isEmpty() -> "None of your games has it."
            else -> null
        },
    ) {
        if (games.isNotEmpty()) {
            InfoTable {
                games.forEachIndexed { index, game ->
                    val open = game.ways.filterNot { it.locked }
                    InfoRow(
                        title = game.name,
                        value = if (game.huntable && open.isEmpty()) "${game.label}, no method yet" else game.label,
                        first = index == 0,
                        emphasis = game.huntable,
                        icon = if (game.locked) PokedexIcons.Locked else null,
                        details = explanation(game),
                    )
                }
            }
        }
        if (!gamesChosen) ActionButton(label = "Choose my games", onClick = onOpenMyGames, primary = false)
    }
}

/** What goes under a game's row, or nothing: the ways it records, or the sentence its word stands for. */
private fun explanation(game: GameHuntUi): (@Composable ColumnScope.() -> Unit)? = when {
    game.ways.isNotEmpty() -> {
        { game.ways.forEach { WayRow(it) } }
    }
    !game.huntable && game.label != game.standing.trimEnd('.') && game.standing != "Not in this game." -> {
        { Muted(game.standing) }
    }
    else -> null
}

@Composable
private fun WayRow(way: WayUi) {
    Column(verticalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceXxs)) {
        MethodBadge(name = if (way.locked) "${way.method}, shiny-locked" else way.method)
        way.location?.let { Body(it) }
        way.via?.let { Body(it) }
        way.prerequisite?.let { Body(it) }
        way.odds?.let { Body("Odds: $it") }
        way.notes?.let { Muted(it) }
        Muted("Source: ${way.source}")
    }
}

@Composable
private fun Body(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = PokedexTheme.colors.onCase)
}

@Composable
private fun Muted(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = PokedexTheme.colors.onCaseMuted)
}
