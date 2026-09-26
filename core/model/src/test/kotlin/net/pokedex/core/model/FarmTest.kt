package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FarmTest {

    private val games = DexFixtures.games
    private val sword = GameId("swsh-sw")
    private val scarlet = GameId("sv-s")
    private val violet = GameId("sv-v")

    @Test
    fun `an order nobody has set reads in release order`() {
        // What every game chosen before the order existed migrates to.
        val ranks = mapOf(violet to 0, sword to 0, scarlet to 0)

        assertThat(farmOrderOf(games, ranks)).containsExactly(sword, scarlet, violet).inOrder()
    }

    @Test
    fun `a set order wins over release order, and ties still fall back to it`() {
        val ranks = mapOf(violet to 0, sword to 1, scarlet to 1)

        assertThat(farmOrderOf(games, ranks)).containsExactly(violet, sword, scarlet).inOrder()
    }

    @Test
    fun `a game the dataset no longer has is left out rather than guessed at`() {
        val ranks = listOf(GameId("gone"), violet).associateWith { 0 }

        assertThat(farmOrderOf(games, ranks)).containsExactly(violet)
    }

    @Test
    fun `moving a game shifts the ones it passes and stops at the ends`() {
        val order = listOf(sword, scarlet, violet)

        assertThat(moveInOrder(order, violet, -1)).containsExactly(sword, violet, scarlet).inOrder()
        assertThat(moveInOrder(order, sword, 1)).containsExactly(scarlet, sword, violet).inOrder()
        assertThat(moveInOrder(order, sword, -1)).isEqualTo(order)
        assertThat(moveInOrder(order, violet, 1)).isEqualTo(order)
        assertThat(moveInOrder(order, GameId("la"), 1)).isEqualTo(order)
    }

    // The fixture as far as farming goes: Pikachu is shiny in Scarlet and Sword, Raichu (which
    // evolves from it) and Flabebe in Violet only, Zacian is locked everywhere it is, Unown is
    // not obtainable, and Pichu, Mew and Mewtwo are in no game.
    private val dex = DexFixtures.dex
    private fun entry(variant: String) = requireNotNull(dex.entry(DexFixtures.key(variant)))

    @Test
    fun `a slot shiny in one of my games belongs there, and only there`() {
        val plan = FarmPlan(dex, listOf(scarlet, violet))

        assertThat(plan.placeOf(entry("flabebe"))).isEqualTo(FarmPlace(violet, onlyHere = true))
        assertThat(plan.matches(entry("flabebe"), violet, FarmScope.OnlyHere)).isTrue()
        assertThat(plan.matches(entry("flabebe"), violet, FarmScope.HereFirst)).isTrue()
    }

    @Test
    fun `a slot shiny in two of my games belongs to whichever comes first in my order`() {
        val scarletFirst = FarmPlan(dex, listOf(scarlet, sword))
        val swordFirst = FarmPlan(dex, listOf(sword, scarlet))

        assertThat(scarletFirst.placeOf(entry("pikachu"))).isEqualTo(FarmPlace(scarlet, onlyHere = false))
        assertThat(swordFirst.placeOf(entry("pikachu"))).isEqualTo(FarmPlace(sword, onlyHere = false))
        // Here first, but not only here, in either order.
        assertThat(swordFirst.matches(entry("pikachu"), sword, FarmScope.HereFirst)).isTrue()
        assertThat(swordFirst.matches(entry("pikachu"), sword, FarmScope.OnlyHere)).isFalse()
        assertThat(swordFirst.matches(entry("pikachu"), scarlet, FarmScope.HereFirst)).isFalse()
    }

    @Test
    fun `a slot none of my games offers shiny belongs nowhere`() {
        val plan = FarmPlan(dex, listOf(scarlet, violet, sword))

        listOf("zacian", "unown", "pichu", "mew").forEach { assertThat(plan.placeOf(entry(it))).isNull() }
    }

    @Test
    fun `a version exclusive is out of reach through the other version of the pair`() {
        // Raichu is shiny in Violet only. Owning Scarlet does not make it a Scarlet job.
        val plan = FarmPlan(dex, listOf(scarlet))

        assertThat(plan.placeOf(entry("raichu"))).isNull()
        assertThat(plan.placeOf(entry("pikachu"))).isEqualTo(FarmPlace(scarlet, onlyHere = true))
    }

    @Test
    fun `an evolution belongs to the game it can be evolved in, not to where its base is`() {
        // Pikachu is in Scarlet and Sword; Raichu can only be had in Violet.
        val plan = FarmPlan(dex, listOf(sword, scarlet, violet))

        assertThat(plan.placeOf(entry("pikachu"))?.game).isEqualTo(sword)
        assertThat(plan.placeOf(entry("raichu"))).isEqualTo(FarmPlace(violet, onlyHere = true))
    }

    @Test
    fun `a variant with no shiny released is nobody's farm job, even where it is obtainable`() {
        val withMagearna = Dex.assemble(
            preset = dex.preset,
            boxes = DexFixtures.boxes,
            slots = DexFixtures.slots,
            variants = DexFixtures.variants,
            species = emptyList(),
            games = games,
            availability = DexFixtures.availability + DexFixtures.available("magearna", "sv-v"),
        )
        val magearna = requireNotNull(withMagearna.entry(DexFixtures.key("magearna")))

        assertThat(FarmPlan(withMagearna, listOf(violet)).placeOf(magearna)).isNull()
    }

    @Test
    fun `catching a slot leaves where it belongs alone and takes it out of the counts`() {
        val plan = FarmPlan(dex, listOf(sword, violet))
        val before = farmCounts(dex, emptyMap(), plan)
        val caught = mapOf(DexFixtures.key("raichu") to Fixtures.caught("raichu"))

        val after = farmCounts(dex, caught, plan)

        assertThat(before).containsExactly(GameFarm(sword, 1, 1), GameFarm(violet, 2, 2)).inOrder()
        assertThat(after).containsExactly(GameFarm(sword, 1, 1), GameFarm(violet, 1, 1)).inOrder()
        assertThat(plan.placeOf(entry("raichu"))).isEqualTo(FarmPlace(violet, onlyHere = true))
    }

    @Test
    fun `a game with nothing left first is done, even if it still offers what others come first for`() {
        val plan = FarmPlan(dex, listOf(scarlet, sword))

        val counts = farmCounts(dex, emptyMap(), plan)

        // Pikachu is Scarlet's; Sword offers it too but comes second.
        assertThat(counts).containsExactly(GameFarm(scarlet, 0, 1), GameFarm(sword, 0, 0)).inOrder()
        assertThat(counts.last().done).isTrue()
    }
}
