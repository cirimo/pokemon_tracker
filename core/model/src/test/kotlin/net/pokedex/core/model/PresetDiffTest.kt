package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The scenario these tests defend against: upstream inserts one form near the front of
 * the preset, every later slot shifts by one position, and a position-keyed design
 * silently reassigns 1300 catch records to the wrong Pokemon.
 */
class PresetDiffTest {

    @Test
    fun `inserting a slot shifts positions but strands nothing`() {
        val old = listOf(
            Fixtures.slot(0, 0, "bulbasaur"),
            Fixtures.slot(0, 1, "ivysaur"),
            Fixtures.slot(0, 2, "venusaur"),
        )
        val new = listOf(
            Fixtures.slot(0, 0, "bulbasaur"),
            Fixtures.slot(0, 1, "ivysaur"),
            Fixtures.slot(0, 2, "venusaur-f"),
            Fixtures.slot(0, 3, "venusaur"),
        )
        val records = Fixtures.records(Fixtures.caught("venusaur"))

        val diff = diffPresets(old, new, records)

        assertThat(diff.added.map { it.toString() }).containsExactly("venusaur-f")
        assertThat(diff.removed).isEmpty()
        assertThat(diff.strandedRecords).isEmpty()
        assertThat(diff.hasLoss).isFalse()
        assertThat(diff.moved.map { it.key.toString() }).containsExactly("venusaur")
    }

    @Test
    fun `a slot moving to another box is a move, not a remove plus add`() {
        val old = listOf(Fixtures.slot(0, 5, "furfrou"))
        val new = listOf(Fixtures.slot(33, 0, "furfrou"))

        val diff = diffPresets(old, new)

        assertThat(diff.added).isEmpty()
        assertThat(diff.removed).isEmpty()
        assertThat(diff.moved).hasSize(1)
        assertThat(diff.moved.single().from).isEqualTo(PresetDiff.Position(0, 5))
        assertThat(diff.moved.single().to).isEqualTo(PresetDiff.Position(33, 0))
    }

    @Test
    fun `removing a slot the user has caught is reported as a stranded record`() {
        val old = listOf(
            Fixtures.slot(0, 0, "bulbasaur"),
            Fixtures.slot(0, 1, "magearna-original"),
        )
        val new = listOf(Fixtures.slot(0, 0, "bulbasaur"))
        val records = Fixtures.records(Fixtures.caught("magearna-original"))

        val diff = diffPresets(old, new, records)

        assertThat(diff.removed.map { it.toString() }).containsExactly("magearna-original")
        assertThat(diff.strandedRecords.map { it.toString() }).containsExactly("magearna-original")
        assertThat(diff.hasLoss).isTrue()
    }

    @Test
    fun `removing a slot the user has not caught is not a loss`() {
        val old = listOf(
            Fixtures.slot(0, 0, "bulbasaur"),
            Fixtures.slot(0, 1, "magearna-original"),
        )
        val new = listOf(Fixtures.slot(0, 0, "bulbasaur"))

        val diff = diffPresets(old, new, records = emptyMap())

        assertThat(diff.removed).hasSize(1)
        assertThat(diff.hasLoss).isFalse()
    }

    @Test
    fun `dropping the second copy of a duplicated variant strands only that copy`() {
        val old = listOf(
            Fixtures.slot(8, 29, "unown", copy = 0),
            Fixtures.slot(12, 0, "unown", copy = 1),
        )
        val new = listOf(Fixtures.slot(8, 29, "unown", copy = 0))
        val records = Fixtures.records(
            Fixtures.caught("unown", copy = 0),
            Fixtures.caught("unown", copy = 1),
        )

        val diff = diffPresets(old, new, records)

        assertThat(diff.strandedRecords.map { it.toString() }).containsExactly("unown#1")
    }

    @Test
    fun `an identical preset diffs to nothing`() {
        val slots = listOf(Fixtures.slot(0, 0, "bulbasaur"), Fixtures.slot(0, 1, "ivysaur"))
        assertThat(diffPresets(slots, slots).isEmpty).isTrue()
    }
}
