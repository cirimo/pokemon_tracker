package net.pokedex.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.pokedex.designsystem.icon.PokedexIcons
import net.pokedex.designsystem.theme.PokedexShapes
import net.pokedex.designsystem.theme.PokedexTheme
import net.pokedex.designsystem.theme.PokemonType

/*
 * The pieces the "how" screens are built from: a hunt as a card with its odds as the figure
 * to read, a stat as a number with its name, facts as a table, and a guide as steps.
 *
 * Prompt 9's counter and prompt 11's guides compose these rather than inventing their own;
 * that is why they live here before either screen exists.
 */

/**
 * A hunt: the species, why it is here, and its odds as the one figure on the right.
 *
 * [featured] lights the surface, for the single hunt a screen puts forward (the next hunt on
 * the boxes screen). A list of hunts stays on the plain surface: a list of lit cards is a
 * list of cards again. Odds are never gold -- a good hunt is not a shiny.
 */
@Composable
fun HuntCard(
    name: String,
    lines: List<String>,
    modifier: Modifier = Modifier,
    odds: String? = null,
    oddsLabel: String = "odds",
    types: List<PokemonType?> = emptyList(),
    featured: Boolean = false,
    onClick: (() -> Unit)? = null,
    sprite: @Composable (SlotSpriteRendering) -> Unit = {},
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val content: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(HUNT_WELL)
                    .clip(PokedexShapes.slot)
                    .background(colors.slotWell)
                    .border(dimens.rimWidth, colors.rim, PokedexShapes.slot),
            ) {
                Box(Modifier.fillMaxSize().padding(dimens.spaceXs)) {
                    sprite(SlotState.Needed.spriteRendering(colors))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.spaceXxs)) {
                Text(
                    text = name,
                    style = if (featured) PokedexTheme.text.sectionTitle else MaterialTheme.typography.titleMedium,
                    color = colors.onCase,
                )
                lines.forEach { line ->
                    Text(text = line, style = MaterialTheme.typography.bodySmall, color = colors.onCaseMuted)
                }
                if (types.any { it != null }) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
                        modifier = Modifier.padding(top = dimens.spaceXxs),
                    ) { types.forEach { TypeBadge(it) } }
                }
            }
            if (odds != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = odds,
                        style = PokedexTheme.text.statValue,
                        color = colors.onCase,
                        textAlign = TextAlign.End
                    )
                    Text(text = oddsLabel, style = MaterialTheme.typography.labelSmall, color = colors.onCaseMuted)
                }
            }
        }
    }
    val spoken = (listOf(name) + lines + listOfNotNull(odds?.let { "$oddsLabel $it" }))
        .plus(types.filterNotNull().map { it.name })
        .joinToString(". ")
    val frame = modifier
        .fillMaxWidth()
        .heightIn(min = dimens.touchTargetMin)
        .clip(PokedexShapes.card)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .clearAndSetSemantics { contentDescription = spoken }
    if (featured) {
        LitSurface(modifier = frame, contentPadding = dimens.spaceMd) { content() }
    } else {
        Box(
            frame
                .background(colors.caseSurface)
                .border(dimens.rimWidth, colors.rim, PokedexShapes.card)
                .padding(dimens.spaceMd),
        ) { content() }
    }
}

private val HUNT_WELL = 64.dp

/**
 * A figure and its name, set as a figure: the serif numerals the rest of the app uses for
 * counts, with the label under it. For odds, counts and encounter totals on a hunt or guide
 * screen. [accent] is gold, and only for a shiny count.
 */
@Composable
fun StatBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val colors = PokedexTheme.colors
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = "$label, $value" }) {
        Text(
            text = value,
            style = PokedexTheme.text.screenTitle,
            color = if (accent) colors.accentText else colors.onCase,
        )
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.onCaseMuted)
    }
}

/**
 * Facts as a table: one row per thing, its name on the left and its state on the right,
 * hairlines between. Replaces a stack of paragraphs that each said the same few words.
 */
@Composable
fun InfoTable(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PokedexShapes.card)
            .background(colors.caseSurface)
            .border(dimens.rimWidth, colors.rim, PokedexShapes.card),
        content = content,
    )
}

/**
 * One row of an [InfoTable]. [icon] is a second signal beside [value] (a padlock for
 * shiny-locked), never the only one. [details] go under the row, for what needs more than a
 * word -- a recorded method, its location, its source.
 */
@Composable
fun InfoRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    emphasis: Boolean = false,
    icon: ImageVector? = null,
    /** A row that opens something: the whole row is the target, and it says where it goes. */
    onClick: (() -> Unit)? = null,
    details: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    Column(modifier = modifier.fillMaxWidth()) {
        if (!first) Box(Modifier.fillMaxWidth().height(dimens.rimWidth).background(colors.rim))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = dimens.touchTargetMin)
                .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
        ) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onCase,
                    modifier = Modifier.weight(1f),
                )
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = colors.onCaseMuted,
                        modifier = Modifier.size(dimens.iconSm)
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (emphasis) colors.onCase else colors.onCaseMuted,
                    textAlign = TextAlign.End,
                )
            }
            details?.invoke(this)
        }
    }
}

