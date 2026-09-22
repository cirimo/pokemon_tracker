package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProgressTest {

    private val slots = listOf(
        Fixtures.slot(0, 0, "bulbasaur"),
        Fixtures.slot(0, 1, "ivysaur"),
        Fixtures.slot(1, 0, "charmander"),
        Fixtures.slot(1, 1, "charmeleon"),
        Fixtures.slot(1, 2, "charizard"),
    )

    @Test
    fun `progress counts slots, not distinct variants`() {
        val records = Fixtures.records(
            Fixtures.caught("bulbasaur"),
            Fixtures.caught("charizard"),
        )
        assertThat(progressOf(slots, records)).isEqualTo(Progress(2, 5))
    }

    @Test
    fun `an uncaught record does not count`() {
        val records = Fixtures.records(
            Fixtures.caught("bulbasaur").copy(caught = false),
        )
        assertThat(progressOf(slots, records).caught).isEqualTo(0)
    }

    @Test
    fun `progress by box splits on box index and boxes need not be equal size`() {
        val records = Fixtures.records(Fixtures.caught("charmander"))
        val byBox = progressByBox(slots, records)

        assertThat(byBox[0]).isEqualTo(Progress(0, 2))
        assertThat(byBox[1]).isEqualTo(Progress(1, 3))
    }

    @Test
    fun `a record with no slot in this preset is reported as orphaned, never dropped`() {
        val records = Fixtures.records(
            Fixtures.caught("bulbasaur"),
            Fixtures.caught("pikachu-partner"),
        )
        val orphans = orphanedRecords(slots, records)

        assertThat(orphans.map { it.key.variantId.value }).containsExactly("pikachu-partner")
    }

    @Test
    fun `empty preset yields zero fraction rather than dividing by zero`() {
        assertThat(progressOf(emptyList(), emptyMap()).fraction).isEqualTo(0f)
    }
}
