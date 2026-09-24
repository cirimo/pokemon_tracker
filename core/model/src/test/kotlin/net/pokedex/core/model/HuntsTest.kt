package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import net.pokedex.core.model.DexFixtures.available
import net.pokedex.core.model.DexFixtures.key
import org.junit.Test

/**
 * The fixture, as far as hunting goes: Pikachu is shiny in Scarlet and Sword, Raichu and
 * Flabebe in Violet, Zacian is locked everywhere that has it, Unown is in Scarlet's data but
 * not obtainable, and Pichu, Mew and Mewtwo are in no game at all.
 */
class HuntsTest {

    private val scarletViolet = setOf(GameId("sv-s"), GameId("sv-v"))
    private val noGuide = HuntGuide(emptyList(), emptyList(), emptyList())

    private fun encounter(
        variant: String,
        game: String,
        method: String = "wild",
        from: String? = null,
        locked: Boolean = false,
    ) = Encounter(
        id = "$variant:$game:$method",
        variantId = VariantId(variant),
        gameId = GameId(game),
        methodId = method,
        location = null,
        prerequisite = null,
        notes = null,
        shinyLocked = locked,
        fromVariantId = from?.let(::VariantId),
        sourceUrl = "https://example.org/$variant",
    )

    private fun charm(game: String, method: String = "wild", rolls: Int = 2) = OddsModifier(
        gameId = GameId(game),
        methodId = method,
        id = "shiny-charm",
        label = "Shiny Charm",
        rollsAdded = rolls,
        denominator = null,
        tier = null,
        inherent = false,
        notes = null,
        sourceUrl = "https://example.org/charm",
    )

    private fun names(plan: HuntPlan) = plan.hunts.map { it.lead.variant.id.value }

    @Test
    fun `only needed slots one of my games offers shiny are hunts`() {
        val plan = huntPlan(DexFixtures.dex, emptyMap(), scarletViolet, noGuide)

        assertThat(names(plan)).containsExactly("flabebe", "pikachu", "raichu")
    }

    @Test
    fun `with nothing else to go on, the nearly finished box comes first, then preset order`() {
        // Box 1 has three needed slots and box 0 seven, so Flabebe leaves the fewest behind.
        val plan = huntPlan(DexFixtures.dex, emptyMap(), scarletViolet, noGuide)

        assertThat(names(plan)).containsExactly("flabebe", "pikachu", "raichu").inOrder()
        assertThat(plan.hunts.first().box.remaining).isEqualTo(3)
    }

    @Test
    fun `priority beats everything, and later sinks below normal`() {
        val records = Fixtures.records(
            Fixtures.caught("raichu").copy(caught = false, priority = Priority.Want.value),
            Fixtures.caught("flabebe").copy(caught = false, priority = Priority.Later.value),
        )

        val plan = huntPlan(DexFixtures.dex, records, scarletViolet, noGuide)

        assertThat(names(plan)).containsExactly("raichu", "pikachu", "flabebe").inOrder()
        assertThat(plan.hunts.first().priority).isEqualTo(Priority.Want)
    }

    @Test
    fun `a recorded method beats a nearer box`() {
        val guide = HuntGuide(listOf(encounter("raichu", "sv-v")), emptyList(), emptyList())

        val plan = huntPlan(DexFixtures.dex, emptyMap(), scarletViolet, guide)

        assertThat(names(plan).first()).isEqualTo("raichu")
        assertThat(plan.hunts.first().way?.encounter?.methodId).isEqualTo("wild")
        // Recorded, but with no curated odds for the method: the odds stay unknown.
        assertThat(plan.hunts.first().way?.odds).isNull()
    }

    @Test
    fun `a method in a game I do not own does not count`() {
        val guide = HuntGuide(listOf(encounter("pikachu", "swsh-sw")), emptyList(), emptyList())

        val plan = huntPlan(DexFixtures.dex, emptyMap(), scarletViolet, guide)

        assertThat(plan.hunts.single { it.lead.variant.id.value == "pikachu" }.way).isNull()
    }

