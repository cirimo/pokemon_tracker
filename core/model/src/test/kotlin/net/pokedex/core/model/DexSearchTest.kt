package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import net.pokedex.core.model.DexFixtures.dex
import org.junit.Test

class DexSearchTest {

    private fun search(filter: DexFilter, records: Map<CatchKey, CatchRecord> = emptyMap()) =
        searchDex(dex, records, filter).map { it.key.toString() }

    @Test
    fun `an empty filter returns every filled slot in preset order`() {
        assertThat(search(DexFilter())).hasSize(DexFixtures.slots.size)
        assertThat(search(DexFilter()).first()).isEqualTo("pichu")
    }

    @Test
    fun `needed finds the uncaught copy of a duplicate and not the caught one`() {
        val records = Fixtures.records(Fixtures.caught("unown", copy = 0))
        val needed = search(DexFilter(query = "unown", caught = CaughtFilter.Needed), records)

        assertThat(needed).containsExactly("unown#1")
    }

    @Test
    fun `needed includes slots with no shiny in existence, which are still needed`() {
        assertThat(search(DexFilter(caught = CaughtFilter.Needed))).contains("magearna")
    }

    @Test
    fun `values inside a category are ORed`() {
        val result = search(DexFilter(types = setOf("fairy", "steel")))
        assertThat(result).containsExactly("flabebe", "zacian", "magearna")
    }

    @Test
    fun `categories are ANDed`() {
        val result = search(DexFilter(types = setOf("electric"), gameSets = setOf("swsh")))
        assertThat(result).containsExactly("pikachu")
    }

    @Test
    fun `a game filter excludes a variant that is shiny-locked there`() {
        assertThat(search(DexFilter(gameSets = setOf("swsh")))).doesNotContain("zacian")
    }

    @Test
    fun `a game filter excludes a variant that is listed but not obtainable there`() {
        assertThat(search(DexFilter(gameSets = setOf("sv")))).doesNotContain("unown")
    }

    @Test
    fun `a dual type matches on either type`() {
        assertThat(search(DexFilter(types = setOf("psychic")))).contains("raichu-alola")
    }

    @Test
    fun `no-shiny filter hides or isolates the variants no game has released a shiny of`() {
        assertThat(search(DexFilter(noShiny = NoShinyFilter.Only))).containsExactly("magearna")
        assertThat(search(DexFilter(noShiny = NoShinyFilter.Hide))).doesNotContain("magearna")
    }

    @Test
    fun `a number is an exact dex number, not a prefix`() {
        assertThat(search(DexFilter(query = "25"))).containsExactly("pikachu")
        assertThat(search(DexFilter(query = "#026"))).containsExactly("raichu", "raichu-alola")
        assertThat(search(DexFilter(query = "No. 151"))).containsExactly("mew")
    }

    @Test
    fun `accents are optional`() {
        assertThat(search(DexFilter(query = "flabebe"))).containsExactly("flabebe")
        assertThat(search(DexFilter(query = "Flabébé"))).containsExactly("flabebe")
    }

    @Test
    fun `form names are searchable and every word must match`() {
        assertThat(search(DexFilter(query = "alolan"))).containsExactly("raichu-alola")
        assertThat(search(DexFilter(query = "raichu alolan"))).containsExactly("raichu-alola")
    }

    @Test
    fun `a name starting with the query ranks above one merely containing it`() {
        // Mewtwo is earlier in preset order than Mew, and must still lose to it.
        assertThat(search(DexFilter(query = "mew"))).containsExactly("mew", "mewtwo").inOrder()
        assertThat(search(DexFilter(query = "chu")))
            .containsExactly("pichu", "pikachu", "raichu", "raichu-alola").inOrder()
    }

    @Test
    fun `a filter survives a round trip through its saved form`() {
        val filter = DexFilter(
            query = "rai",
            caught = CaughtFilter.Needed,
            gameSets = setOf("sv"),
            types = setOf("electric", "psychic"),
            noShiny = NoShinyFilter.Hide,
        )
        val restored = Json.decodeFromString<DexFilter>(Json.encodeToString(filter))
        assertThat(restored).isEqualTo(filter)
        assertThat(restored.refinementCount).isEqualTo(5)
    }

    @Test
    fun `normalising folds case, accents and punctuation the way a phone keyboard types`() {
        assertThat(normalizeForSearch("Flabébé")).isEqualTo("flabebe")
        assertThat(normalizeForSearch("Mr. Mime")).isEqualTo("mr mime")
        assertThat(normalizeForSearch("Farfetch’d")).isEqualTo("farfetch d")
        assertThat(normalizeForSearch("Nidoran♀")).isEqualTo("nidoran")
        assertThat(normalizeForSearch("  Porygon-Z  ")).isEqualTo("porygon z")
    }

    @Test
    fun `a farm filter keeps the slots that belong to that game and combines with the rest`() {
        val plan = FarmPlan(DexFixtures.dex, listOf(GameId("swsh-sw"), GameId("sv-s"), GameId("sv-v")))
        fun search(filter: DexFilter, records: Map<CatchKey, CatchRecord> = emptyMap()) =
            searchDex(DexFixtures.dex, records, filter, farm = plan).map { it.variant.id.value }

        assertThat(search(DexFilter(farm = FarmFilter("swsh-sw")))).containsExactly("pikachu")
        // Scarlet also has Pikachu, but Sword comes first.
        assertThat(search(DexFilter(farm = FarmFilter("sv-s")))).isEmpty()
        val onlyViolet = DexFilter(farm = FarmFilter("sv-v", FarmScope.OnlyHere))
        assertThat(search(onlyViolet)).containsExactly("raichu", "flabebe")
        assertThat(search(DexFilter(query = "fla", farm = FarmFilter("sv-v")))).containsExactly("flabebe")
        assertThat(
            search(
                DexFilter(caught = CaughtFilter.Needed, farm = FarmFilter("sv-v")),
                records = mapOf(DexFixtures.key("raichu") to Fixtures.caught("raichu")),
            ),
        ).containsExactly("flabebe")
    }

    @Test
    fun `a farm filter with no plan to answer it matches nothing rather than everything`() {
        assertThat(searchDex(DexFixtures.dex, emptyMap(), DexFilter(farm = FarmFilter("sv-v")))).isEmpty()
    }

    @Test
    fun `the farm filter counts as one refinement`() {
        val filter = DexFilter(farm = FarmFilter("sv-v"))

        assertThat(filter.hasRefinements).isTrue()
        assertThat(filter.refinementCount).isEqualTo(1)
    }
}
