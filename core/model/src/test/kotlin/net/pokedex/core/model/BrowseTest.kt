package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import net.pokedex.core.model.DexFixtures.key
import org.junit.Test

/**
 * Browsing between slots in detail: the list is re-asked from a small context, so these
 * check that the answer is the list the user was looking at.
 *
 * The fixture's box 0 is pichu, pikachu, raichu, raichu-alola, unown, a hole, mewtwo, mew;
 * box 1 is the second unown, flabebe, zacian, magearna.
 */
class BrowseTest {

    private val dex = DexFixtures.dex
    private val scarletViolet = listOf(GameId("sv-s"), GameId("sv-v"))
    private val noGuide = HuntGuide(emptyList(), emptyList(), emptyList())

    private fun caught(vararg keys: CatchKey, priority: Int = 0) =
        keys.associateWith { CatchRecord.empty(it, now = 0).copy(caught = true, priority = priority) }

    private fun keys(browse: Browse, keep: CatchKey, records: Map<CatchKey, CatchRecord> = emptyMap()) =
        browseKeys(browse, dex, records, scarletViolet, noGuide, keep)

    @Test
    fun `a box lists its filled slots in grid order and skips the hole`() {
        assertThat(keys(Browse.Box(0), keep = key("pikachu"))).containsExactly(
            key("pichu"),
            key("pikachu"),
            key("raichu"),
            key("raichu-alola"),
            key("unown"),
            key("mewtwo"),
            key("mew"),
        ).inOrder()
    }

    @Test
    fun `a box stops at its own ends and never runs into the next box`() {
        val box = keys(Browse.Box(0), keep = key("mew"))

        assertThat(box.first()).isEqualTo(key("pichu"))
        assertThat(box.last()).isEqualTo(key("mew"))
        assertThat(box).doesNotContain(key("unown", copy = 1))
    }

    @Test
    fun `two copies of one variant in the same box are two stops, in slot order`() {
        // The hole at 0:5 filled with the second Unown, so both copies share a box.
        val slots = DexFixtures.slots.map {
            if (it.variantId.value == "unown" && it.copyIndex == 1) it.copy(boxIndex = 0, slotIndex = 5) else it
        }
        val twoUnown = Dex.assemble(
            preset = DexPreset(PresetId("grouped-balanced"), "Grouped", "", 1, 1, boxCount = 2, filledSlotCount = 11),
            boxes = DexFixtures.boxes,
            slots = slots,
            variants = DexFixtures.variants,
            species = emptyList(),
            games = DexFixtures.games,
            availability = DexFixtures.availability,
        )

        val box = browseKeys(Browse.Box(0), twoUnown, emptyMap(), scarletViolet, noGuide, keep = key("unown"))

        assertThat(box.filter { it.variantId.value == "unown" })
            .containsExactly(key("unown"), key("unown", copy = 1)).inOrder()
        assertThat(box).hasSize(8)
    }

    @Test
    fun `a search lists exactly what the search list shows, in the same order`() {
        val filter = DexFilter(query = "rai")
        val records = caught(key("raichu-alola"))

        assertThat(keys(Browse.Search(filter), keep = key("raichu"), records = records))
            .isEqualTo(searchDex(dex, records, filter).map { it.key })
    }

    @Test
    fun `both copies of a duplicate appear in a search, never merged`() {
        assertThat(keys(Browse.Search(DexFilter(query = "unown")), keep = key("unown")))
            .containsExactly(key("unown"), key("unown", copy = 1)).inOrder()
    }

    @Test
    fun `a slot caught out of a needed search keeps its place when the list is asked again`() {
        val needed = Browse.Search(DexFilter(caught = CaughtFilter.Needed))
        val before = keys(needed, keep = key("pikachu"))

        val after = keys(needed, keep = key("pikachu"), records = caught(key("pikachu")))

        assertThat(after).isEqualTo(before)
    }

    @Test
    fun `other slots caught meanwhile do drop out of a needed search`() {
        val needed = Browse.Search(DexFilter(caught = CaughtFilter.Needed))

        val after = keys(needed, keep = key("pikachu"), records = caught(key("raichu")))

        assertThat(after).contains(key("pikachu"))
        assertThat(after).doesNotContain(key("raichu"))
    }

    @Test
    fun `the hunt list is one stop per hunt, on its lead, in ranked order`() {
        val plan = huntPlan(dex, emptyMap(), scarletViolet.toSet(), noGuide)

        assertThat(keys(Browse.Hunt(), keep = key("pikachu"))).isEqualTo(plan.hunts.map { it.lead.key })
    }

