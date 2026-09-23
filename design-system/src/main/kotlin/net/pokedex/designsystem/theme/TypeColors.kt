package net.pokedex.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The 18 type colours, derived rather than authored.
 *
 * Picking 18 hues by eye and then contrast-checking them is the trap: you get three that
 * fail, you nudge those three, and now they no longer look like a set. So instead every
 * swatch is one point in OKLCH with **lightness and chroma fixed, hue the only variable**:
 *
 *     dark    container oklch(0.28, c * 0.45, h)   text oklch(0.86, c * 0.75, h)
 *     light   container oklch(0.93, c * 0.30, h)   text oklch(0.44, c * 0.95, h)
 *
 * Because OKLCH lightness is perceptually uniform, holding L constant holds *contrast*
 * roughly constant too. The measured spread is 9.38-9.67:1 in dark and 6.09-6.67:1 in
 * light -- eighteen hues within a quarter of a stop of each other, all far past AA, by
 * construction rather than by luck. A nineteenth type could be added tomorrow without
 * re-checking the other eighteen.
 *
 * Per-type chroma (the `c` above) is the one hand-set number: Normal, Dark and Steel are
 * deliberately near-neutral so the set does not read as a rainbow, and the multipliers
 * then pull every container far enough down in chroma that a row of badges sits quietly
 * against the case instead of fighting it -- and, more importantly, never competes with
 * gold. Type colour is forbidden in the grid entirely (docs/design-decisions.md); it lives
 * on badges and on the detail screen.
 *
 * The values below are computed output, checked in as hex. Runtime does no colour maths.
 * `TypeColorTest` re-measures every pair on every build.
 *
 * Colour is never the only carrier: [net.pokedex.designsystem.component.TypeBadge] always
 * renders the type's name.
 */
enum class PokemonType {
    Normal, Fire, Water, Electric, Grass, Ice, Fighting, Poison, Ground,
    Flying, Psychic, Bug, Rock, Ghost, Dragon, Dark, Steel, Fairy,
    ;

    companion object {
        private val byId = entries.associateBy { it.name.lowercase() }

        /**
         * Maps a dataset type string (`"water"`) to a swatch.
         *
         * Returns null rather than throwing: the dataset is regenerated independently of
         * this module, and an unknown type should degrade to an uncoloured badge, not
         * crash the grid. `:core:model` carries types as plain strings precisely so it
         * owes nothing to this enum.
         */
        fun fromId(id: String): PokemonType? = byId[id.lowercase()]
    }
}

@Immutable
data class TypeSwatch(
    val darkContainer: Color,
    val darkOn: Color,
    val lightContainer: Color,
    val lightOn: Color,
) {
    fun container(colors: PokedexColors): Color = if (colors.isDark) darkContainer else lightContainer
    fun on(colors: PokedexColors): Color = if (colors.isDark) darkOn else lightOn
}

