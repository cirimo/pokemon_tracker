package net.pokedex.core.model

import kotlin.math.pow

/**
 * A shiny chance per encounter, carried as "1 in [oneIn]".
 *
 * The figure is exact: n rolls at 1/4096 is 1 - (4095/4096)^n, not n/4096. The two are close
 * at the Shiny Charm's three rolls (1365.67 against 1365.33) and drift apart as rolls grow
 * (a Legends Arceus outbreak with everything is 1/128.49 exact, 1/128 by the shortcut).
 * Bulbapedia's tables print the exact figure, so the app matches the page it cites.
 */
data class Odds(
    val oneIn: Double,
    /** Total rolls behind the figure, or null when a flat rate replaced the roll model. */
    val rolls: Int?,
) {
    val chance: Double get() = 1.0 / oneIn

    companion object {
        /** Every Switch game: one roll at 1/4096. */
        const val BASE_DENOMINATOR = 4096

        fun ofRolls(rolls: Int): Odds {
            require(rolls >= 1) { "a shiny check rolls at least once, got $rolls" }
            val miss = (BASE_DENOMINATOR - 1.0) / BASE_DENOMINATOR
            return Odds(oneIn = 1.0 / (1.0 - miss.pow(rolls)), rolls = rolls)
        }

        fun flat(denominator: Int): Odds = Odds(oneIn = denominator.toDouble(), rolls = null)
    }
}

/**
 * The odds for one (game, method): what the method gives by itself, and the best a player
 * can set up, with the modifiers that best case assumes.
 *
 * Both are shown because they answer different questions. [plain] is what happens if you
 * walk in today; [best] is what the hunt is worth once the Shiny Charm and the research are
 * done, and [assumed] is how you get there.
 */
data class MethodOdds(
    val plain: Odds,
    val best: Odds,
    val assumed: List<OddsModifier>,
)

/**
 * Combines the curated modifiers for one (game, method).
 *
 * The rules are the ones `data/curated/odds-modifiers.yaml` states:
 *  - untiered rows stack, their rolls summed;
 *  - rows sharing a tier are levels of one thing, and only the best level counts;
 *  - inherent rows come with the method, so they are in [MethodOdds.plain] as well;
 *  - a denominator replaces the roll model, and the best applicable one wins.
 *
 * Returns null when nothing is curated for the method. That is "odds not recorded", and it
 * is shown as such -- not as 1/4096, which would be a claim the data does not make.
 */
fun oddsFor(modifiers: List<OddsModifier>): MethodOdds? {
    if (modifiers.isEmpty()) return null
    val inherent = modifiers.filter { it.inherent }
    val chosen = bestSetup(modifiers)

    val flat = chosen.mapNotNull { it.denominator }.minOrNull()
    val best = if (flat != null) Odds.flat(flat) else Odds.ofRolls(1 + chosen.sumOf { it.rollsAdded ?: 0 })

    val plainFlat = inherent.mapNotNull { it.denominator }.minOrNull()
    val plain = if (plainFlat != null) {
        Odds.flat(plainFlat)
    } else {
        Odds.ofRolls(1 + bestSetup(inherent).sumOf { it.rollsAdded ?: 0 })
    }

    return MethodOdds(plain = plain, best = best, assumed = chosen.filterNot { it.inherent })
}

/** Every untiered modifier, plus the best level of each tier. */
private fun bestSetup(modifiers: List<OddsModifier>): List<OddsModifier> {
    val (tiered, untiered) = modifiers.partition { it.tier != null }
    val bestPerTier = tiered.groupBy { it.tier }.values.map { levels -> levels.maxBy(::strength) }
    return untiered + bestPerTier
}

/** A flat rate beats any roll count it replaces; among flats, the smaller denominator wins. */
private fun strength(modifier: OddsModifier): Double =
    modifier.denominator?.let { FLAT_WEIGHT / it } ?: (modifier.rollsAdded ?: 0).toDouble()

private const val FLAT_WEIGHT = 1_000_000.0