    @Test
    fun `a hunt caught while browsing keeps its place and its priority`() {
        val wanted = mapOf(key("raichu") to CatchRecord.empty(key("raichu"), now = 0).copy(priority = 1))
        val before = keys(Browse.Hunt(), keep = key("raichu"), records = wanted)

        val after = keys(Browse.Hunt(), keep = key("raichu"), records = caught(key("raichu"), priority = 1))

        assertThat(before.first()).isEqualTo(key("raichu"))
        assertThat(after).isEqualTo(before)
    }

    @Test
    fun `a hunt filtered to a game I no longer have falls back to all my games, as the hunt screen does`() {
        assertThat(keys(Browse.Hunt(gameId = "swsh-sw"), keep = key("pikachu")))
            .isEqualTo(keys(Browse.Hunt(), keep = key("pikachu")))
        assertThat(huntGames(scarletViolet.toSet(), GameId("swsh-sw"))).isEqualTo(scarletViolet.toSet())
        assertThat(huntGames(scarletViolet.toSet(), GameId("sv-v"))).containsExactly(GameId("sv-v"))
    }

    @Test
    fun `a slot the list cannot place is shown alone rather than dropped`() {
        assertThat(keys(Browse.Box(1), keep = key("pikachu"))).containsExactly(key("pikachu"))
    }

    @Test
    fun `a context survives the route and an unreadable one is ignored`() {
        val contexts = listOf(
            Browse.Box(3),
            Browse.Search(DexFilter(query = "mew", caught = CaughtFilter.Needed, gameSets = setOf("sv"))),
            Browse.Search(DexFilter(farm = FarmFilter("sv-v", FarmScope.OnlyHere))),
            Browse.Hunt(gameId = "sv-s"),
            Browse.Hunt(gameId = "sv-v", scope = FarmScope.OnlyHere),
            Browse.Hunt(),
        )

        contexts.forEach { assertThat(Browse.decode(Browse.encode(it))).isEqualTo(it) }
        assertThat(Browse.decode("""{"type":"somewhere-new"}""")).isNull()
        assertThat(Browse.decode("not json")).isNull()
    }

    @Test
    fun `a search context written before the farm filter still decodes, with no farm filter`() {
        // Exactly what prompt 6 put in a route.
        val old = """{"type":"search","filter":{"query":"mew","caught":"Needed","gameSets":["sv"]}}"""

        assertThat(Browse.decode(old))
            .isEqualTo(Browse.Search(DexFilter(query = "mew", caught = CaughtFilter.Needed, gameSets = setOf("sv"))))
    }

    @Test
    fun `a farm search browses the list it showed, in my order`() {
        // Scarlet first: Pikachu is Scarlet's. Violet keeps Raichu and Flabebe, both only there.
        val violetFirst = Browse.Search(DexFilter(caught = CaughtFilter.Needed, farm = FarmFilter("sv-v")))
        val onlyScarlet = Browse.Search(DexFilter(farm = FarmFilter("sv-s", FarmScope.OnlyHere)))

        assertThat(keys(violetFirst, keep = key("raichu"))).containsExactly(key("raichu"), key("flabebe")).inOrder()
        assertThat(keys(onlyScarlet, keep = key("pikachu"))).containsExactly(key("pikachu"))
    }

    @Test
    fun `a slot caught out of a farm search keeps its place`() {
        val browse = Browse.Search(DexFilter(caught = CaughtFilter.Needed, farm = FarmFilter("sv-v")))

        val after = keys(browse, keep = key("flabebe"), records = caught(key("flabebe")))

        assertThat(after).containsExactly(key("raichu"), key("flabebe")).inOrder()
    }

    @Test
    fun `a hunt context written before scopes still decodes, as everything the game offers`() {
        assertThat(Browse.decode("""{"type":"hunt","gameId":"sv-s"}""")).isEqualTo(Browse.Hunt("sv-s", scope = null))
    }

    @Test
    fun `a hunt list narrowed to a game's share browses that share`() {
        // Scarlet first, then Violet: Pikachu is Scarlet's; Raichu and Flabebe are Violet's.
        val order = listOf(GameId("sv-s"), GameId("sv-v"))
        val violetFirst = Browse.Hunt("sv-v", FarmScope.HereFirst)

        val keys = browseKeys(violetFirst, dex, emptyMap(), order, noGuide, keep = key("raichu"))

        assertThat(keys).isEqualTo(
            huntPlanFor(dex, emptyMap(), order, GameId("sv-v"), FarmScope.HereFirst, noGuide).hunts.map { it.lead.key },
        )
        assertThat(keys).doesNotContain(key("pikachu"))
    }
}
