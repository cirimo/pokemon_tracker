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
}
