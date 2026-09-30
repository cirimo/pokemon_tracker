package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import net.pokedex.core.model.DexFixtures.dex
import net.pokedex.core.model.DexFixtures.key
import org.junit.Test

class RegularMarksTest {

    private val pikachu = key("pikachu")
    private val raichu = key("raichu")

    /** Applies a change the way CatchRepository.setRegular does. */
    private fun Map<CatchKey, CatchRecord>.written(changes: Map<CatchKey, Boolean>) =
        this + changes.map { (key, on) -> key to (this[key] ?: CatchRecord.empty(key, 0)).withRegular(on, 1) }

    @Test
    fun `a tap marks an empty slot and unmarks a regular one`() {
        assertThat(regularToggle(pikachu, emptyMap())).containsExactly(pikachu, true)
        assertThat(
            regularToggle(pikachu, Fixtures.records(Fixtures.regular("pikachu"))),
        ).containsExactly(pikachu, false)
    }

    @Test
    fun `a tap never takes a shiny off, even one with a regular behind it`() {
        assertThat(regularToggle(pikachu, Fixtures.records(Fixtures.caught("pikachu")))).isEmpty()
        assertThat(regularToggle(pikachu, Fixtures.records(Fixtures.caught("pikachu").copy(regular = true)))).isEmpty()
    }

    @Test
    fun `a tap on a record holding only a priority keeps it and marks it`() {
        val records = Fixtures.records(CatchRecord.empty(pikachu, 0).withPriority(Priority.Want, 0))

        assertThat(regularToggle(pikachu, records)).containsExactly(pikachu, true)
    }

    @Test
    fun `mark all marks only the slots holding nothing`() {
        val box = dex.entries.filter { it.slot.boxIndex == 0 }
        val records = Fixtures.records(Fixtures.caught("pichu"), Fixtures.regular("pikachu"))

        val changes = regularForAll(box, records)

        assertThat(changes.keys).containsNoneOf(key("pichu"), pikachu)
        assertThat(changes.keys).hasSize(box.size - 2)
        assertThat(changes.values.toSet()).containsExactly(true)
    }

    @Test
    fun `undo returns every slot to where the session found it`() {
        val start = Fixtures.records(Fixtures.regular("raichu"))
        var records = start
        var ledger = MarkLedger()
        fun apply(changes: Map<CatchKey, Boolean>) {
            ledger = ledger.after(records, changes)
            records = records.written(changes)
        }

        apply(regularToggle(pikachu, records)) // on
        apply(regularToggle(raichu, records)) // off
        apply(regularToggle(pikachu, records)) // off again
        apply(regularToggle(pikachu, records)) // and on

        assertThat(ledger.undo).containsExactly(pikachu, false, raichu, true)
        records = records.written(ledger.undo)
        assertThat(records.mapValues { it.value.ownership })
            .containsExactly(pikachu, Ownership.None, raichu, Ownership.Regular)
    }

    @Test
    fun `a slot tapped back to where it started is no change`() {
        var records = emptyMap<CatchKey, CatchRecord>()
        var ledger = MarkLedger()
        repeat(2) {
            val changes = regularToggle(pikachu, records)
            ledger = ledger.after(records, changes)
            records = records.written(changes)
        }

        assertThat(ledger.changed).isEqualTo(0)
    }

    @Test
    fun `undo after mark all takes back the whole box and nothing else`() {
        val box = dex.entries.filter { it.slot.boxIndex == 0 }
        val start = Fixtures.records(Fixtures.regular("pikachu"))
        val changes = regularForAll(box, start)

        val ledger = MarkLedger().after(start, changes)

        assertThat(ledger.undo.keys).isEqualTo(changes.keys)
        assertThat(ledger.undo.values.toSet()).containsExactly(false)
        assertThat(ledger.undo).doesNotContainKey(pikachu)
    }
}