    @Test
    fun `a shiny-locked encounter is not a way`() {
        val guide = HuntGuide(listOf(encounter("raichu", "sv-v", locked = true)), emptyList(), emptyList())

        assertThat(guide.bestWay(VariantId("raichu"), listOf(GameId("sv-v")))).isNull()
    }

    @Test
    fun `the best way is the one with the best odds, then one with odds at all`() {
        val guide = HuntGuide(
            listOf(encounter("pikachu", "sv-s", "static"), encounter("pikachu", "sv-s", "wild")),
            listOf(charm("sv-s", "wild")),
            emptyList(),
        )

        val way = guide.bestWay(VariantId("pikachu"), listOf(GameId("sv-s")))!!

        assertThat(way.encounter.methodId).isEqualTo("wild")
        assertThat(way.odds!!.best.rolls).isEqualTo(3)
    }

    @Test
    fun `an evolution takes the odds of hunting what it evolves from`() {
        val guide = HuntGuide(
            listOf(encounter("raichu", "sv-s", "evolution", from = "pikachu"), encounter("pikachu", "sv-s")),
            listOf(charm("sv-s")),
            emptyList(),
        )

        val way = guide.bestWay(VariantId("raichu"), listOf(GameId("sv-s")))!!

        assertThat(way.encounter.methodId).isEqualTo("evolution")
        assertThat(way.via!!.encounter.variantId).isEqualTo(VariantId("pikachu"))
        assertThat(way.odds!!.best.rolls).isEqualTo(3)
    }

    @Test
    fun `an evolution from something with no way to find it is not a way`() {
        val evolution = encounter("raichu", "sv-s", "evolution", from = "pikachu")
        val guide = HuntGuide(listOf(evolution), emptyList(), emptyList())

        assertThat(guide.bestWay(VariantId("raichu"), listOf(GameId("sv-s")))).isNull()
    }

    @Test
    fun `both copies of a duplicated variant are one hunt filling two slots`() {
        val dex = assemble(DexFixtures.availability + available("unown", "sv-v"))

        val plan = huntPlan(dex, emptyMap(), scarletViolet, noGuide)
        val unown = plan.hunts.single { it.key.dexNum == 201 }

        assertThat(unown.slots.map { it.key }).containsExactly(key("unown", 0), key("unown", 1)).inOrder()
        // More slots from one hunt outranks a nearer box.
        assertThat(plan.hunts.first()).isEqualTo(unown)
    }

    @Test
    fun `a hunt counts only the slots its method reaches`() {
        val variants = DexFixtures.variants.map {
            if (it.id.value == "raichu-alola") it.copy(isRegional = false) else it
        }
        val dex = assemble(DexFixtures.availability + available("raichu-alola", "sv-v"), variants)
        val guide = HuntGuide(listOf(encounter("raichu", "sv-v")), emptyList(), emptyList())

        val raichu = huntPlan(dex, emptyMap(), scarletViolet, guide).hunts.single { it.key.dexNum == 26 }

        assertThat(raichu.slots).hasSize(2)
        assertThat(raichu.slotsWithWay).isEqualTo(1)
    }

    @Test
    fun `catching one copy leaves a hunt for the other`() {
        val dex = assemble(DexFixtures.availability + available("unown", "sv-v"))
        val records = Fixtures.records(Fixtures.caught("unown", copy = 1))

        val unown = huntPlan(dex, records, scarletViolet, noGuide).hunts.single { it.key.dexNum == 201 }

        assertThat(unown.slots.map { it.key }).containsExactly(key("unown", 0))
    }

    @Test
    fun `a regional form is its own hunt`() {
        val variants = DexFixtures.variants.map {
            if (it.id.value == "raichu-alola") it.copy(isRegional = true, formId = "alola") else it
        }
        val dex = assemble(DexFixtures.availability + available("raichu-alola", "sv-v"), variants)

        val raichus = huntPlan(dex, emptyMap(), scarletViolet, noGuide).hunts.filter { it.key.dexNum == 26 }

        assertThat(raichus.map { it.key.regionalForm }).containsExactly(null, "alola")
    }

