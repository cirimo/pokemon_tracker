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
}