/** Where a guide step stands. */
enum class StepState { Done, Current, Upcoming }

@Immutable
data class GuideStep(val title: String, val body: String? = null, val state: StepState = StepState.Upcoming)

/**
 * A guide, step by step: a numbered marker per step joined by a line, the step you are on
 * ringed, the ones behind you ticked. Numbers because the order is the information. No gold:
 * a finished step is not a shiny.
 */
@Composable
fun StepList(
    steps: List<GuideStep>,
    modifier: Modifier = Modifier,
    onStepClick: ((Int) -> Unit)? = null,
) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    Column(modifier = modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            val state = when (step.state) {
                StepState.Done -> "done"
                StepState.Current -> "current step"
                StepState.Upcoming -> "to do"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .then(if (onStepClick != null) Modifier.clickable { onStepClick(index) } else Modifier)
                    .clearAndSetSemantics {
                        contentDescription = listOfNotNull(
                            "Step ${index + 1} of ${steps.size}, $state",
                            step.title,
                            step.body,
                        ).joinToString(". ")
                    },
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
                    StepMarker(index + 1, step.state)
                    if (index < steps.lastIndex) {
                        Box(Modifier.width(dimens.rimWidth).weight(1f).background(colors.rim))
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).padding(bottom = dimens.spaceLg, top = dimens.spaceXs),
                    verticalArrangement = Arrangement.spacedBy(dimens.spaceXxs),
                ) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (step.state == StepState.Done) colors.onCaseMuted else colors.onCase,
                    )
                    step.body?.let {
                        Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.onCaseMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepMarker(number: Int, state: StepState) {
    val colors = PokedexTheme.colors
    val dimens = PokedexTheme.dimens
    val base = Modifier.size(STEP_MARKER).clip(CircleShape)
    val marker = when (state) {
        StepState.Done -> base.background(colors.onCaseMuted)
        StepState.Current -> base.background(
            colors.caseSurfaceHigh
        ).border(dimens.focusRingWidth, colors.rimFocus, CircleShape)
        StepState.Upcoming -> base.border(dimens.rimWidth, colors.onCaseMuted, CircleShape)
    }
    Box(marker, contentAlignment = Alignment.Center) {
        if (state == StepState.Done) {
            Icon(
                PokedexIcons.Check,
                contentDescription = null,
                tint = colors.case,
                modifier = Modifier.size(dimens.iconSm)
            )
        } else {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (state == StepState.Current) colors.onCase else colors.onCaseMuted,
            )
        }
    }
}

private val STEP_MARKER = 32.dp

@Composable
internal fun GuideSamples() {
    val dimens = PokedexTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceLg)) {
        HuntCard(
            name = "Unown",
            lines = listOf("Massive mass outbreak · Legends Arceus", "Fills 25 slots, finishes Unown Dex"),
            odds = "1 in 216",
            featured = true,
        ) { DemoSprite(it) }
        HuntCard(
            name = "Lumineon",
            lines = listOf("Mass outbreak · Legends Arceus", "Sinnoh 4: 9 to go"),
            odds = "1 in 128",
            types = listOf(PokemonType.Water),
        ) { DemoSprite(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceXl)) {
            StatBlock(value = "1 in 512", label = "with the Shiny Charm")
            StatBlock(value = "766", label = "shiny caught", accent = true)
        }
        InfoTable {
            InfoRow(title = "Violet", value = "Shiny", first = true, emphasis = true)
            InfoRow(title = "Legends Z-A", value = "Caught here")
            InfoRow(title = "Sword", value = "Shiny-locked", icon = PokedexIcons.Locked)
        }
        StepList(
            steps = listOf(
                GuideStep("Build a Ditto team", "Catch four foreign Ditto in Dynamax Adventures.", StepState.Done),
                GuideStep("Unlock Dynamax Adventures", "Talk to Peony in the Max Lair.", StepState.Current),
                GuideStep("Run for the legendary", "Pick it as the den's final boss."),
            ),
        )
    }
}

@Preview(name = "Guide pieces dark", widthDp = 360, heightDp = 1000)
@Composable
private fun GuideDarkPreview() {
    PokedexTheme(darkTheme = true) { GuideSamples() }
}

@Preview(name = "Guide pieces light", widthDp = 360, heightDp = 1000)
@Composable
private fun GuideLightPreview() {
    PokedexTheme(darkTheme = false) { GuideSamples() }
}

@Preview(name = "Guide pieces 200% font", widthDp = 360, heightDp = 1400, fontScale = 2f)
@Composable
private fun GuideLargeFontPreview() {
    PokedexTheme(darkTheme = true) { GuideSamples() }
}
