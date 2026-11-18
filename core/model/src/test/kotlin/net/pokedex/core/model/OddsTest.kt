package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Every expected figure here is one Bulbapedia's shiny-rate tables print, so a test that
 * fails means the arithmetic disagrees with the page the curated rows cite.
 */
class OddsTest {

    private fun mod(
        id: String,
        rolls: Int? = null,
        denominator: Int? = null,
        tier: String? = null,
        inherent: Boolean = false,
        skipsBaseRoll: Boolean = false,
    ) = OddsModifier(
        gameId = GameId("la"),
        methodId = "mass-outbreak",
        id = id,
        label = id,
        rollsAdded = rolls,
        denominator = denominator,
        tier = tier,
        inherent = inherent,
        notes = null,
        sourceUrl = "https://bulbapedia.bulbagarden.net/wiki/Shiny_Pok%C3%A9mon",
        skipsBaseRoll = skipsBaseRoll,
    )

    private val research10 = mod("research-10", rolls = 1, tier = "research")
    private val researchPerfect = mod("research-perfect", rolls = 3, tier = "research")
    private val charm = mod("shiny-charm", rolls = 3)

    @Test
    fun `odds are exact, not n over 4096`() {
        assertThat(Odds.ofRolls(1).oneIn).isWithin(1e-9).of(4096.0)
        assertThat(Odds.ofRolls(3).oneIn).isWithin(0.01).of(1365.67)
        assertThat(Odds.ofRolls(3).oneIn).isNotWithin(0.1).of(4096.0 / 3)
    }

    @Test
    fun `a Legends Arceus outbreak with everything is 1 in 128_49, and 1 in 158_02 as it comes`() {
        val odds = oddsFor(listOf(mod("outbreak", rolls = 25, inherent = true), research10, researchPerfect, charm))!!

        assertThat(odds.best.rolls).isEqualTo(32)
        assertThat(odds.best.oneIn).isWithin(0.01).of(128.49)
        assertThat(odds.plain.rolls).isEqualTo(26)
        assertThat(odds.plain.oneIn).isWithin(0.01).of(158.02)
    }

    @Test
    fun `levels of one tier do not stack, only the best counts`() {
        val odds = oddsFor(listOf(research10, researchPerfect))!!

        // Base, plus perfect research's 3. Not 1 + 1 + 3.
        assertThat(odds.best.rolls).isEqualTo(4)
        assertThat(odds.best.oneIn).isWithin(0.01).of(1024.38)
        assertThat(odds.assumed).containsExactly(researchPerfect)
    }

    @Test
    fun `a Scarlet outbreak at 60 with Sparkling Power 3 and the charm is 1 in 512_44`() {
        val odds = oddsFor(
            listOf(
                mod("charm", rolls = 2),
                mod("outbreak-30", rolls = 1, tier = "outbreak"),
                mod("outbreak-60", rolls = 2, tier = "outbreak"),
                mod("sparkling-1", rolls = 1, tier = "sparkling"),
                mod("sparkling-3", rolls = 3, tier = "sparkling"),
            ),
        )!!

        assertThat(odds.best.rolls).isEqualTo(8)
        assertThat(odds.best.oneIn).isWithin(0.01).of(512.44)
        // Nothing comes with the method itself in Scarlet: walking in, it is the base rate.
        assertThat(odds.plain.oneIn).isWithin(1e-9).of(4096.0)
    }

    @Test
    fun `a flat rate replaces the rolls, and the better flat rate wins`() {
        val odds = oddsFor(
            listOf(mod("dynamax", denominator = 300, inherent = true), mod("charm", denominator = 100)),
        )!!

        assertThat(odds.plain).isEqualTo(Odds.flat(300))
        assertThat(odds.best).isEqualTo(Odds.flat(100))
        assertThat(odds.best.rolls).isNull()
    }

    @Test
    fun `what comes with the method is not listed as something to set up`() {
        val outbreak = mod("outbreak", rolls = 25, inherent = true)

        assertThat(oddsFor(listOf(outbreak, charm))!!.assumed).containsExactly(charm)
    }

    @Test
    fun `nothing curated means no odds, not the base rate`() {
        assertThat(oddsFor(emptyList())).isNull()
    }

    // Bulbapedia, Masuda method and Shiny Charm (Generation VIII): once any reroll applies to
    // an egg the first roll is skipped, so the charm alone is 2 rolls, Masuda alone 6, both 8.
    private val eggCharm = mod(SHINY_CHARM, rolls = 2, skipsBaseRoll = true)
    private val masuda = mod("masuda", rolls = 6, skipsBaseRoll = true)

    @Test
    fun `a Sword egg is 1 in 512 with Masuda and the charm, 1 in 683 with Masuda alone`() {
        assertThat(oddsFor(listOf(eggCharm, masuda))!!.best.oneIn).isWithin(0.01).of(512.44)
        assertThat(oddsFor(listOf(eggCharm, masuda))!!.best.rolls).isEqualTo(8)
        assertThat(oddsFor(listOf(masuda))!!.best.rolls).isEqualTo(6)
        assertThat(oddsFor(listOf(masuda))!!.best.oneIn).isWithin(0.01).of(683.08)
    }

    @Test
    fun `the charm alone hatches at 1 in 2048, not 1 in 1365`() {
        val odds = oddsFor(listOf(eggCharm, masuda))!!

        assertThat(odds.withCharm!!.rolls).isEqualTo(2)
        assertThat(odds.withCharm!!.oneIn).isWithin(0.01).of(2048.25)
        // Walking in with neither, the egg still rolls once.
        assertThat(odds.plain).isEqualTo(Odds.ofRolls(1))
    }

    @Test
    fun `with the charm is shown beside the plain rate, and is absent where the charm does nothing`() {
        val dynamax = mod("dynamax", denominator = 300, inherent = true)
        val dynamaxCharm = mod(SHINY_CHARM, denominator = 100)

        assertThat(oddsFor(listOf(dynamax, dynamaxCharm))!!.withCharm).isEqualTo(Odds.flat(100))
        assertThat(oddsFor(listOf(research10))!!.withCharm).isNull()
    }
}
