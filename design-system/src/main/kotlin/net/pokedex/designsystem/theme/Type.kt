package net.pokedex.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.pokedex.designsystem.R

/**
 * The type scale.
 *
 * Two families, with a clear division of labour:
 *
 *  - [PokedexSans] is Inter: neutral, high x-height, and -- the reason it was chosen --
 *    it has real tabular figures rather than synthesised ones.
 *  - [PokedexNumerals] is Instrument Serif, and appears in exactly two places: large
 *    progress numerals and box headers. That restraint is where the "premium" comes from.
 *    A serif everywhere would be a costume; a serif on "412 / 1394" is a label on a case.
 *
 * No official Pokemon fonts, ever (docs/design-decisions.md).
 *
 * ## Numerals
 *
 * Every number in this app is read in comparison to another number: counts against
 * totals, dex numbers down a column, odds against odds. Proportional figures make those
 * columns jitter, so `tnum` is switched on for every style that can hold a digit. This is
 * not a detail -- it is most of why a dense screen of numbers looks composed.
 */
private const val TABULAR = "tnum"

/**
 * Inter, as a single variable font.
 *
 * One 73KB file covers every weight rather than three static files at ~300KB each, because
 * the `wght` axis survived subsetting and `FontVariation` can drive it. Variable-font
 * settings need API 26, which is exactly minSdk -- so there is no guard here and no
 * fallback path, and that is deliberate rather than an oversight.
 *
 * The optical-size axis was pinned at subset time. Inter's `opsz` is a genuine improvement
 * at display sizes, but it would mean carrying a second axis for text that is mostly 12-18sp.
 *
 * See design-system/licenses/ -- both faces are SIL OFL and both are subset copies.
 *
 * The opt-in is for `Font(variationSettings = ...)`, which is still marked experimental in
 * the stable Compose line. It is contained to this one function on purpose -- nothing else
 * in the module, and nothing in any feature, sees the annotation.
 */
@OptIn(ExperimentalTextApi::class)
private fun interAt(weight: Int, weightToken: FontWeight) = Font(
    resId = R.font.inter,
    weight = weightToken,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

internal val PokedexSans: FontFamily = FontFamily(
    interAt(WEIGHT_REGULAR, FontWeight.Normal),
    interAt(WEIGHT_MEDIUM, FontWeight.Medium),
    interAt(WEIGHT_SEMIBOLD, FontWeight.SemiBold),
)

private const val WEIGHT_REGULAR = 400
private const val WEIGHT_MEDIUM = 500
private const val WEIGHT_SEMIBOLD = 600

/**
 * Instrument Serif, and only where it earns its place.
 *
 * One weight, because it appears in exactly two roles -- large progress numerals and box
 * headers -- and neither of them wants a bold. A serif with a weight range would invite
 * using it somewhere else.
 */
internal val PokedexNumerals: FontFamily = FontFamily(
    Font(resId = R.font.instrument_serif, weight = FontWeight.Normal),
)

/**
 * Styles that are ours rather than M3 roles.
 *
 * [displayNumerals] is the one gold is allowed on, so it exists as a named style instead
 * of being an ad-hoc colour override at the call site.
 */
@Immutable
data class PokedexTextStyles(
    val displayNumerals: TextStyle = TextStyle(
        fontFamily = PokedexNumerals,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = TABULAR,
    ),
    val titleNumerals: TextStyle = TextStyle(
        fontFamily = PokedexNumerals,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontFeatureSettings = TABULAR,
    ),
    val boxTitle: TextStyle = TextStyle(
        fontFamily = PokedexNumerals,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    /** Dex numbers in lists. Small, tabular, and never inside a grid tile. */
    val dexNumber: TextStyle = TextStyle(
        fontFamily = PokedexSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        fontFeatureSettings = TABULAR,
    ),
    /** Odds, ratios, anything monospaced-feeling that is not actually mono. */
    val statValue: TextStyle = TextStyle(
        fontFamily = PokedexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = TABULAR,
    ),
    val badgeLabel: TextStyle = TextStyle(
        fontFamily = PokedexSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)

private fun TextStyle.sans(weight: FontWeight, tabular: Boolean = false) = copy(
    fontFamily = PokedexSans,
    fontWeight = weight,
    fontFeatureSettings = if (tabular) TABULAR else null,
)

internal val PokedexTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.sans(FontWeight.SemiBold, tabular = true),
        displayMedium = base.displayMedium.sans(FontWeight.SemiBold, tabular = true),
        displaySmall = base.displaySmall.sans(FontWeight.SemiBold, tabular = true),
        headlineLarge = base.headlineLarge.sans(FontWeight.SemiBold, tabular = true),
        headlineMedium = base.headlineMedium.sans(FontWeight.SemiBold, tabular = true),
        headlineSmall = base.headlineSmall.sans(FontWeight.SemiBold, tabular = true),
        titleLarge = base.titleLarge.sans(FontWeight.Medium, tabular = true),
        titleMedium = base.titleMedium.sans(FontWeight.Medium, tabular = true),
        titleSmall = base.titleSmall.sans(FontWeight.Medium, tabular = true),
        bodyLarge = base.bodyLarge.sans(FontWeight.Normal),
        bodyMedium = base.bodyMedium.sans(FontWeight.Normal),
        bodySmall = base.bodySmall.sans(FontWeight.Normal),
        labelLarge = base.labelLarge.sans(FontWeight.Medium, tabular = true),
        labelMedium = base.labelMedium.sans(FontWeight.Medium, tabular = true),
        labelSmall = base.labelSmall.sans(FontWeight.Medium, tabular = true),
    )
}
