package net.pokedex.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import com.google.common.truth.Truth.assertWithMessage
import net.pokedex.designsystem.theme.DarkCase
import net.pokedex.designsystem.theme.LightCase
import net.pokedex.designsystem.theme.PokedexColors
import net.pokedex.designsystem.theme.PokedexDimens
import org.junit.Test
import java.io.File

/**
 * `DESIGN.md` must describe the tokens the app actually runs on.
 *
 * The repository has two descriptions of the design language: the Kotlin in `theme/`, which
 * the app runs, and `DESIGN.md` at the root, which design tooling and every future session
 * read. Two descriptions drift. This makes the drift a build failure instead of a surprise:
 * every colour, spacing and radius in the DESIGN.md frontmatter is compared with the token it
 * names, and every token this table knows about must appear in the file.
 *
 * Plain JVM, no YAML library: the frontmatter is flat `key: "value"` lines under three
 * section headings, and a parser that only understands that shape is the honest one.
 */
class DesignDocTest {

    private val frontmatter: Map<String, Map<String, String>> by lazy { parse(File("../DESIGN.md")) }

    @Test
    fun everyColourMatchesItsToken() {
        val colours = section("colors")
        val failures = mutableListOf<String>()
        ColourSlugs.forEach { (slug, token) ->
            val written = colours[slug]
            when {
                written == null -> failures += "$slug is missing from DESIGN.md"
                hex(written) != hex(token) -> failures += "$slug: DESIGN.md says $written, Kotlin says ${hex(token)}"
            }
        }
        (colours.keys - ColourSlugs.keys).forEach { failures += "$it is in DESIGN.md but names no token" }
        assertWithMessage(failures.joinToString("\n")).that(failures).isEmpty()
    }

    @Test
    fun everySpacingAndRadiusMatchesItsToken() {
        val failures = mutableListOf<String>()
        listOf("spacing" to SpacingSlugs, "rounded" to RadiusSlugs).forEach { (name, slugs) ->
            val written = section(name)
            slugs.forEach { (slug, dp) ->
                val value = written[slug]
                when {
                    value == null -> failures += "$name.$slug is missing from DESIGN.md"
                    value != "${dp.value.toInt()}dp" ->
                        failures += "$name.$slug: DESIGN.md says $value, Kotlin says $dp"
                }
            }
            (written.keys - slugs.keys).forEach { failures += "$name.$it is in DESIGN.md but names no token" }
        }
        assertWithMessage(failures.joinToString("\n")).that(failures).isEmpty()
    }

    private fun section(name: String): Map<String, String> =
        requireNotNull(frontmatter[name]) { "DESIGN.md frontmatter has no `$name:` section" }

    private companion object {
        private val dimens = PokedexDimens()

        private fun both(base: String, token: (PokedexColors) -> Color) =
            listOf("$base-dark" to token(DarkCase), "$base-light" to token(LightCase))

        /**
         * Slug to token. Gold is one slug in dark because all three gold roles are the same
         * colour there; light splits graphic from text (docs/adr/0009-light-theme.md).
         */
        val ColourSlugs: Map<String, Color> = (
            both("case") { it.case } +
                both("case-surface") { it.caseSurface } +
                both("case-surface-high") { it.caseSurfaceHigh } +
                both("case-highlight") { it.caseHighlight } +
                both("slot-well") { it.slotWell } +
                both("slot-void") { it.slotVoid } +
                both("rim") { it.rim } +
                both("rim-focus") { it.rimFocus } +
                both("progress-track") { it.progressTrack } +
                both("on-case") { it.onCase } +
                both("on-case-muted") { it.onCaseMuted } +
                both("silhouette") { it.silhouette } +
                both("silhouette-regular") { it.silhouetteRegular } +
                both("error-text") { it.errorText } +
                both("error-graphic") { it.errorGraphic } +
                listOf(
                    "shiny-gold-dark" to DarkCase.accentGraphic,
                    "shiny-gold-graphic-light" to LightCase.accentGraphic,
                    "shiny-gold-text-light" to LightCase.accentText,
                )
            ).toMap()

        val SpacingSlugs: Map<String, Dp> = mapOf(
            "xxs" to dimens.spaceXxs, "xs" to dimens.spaceXs, "sm" to dimens.spaceSm,
            "md" to dimens.spaceMd, "lg" to dimens.spaceLg, "xl" to dimens.spaceXl,
            "xxl" to dimens.spaceXxl, "slot-gutter" to dimens.slotGutter,
            "slot-min" to dimens.slotMinSize, "touch-target-min" to dimens.touchTargetMin,
        )

        val RadiusSlugs: Map<String, Dp> = mapOf(
            "badge" to dimens.badgeRadius,
            "slot" to dimens.slotRadius,
            "slot-pressed" to dimens.slotPressedRadius,
            "field" to dimens.fieldRadius,
            "card" to dimens.cardRadius,
            "dialog" to dimens.dialogRadius,
            "hero" to dimens.heroRadius,
            "sheet" to dimens.sheetRadius,
        )

        fun hex(c: Color): String = "#%06X".format(c.toArgb() and 0xFFFFFF)

        fun hex(written: String): String = written.trim('"').uppercase()

        /** `name:` at column 0 opens a section; `  key: "value"` lines fill it. Nested maps are skipped. */
        fun parse(file: File): Map<String, Map<String, String>> {
            val lines = file.readLines()
            val end = lines.drop(1).indexOfFirst { it.trim() == "---" } + 1
            require(lines.first().trim() == "---" && end > 0) { "DESIGN.md has no frontmatter" }
            val out = mutableMapOf<String, MutableMap<String, String>>()
            var current: MutableMap<String, String>? = null
            lines.subList(1, end).forEach { line ->
                when {
                    line.isBlank() || line.trimStart().startsWith("#") -> Unit
                    !line.startsWith(
                        " "
                    ) -> current = line.substringBefore(":").let { out.getOrPut(it) { mutableMapOf() } }
                    line.startsWith("  ") && !line.startsWith("   ") -> {
                        val key = line.trim().substringBefore(":")
                        val value = line.substringAfter(":").trim()
                        if (value.isNotEmpty()) current?.put(key, value.trim('"'))
                    }
                }
            }
            return out
        }
    }
}