    @Test
    fun `a female regional form is the same hunt as the male`() {
        val female = DexFixtures.variant("raichu-alola-f", 26, "Raichu (Alolan, Female)")
            .copy(isRegional = true, formId = "alola-f", isFemaleForm = true)
        val variants = DexFixtures.variants.map {
            if (it.id.value == "raichu-alola") it.copy(isRegional = true, formId = "alola") else it
        } + female
        val dex = Dex.assemble(
            preset = DexFixtures.dex.preset,
            boxes = DexFixtures.boxes,
            // The mewtwo slot becomes the female form's, so the preset keeps its size.
            slots = DexFixtures.slots.map {
                if (it.variantId.value == "mewtwo") it.copy(variantId = VariantId("raichu-alola-f")) else it
            },
            variants = variants,
            species = emptyList(),
            games = DexFixtures.games,
            availability = DexFixtures.availability +
                available("raichu-alola", "sv-v") + available("raichu-alola-f", "sv-v"),
        )

        val alolan = huntPlan(dex, emptyMap(), scarletViolet, noGuide).hunts.single { it.key.regionalForm == "alola" }

        assertThat(alolan.slots.map { it.variant.id.value }).containsExactly("raichu-alola", "raichu-alola-f")
    }

    @Test
    fun `slots out of reach are kept, each with its reason`() {
        val plan = huntPlan(DexFixtures.dex, emptyMap(), setOf(GameId("sv-s")), noGuide)
        val reasons = plan.outOfReach.associate { it.entry.key.toString() to it.reason }

        assertThat(reasons["raichu"]).isEqualTo(OutOfReach.Reason.OtherGames)
        assertThat(plan.outOfReach.single { it.entry.key == key("raichu") }.games).containsExactly(GameId("sv-v"))
        assertThat(reasons["zacian"]).isEqualTo(OutOfReach.Reason.ShinyLocked)
        assertThat(reasons["mew"]).isEqualTo(OutOfReach.Reason.TransferOnly)
        // Magearna has no shiny at all: not a gap anything can fill, so not listed.
        assertThat(reasons).doesNotContainKey("magearna")
    }

    @Test
    fun `a hunt that fills every needed slot left in a box finishes it`() {
        val records = Fixtures.records(Fixtures.caught("unown", copy = 1), Fixtures.caught("zacian"))

        val flabebe = huntPlan(DexFixtures.dex, records, scarletViolet, noGuide).hunts.first()

        assertThat(flabebe.lead.variant.id.value).isEqualTo("flabebe")
        assertThat(flabebe.box.finishes).isTrue()
    }

    private fun assemble(
        availability: List<GameAvailability>,
        variants: List<Variant> = DexFixtures.variants,
    ): Dex = Dex.assemble(
        preset = DexFixtures.dex.preset,
        boxes = DexFixtures.boxes,
        slots = DexFixtures.slots,
        variants = variants,
        species = emptyList(),
        games = DexFixtures.games,
        availability = availability,
    )

    @Test
    fun `a game's standing says why it is or is not a place to hunt`() {
        val dex = DexFixtures.dex

        assertThat(standingOf(dex, VariantId("pikachu"), GameId("sv-s"))).isEqualTo(Standing.Shiny)
        assertThat(standingOf(dex, VariantId("zacian"), GameId("swsh-sw"))).isEqualTo(Standing.ShinyLocked)
        assertThat(standingOf(dex, VariantId("unown"), GameId("sv-s"))).isEqualTo(Standing.Absent)
        assertThat(standingOf(dex, VariantId("raichu"), GameId("sv-s"))).isEqualTo(Standing.Absent)
    }

    @Test
    fun `setting the bucket already chosen is not a change`() {
        val record = Fixtures.caught("pikachu").copy(priority = 5, updatedAt = 1L)

        assertThat(record.withPriority(Priority.Want, now = 2L)).isSameInstanceAs(record)
        assertThat(record.withPriority(Priority.Later, now = 2L).priority).isEqualTo(-1)
        assertThat(record.withPriority(Priority.Later, now = 2L).updatedAt).isEqualTo(2L)
    }
}
