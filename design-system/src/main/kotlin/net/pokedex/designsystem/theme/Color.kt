package net.pokedex.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The "Display case" palette, in semantic roles rather than colour names.
 *
 * Call sites ask for [rim] or [silhouette], never for "the grey one". That is what makes
 * a second theme possible at all: [DarkCase] and [LightCase] hold different values under
 * identical names, and no component knows which one it is running under.
 *
 * Every value here was solved for, not picked. The rims and the accent are the output of
 * a WCAG contrast solve against the surface they actually sit on -- the ratios are listed
 * beside each, and [ContrastPairs] re-checks all of them on every build. If you change a
 * value, `./gradlew :design-system:designCheck` is what tells you whether you got away
 * with it.
 *
 * Two rules that outlive any value in this file (docs/design-decisions.md):
 *
 *  1. NO DYNAMIC COLOUR. Gold has to keep meaning "shiny"; Monet would rotate the accent
 *     to whatever the wallpaper is, and would also make it impossible to contrast-check
 *     18 type colours against an accent we do not control.
 *  2. Gold earns exactly three places: the caught slot rim and pip, progress numerals and
 *     arcs, and the catch celebration. Never backgrounds, navigation, headers or badges.
 */
@Immutable
data class PokedexColors(
    /** The case itself. Warm near-black, or warm paper. Never pure #000 or #FFF. */
    val case: Color,
    val caseSurface: Color,
    val caseSurfaceHigh: Color,

    /** A slot well: the recess a Pokemon sits in. */
    val slotWell: Color,

    /** A position the preset leaves empty. Reads as absence, and carries no rim. */
    val slotVoid: Color,

    /** The hairline that delineates a well. Solved to 3:1 against [slotWell]. */
    val rim: Color,

    /** Focus ring. Solved to 3:1 against both [case] and [slotWell]. */
    val rimFocus: Color,

    /** The caught rim. Gold, and the only gold in the grid. */
    val rimCaught: Color,

    /** Gold for graphical objects -- rims and arcs. Held to WCAG's 3:1 non-text bar. */
    val accentGraphic: Color,

    /** Gold for numerals. Held to 4.5:1, which is why light theme darkens it to bronze. */
    val accentText: Color,

    /**
     * The unfilled part of a progress arc or bar.
     *
     * Neutral, emphatically not a dimmed gold. A gold track makes an empty ring look like a
     * full one -- the first version of this shipped as `accentDim` and the screenshots
     * showed 0/29, 21/29 and 29/29 rendering as three identical gold circles.
     */
    val progressTrack: Color,

    val onCase: Color,
    val onCaseMuted: Color,

    /**
     * An uncaught sprite, drawn as a flat silhouette.
     *
     * This is the token that solves the 1400-tile problem: a box is quiet until you fill
     * it, and colour arriving in a slot *is* the progress. Solved to 3:1 against
     * [slotWell] so the silhouette is a legible shape, not a smudge.
     */
    val silhouette: Color,

    val errorText: Color,
    val errorGraphic: Color,

    val scrim: Color,

    /** True when this is the dark instance. Read by [TypeSwatch] and by nothing else. */
    val isDark: Boolean,
)

/**
 * Dark: a warm near-black case.
 *
 * The case is warm rather than neutral (`#121011`, not `#111111`) because a neutral
 * near-black next to a gold accent reads as blue, and the whole direction depends on
 * gold looking like metal rather than like yellow.
 */
val DarkCase = PokedexColors(
    case = Color(0xFF121011),
    caseSurface = Color(0xFF1A1718),
    caseSurfaceHigh = Color(0xFF232021),
    slotWell = Color(0xFF0E0C0D),
    slotVoid = Color(0xFF151314),
    rim = Color(0xFF625F5C), // 3.07:1 on slotWell
    rimFocus = Color(0xFF807B78), // 4.53:1 on case, 4.66:1 on slotWell
    rimCaught = Color(0xFFD8B26A), // 9.74:1 on slotWell
    accentGraphic = Color(0xFFD8B26A), // 8.90:1 on caseSurface
    accentText = Color(0xFFD8B26A), // 9.47:1 on case
    progressTrack = Color(0xFF38342F), // gold arc reads 6.17:1 against it
    onCase = Color(0xFFEDE7E3), // 15.47:1 on case
    onCaseMuted = Color(0xFFA9A19E), // 7.47:1 on case
    silhouette = Color(0xFF6A6461), // 3.35:1 on slotWell
    errorText = Color(0xFFF2B8B5), // 10.43:1 on caseSurface
    errorGraphic = Color(0xFFE0757A), // 5.93:1 on caseSurface
    scrim = Color(0xCC0A0909),
    isDark = true,
)

/**
 * Light: the case becomes paper and the wells become impressions.
 *
 * Not a tinted copy of the dark theme. Two roles genuinely invert: [accentText] drops to
 * bronze because `#D8B26A` on paper is 1.9:1 and fails outright, and [silhouette] goes
 * darker than its surface rather than lighter. The `accentGraphic` / `accentText` split
 * exists entirely because of this theme -- in dark the two are the same colour.
 */
val LightCase = PokedexColors(
    case = Color(0xFFF5F2ED),
    caseSurface = Color(0xFFEBE6DE),
    caseSurfaceHigh = Color(0xFFE1DBD1),
    slotWell = Color(0xFFE6E1D9),
    slotVoid = Color(0xFFEFEBE4),
    rim = Color(0xFF837E77), // 3.09:1 on slotWell
    rimFocus = Color(0xFF746F68), // 4.46:1 on case, 3.83:1 on slotWell
    rimCaught = Color(0xFF7A5A10), // 4.89:1 on slotWell
    accentGraphic = Color(0xFF7A5A10), // 3.46:1 on caseSurface
    accentText = Color(0xFF6B4E0C), // 6.91:1 on case
    progressTrack = Color(0xFFD8D1C4), // bronze arc reads 4.20:1 against it
    onCase = Color(0xFF1E1A18), // 15.46:1 on case
    onCaseMuted = Color(0xFF5B534D), // 6.74:1 on case
    silhouette = Color(0xFF7A756E), // 3.51:1 on slotWell
    errorText = Color(0xFF7A2C2C), // 7.60:1 on caseSurface
    errorGraphic = Color(0xFFA33B3B), // 5.21:1 on caseSurface
    scrim = Color(0x99201C19),
    isDark = false,
)
