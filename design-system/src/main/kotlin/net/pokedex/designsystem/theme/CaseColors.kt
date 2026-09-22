package net.pokedex.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Colour tokens for the "Display case" direction.
 *
 * M0 ships the skeleton of the palette so the app has a coherent surface to boot into.
 * M1 replaces these with the authored values and adds the 18-type OKLCH ramp; the
 * names here are the contract, the values are not final.
 *
 * Two rules from docs/design-decisions.md that outlive any value change:
 *
 *  1. NO DYNAMIC COLOUR. Monet would rotate the accent to the wallpaper, and gold has
 *     to keep meaning "shiny". This is why the theme takes no dynamicColor parameter --
 *     there is nothing to opt into.
 *  2. Gold earns exactly three places: the caught-shiny slot rim, progress numerals and
 *     arcs, and the catch celebration. Never backgrounds, navigation, headers or badges.
 */
internal object CaseColors {
    // The case itself: warm near-black, never pure #000.
    val CaseBlack = Color(0xFF121011)
    val CaseSurface = Color(0xFF1A1718)
    val CaseSurfaceHigh = Color(0xFF232021)

    // A recessed slot well, which reads as a hole rather than a card.
    val SlotWell = Color(0xFF0E0C0D)
    val SlotRim = Color(0xFF2E2A2B)

    // The one accent. Used sparingly and on purpose.
    val Gold = Color(0xFFD8B26A)
    val GoldDim = Color(0xFF8A7142)

    val OnCase = Color(0xFFEDE7E3)
    val OnCaseMuted = Color(0xFF9B9391)

    val Error = Color(0xFFCF6679)
    val OnError = Color(0xFF1A1718)
}
