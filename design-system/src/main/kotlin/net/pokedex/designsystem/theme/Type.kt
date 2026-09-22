package net.pokedex.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale.
 *
 * M0 uses the platform default family deliberately. No official Pokemon fonts, ever
 * (docs/design-decisions.md), and picking a real display face is M1 work that should
 * happen with the components in front of you rather than in a vacuum.
 */
internal val PokedexTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
        ),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        // Progress numerals are one of the three places gold is allowed, so they get a
        // style of their own rather than being an ad-hoc colour override at the call site.
        labelLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            letterSpacing = 0.4.sp,
        ),
    )
}