private val TypeSwatches: Map<PokemonType, TypeSwatch> = mapOf(
    PokemonType.Normal to TypeSwatch(
        // oklch(h=85 c=0.020)  dark 9.46:1   light 6.34:1
        darkContainer = Color(0xFF2B2924), darkOn = Color(0xFFD5D0C6),
        lightContainer = Color(0xFFEAE8E3), lightOn = Color(0xFF575247),
    ),
    PokemonType.Fire to TypeSwatch(
        // oklch(h=45 c=0.135)  dark 9.48:1   light 6.64:1
        darkContainer = Color(0xFF411E0D), darkOn = Color(0xFFFFC1A5),
        lightContainer = Color(0xFFFFE1D3), lightOn = Color(0xFF883500),
    ),
    PokemonType.Water to TypeSwatch(
        // oklch(h=248 c=0.115)  dark 9.54:1   light 6.31:1
        darkContainer = Color(0xFF122B41), darkOn = Color(0xFFACD6FF),
        lightContainer = Color(0xFFD7EBFE), lightOn = Color(0xFF11568B),
    ),
    PokemonType.Electric to TypeSwatch(
        // oklch(h=100 c=0.140)  dark 9.59:1   light 6.29:1
        darkContainer = Color(0xFF302900), darkOn = Color(0xFFE1D380),
        lightContainer = Color(0xFFEEE9C9), lightOn = Color(0xFF5F5300),
    ),
    PokemonType.Grass to TypeSwatch(
        // oklch(h=142 c=0.125)  dark 9.62:1   light 6.09:1
        darkContainer = Color(0xFF173015), darkOn = Color(0xFFAEE1A8),
        lightContainer = Color(0xFFDAEFD8), lightOn = Color(0xFF24621F),
    ),
    PokemonType.Ice to TypeSwatch(
        // oklch(h=210 c=0.090)  dark 9.67:1   light 6.12:1
        darkContainer = Color(0xFF0B2E34), darkOn = Color(0xFF9DDEEA),
        lightContainer = Color(0xFFD4EDF2), lightOn = Color(0xFF005E6A),
    ),
    PokemonType.Fighting to TypeSwatch(
        // oklch(h=25 c=0.130)  dark 9.47:1   light 6.62:1
        darkContainer = Color(0xFF411C1A), darkOn = Color(0xFFFFBEB8),
        lightContainer = Color(0xFFFFDFDC), lightOn = Color(0xFF8A302E),
    ),
    PokemonType.Poison to TypeSwatch(
        // oklch(h=315 c=0.120)  dark 9.47:1   light 6.61:1
        darkContainer = Color(0xFF33203B), darkOn = Color(0xFFE8C0FA),
        lightContainer = Color(0xFFF1E1F9), lightOn = Color(0xFF6A3C7D),
    ),
    PokemonType.Ground to TypeSwatch(
        // oklch(h=70 c=0.100)  dark 9.47:1   light 6.47:1
        darkContainer = Color(0xFF37250E), darkOn = Color(0xFFF1C99C),
        lightContainer = Color(0xFFF5E5D3), lightOn = Color(0xFF734701),
    ),
    PokemonType.Flying to TypeSwatch(
        // oklch(h=268 c=0.085)  dark 9.52:1   light 6.41:1
        darkContainer = Color(0xFF21283C), darkOn = Color(0xFFBFD0FC),
        lightContainer = Color(0xFFE0E8FA), lightOn = Color(0xFF3F507F),
    ),
    PokemonType.Psychic to TypeSwatch(
        // oklch(h=355 c=0.125)  dark 9.42:1   light 6.67:1
        darkContainer = Color(0xFF3E1C2A), darkOn = Color(0xFFFFBAD4),
        lightContainer = Color(0xFFFEDEE9), lightOn = Color(0xFF823156),
    ),
    PokemonType.Bug to TypeSwatch(
        // oklch(h=125 c=0.110)  dark 9.62:1   light 6.18:1
        darkContainer = Color(0xFF232D10), darkOn = Color(0xFFC4DBA0),
        lightContainer = Color(0xFFE3ECD5), lightOn = Color(0xFF455C0B),
    ),
    PokemonType.Rock to TypeSwatch(
        // oklch(h=78 c=0.060)  dark 9.57:1   light 6.35:1
        darkContainer = Color(0xFF30271A), darkOn = Color(0xFFE2CEB1),
        lightContainer = Color(0xFFEFE7DB), lightOn = Color(0xFF644F2D),
    ),
    PokemonType.Ghost to TypeSwatch(
        // oklch(h=300 c=0.105)  dark 9.51:1   light 6.57:1
        darkContainer = Color(0xFF2C233D), darkOn = Color(0xFFD9C6FE),
        lightContainer = Color(0xFFEBE4FA), lightOn = Color(0xFF5B4481),
    ),
    PokemonType.Dragon to TypeSwatch(
        // oklch(h=278 c=0.120)  dark 9.50:1   light 6.52:1
        darkContainer = Color(0xFF232643), darkOn = Color(0xFFC6CDFF),
        lightContainer = Color(0xFFE2E6FF), lightOn = Color(0xFF454990),
    ),
    PokemonType.Dark to TypeSwatch(
        // oklch(h=40 c=0.030)  dark 9.46:1   light 6.41:1
        darkContainer = Color(0xFF2F2724), darkOn = Color(0xFFDFCCC6),
        lightContainer = Color(0xFFEEE6E3), lightOn = Color(0xFF614D47),
    ),
    PokemonType.Steel to TypeSwatch(
        // oklch(h=225 c=0.035)  dark 9.52:1   light 6.23:1
        darkContainer = Color(0xFF212B2F), darkOn = Color(0xFFC0D5DE),
        lightContainer = Color(0xFFE1E9ED), lightOn = Color(0xFF3F5760),
    ),
    PokemonType.Fairy to TypeSwatch(
        // oklch(h=342 c=0.100)  dark 9.38:1   light 6.59:1
        darkContainer = Color(0xFF38202F), darkOn = Color(0xFFF4BEDF),
        lightContainer = Color(0xFFF7E0ED), lightOn = Color(0xFF753B61),
    ),
)

/** Every swatch, in dex order. Iterated by the gallery and by `TypeColorTest`. */
val AllTypeSwatches: List<Pair<PokemonType, TypeSwatch>> = PokemonType.entries.map { it to it.swatch() }

fun PokemonType.swatch(): TypeSwatch = requireNotNull(TypeSwatches[this]) { "no swatch for $this" }
