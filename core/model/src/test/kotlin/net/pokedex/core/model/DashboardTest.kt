package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import net.pokedex.core.model.DexFixtures.dex
import net.pokedex.core.model.DexFixtures.key
import org.junit.Test

class DashboardTest {

    private fun caught(variant: String, copy: Int = 0, at: Long = T0) =
        CatchRecord.empty(key(variant, copy), at).withCaught(true, at)

    private fun records(vararg r: CatchRecord) = r.associateBy { it.key }

    @Test
    fun `overall counts slots, so both unown copies are needed`() {
        val board = dashboardOf(dex, records(caught("unown")))

        assertThat(board.overall).isEqualTo(Progress(caught = 1, total = 11))
    }

    @Test
    fun `slots with no released shiny are counted separately, and only while uncaught`() {
        assertThat(dashboardOf(dex, emptyMap()).noShinyYet).isEqualTo(1)
        assertThat(dashboardOf(dex, records(caught("magearna"))).noShinyYet).isEqualTo(0)
    }

    @Test
    fun `needed by game counts shiny-obtainable slots only, and never HOME`() {
        val board = dashboardOf(dex, records(caught("pikachu")))

        val bySet = board.neededByGame.associate { it.gameSet.id to it.needed }
        // SV: raichu and flabebe (unown is not obtainable there, pikachu is caught).
        assertThat(bySet["sv"]).isEqualTo(2)
        // SwSh: nothing: pikachu is caught and zacian is shiny-locked.
        assertThat(bySet["swsh"]).isEqualTo(0)
        assertThat(bySet).doesNotContainKey("home")
    }

    @Test
    fun `closest to done skips empty and finished boxes, fewest left first`() {
        val board = dashboardOf(dex, records(caught("unown", copy = 1), caught("pichu")))

        // Kanto 1 has 6 left of 7, Unown Dex 3 left of 4.
        assertThat(board.closest.map { it.name }).containsExactly("Unown Dex", "Kanto 1").inOrder()
    }

    @Test
    fun `recent catches are newest first and skip records without a date or a slot`() {
        val undated = caught("mew").copy(caughtAt = null)
        val orphan = caught("pikachu-cosplay", at = T0 + 3)
        val board = dashboardOf(
            dex,
            records(caught("pichu", at = T0 + 1), caught("mewtwo", at = T0 + 2), undated, orphan),
        )

        assertThat(board.recent.map { it.entry.variant.id.value }).containsExactly("mewtwo", "pichu").inOrder()
        assertThat(board.orphans.map { it.key.variantId.value }).containsExactly("pikachu-cosplay")
    }

    @Test
    fun `the real preset's box names group into regions`() {
        val names = listOf(
            "Kanto 1", "Kanto 6", "Cap Pikachu", "Johto 1", "Unown Dex", "Kalos 3", "Trims & Flowers",
            "Vivillon Patt.", "Alola 4 & Other", "Galar 4", "Alcremie 1", "Alcremie 3", "Hisui", "Paldea 1",
        )
        val boxes = names.mapIndexed { i, name -> BoxProgress(i, name, Progress(caught = 1, total = 2)) }

        val regions = regionsOf(boxes)

        assertThat(regions.map { it.name })
            .containsExactly("Kanto", "Johto", "Kalos", "Alola", "Galar", "Alcremie", "Hisui", "Paldea").inOrder()
        assertThat(regions.first().boxes.map { it.name }).containsExactly("Kanto 1", "Kanto 6", "Cap Pikachu")
        assertThat(regions.first { it.name == "Kalos" }.progress).isEqualTo(Progress(caught = 3, total = 6))
    }

    private companion object {
        const val T0 = 1_700_000_000_000L
    }
}
