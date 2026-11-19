package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PrepTest {

    private val src = "https://example.org/guide"
    private val sword = GameId("swsh-sw")
    private val shield = GameId("swsh-sh")

    private fun guide(id: String, builds: List<String>, requirements: List<String>) = Guide(
        id = id,
        gameSet = "swsh",
        title = id,
        methodId = "static",
        procedureId = null,
        summary = null,
        oneInSave = false,
        targets = setOf(VariantId(id)),
        steps = emptyList(),
        builds = builds.map { GuideLink(it, "catcher") },
        requirements = requirements.map { GuideLink(it, null) },
        advice = emptyList(),
    )

    private fun requirement(id: String, kind: RequirementKind, optional: Boolean = false) =
        Requirement("swsh", id, id, kind, optional, null, src)

    private val book = GuideBook(
        guides = listOf(
            guide("regirock", listOf("catcher"), listOf("crown-tundra", "clue-2")),
            guide("regice", listOf("catcher"), listOf("crown-tundra", "clue-2")),
            guide("zygarde", emptyList(), listOf("crown-tundra", SHINY_CHARM, "nso")),
        ),
        procedures = emptyList(),
        builds = listOf(Build("catcher", "swsh", VariantId("gallade"), "Catcher", "x", emptyList())),
        requirements = listOf(
            requirement(SHINY_CHARM, RequirementKind.Item, optional = true),
            requirement("nso", RequirementKind.Service),
            requirement("clue-2", RequirementKind.Story),
            requirement("crown-tundra", RequirementKind.Dlc),
        ),
        locations = emptyList(),
        farm = mapOf((GuideBook.FARM_BUILD to "catcher") to listOf(FarmSource(FarmKind.Catch, null, "Ralts.", src))),
    )

    @Test
    fun `what every hunt waits on comes first, builds next, the optional charm last`() {
        val list = prepListOf(book, "swsh", sword, emptySet())

        assertThat(list.map { it.refId })
            .containsExactly("crown-tundra", "clue-2", "nso", "catcher", SHINY_CHARM).inOrder()
        assertThat(list.first { it.refId == "catcher" }.farm.map { it.detail }).containsExactly("Ralts.")
    }

    @Test
    fun `ticking a row never moves it`() {
        val before = prepListOf(book, "swsh", sword, emptySet()).map { it.refId }
        val ticks = setOf(PrepTick(sword, "crown-tundra", 1), PrepTick(sword, "catcher", 2))
        val after = prepListOf(book, "swsh", sword, ticks)

        assertThat(after.map { it.refId }).isEqualTo(before)
        assertThat(after.filter { it.done }.map { it.refId }).containsExactly("crown-tundra", "catcher")
    }

    @Test
    fun `a tick belongs to one game, so Shield's charm is not Sword's`() {
        val ticks = setOf(PrepTick(shield, SHINY_CHARM, 1))

        assertThat(prepListOf(book, "swsh", sword, ticks).none { it.done }).isTrue()
        assertThat(hasShinyCharm(ticks, sword)).isFalse()
        assertThat(hasShinyCharm(ticks, shield)).isTrue()
    }

    @Test
    fun `each row says how many guides ask for it`() {
        val used = prepListOf(book, "swsh", sword, emptySet()).associate { it.refId to it.usedBy }

        assertThat(used).containsExactly("crown-tundra", 3, "clue-2", 2, "nso", 1, "catcher", 2, SHINY_CHARM, 1)
    }

    @Test
    fun `the charm leads a hunt's figure only when I have it and it applies`() {
        val da = MethodOdds(
            plain = Odds.flat(300),
            best = Odds.flat(100),
            assumed = emptyList(),
            withCharm = Odds.flat(100)
        )
        val giant = Odds.ofRolls(1).let { MethodOdds(plain = it, best = it, assumed = emptyList()) }

        assertThat(leadingOdds(da, charm = false).oneIn).isEqualTo(300.0)
        assertThat(leadingOdds(da, charm = true).oneIn).isEqualTo(100.0)
        assertThat(leadingOdds(giant, charm = true).oneIn).isEqualTo(4096.0)
    }
}
