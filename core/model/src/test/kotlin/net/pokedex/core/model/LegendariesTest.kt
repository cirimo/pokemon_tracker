package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import net.pokedex.core.model.DexFixtures.available
import net.pokedex.core.model.DexFixtures.game
import net.pokedex.core.model.DexFixtures.variant
import org.junit.Test

/**
 * A small Sword: Zygarde and Tornadus in the Max Lair at 1/100, Regirock by reset at 1/4096,
 * Galarian Articuno locked, Thundurus online-only, Articuno also in Let's Go earlier in my
 * order, and a common Pokemon that is never a legendary row.
 */
class LegendariesTest {

    private val preset = PresetId("grouped-balanced")
    private val sword = GameId("swsh-sw")
    private val lge = GameId("lgpe-lge")
    private val violet = GameId("sv-v")

    private val variants = listOf(
        variant("articuno", 144, "Articuno"),
        variant("articuno-galar", 144, "Articuno (Galarian)", formName = "Galarian Form")
            .copy(isRegional = true, formId = "galar"),
        variant("regirock", 377, "Regirock"),
        variant("tornadus", 641, "Tornadus"),
        variant("thundurus", 642, "Thundurus"),
        variant("zygarde", 718, "Zygarde"),
        variant("pikachu", 25, "Pikachu"),
    )
    private val species = listOf(
        Species(144, "Articuno", 1, "kanto", isLegendary = true),
        Species(377, "Regirock", 3, "hoenn", isLegendary = true),
        Species(641, "Tornadus", 5, "unova", isLegendary = true),
        Species(642, "Thundurus", 5, "unova", isLegendary = true),
        Species(718, "Zygarde", 6, "kalos", isLegendary = true),
        Species(25, "Pikachu", 1, "kanto"),
    )
    private val dex = Dex.assemble(
        preset = DexPreset(preset, "Grouped", "", 1, 1, boxCount = 1, filledSlotCount = variants.size),
        boxes = listOf(Box(preset, 0, "Legends", slotCount = 30, filledSlotCount = variants.size)),
        slots = variants.mapIndexed { i, v -> Slot(preset, 0, i, v.id, 0) },
        variants = variants,
        species = species,
        games = listOf(game("lgpe-lge", "lgpe", 20), game("swsh-sw", "swsh", 30), game("sv-v", "sv", 90)),
        availability = listOf(
            available("articuno", "lgpe-lge"),
            available("articuno", "swsh-sw"),
            available("articuno-galar", "swsh-sw", shinyLocked = true),
            available("regirock", "swsh-sw"),
            available("tornadus", "swsh-sw"),
            available("thundurus", "swsh-sw").copy(exclusiveTo = GameId("swsh-sh")),
            available("zygarde", "swsh-sw"),
            available("zygarde", "sv-v"),
            available("pikachu", "swsh-sw"),
        ),
    )

    private fun encounter(variant: String, method: String) = Encounter(
        id = "$variant:swsh-sw:0",
        variantId = VariantId(variant),
        gameId = sword,
        methodId = method,
        location = null,
        prerequisite = null,
        notes = null,
        shinyLocked = false,
        fromVariantId = null,
        sourceUrl = "https://example.org/$variant",
        oddsExclude = if (method == "static") setOf(SHINY_CHARM) else emptySet(),
    )

    private fun modifier(
        method: String,
        id: String,
        rolls: Int? = null,
        denominator: Int? = null,
        inherent: Boolean = false,
    ) = OddsModifier(sword, method, id, id, rolls, denominator, null, inherent, null, "https://example.org/$id")

    private val curation = Curation(
        encounters = listOf(
            encounter("articuno", "dynamax-adventure"),
            encounter("regirock", "static"),
            encounter("tornadus", "dynamax-adventure"),
            encounter("thundurus", "dynamax-adventure"),
            encounter("zygarde", "dynamax-adventure"),
        ),
        modifiers = listOf(
            modifier("dynamax-adventure", "dynamax-adventure", denominator = 300, inherent = true),
            modifier("dynamax-adventure", SHINY_CHARM, denominator = 100),
            modifier("static", SHINY_CHARM, rolls = 2),
        ),
        methods = emptyList(),
    )

    private val order = listOf(lge, sword, violet)

    private fun plan(records: Map<CatchKey, CatchRecord> = emptyMap()) =
        legendaryPlan(dex, records, order, curation, sword)

    private fun names(p: LegendaryPlan) = p.hunts.map { it.lead.variant.id.value }

    @Test
    fun `odds lead, then where Sword stands in my farm order, then preset order`() {
        // Max Lair first (1/100): Tornadus and Thundurus only here, Zygarde here first,
        // Articuno covered earlier by Let's Go. Regirock's reset is 1/4096, so it comes last.
        assertThat(names(plan())).containsExactly("tornadus", "thundurus", "zygarde", "articuno", "regirock").inOrder()
        assertThat(plan().hunts.map { it.standing }).containsExactly(
            FarmStanding.OnlyHere,
            FarmStanding.OnlyHere,
            FarmStanding.HereFirst,
            FarmStanding.EarlierElsewhere,
            FarmStanding.OnlyHere,
        ).inOrder()
    }

    @Test
    fun `the legendary giants keep 1 in 4096 though the Shiny Charm is curated for statics`() {
        val regirock = plan().hunts.single { it.lead.variant.id.value == "regirock" }

        assertThat(regirock.way!!.odds!!.best).isEqualTo(Odds.ofRolls(1))
    }

    @Test
    fun `locked legendaries are listed apart, never as hunts`() {
        assertThat(plan().locked.map { it.variant.id.value }).containsExactly("articuno-galar")
        assertThat(names(plan())).doesNotContain("articuno-galar")
    }

    @Test
    fun `an online-only boss says so, and an ordinary Pokemon is never a row`() {
        assertThat(plan().hunts.single { it.lead.variant.id.value == "thundurus" }.onlineOnly).isTrue()
        assertThat(plan().hunts.filter { it.onlineOnly }).hasSize(1)
        assertThat(names(plan())).doesNotContain("pikachu")
    }

    @Test
    fun `a caught legendary drops out`() {
        assertThat(names(plan(Fixtures.records(Fixtures.caught("zygarde"))))).doesNotContain("zygarde")
    }
}
