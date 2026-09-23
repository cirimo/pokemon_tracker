package net.pokedex.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * WCAG contrast, and the table of every token pair the components actually rely on.
 *
 * This lives in main rather than in test on purpose. A component that starts using a new
 * pair needs somewhere honest to declare it, and a table only the tests can see is a
 * table that silently stops describing the app. `ContrastTest` walks [ContrastPairs] and
 * fails the build on anything below its required ratio, in both themes.
 *
 * The bars are WCAG 2.2 AA: 4.5:1 for body text, 3:1 for large text and for graphical
 * objects and UI component state (1.4.11). Where a pair is decorative and carries no
 * information, it is not in this table at all -- listing it with a bar of 1.0 would be
 * theatre.
 */
/** The WCAG 2.2 AA thresholds, named so the table below reads as prose. */
private const val AA_NORMAL_TEXT = 4.5
private const val AA_LARGE_TEXT = 3.0
private const val AA_NON_TEXT = 3.0

enum class ContrastKind(val minimumRatio: Double) {
    /** Body and label text. */
    Text(AA_NORMAL_TEXT),

    /** Text at >= 24sp, or >= 18.66sp bold. Progress numerals qualify. */
    LargeText(AA_LARGE_TEXT),

    /** Rims, arcs, focus rings, silhouettes -- anything shape carries meaning through. */
    Graphical(AA_NON_TEXT),
}

data class ContrastPair(
    val description: String,
    val foreground: (PokedexColors) -> Color,
    val background: (PokedexColors) -> Color,
    val kind: ContrastKind,
)

/**
 * Note what is absent: "caught rim against needed rim".
 *
 * In light theme those two sit at 1.58:1 and would fail if we claimed colour told them
 * apart. It does not. Caught is carried by the gold pip, the thicker rim, and the sprite
 * arriving in colour -- three non-colour signals. Adding the pair here with a lowered bar
 * would be a way of pretending otherwise.
 */
val ContrastPairs: List<ContrastPair> = listOf(
    ContrastPair("body text on case", { it.onCase }, { it.case }, ContrastKind.Text),
    ContrastPair("body text on surface", { it.onCase }, { it.caseSurface }, ContrastKind.Text),
    ContrastPair("muted text on case", { it.onCaseMuted }, { it.case }, ContrastKind.Text),
    ContrastPair("muted text on surface", { it.onCaseMuted }, { it.caseSurface }, ContrastKind.Text),
    ContrastPair("muted text on raised surface", { it.onCaseMuted }, { it.caseSurfaceHigh }, ContrastKind.Text),
    ContrastPair("progress numerals on case", { it.accentText }, { it.case }, ContrastKind.Text),
    ContrastPair("progress numerals on surface", { it.accentText }, { it.caseSurface }, ContrastKind.Text),
    ContrastPair("error text on surface", { it.errorText }, { it.caseSurface }, ContrastKind.Text),
    ContrastPair("needed slot rim on well", { it.rim }, { it.slotWell }, ContrastKind.Graphical),
    ContrastPair("caught slot rim on well", { it.rimCaught }, { it.slotWell }, ContrastKind.Graphical),
    ContrastPair("focus ring on case", { it.rimFocus }, { it.case }, ContrastKind.Graphical),
    ContrastPair("focus ring on well", { it.rimFocus }, { it.slotWell }, ContrastKind.Graphical),
    ContrastPair("uncaught silhouette on well", { it.silhouette }, { it.slotWell }, ContrastKind.Graphical),
    ContrastPair("progress arc on surface", { it.accentGraphic }, { it.caseSurface }, ContrastKind.Graphical),
    // The one that would have been missed: an arc invisible against its own track is
    // still "on-surface compliant" and still unreadable.
    ContrastPair("progress arc on its track", { it.accentGraphic }, { it.progressTrack }, ContrastKind.Graphical),
    ContrastPair("error graphic on surface", { it.errorGraphic }, { it.caseSurface }, ContrastKind.Graphical),
)

// The sRGB linearisation and luminance coefficients, straight from the WCAG 2.2 text.
// Named rather than inlined because a typo in one of them would make every check in this
// file wrong in the same direction, and therefore invisible.
private const val SRGB_KNEE = 0.04045
private const val SRGB_LOW_SLOPE = 12.92
private const val SRGB_OFFSET = 0.055
private const val SRGB_SCALE = 1.055
private const val SRGB_GAMMA = 2.4
private const val LUMA_R = 0.2126
private const val LUMA_G = 0.7152
private const val LUMA_B = 0.0722
private const val CONTRAST_FLARE = 0.05

/** Relative luminance, WCAG 2.2 definition. */
fun relativeLuminance(color: Color): Double {
    fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= SRGB_KNEE) {
            c / SRGB_LOW_SLOPE
        } else {
            ((c + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_GAMMA)
        }
    }
    return LUMA_R * channel(color.red) + LUMA_G * channel(color.green) + LUMA_B * channel(color.blue)
}

/** Contrast ratio between two opaque colours, 1.0 to 21.0. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + CONTRAST_FLARE) / (minOf(la, lb) + CONTRAST_FLARE)
}
