package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import net.pokedex.core.model.DexFixtures.dex
import net.pokedex.core.model.DexFixtures.key
import org.junit.Assert.assertThrows
import org.junit.Test

class DexTest {

    @Test
    fun `a duplicated variant resolves each copy to its own record`() {
        val records = Fixtures.records(Fixtures.caught("unown", copy = 1))
        val (first, second) = dex.copiesOf(VariantId("unown"))

        assertThat(first.key).isEqualTo(key("unown", 0))
        assertThat(statusOf(first, records)).isEqualTo(SlotStatus.Needed)
        assertThat(statusOf(second, records)).isEqualTo(SlotStatus.Caught)
    }

    @Test
    fun `copies come back in preset order, generation box before form box`() {
        assertThat(dex.copiesOf(VariantId("unown")).map { it.boxName })
            .containsExactly("Kanto 1", "Unown Dex").inOrder()
    }

    @Test
    fun `a box layout keeps holes as holes instead of sliding slots left`() {
        val layout = dex.layout(0)

        assertThat(layout).hasSize(8)
        assertThat(layout[5]).isNull()
        assertThat(layout[6]?.variant?.id).isEqualTo(VariantId("mewtwo"))
    }

    @Test
    fun `no shiny exists is a status, but a caught record overrides it`() {
        val magearna = dex.entry(key("magearna"))!!
        assertThat(statusOf(magearna, emptyMap())).isEqualTo(SlotStatus.NoShinyExists)
        assertThat(statusOf(magearna, Fixtures.records(Fixtures.caught("magearna"))))
            .isEqualTo(SlotStatus.Caught)
    }

    @Test
    fun `a slot demanding an unknown variant is a corrupt dataset, not a skipped row`() {
        assertThrows(IllegalArgumentException::class.java) {
            Dex.assemble(
                preset = dex.preset,
                boxes = DexFixtures.boxes,
                slots = DexFixtures.slots + Slot(PresetId("grouped-balanced"), 1, 3, VariantId("missingno"), 0),
                variants = DexFixtures.variants,
                species = emptyList(),
                games = DexFixtures.games,
                availability = emptyList(),
            )
        }
    }

    @Test
    fun `forms group by dex number and evolutions are found in both directions`() {
        assertThat(dex.forms(26).map { it.id.value }).containsExactly("raichu", "raichu-alola").inOrder()
        assertThat(dex.evolvesInto(VariantId("pikachu")).map { it.id.value })
            .containsExactly("raichu", "raichu-alola").inOrder()
        assertThat(dex.variant(VariantId("raichu"))?.evolvesFromId).isEqualTo(VariantId("pikachu"))
    }

    @Test
    fun `game sets are pairs in release order, and HOME is not one`() {
        assertThat(dex.gameSets.map { it.id }).containsExactly("swsh", "sv").inOrder()
    }

    @Test
    fun `a shiny-locked game does not count as somewhere to get it shiny`() {
        assertThat(dex.entry(key("zacian"))!!.shinyGameSets).isEmpty()
        assertThat(dex.entry(key("pikachu"))!!.shinyGameSets).containsExactly("sv", "swsh")
    }

    @Test
    fun `a duplicated variant is caught only when every copy is`() {
        val copies = dex.copiesOf(VariantId("unown"))
        val oneOfTwo = Fixtures.records(Fixtures.caught("unown", copy = 1))
        val both = Fixtures.records(Fixtures.caught("unown", copy = 0), Fixtures.caught("unown", copy = 1))

        assertThat(variantStatusOf(copies, oneOfTwo)).isEqualTo(SlotStatus.Needed)
        assertThat(variantStatusOf(copies, both)).isEqualTo(SlotStatus.Caught)
    }

    @Test
    fun `with no games chosen nothing is unavailable`() {
        assertThat(statusOf(dex.entry(key("raichu"))!!, emptyMap())).isEqualTo(SlotStatus.Needed)
    }

    @Test
    fun `a slot is unavailable only when none of my games offers it shiny`() {
        val scarlet = setOf(GameId("sv-s"))

        // Raichu is Violet-only in the fixture: owning Scarlet does not reach it.
        assertThat(statusOf(dex.entry(key("raichu"))!!, emptyMap(), scarlet)).isEqualTo(SlotStatus.Unavailable)
        assertThat(statusOf(dex.entry(key("pikachu"))!!, emptyMap(), scarlet)).isEqualTo(SlotStatus.Needed)
        // Shiny-locked in every game that has it, so owning those games does not help.
        val swsh = setOf(GameId("swsh-sw"), GameId("swsh-sh"))
        assertThat(statusOf(dex.entry(key("zacian"))!!, emptyMap(), swsh)).isEqualTo(SlotStatus.Unavailable)
    }

    @Test
    fun `caught and no-shiny win over unavailable`() {
        val scarlet = setOf(GameId("sv-s"))
        val caught = Fixtures.records(Fixtures.caught("raichu"))

        assertThat(statusOf(dex.entry(key("raichu"))!!, caught, scarlet)).isEqualTo(SlotStatus.Caught)
        assertThat(statusOf(dex.entry(key("magearna"))!!, emptyMap(), scarlet)).isEqualTo(SlotStatus.NoShinyExists)
    }

    @Test
    fun `HOME is never one of the games a slot can be hunted in`() {
        assertThat(dex.entry(key("zacian"))!!.shinyGames).isEmpty()
        assertThat(dex.entry(key("pikachu"))!!.shinyGames).containsExactly(GameId("sv-s"), GameId("swsh-sw"))
    }
}
