package net.pokedex.feature.dex.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.SlotStatus
import net.pokedex.designsystem.component.CaughtToggle
import net.pokedex.designsystem.component.ErrorState
import net.pokedex.designsystem.component.GameBadge
import net.pokedex.designsystem.component.ScreenScaffold
import net.pokedex.designsystem.component.ScreenSection
import net.pokedex.designsystem.component.SkeletonBox
import net.pokedex.designsystem.component.SpeciesCard
import net.pokedex.designsystem.component.SpeciesHeader
import net.pokedex.designsystem.theme.PokedexTheme
import net.pokedex.feature.dex.DexSprite
import net.pokedex.feature.dex.errorBody
import net.pokedex.feature.dex.errorTitle
import net.pokedex.feature.dex.slotDestination
import net.pokedex.feature.dex.toSlotState
import net.pokedex.feature.dex.typesOf

@Composable
internal fun SlotDetailDestination(
    onBack: () -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
    onOpenVariant: (String) -> Unit,
    onShowInBox: (Int) -> Unit,
    onOpenMyGames: () -> Unit,
    viewModel: SlotDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SlotDetailScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenSlot = onOpenSlot,
        onOpenVariant = onOpenVariant,
        onShowInBox = onShowInBox,
        onOpenMyGames = onOpenMyGames,
    )
}

/**
 * "What does this slot want, and where do I get it?"
 *
 * Order is the order of the questions: what it is (the hero), whether I have it (the
 * toggle, directly under the thing it toggles), whether I need another one (only for the
 * seven duplicates), how much I want it, then how to catch it in my games.
 */
@Composable
internal fun SlotDetailScreen(
    state: SlotDetailUiState,
    onEvent: (SlotDetailEvent) -> Unit,
    onBack: () -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
    onOpenVariant: (String) -> Unit,
    onShowInBox: (Int) -> Unit,
    onOpenMyGames: () -> Unit,
) {
    val slot = state.slot
    ScreenScaffold(title = slot?.location ?: "Slot", onBack = onBack) {
        when {
            state.error != null -> ErrorState(
                title = errorTitle(state.error),
                body = errorBody(state.error),
                actionLabel = null,
            )
            slot == null -> SkeletonBox(Modifier.fillMaxWidth().aspectRatio(1f))
            else -> SlotDetailContent(state, slot, onEvent, onOpenSlot, onOpenVariant, onShowInBox, onOpenMyGames)
        }
    }
}

@Composable
private fun SlotDetailContent(
    state: SlotDetailUiState,
    slot: SlotUi,
    onEvent: (SlotDetailEvent) -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
    onOpenVariant: (String) -> Unit,
    onShowInBox: (Int) -> Unit,
    onOpenMyGames: () -> Unit,
) {
    val colors = PokedexTheme.colors
    val caught = slot.status == SlotStatus.Caught

    SpeciesHeader(
        name = slot.name,
        dexNumber = slot.dexNumber,
        types = typesOf(slot.type1, slot.type2),
        caught = caught,
        // The display name already carries the form for most variants ("Venusaur (Female)");
        // repeating it underneath would say it twice.
        formName = slot.formName?.takeUnless { slot.name.contains(it, ignoreCase = true) },
        sprite = { rendering -> DexSprite(slot.spriteFile, rendering, Modifier.slotDestination(slot.slotKey)) },
    )

    if (!state.shinyReleased) {
        // Enabled anyway: the dataset can lag a real distribution, and refusing to record a
        // Pokemon the user is holding would be the app contradicting a fact.
        Text(
            text = "No game has released a shiny ${slot.name} yet. If one has arrived since " +
                "this dataset was built, you can still mark it caught.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onCaseMuted,
        )
    }

    CaughtToggle(caught = caught, onCaughtChange = { onEvent(SlotDetailEvent.SetCaught(it)) })
    CatchDetails(
        caught = caught,
        record = state.record,
        origins = state.origins,
        onSave = { origin, at, notes -> onEvent(SlotDetailEvent.SaveDetails(origin, at, notes)) },
        onForget = { onEvent(SlotDetailEvent.Forget) },
    )

    if (state.copies.size > 1) {
        ScreenSection(
            title = "Needed ${state.copies.size} times",
            body = "This preset asks for ${state.copies.size} of this Pokémon. This slot is copy " +
                "${state.copyNumber} of ${state.copies.size}, and each copy is marked caught on its own.",
        ) {
            state.copies.filter { it.key != slot.key }.forEach { copy ->
                SpeciesCard(
                    name = copy.name,
                    dexNumber = copy.dexNumber,
                    types = typesOf(copy.type1, copy.type2),
                    state = copy.status.toSlotState(),
                    formName = copy.location,
                    onClick = { onOpenSlot(copy.key) },
                    sprite = { rendering -> DexSprite(copy.spriteFile, rendering) },
                )
            }
        }
    }

    if (!caught) PrioritySection(state.priority, onPick = { onEvent(SlotDetailEvent.SetPriority(it)) })

    HuntingSection(state.hunting, state.gamesChosen, onOpenMyGames)

    // Every game at a glance, once "In your games" has narrowed the section above. Before
    // games are chosen that section already lists every game, so this would repeat it.
    if (state.gamesChosen) GamesSection(state.games)

    ChipFlow {
        OutlinedButton(onClick = { onShowInBox(slot.boxIndex) }) {
            Text("Show in box", style = PokedexTheme.text.badgeLabel)
        }
        OutlinedButton(onClick = { onOpenVariant(slot.variantId) }) {
            Text("Species and forms", style = PokedexTheme.text.badgeLabel)
        }
    }
}

/**
 * "Where can I get it shiny?", per version, in release order.
 *
 * Versions rather than pairs here, unlike the filter: the filter answers "is it in the game
 * I am playing", this answers "which cartridge", and version exclusives are the reason the
 * two differ. A lock is a badge with a padlock and its reason spelled out underneath, never
 * a colour.
 */
@Composable
private fun GamesSection(games: List<GameUi>) {
    val dimens = PokedexTheme.dimens
    val colors = PokedexTheme.colors
    val obtainableShiny = games.any { !it.shinyLocked }
    ScreenSection(
        title = "Every game",
        body = when {
            games.isEmpty() ->
                "Not obtainable in any Switch game. It can only arrive in HOME by transfer or through an event."
            !obtainableShiny -> "Obtainable, but shiny-locked in every game that has it."
            else -> null
        },
    ) {
        if (games.isNotEmpty()) {
            ChipFlow {
                games.forEach { game ->
                    GameBadge(
                        name = if (game.eventOnly) "${game.name} (event)" else game.name,
                        shinyLocked = game.shinyLocked,
                    )
                }
            }
            val reasons = games.filter { it.shinyLocked && it.lockReason != null }
            if (reasons.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceXs)) {
                    reasons.forEach { game ->
                        Text(
                            text = "${game.name}: ${game.lockReason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onCaseMuted,
                        )
                    }
                }
            }
        }
    }
}
