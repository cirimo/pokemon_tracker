package net.pokedex.feature.dex.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.SlotStatus
import net.pokedex.designsystem.component.ErrorState
import net.pokedex.designsystem.component.ScreenScaffold
import net.pokedex.designsystem.component.ScreenSection
import net.pokedex.designsystem.component.SkeletonBox
import net.pokedex.designsystem.component.SpeciesCard
import net.pokedex.designsystem.component.SpeciesHeader
import net.pokedex.designsystem.theme.PokedexTheme
import net.pokedex.feature.dex.DexSprite
import net.pokedex.feature.dex.errorBody
import net.pokedex.feature.dex.errorTitle
import net.pokedex.feature.dex.toSlotState
import net.pokedex.feature.dex.typesOf

@Composable
internal fun VariantDetailDestination(
    onBack: () -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
    onOpenVariant: (String) -> Unit,
    viewModel: VariantDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VariantDetailScreen(state = state, onBack = onBack, onOpenSlot = onOpenSlot, onOpenVariant = onOpenVariant)
}

@Composable
internal fun VariantDetailScreen(
    state: VariantDetailUiState,
    onBack: () -> Unit,
    onOpenSlot: (CatchKey) -> Unit,
    onOpenVariant: (String) -> Unit,
) {
    val variant = state.variant
    ScreenScaffold(title = variant?.name ?: "Species", onBack = onBack) {
        when {
            state.error != null -> ErrorState(
                title = errorTitle(state.error),
                body = errorBody(state.error),
                actionLabel = null,
            )
            variant == null -> SkeletonBox(Modifier.fillMaxWidth().aspectRatio(1f))
            else -> VariantDetailContent(state, variant, onOpenSlot, onOpenVariant)
        }
    }
}

@Composable
private fun VariantDetailContent(
    state: VariantDetailUiState,
    variant: VariantUi,
    onOpenSlot: (CatchKey) -> Unit,
    onOpenVariant: (String) -> Unit,
) {
    SpeciesHeader(
        name = variant.name,
        dexNumber = variant.dexNumber,
        types = typesOf(variant.type1, variant.type2),
        caught = variant.status == SlotStatus.Caught,
        formName = variant.formName?.takeUnless { variant.name.contains(it, ignoreCase = true) },
        sprite = { rendering -> DexSprite(variant.spriteFile, rendering) },
    )

    state.origin?.let {
        Text(text = it, style = MaterialTheme.typography.bodyMedium, color = PokedexTheme.colors.onCaseMuted)
    }

    ScreenSection(
        title = "In your dex",
        body = if (state.slots.size > 1) "This preset asks for ${state.slots.size} of these." else null,
    ) {
        state.slots.forEach { copy ->
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

    if (state.evolvesFrom != null || state.evolvesInto.isNotEmpty()) {
        ScreenSection(title = "Evolution") {
            state.evolvesFrom?.let { from ->
                RelatedRow(from, prefix = "Evolves from", onOpen = onOpenVariant)
            }
            state.evolvesInto.forEach { into ->
                RelatedRow(into, prefix = "Evolves into", onOpen = onOpenVariant)
            }
        }
    }

    if (state.forms.size > 1) {
        ScreenSection(title = "Forms", body = "Every form of No. ${variant.dexNumber} this preset asks for.") {
            state.forms.forEach { form ->
                RelatedRow(form, prefix = if (form.isCurrent) "This form" else null, onOpen = onOpenVariant)
            }
        }
    }
}

/**
 * A related variant as a card, with the relationship spelled out above it.
 *
 * The condition goes in the card's secondary line so TalkBack reads it with the name
 * ("Raichu, not yet caught. Thunder stone."), rather than as a stray label nearby.
 */
@Composable
private fun RelatedRow(related: RelatedUi, prefix: String?, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PokedexTheme.dimens.spaceXs)) {
        if (prefix != null) {
            Text(text = prefix, style = PokedexTheme.text.dexNumber, color = PokedexTheme.colors.onCaseMuted)
        }
        SpeciesCard(
            name = related.name,
            dexNumber = related.dexNumber,
            types = typesOf(related.type1, related.type2),
            state = related.status.toSlotState(),
            formName = related.note?.replaceFirstChar(Char::uppercase),
            onClick = if (related.isCurrent) null else ({ onOpen(related.id) }),
            sprite = { rendering -> DexSprite(related.spriteFile, rendering) },
        )
    }
}
