package net.pokedex.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The app theme.
 *
 * Note what this function does NOT take: a dynamicColor flag and a darkTheme flag.
 * Both omissions are decisions, not oversights (docs/design-decisions.md):
 *
 *  - Dynamic colour is off permanently. Gold must mean "shiny", and Monet would rotate
 *    the accent to whatever the wallpaper is. The accepted cost is that the app does
 *    not match the system theme.
 *  - There is one scheme. A display case is a dark object; a light variant would be a
 *    different product, not a different mode.
 */
private val CaseScheme = darkColorScheme(
    primary = CaseColors.Gold,
    onPrimary = CaseColors.CaseBlack,
    secondary = CaseColors.GoldDim,
    onSecondary = CaseColors.OnCase,
    background = CaseColors.CaseBlack,
    onBackground = CaseColors.OnCase,
    surface = CaseColors.CaseSurface,
    onSurface = CaseColors.OnCase,
    surfaceVariant = CaseColors.CaseSurfaceHigh,
    onSurfaceVariant = CaseColors.OnCaseMuted,
    outline = CaseColors.SlotRim,
    error = CaseColors.Error,
    onError = CaseColors.OnError,
)

val LocalPokedexDimens = staticCompositionLocalOf { PokedexDimens() }

@Composable
fun PokedexTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPokedexDimens provides PokedexDimens()) {
        MaterialTheme(
            colorScheme = CaseScheme,
            typography = PokedexTypography,
            content = content,
        )
    }
}

/** Our tokens, alongside MaterialTheme rather than hidden inside it. */
object PokedexTheme {
    val dimens: PokedexDimens
        @Composable
        @ReadOnlyComposable
        get() = LocalPokedexDimens.current
}
