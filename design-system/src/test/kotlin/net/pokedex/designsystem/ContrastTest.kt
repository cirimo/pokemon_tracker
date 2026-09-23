package net.pokedex.designsystem

import com.google.common.truth.Truth.assertWithMessage
import net.pokedex.designsystem.theme.AllTypeSwatches
import net.pokedex.designsystem.theme.ContrastPairs
import net.pokedex.designsystem.theme.DarkCase
import net.pokedex.designsystem.theme.LightCase
import net.pokedex.designsystem.theme.PokedexColors
import net.pokedex.designsystem.theme.contrastRatio
import org.junit.Test

/**
 * The accessibility claim, made checkable.
 *
 * "Contrast verified against WCAG AA in both themes" is worth nothing as a sentence in a
 * document, because the document does not fail when somebody nudges a hex value. This does.
 *
 * Plain JVM tests -- no Robolectric, no device. `Color` is a value class over a ULong and
 * the luminance maths is arithmetic, so the whole suite runs in milliseconds and can sit on
 * every push.
 */
class ContrastTest {

    private val themes = listOf("dark" to DarkCase, "light" to LightCase)

    @Test
    fun everyDeclaredPairMeetsItsBar() {
        val failures = mutableListOf<String>()
        themes.forEach { (themeName, colors) ->
            ContrastPairs.forEach { pair ->
                val ratio = contrastRatio(pair.foreground(colors), pair.background(colors))
                if (ratio < pair.kind.minimumRatio) {
                    failures += "$themeName / ${pair.description}: %.2f:1, needs %.1f:1"
                        .format(ratio, pair.kind.minimumRatio)
                }
            }
        }
        assertWithMessage(failures.joinToString("\n")).that(failures).isEmpty()
    }

    /**
     * Guards the OKLCH derivation.
     *
     * The method's whole promise is that holding lightness constant holds contrast constant,
     * so the interesting assertion is not just "all pass" but "all pass by a similar margin".
     * If somebody hand-edits one swatch, the spread widens and this catches it even when the
     * edited value still technically passes.
     */
    @Test
    fun everyTypeBadgeMeetsAaInBothThemes() {
        val failures = mutableListOf<String>()
        themes.forEach { (themeName, colors) ->
            AllTypeSwatches.forEach { (type, swatch) ->
                val ratio = contrastRatio(swatch.on(colors), swatch.container(colors))
                if (ratio < AA_TEXT) {
                    failures += "$themeName / $type: %.2f:1, needs %.1f:1".format(ratio, AA_TEXT)
                }
            }
        }
        assertWithMessage(failures.joinToString("\n")).that(failures).isEmpty()
    }

    @Test
    fun typeBadgeContrastIsUniformWithinATheme() {
        themes.forEach { (themeName, colors) ->
            val ratios = AllTypeSwatches.map { (_, swatch) ->
                contrastRatio(swatch.on(colors), swatch.container(colors))
            }
            val spread = ratios.max() - ratios.min()
            assertWithMessage(
                "$themeName type contrast spread is %.2f (min %.2f, max %.2f). The OKLCH " +
                    "derivation should keep all 18 within %.1f of each other -- a wider spread " +
                    "means a swatch was hand-edited off the ramp."
                        .format(spread, ratios.min(), ratios.max(), MAX_SPREAD),
            ).that(spread).isLessThan(MAX_SPREAD)
        }
    }

    /** A theme with no distinct values is a copy-paste accident, not a theme. */
    @Test
    fun themesAreActuallyDifferent() {
        assertWithMessage("light theme must not be the dark palette")
            .that(LightCase.copy(isDark = true)).isNotEqualTo(DarkCase)
        assertWithMessage("dark theme must report isDark").that(DarkCase.isDark).isTrue()
        assertWithMessage("light theme must not report isDark").that(LightCase.isDark).isFalse()
    }

    /**
     * The surface a slot sits in must not be the slot.
     *
     * Not a WCAG rule -- a design one. The recessed-well look is the direction, and a well
     * that matches its case is just a flat rectangle.
     */
    @Test
    fun slotWellIsDistinguishableFromTheCase() {
        themes.forEach { (themeName, colors: PokedexColors) ->
            val ratio = contrastRatio(colors.slotWell, colors.case)
            assertWithMessage("$themeName: the well and the case are the same colour")
                .that(ratio).isGreaterThan(1.0)
        }
    }

    private companion object {
        const val AA_TEXT = 4.5
        const val MAX_SPREAD = 1.0
    }
}
