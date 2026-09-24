package net.pokedex.feature.dex.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import net.pokedex.core.model.Priority
import net.pokedex.designsystem.component.FilterChip
import net.pokedex.designsystem.component.MethodBadge
import net.pokedex.designsystem.component.ScreenSection
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
 * "How do I catch it?", per game. Obtainability is complete and always shown; a method only
 * where one is curated. A huntable game with none says so in words rather than going quiet,
 * because an empty space reads as "nothing to do here".
 */
@Composable
internal fun HuntingSection(games: List<GameHuntUi>, gamesChosen: Boolean, onOpenMyGames: () -> Unit) {
    val dimens = PokedexTheme.dimens
    ScreenSection(
        title = if (gamesChosen) "In your games" else "How to catch it",
        body = when {
            !gamesChosen -> "Showing every game that has it. Choose your games to see only those."
            games.isEmpty() -> "None of your games has it."
            else -> null
        },
    ) {
        games.forEach { game ->
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
                Text(text = game.name, style = MaterialTheme.typography.titleSmall, color = PokedexTheme.colors.onCase)
                Muted(game.standing)
                if (game.huntable && game.ways.none { !it.locked }) Body("No method recorded yet.")
                game.ways.forEach { WayRow(it) }
            }
        }
        if (!gamesChosen) {
            OutlinedButton(onClick = onOpenMyGames) {
                Text("Choose my games", style = PokedexTheme.text.badgeLabel)
            }
        }
    }
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
