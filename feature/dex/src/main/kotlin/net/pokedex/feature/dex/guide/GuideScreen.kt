package net.pokedex.feature.dex.guide

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pokedex.core.model.CatchKey
import net.pokedex.designsystem.component.ErrorState
import net.pokedex.designsystem.component.GuideStep
import net.pokedex.designsystem.component.HuntCard
import net.pokedex.designsystem.component.InfoRow
import net.pokedex.designsystem.component.InfoTable
import net.pokedex.designsystem.component.LoadingState
import net.pokedex.designsystem.component.NoticeCard
import net.pokedex.designsystem.component.ScreenScaffold
import net.pokedex.designsystem.component.ScreenSection
import net.pokedex.designsystem.component.StatBlock
import net.pokedex.designsystem.component.StatRow
import net.pokedex.designsystem.component.StepList
import net.pokedex.designsystem.theme.PokedexTheme
import net.pokedex.feature.dex.DexSprite
import net.pokedex.feature.dex.errorBody
import net.pokedex.feature.dex.errorTitle
import net.pokedex.feature.dex.game.MutedLine
import net.pokedex.feature.dex.game.PrepTable
import net.pokedex.feature.dex.typesOf

@Composable
internal fun GuideDestination(
    onBack: () -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
    viewModel: GuideViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GuideScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, onOpenSlot = onOpenSlot)
}

/**
 * A hunt, step by step, for the Switch in my hands (prompt 11). Read top to bottom in the order
 * it is done: the odds and what it costs, what it needs, the builds, the rentals, the steps,
 * the mistakes, and where every claim came from.
 *
 * The hunt is the one lit card. Odds are figures in a [StatRow], never gold, and never
 * mid-sentence: they are what a person compares between guides.
 */
@Composable
internal fun GuideScreen(
    state: GuideUiState,
    onEvent: (GuideEvent) -> Unit,
    onBack: () -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
) {
    ScreenScaffold(title = state.title.ifEmpty { "Guide" }, onBack = onBack) {
        when {
            state.error != null -> ErrorState(
                title = errorTitle(state.error),
                body = errorBody(state.error),
                action = { onEvent(GuideEvent.Retry) },
            )
            state.loading -> LoadingState()
            else -> {
                val toggle = { refId: String, done: Boolean -> onEvent(GuideEvent.TogglePrep(refId, done)) }
                state.target?.let { target ->
                    HuntCard(
                        name = target.name,
                        lines = target.lines,
                        types = typesOf(target.type1, target.type2),
                        featured = true,
                        onClick = target.lead?.let { lead -> { onOpenSlot(lead) } },
                        sprite = { rendering -> DexSprite(target.spriteFile, rendering) },
                    )
                }
                if (state.figures.isNotEmpty()) {
                    StatRow { state.figures.forEach { StatBlock(value = it.value, label = it.label) } }
                }
                if (state.oneInSave) {
                    NoticeCard(
                        title = "One per save",
                        body = "Once you keep one, it never appears again on this save. Keep only a shiny one.",
                        warning = true,
                    )
                }

                if (state.requirements.isNotEmpty()) {
                    ScreenSection(
                        title = "What it needs",
                        body = "Ticked per game, and the same ticks as the game page."
                    ) {
                        PrepTable(state.requirements, onToggle = toggle)
                    }
                }

                state.builds.forEach { build -> Build(build, toggle) }

                Rentals(state.advice)
                Steps(state)
                if (state.sources.isNotEmpty()) {
                    ScreenSection(title = "Sources", body = "Every step above was read from one of these.") {
                        state.sources.forEach { MutedLine(it) }
                    }
                }
            }
        }
    }
}

/** A build: tick it when made, what it is for, its parts with a reason each, and how to get it in order. */
@Composable
private fun Build(build: BuildUi, onToggle: (String, Boolean) -> Unit) {
    ScreenSection(
        title = build.tick.title,
        body = listOfNotNull(build.role?.let { "Role: $it." }, build.purpose).joinToString(" "),
    ) {
        // The purpose is already the section's body; the row only ticks.
        PrepTable(
            listOf(build.tick.copy(title = "Made and ready", lines = emptyList())),
            onToggle = onToggle,
            showFarm = false
        )
        InfoTable {
            build.parts.forEachIndexed { index, part ->
                InfoRow(
                    title = part.value,
                    value = part.label,
                    first = index == 0,
                    emphasis = true,
                    details = { MutedLine(part.why) },
                )
            }
        }
        if (build.farm.isNotEmpty()) {
            StepList(steps = build.farm.map { GuideStep(it.title, it.body) })
        }
    }
}

/** Rental advice for hunts where you cannot bring your own Pokemon: what to take, what to avoid, and why. */
@Composable
private fun Rentals(advice: List<AdviceUi>) {
    if (advice.isEmpty()) return
    ScreenSection(
        title = "Rentals",
        body = "You cannot bring your own Pokémon. Pick from the three offered with this in mind.",
    ) {
        InfoTable {
            advice.forEachIndexed { index, row ->
                InfoRow(
                    title = row.subject,
                    value = row.label,
                    first = index == 0,
                    emphasis = true,
                    details = { MutedLine(row.why) },
                )
            }
        }
    }
}

/** The steps, section by section, then the mistakes, which have no order and so no numbers. */
@Composable
private fun Steps(state: GuideUiState) {
    state.sections.forEach { section ->
        ScreenSection(title = section.title) {
            StepList(steps = section.steps.map { GuideStep(it.title, it.body) })
        }
    }
    if (state.mistakes.isNotEmpty()) {
        ScreenSection(title = "Mistakes that lose it") {
            state.mistakes.forEach { mistake ->
                Text(text = mistake, style = MaterialTheme.typography.bodyMedium, color = PokedexTheme.colors.onCase)
            }
        }
    }
}
