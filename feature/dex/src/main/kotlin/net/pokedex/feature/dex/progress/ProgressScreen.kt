package net.pokedex.feature.dex.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pokedex.core.model.CatchKey
import net.pokedex.designsystem.component.ActionButton
import net.pokedex.designsystem.component.BoxSummary
import net.pokedex.designsystem.component.ErrorState
import net.pokedex.designsystem.component.NoticeCard
import net.pokedex.designsystem.component.PokedexDialog
import net.pokedex.designsystem.component.ProgressBar
import net.pokedex.designsystem.component.ProgressReadout
import net.pokedex.designsystem.component.ScreenScaffold
import net.pokedex.designsystem.component.ScreenSection
import net.pokedex.designsystem.component.SettingRow
import net.pokedex.designsystem.component.SkeletonBox
import net.pokedex.designsystem.component.SlotState
import net.pokedex.designsystem.component.SpeciesCard
import net.pokedex.designsystem.theme.PokedexTheme
import net.pokedex.feature.dex.DexSprite
import net.pokedex.feature.dex.errorBody
import net.pokedex.feature.dex.errorTitle
import net.pokedex.feature.dex.typesOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun ProgressDestination(
    onBack: () -> Unit,
    onShowBox: (Int) -> Unit,
    onShowNeededIn: (gameSetId: String) -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgressScreen(state, viewModel::onEvent, onBack, onShowBox, onShowNeededIn, onOpenSlot)
}

/**
 * Where the collection stands. The order is the order of the questions: how far along, what
 * is nearly finished, what each game still has to offer, what just happened. Gold appears
 * only in the progress numerals, arcs and completed-box rims, where it always does.
 */
@Composable
internal fun ProgressScreen(
    state: ProgressUiState,
    onEvent: (ProgressEvent) -> Unit,
    onBack: () -> Unit,
    onShowBox: (Int) -> Unit,
    onShowNeededIn: (gameSetId: String) -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
) {
    ScreenScaffold(title = "Progress", onBack = onBack) {
        when {
            state.error != null -> ErrorState(
                title = errorTitle(state.error),
                body = errorBody(state.error),
                action = { onEvent(ProgressEvent.Retry) },
            )
            state.loading -> repeat(SKELETON_ROWS) {
                SkeletonBox(Modifier.fillMaxWidth().height(PokedexTheme.dimens.touchTargetMin))
            }
            else -> {
                Overall(state)
                if (state.orphans.isNotEmpty()) Orphans(state.orphans, onEvent)
                if (state.closest.isNotEmpty()) {
                    ScreenSection(title = "Closest to done", body = "Boxes you have started, fewest left first.") {
                        state.closest.forEach { box ->
                            BoxSummary(box.name, box.caught, box.total, onClick = { onShowBox(box.boxIndex) })
                        }
                    }
                }
                ScreenSection(title = "Still needed, by game", body = NEEDED_BODY) {
                    state.games.forEach { game ->
                        SettingRow(
                            title = game.label,
                            summary = if (game.needed == 0) {
                                "Nothing left to catch shiny here"
                            } else {
                                "${game.needed} still needed"
                            },
                            onClick = { onShowNeededIn(game.gameSetId) },
                        )
                    }
                }
                Regions(state.regions, onShowBox)
                Recent(state.recent, onOpenSlot)
            }
        }
    }
}

@Composable
private fun Overall(state: ProgressUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceSm)) {
        ProgressReadout(caught = state.overall.caught, total = state.overall.total, label = "shiny")
        ProgressBar(caught = state.overall.caught, total = state.overall.total, label = "Whole dex")
        if (state.noShinyYet > 0) {
            Text(
                text = "${state.noShinyYet} of the ${state.overall.remaining} left have no shiny released yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = PokedexTheme.colors.onCaseMuted,
            )
        }
    }
}

@Composable
private fun Regions(regions: List<RegionRow>, onShowBox: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    ScreenSection(title = "By region", body = "Tap a region for its boxes.") {
        regions.forEach { region ->
            BoxSummary(
                name = region.name,
                caught = region.caught,
                total = region.total,
                onClick = { open = region.name.takeIf { it != open } },
            )
            if (open == region.name) {
                region.boxes.forEach { box ->
                    BoxSummary(box.name, box.caught, box.total, onClick = { onShowBox(box.boxIndex) })
                }
            }
        }
    }
}

@Composable
private fun Recent(recent: List<RecentRow>, onOpenSlot: (CatchKey) -> Unit) {
    if (recent.isEmpty()) return
    ScreenSection(title = "Recent catches") {
        recent.forEach { row ->
            SpeciesCard(
                name = row.name,
                dexNumber = row.dexNumber,
                types = typesOf(row.type1, row.type2),
                state = SlotState.Caught,
                formName = listOfNotNull(row.originName, formatDay(row.caughtAt)).joinToString(" · "),
                onClick = { onOpenSlot(row.key) },
                sprite = { rendering -> DexSprite(row.spriteFile, rendering) },
            )
        }
    }
}

@Composable
private fun Orphans(orphans: List<OrphanRow>, onEvent: (ProgressEvent) -> Unit) {
    var forgetting by rememberSaveable { mutableStateOf<String?>(null) }
    NoticeCard(
        title = "${orphans.size} caught without a slot",
        body = "The current preset has no slot for these. They are kept and still yours, and they are in every backup.",
    )
    orphans.forEach { orphan ->
        SettingRow(
            title = orphan.name,
            summary = listOfNotNull(orphan.originName, orphan.caughtAt?.let(::formatDay), orphan.notes)
                .joinToString(" · ")
                .ifEmpty { "No details recorded" },
        )
        ActionButton(
            label = "Forget ${orphan.name}",
            onClick = { forgetting = orphan.key.toString() },
            primary = false,
            destructive = true,
        )
    }
    val target = orphans.firstOrNull { it.key.toString() == forgetting }
    if (target != null) {
        PokedexDialog(
            onDismissRequest = { forgetting = null },
            title = "Forget ${target.name}?",
            body = "The record is deleted. Automatic backups made before now still hold it.",
            confirmLabel = "Forget",
            onConfirm = {
                forgetting = null
                onEvent(ProgressEvent.ForgetOrphan(target.key))
            },
            destructive = true,
        )
    }
}

private val DAY: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun formatDay(epochMillis: Long): String =
    DAY.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate())

private const val NEEDED_BODY =
    "Slots you still need that can be caught shiny in each game. Tap one to see them in the boxes."

private const val SKELETON_ROWS = 6
