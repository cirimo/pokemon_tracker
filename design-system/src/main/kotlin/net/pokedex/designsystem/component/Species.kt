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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.pokedex.designsystem.theme.PokedexShapes
import net.pokedex.designsystem.theme.PokedexTheme
import net.pokedex.designsystem.theme.PokemonType

/**
 * The detail-screen hero, and its list-row sibling.
 *
 * [SpeciesHeader] is the only place in the app where a sprite is drawn large, so it is the
 * only place the shiny art is actually *looked at* rather than scanned. It gets the space
 * accordingly: a big well, the name, the dex number, and the types. Everything else on the
 * detail screen goes below it.
 */

/**
 * The detail hero.
 *
 * Note the well is the same component language as a grid slot -- same radius family, same
 * rim, same silhouette rule. Opening a slot should feel like the slot got bigger, not like
 * you arrived somewhere else. That continuity is what the shared element in
 * [slotSharedElementKey] is animating between.
 */
@Composable
fun SpeciesHeader(
    name: String,
    dexNumber: Int,
    types: List<PokemonType?>,
    caught: Boolean,
    modifier: Modifier = Modifier,
    formName: String? = null,
    sprite: @Composable (SlotSpriteRendering) -> Unit = {},
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val rendering = if (caught) {
        SlotSpriteRendering(colorFilter = null, alpha = 1f)
    } else {
        SlotSpriteRendering(
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.silhouette),
            alpha = 1f,
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        Box(
            modifier = Modifier
                .size(HERO_SIZE)
                .clip(PokedexShapes.card)
                .background(colors.slotWell)
                .border(
                    width = if (caught) dimens.rimCaughtWidth else dimens.rimWidth,
                    color = if (caught) colors.rimCaught else colors.rim,
                    shape = PokedexShapes.card,
                )
                .clearAndSetSemantics {
                    contentDescription = if (caught) "$name, shiny caught" else "$name, not yet caught"
                },
            contentAlignment = Alignment.Center,
        ) {
            sprite(rendering)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No. ${dexNumber.toString().padStart(DEX_DIGITS, '0')}",
                style = PokedexTheme.text.dexNumber,
                color = colors.onCaseMuted,
            )
            Text(text = name, style = PokedexTheme.text.displayNumerals, color = colors.onCase)
            if (formName != null) {
                Text(text = formName, style = PokedexTheme.text.dexNumber, color = colors.onCaseMuted)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
            types.forEach { TypeBadge(it) }
        }
    }
}

private val HERO_SIZE = 168.dp
private const val DEX_DIGITS = 4

/**
 * A species in a list.
 *
 * Deliberately not a Material `Card`: a card brings elevation, and elevation is reserved
 * for sheets and dialogs (see [net.pokedex.designsystem.theme.PokedexElevation]). A border
 * and a surface do the same job with no render node.
 *
 * [details] are short lines under the types, for a list that has to say why a row is there
 * (the hunt list: "Fills 2 slots", "Mass outbreak in Legends Arceus, 1 in 128"). Plain text,
 * never gold -- a top pick is not a shiny -- and spoken after everything else.
 */
@Composable
fun SpeciesCard(
    name: String,
    dexNumber: Int,
    types: List<PokemonType?>,
    state: SlotState,
    modifier: Modifier = Modifier,
    formName: String? = null,
    details: List<String> = emptyList(),
    onClick: (() -> Unit)? = null,
    sprite: @Composable (SlotSpriteRendering) -> Unit = {},
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.touchTargetMin)
            .clip(PokedexShapes.card)
            .background(colors.caseSurface)
            .border(dimens.rimWidth, colors.rim, PokedexShapes.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(dimens.spaceMd)
            .clearAndSetSemantics {
                val typeNames = types.filterNotNull().joinToString(" ") { it.name }
                // The secondary line is spoken too. It is what tells two rows with the same
                // name apart -- the two Unown-A slots in a search list differ only there.
                val secondary = formName?.let { " $it." }.orEmpty()
                val more = if (details.isEmpty()) "" else "." + details.joinToString("") { " $it." }
                contentDescription = "${state.describe(name)}.$secondary Number $dexNumber. $typeNames$more"
            },
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxSlot(state = state, label = name, sprite = sprite, modifier = Modifier.size(CARD_SLOT))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceXxs),
        ) {
            Text(
                text = "No. ${dexNumber.toString().padStart(DEX_DIGITS, '0')}",
                style = PokedexTheme.text.dexNumber,
                color = colors.onCaseMuted,
            )
            Text(text = name, style = PokedexTheme.text.statValue, color = colors.onCase)
            if (formName != null) {
                Text(text = formName, style = PokedexTheme.text.dexNumber, color = colors.onCaseMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
                types.forEach { TypeBadge(it) }
            }
            details.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodySmall, color = colors.onCase)
            }
        }
    }
}

private val CARD_SLOT = 56.dp

/**
 * A number with a label. Progress dashboards are made of these.
 *
 * [accent] is the opt-in for gold, and it is opt-in rather than automatic so that a
 * dashboard of eight tiles does not become eight gold numbers. Use it on the one figure
 * the screen is about.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    accent: Boolean = false,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    Column(
        modifier = modifier
            .clip(PokedexShapes.card)
            .background(colors.caseSurface)
            .border(dimens.rimWidth, colors.rim, PokedexShapes.card)
            .padding(dimens.spaceMd)
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(label, value, caption).joinToString(", ")
            },
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXxs),
    ) {
        Text(text = label, style = PokedexTheme.text.badgeLabel, color = colors.onCaseMuted)
        Text(
            text = value,
            style = PokedexTheme.text.titleNumerals,
            color = if (accent) colors.accentText else colors.onCase,
        )
        if (caption != null) {
            Text(text = caption, style = PokedexTheme.text.dexNumber, color = colors.onCaseMuted)
        }
    }
}

@Composable
private fun SpeciesSamples() {
    val dimens = PokedexTheme.dimens
    Column(
        modifier = Modifier.padding(dimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceLg),
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
        SpeciesCard(
            name = "Unown",
            dexNumber = 201,
            types = listOf(PokemonType.Psychic),
            state = SlotState.Needed,
            formName = "Want",
            details = listOf("Fills 28 slots", "Massive mass outbreak in Legends Arceus, 1 in 216"),
            onClick = {},
            sprite = { DemoSprite(it) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
            StatTile(
                label = "Collection",
                value = "412",
                caption = "of 1394",
                accent = true,
                modifier = Modifier.weight(1f),
            )
            StatTile(label = "Boxes done", value = "9", caption = "of 52", modifier = Modifier.weight(1f))
            StatTile(label = "Shiny locked", value = "43", caption = "unreachable", modifier = Modifier.weight(1f))
        }
    }
}

@Preview(name = "Species dark", widthDp = 380, heightDp = 820)
@Composable
private fun SpeciesDarkPreview() {
    PokedexTheme(darkTheme = true) { SpeciesSamples() }
}

@Preview(name = "Species light", widthDp = 380, heightDp = 820)
@Composable
private fun SpeciesLightPreview() {
    PokedexTheme(darkTheme = false) { SpeciesSamples() }
}
