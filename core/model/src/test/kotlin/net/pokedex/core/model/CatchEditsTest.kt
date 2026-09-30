package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CatchEditsTest {

    private val key = CatchKey(VariantId("bulbasaur"), 0)
    private val scarlet = GameId("sv-s")
    private val arceus = GameId("la")
    private val t1 = 1_700_000_000_000L
    private val t2 = t1 + 60_000

    private fun availability(game: GameId, obtainable: Boolean = true, locked: Boolean = false) = GameAvailability(
        variantId = key.variantId,
        gameId = game,
        obtainable = obtainable,
        eventOnly = false,
        storable = true,
        transferOnly = false,
        shinyLocked = locked,
        shinyLockReason = null,
    )

    @Test
    fun `ticking a fresh slot stamps the date and takes the prefill`() {
        val record = CatchRecord.empty(key, t1).withCaught(true, t2, prefill = scarlet)

        assertThat(record.caught).isTrue()
        assertThat(record.caughtAt).isEqualTo(t2)
        assertThat(record.originGameId).isEqualTo(scarlet)
        assertThat(record.updatedAt).isEqualTo(t2)
    }

    @Test
    fun `an accidental untick keeps origin, date and notes`() {
        val caught = CatchRecord.empty(key, t1).withCaught(true, t1, scarlet).copy(notes = "412 resets")

        val unticked = caught.withCaught(false, t2)

        assertThat(unticked.caught).isFalse()
        assertThat(unticked.originGameId).isEqualTo(scarlet)
        assertThat(unticked.caughtAt).isEqualTo(t1)
        assertThat(unticked.notes).isEqualTo("412 resets")
        assertThat(unticked.hasDetails).isTrue()
    }

    @Test
    fun `re-ticking restores the original date and origin, not the prefill`() {
        val unticked = CatchRecord.empty(key, t1).withCaught(true, t1, scarlet).withCaught(false, t2)

        val again = unticked.withCaught(true, t2 + 1, prefill = arceus)

        assertThat(again.caughtAt).isEqualTo(t1)
        assertThat(again.originGameId).isEqualTo(scarlet)
    }

    @Test
    fun `unticking never picks up a prefill`() {
        val record = CatchRecord.empty(key, t1).withCaught(false, t2, prefill = scarlet)

        assertThat(record.originGameId).isNull()
    }

    @Test
    fun `saving blank notes stores none`() {
        val record = CatchRecord.empty(key, t1).withDetails(scarlet, t1, notes = "   ", now = t2)

        assertThat(record.notes).isNull()
        assertThat(record.updatedAt).isEqualTo(t2)
    }

    @Test
    fun `details can be cleared back to nothing`() {
        val record = CatchRecord.empty(key, t1).withDetails(scarlet, t1, "x", t1).withDetails(null, null, null, t2)

        assertThat(record.hasDetails).isFalse()
    }

    @Test
    fun `the last game is prefilled where the variant can be shiny`() {
        assertThat(prefillOrigin(scarlet, listOf(availability(scarlet)))).isEqualTo(scarlet)
    }

    @Test
    fun `no prefill where the variant is shiny-locked, absent, or not obtainable`() {
        assertThat(prefillOrigin(scarlet, listOf(availability(scarlet, locked = true)))).isNull()
        assertThat(prefillOrigin(scarlet, listOf(availability(arceus)))).isNull()
        assertThat(prefillOrigin(scarlet, listOf(availability(scarlet, obtainable = false)))).isNull()
        assertThat(prefillOrigin(null, listOf(availability(scarlet)))).isNull()
    }

    @Test
    fun `ownership reads shiny over regular, and nothing as none`() {
        val empty = CatchRecord.empty(key, t1)

        assertThat(empty.ownership).isEqualTo(Ownership.None)
        assertThat(empty.withRegular(true, t2).ownership).isEqualTo(Ownership.Regular)
        assertThat(empty.withCaught(true, t2).ownership).isEqualTo(Ownership.Shiny)
        assertThat(empty.withRegular(true, t2).withCaught(true, t2).ownership).isEqualTo(Ownership.Shiny)
    }

    @Test
    fun `marking regular records no game and no date`() {
        val marked = CatchRecord.empty(key, t1).withRegular(true, t2)

        assertThat(marked.regular).isTrue()
        assertThat(marked.caught).isFalse()
        assertThat(marked.originGameId).isNull()
        assertThat(marked.caughtAt).isNull()
        assertThat(marked.updatedAt).isEqualTo(t2)
    }

    @Test
    fun `a mark already in place is not a change`() {
        val marked = CatchRecord.empty(key, t1).withRegular(true, t1)

        assertThat(marked.withRegular(true, t2)).isSameInstanceAs(marked)
    }

    @Test
    fun `catching the shiny of a regular slot stamps a fresh catch and keeps the regular behind it`() {
        val regular = CatchRecord.empty(key, t1).withRegular(true, t1)

        val upgraded = regular.withCaught(true, t2, prefill = scarlet)

        assertThat(upgraded.ownership).isEqualTo(Ownership.Shiny)
        assertThat(upgraded.caughtAt).isEqualTo(t2)
        assertThat(upgraded.originGameId).isEqualTo(scarlet)
        assertThat(upgraded.regular).isTrue()
    }

    @Test
    fun `an accidental untick of an upgrade gives the regular back`() {
        val upgraded = CatchRecord.empty(key, t1).withRegular(true, t1).withCaught(true, t1, scarlet)

        assertThat(upgraded.withCaught(false, t2).ownership).isEqualTo(Ownership.Regular)
    }
}
