package net.pokedex.core.model

import com.google.common.truth.Truth.assertThat
import net.pokedex.core.model.DexFixtures.variant
import org.junit.Test

class GuidesTest {

    private val src = "https://example.org/guide"
    private fun step(section: GuideSection, text: String, location: String? = null) =
        GuideStep(section, text, location, src)

    private val procedure = Procedure(
        id = "dynamax-adventure",
        gameSet = "swsh",
        title = "Dynamax Adventure",
        steps = listOf(
            step(GuideSection.Loop, "Choose the noted path.", location = "max-lair"),
            step(GuideSection.Setup, "Reach the boss once."),
            step(GuideSection.Mistake, "Keeping a boss that is not shiny."),
        ),
    )

    private val zygarde = Guide(
        id = "zygarde-max-lair",
        gameSet = "swsh",
        title = "Zygarde in a Dynamax Adventure",
        methodId = "dynamax-adventure",
        procedureId = procedure.id,
        summary = null,
        oneInSave = true,
        targets = setOf(VariantId("zygarde")),
        steps = listOf(step(GuideSection.Setup, "Its own setup step.")),
        builds = emptyList(),
        requirements = listOf(GuideLink("shiny-charm", "odds")),
        advice = emptyList(),
    )

    private val catcher = Build("false-swipe-catcher", "swsh", VariantId("gallade"), "Catcher", "x", emptyList())
    private val charm = Requirement("swsh", "shiny-charm", "Shiny Charm", RequirementKind.Item, true, null, src)

    private val book = GuideBook(
        guides = listOf(zygarde),
        procedures = listOf(procedure),
        builds = listOf(catcher),
        requirements = listOf(charm),
        locations = listOf(GuideLocation("swsh", "max-lair", "Max Lair", Dlc.CrownTundra, src)),
        farm = mapOf(
            (GuideBook.FARM_REQUIREMENT to "shiny-charm") to
                listOf(FarmSource(FarmKind.Reward, null, "Galar dex.", src)),
        ),
    )

    @Test
    fun `steps read by section, the procedure's before the guide's own`() {
        assertThat(book.steps(zygarde).map { it.text }).containsExactly(
            "Reach the boss once.",
            "Its own setup step.",
            "Choose the noted path.",
            "Keeping a boss that is not shiny.",
        ).inOrder()
    }

    @Test
    fun `a guide reaches a hunt through its variants, so the 10 percent Forme gets none`() {
        val fifty = variant("zygarde", 718, "Zygarde")
        val ten = variant("zygarde-10", 718, "Zygarde (10% Forme)", formName = "10% Forme")
        val hunt = huntKeyOf(fifty)

        assertThat(book.guidesFor(hunt, listOf(fifty, ten), "swsh")).containsExactly(zygarde)
        assertThat(book.guidesFor(ten.id, "swsh")).isEmpty()
        assertThat(book.guidesFor(fifty.id, "sv")).isEmpty()
    }

    @Test
    fun `the DLC a guide needs comes from where its steps are`() {
        assertThat(book.dlcOf(zygarde)).containsExactly(Dlc.CrownTundra)
    }

    @Test
    fun `a requirement carries its role and its farm chain`() {
        assertThat(book.requirements(zygarde)).containsExactly(charm to "odds")
        assertThat(book.farmChain(charm).map { it.detail }).containsExactly("Galar dex.")
    }
}
