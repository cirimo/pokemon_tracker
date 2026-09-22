package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The seven duplicated variants in grouped-balanced are the reason CatchKey carries a
 * copyIndex at all. If this ever regresses, the app silently claims you own 1394
 * Pokemon when you own 1387.
 */
class CopyIndexTest {

    @Test
    fun `repeated variant gets an incrementing copy index in preset order`() {
        val keys = assignCopyIndices(
            listOf("bulbasaur", "unown", "ivysaur", "unown", "unown").map(::VariantId),
        )

        assertThat(keys.map { it.copyIndex }).containsExactly(0, 0, 0, 1, 2).inOrder()
    }

    @Test
    fun `distinct variants all get copy index zero`() {
        val keys = assignCopyIndices(
            listOf("bulbasaur", "ivysaur", "venusaur").map(::VariantId),
        )
        assertThat(keys.map { it.copyIndex }).containsExactly(0, 0, 0)
    }

    @Test
    fun `two slots demanding the same variant resolve to different catch keys`() {
        val johto = Fixtures.slot(box = 8, index = 29, variant = "unown", copy = 0)
        val unownDex = Fixtures.slot(box = 12, index = 0, variant = "unown", copy = 1)

        assertThat(johto.catchKey).isNotEqualTo(unownDex.catchKey)
    }

    @Test
    fun `catching one copy does not fill the other`() {
        val slots = listOf(
            Fixtures.slot(8, 29, "unown", copy = 0),
            Fixtures.slot(12, 0, "unown", copy = 1),
        )
        val records = Fixtures.records(Fixtures.caught("unown", copy = 0))

        assertThat(progressOf(slots, records)).isEqualTo(Progress(caught = 1, total = 2))
    }

    @Test
    fun `catch key renders the copy suffix only when it is non-zero`() {
        assertThat(CatchKey(VariantId("unown"), 0).toString()).isEqualTo("unown")
        assertThat(CatchKey(VariantId("unown"), 1).toString()).isEqualTo("unown#1")
    }
}
