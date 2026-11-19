package net.pokedex.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.pokedex.designsystem.theme.PokedexShapes
import net.pokedex.designsystem.theme.PokedexTheme
import net.pokedex.designsystem.theme.PokemonType
import net.pokedex.designsystem.theme.swatch

/**
 * The detail-screen hero, and its list-row sibling.
 *
 * [SpeciesHeader] is the only place in the app where a sprite is drawn large, so it is the
 * only place the shiny art is actually *looked at* rather than scanned. It gets the space
 * accordingly: a big well, the name, the dex number, and the types. Everything else on the
 * detail screen goes below it.
 */

/**
 * The detail hero: the slot, grown into a plinth.
 *
 * The plinth is the same language as a grid slot -- a well, a rim, the silhouette rule --
 * at screen width, so opening a slot reads as the slot getting bigger, which is what the
 * shared element in [slotSharedElementKey] animates. Three things the grid cannot afford
 * happen here, because this is one surface rather than thirty: the type's container colour
 * rises from the base, a soft light sits behind the sprite, and a shadow pools under it.
 * All three are brushes allocated once in `drawWithCache`.
 *
 * Caught keeps the grid's own signals at hero scale -- the 2dp gold rim and the pip -- and a
 * regular keeps its ring. The type colour is the container tone, never gold, and the
 * type's name is always spoken by the badges under the name.
 */
@Composable
fun SpeciesHeader(
    name: String,
    dexNumber: Int,
    types: List<PokemonType?>,
    caught: Boolean,
    modifier: Modifier = Modifier,
    formName: String? = null,
    /** The regular one is held and the shiny is not: drawn and spoken as the grid's regular slot. */
    regular: Boolean = false,
    sprite: @Composable (SlotSpriteRendering) -> Unit = {},
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val state = when {
        caught -> SlotState.Caught
        regular -> SlotState.Regular
        else -> SlotState.Needed
    }
    val rendering = state.spriteRendering(colors)
    val swatch = types.firstOrNull { it != null }?.swatch()
    val tint = swatch?.container(colors) ?: colors.caseSurfaceHigh
    val glow = swatch?.on(colors) ?: colors.onCaseMuted
    val shape = RoundedCornerShape(dimens.heroRadius)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(HERO_ASPECT)
                .clip(shape)
                .drawWithCache {
                    val base = Brush.verticalGradient(0f to colors.slotWell, BASE_RISE to colors.slotWell, 1f to tint)
                    val light = Brush.radialGradient(
                        0f to glow.copy(alpha = if (colors.isDark) GLOW_DARK else GLOW_LIGHT),
                        1f to Color.Transparent,
                        center = Offset(size.width / 2, size.height * LIGHT_Y),
                        radius = size.minDimension * HALF,
                    )
                    // The pool is a circle of shadow squashed into an ellipse, so it fades out on
                    // every side. A radial brush in a thin rectangle is cut flat at the rectangle.
                    val poolCentre = Offset(size.width / 2, size.height * POOL_Y)
                    val poolRadius = size.width * POOL_RADIUS
                    val pool = Brush.radialGradient(
                        0f to colors.scrim.copy(alpha = colors.scrim.alpha * POOL_DEPTH),
                        1f to Color.Transparent,
                        center = poolCentre,
                        radius = poolRadius,
                    )
                    onDrawBehind {
                        drawRect(base)
                        drawRect(light)
                        scale(scaleX = 1f, scaleY = POOL_SQUASH, pivot = poolCentre) {
                            drawCircle(pool, radius = poolRadius, center = poolCentre)
                        }
                    }
                }
                .border(
                    width = if (caught) dimens.rimCaughtWidth else dimens.rimWidth,
                    color = if (caught) colors.rimCaught else colors.rim,
                    shape = shape,
                )
                .clearAndSetSemantics { contentDescription = state.describe(name) },
        ) {
            Box(Modifier.fillMaxSize().padding(dimens.spaceXxl)) { sprite(rendering) }
            if (state != SlotState.Needed) {
                // The grid's mark, in the grid's place, sized like a grid tile's corner.
                SlotMarkGlyph(
                    state.mark,
                    colors,
                    Modifier.align(Alignment.TopEnd).padding(dimens.spaceSm).size(HERO_MARK),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXxs)) {
            Text(
                text = "No. ${dexNumber.toString().padStart(DEX_DIGITS, '0')}",
                style = PokedexTheme.text.dexNumber,
                color = colors.onCaseMuted,
            )
            Text(text = name, style = PokedexTheme.text.heroTitle, color = colors.onCase)
            if (formName != null) {
                Text(text = formName, style = MaterialTheme.typography.bodyMedium, color = colors.onCaseMuted)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
            types.forEach { TypeBadge(it) }
        }
    }
}

private const val HERO_ASPECT = 1.1f
private val HERO_MARK = 64.dp
private const val BASE_RISE = 0.55f
private const val GLOW_DARK = 0.22f
private const val GLOW_LIGHT = 0.35f
private const val LIGHT_Y = 0.48f
private const val HALF = 0.5f
private const val POOL_Y = 0.86f
private const val POOL_RADIUS = 0.32f
private const val POOL_SQUASH = 0.18f
private const val POOL_DEPTH = 0.8f
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
