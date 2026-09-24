package net.pokedex.feature.dex

import net.pokedex.core.model.AppError
import net.pokedex.core.model.DexEntry
import net.pokedex.core.model.GameSet
import net.pokedex.core.model.SlotStatus
import net.pokedex.designsystem.component.SlotState
import net.pokedex.designsystem.theme.PokemonType

/**
 * Domain to design-system translation, in one place.
 *
 * `:design-system` knows nothing of `:core:model` by rule, so something has to say that
 * "no shiny exists" is drawn as the shiny-locked slot. Doing it once here keeps the grid,
 * the search list and the detail screens from each deciding differently.
 */
internal fun SlotStatus.toSlotState(): SlotState = when (this) {
    SlotStatus.Caught -> SlotState.Caught
    SlotStatus.Needed -> SlotState.Needed
    SlotStatus.NoShinyExists -> SlotState.ShinyLocked
    SlotStatus.Unavailable -> SlotState.Unavailable
}

internal fun typesOf(type1: String, type2: String?): List<PokemonType?> =
    listOfNotNull(type1, type2).map(PokemonType::fromId)

internal fun typeLabel(id: String): String =
    PokemonType.fromId(id)?.name ?: id.replaceFirstChar(Char::uppercase)

/**
 * "Kanto 1, slot 3". Slots are counted from one, the way HOME and PokePC count them.
 *
 * A comma rather than a middle dot: TalkBack reads a comma as a pause and a dot as "dot".
 */
internal fun locationOf(entry: DexEntry): String = "${entry.boxName}, slot ${entry.slot.slotIndex + 1}"

/**
 * The chip label for a game pair.
 *
 * Short on purpose: nine chips of "Brilliant Diamond and Shining Pearl" do not fit a phone
 * row, and these are the names players actually use. A pair the dataset adds later falls
 * back to its version names rather than to a raw id.
 */
internal fun gameSetLabel(set: GameSet): String = GAME_SET_LABELS[set.id]
    ?: set.games.joinToString(" & ") { it.name }

private val GAME_SET_LABELS = mapOf(
    "lgpe" to "Let's Go",
    "swsh" to "Sword & Shield",
    "bdsp" to "BD & SP",
    "la" to "Legends Arceus",
    "sv" to "Scarlet & Violet",
    "lza" to "Legends Z-A",
)

/** Named precisely, per `ErrorState`'s contract: this app has no network to blame. */
internal fun errorTitle(error: AppError): String = when (error) {
    is AppError.Fatal -> "The bundled dataset could not be read"
    else -> "Something in the dex could not be loaded"
}

internal fun errorBody(error: AppError): String = when (error) {
    is AppError.Fatal ->
        "Reinstalling the app restores it. Your catch records are kept in a separate " +
            "database and are not affected."
    else -> error.toString()
}
