package net.pokedex.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import net.pokedex.designsystem.icon.PokedexIcons
import net.pokedex.designsystem.theme.PokedexShapes
import net.pokedex.designsystem.theme.PokedexTheme
import net.pokedex.designsystem.theme.PokemonType
import net.pokedex.designsystem.theme.swatch

/**
 * The three badges.
 *
 * All of them render a word. None of them is a coloured dot you have to have learned. That
 * is the "never colour alone" rule applied to the one component family where the temptation
 * to break it is strongest -- eighteen types is exactly the situation where a legend of
 * colour chips looks efficient and is not.
 *
 * Badges are also where type colour is *allowed to exist at all*. It is forbidden in the
 * grid (docs/design-decisions.md) because eighteen hues across thirty tiles is noise, and
 * because it would compete with the one gold the grid depends on.
 */

/**
 * A Pokemon type.
 *
 * Takes [PokemonType] rather than a string so an unknown type from a regenerated dataset
 * degrades to a plain uncoloured badge instead of throwing. Use `PokemonType.fromId`.
 */
@Composable
fun TypeBadge(
    type: PokemonType?,
    modifier: Modifier = Modifier,
    /** The label to show when [type] is null -- usually the raw dataset string. */
    fallbackLabel: String? = null,
) {
    val colors = PokedexTheme.colors
    val swatch = type?.swatch()
    val label = type?.name ?: fallbackLabel ?: "Unknown"
    BadgeSurface(
        modifier = modifier,
        container = swatch?.container(colors) ?: colors.caseSurfaceHigh,
        border = null,
    ) {
        Text(
            text = label,
            style = PokedexTheme.text.badgeLabel,
            color = swatch?.on(colors) ?: colors.onCaseMuted,
        )
    }
}

/**
 * A game the variant can be obtained in.
 *
 * Deliberately not coloured per game. Eighteen type colours already carry the colour
 * budget, and version colours would put a second unlearnable palette on the same screen.
 * Availability is the meaning here, and [shinyLocked] is the only modifier it has.
 */
@Composable
fun GameBadge(
    name: String,
    modifier: Modifier = Modifier,
    shinyLocked: Boolean = false,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    BadgeSurface(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = if (shinyLocked) "$name, shiny locked" else name
        },
        container = colors.caseSurfaceHigh,
        border = colors.rim,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (shinyLocked) {
                Icon(
                    imageVector = PokedexIcons.Locked,
                    contentDescription = null,
                    tint = colors.onCaseMuted,
                    modifier = Modifier.size(dimens.iconSm),
                )
            }
            Text(
                text = name,
                style = PokedexTheme.text.badgeLabel,
                color = if (shinyLocked) colors.onCaseMuted else colors.onCase,
            )
        }
    }
}

/** How you catch it: outbreak, fishing, raid, breeding. */
@Composable
fun MethodBadge(name: String, modifier: Modifier = Modifier) {
    val colors = PokedexTheme.colors
    BadgeSurface(modifier = modifier, container = colors.caseSurface, border = colors.rim) {
        Text(text = name, style = PokedexTheme.text.badgeLabel, color = colors.onCase)
    }
}

@Composable
private fun BadgeSurface(
    modifier: Modifier,
    container: androidx.compose.ui.graphics.Color,
    border: androidx.compose.ui.graphics.Color?,
    content: @Composable () -> Unit,
) {
    val dimens = PokedexTheme.dimens
    Row(
        modifier = modifier
            .clip(PokedexShapes.badge)
            .background(container)
            .then(
                if (border != null) {
                    Modifier.border(dimens.rimWidth, border, PokedexShapes.badge)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = dimens.spaceSm, vertical = dimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun BadgeSamples() {
    val dimens = PokedexTheme.dimens
    FlowRow(
        modifier = Modifier.padding(dimens.spaceLg),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        PokemonType.entries.forEach { TypeBadge(it) }
        TypeBadge(type = null, fallbackLabel = "stellar")
        GameBadge("Scarlet")
        GameBadge("Legends: Arceus", shinyLocked = true)
        MethodBadge("Mass outbreak")
        MethodBadge("Masuda breeding")
    }
}

@Preview(name = "Badges dark", widthDp = 360)
@Composable
private fun BadgesDarkPreview() {
    PokedexTheme(darkTheme = true) { BadgeSamples() }
}

@Preview(name = "Badges light", widthDp = 360)
@Composable
private fun BadgesLightPreview() {
    PokedexTheme(darkTheme = false) { BadgeSamples() }
}
