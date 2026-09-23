package net.pokedex.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The app theme.
 *
 * Note what this function does NOT take: a `dynamicColor` flag. That omission is a
 * decision, not an oversight. Gold has to keep meaning "shiny", and Monet would rotate the
 * accent to whatever the wallpaper is; it would also make it impossible to contrast-check
 * 18 type colours against an accent we do not control. There is nothing to opt into.
 *
 * It *does* take [darkTheme], which reverses the position this file held at M0 -- see
 * docs/adr/0009-light-theme.md. Dark is still the direction the app is designed from;
 * light is an authored second palette, not a tint of the first.
 *
 * Three token sets are provided alongside the M3 one rather than inside it. [PokedexColors]
 * roles do not map onto M3 roles -- "the rim of a recessed well" is not `outline` -- and
 * quietly redefining M3 tokens to mean something else is how a design system stops being
 * readable. The M3 scheme below exists so that sheets, dialogs, chips and text fields
 * inherit sensible values; features read [PokedexTheme] for everything else.
 */
private fun PokedexColors.toMaterialScheme() = if (isDark) {
    darkColorScheme(
        primary = accentGraphic,
        onPrimary = case,
        secondary = onCaseMuted,
        onSecondary = case,
        background = case,
        onBackground = onCase,
        surface = caseSurface,
        onSurface = onCase,
        surfaceVariant = caseSurfaceHigh,
        onSurfaceVariant = onCaseMuted,
        surfaceContainer = caseSurface,
        surfaceContainerHigh = caseSurfaceHigh,
        outline = rim,
        outlineVariant = rim,
        error = errorGraphic,
        onError = case,
        scrim = scrim,
    )
} else {
    lightColorScheme(
        primary = accentGraphic,
        onPrimary = case,
        secondary = onCaseMuted,
        onSecondary = case,
        background = case,
        onBackground = onCase,
        surface = caseSurface,
        onSurface = onCase,
        surfaceVariant = caseSurfaceHigh,
        onSurfaceVariant = onCaseMuted,
        surfaceContainer = caseSurface,
        surfaceContainerHigh = caseSurfaceHigh,
        outline = rim,
        outlineVariant = rim,
        error = errorGraphic,
        onError = case,
        scrim = scrim,
    )
}

val LocalPokedexColors = staticCompositionLocalOf { DarkCase }
val LocalPokedexDimens = staticCompositionLocalOf { PokedexDimens() }
val LocalPokedexTextStyles = staticCompositionLocalOf { PokedexTextStyles() }
val LocalPokedexMotion = staticCompositionLocalOf { FullMotion }

@Composable
fun PokedexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkCase else LightCase
    // Resolved here, once, so that no component downstream has an opportunity to forget.
    val motion = if (rememberReduceMotion()) ReducedMotion else FullMotion

    CompositionLocalProvider(
        LocalPokedexColors provides colors,
        LocalPokedexDimens provides PokedexDimens(),
        LocalPokedexTextStyles provides PokedexTextStyles(),
        LocalPokedexMotion provides motion,
        LocalPokedexElevation provides PokedexElevation(),
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = PokedexTypography,
            content = content,
        )
    }
}

/** Our tokens, alongside MaterialTheme rather than hidden inside it. */
object PokedexTheme {
    val colors: PokedexColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPokedexColors.current

    val dimens: PokedexDimens
        @Composable
        @ReadOnlyComposable
        get() = LocalPokedexDimens.current

    val text: PokedexTextStyles
        @Composable
        @ReadOnlyComposable
        get() = LocalPokedexTextStyles.current

    val motion: PokedexMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalPokedexMotion.current

    val elevation: PokedexElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalPokedexElevation.current
}
