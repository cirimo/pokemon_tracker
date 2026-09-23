package net.pokedex.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * See [PokedexElevation] in Dimens.kt for why this token set is deliberately tiny.
 *
 * Kept in its own file because "where is elevation allowed" is a question people ask, and
 * a file named after it is the answer.
 */
internal val LocalPokedexElevation = staticCompositionLocalOf { PokedexElevation() }
