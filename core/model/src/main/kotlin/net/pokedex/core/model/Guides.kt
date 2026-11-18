package net.pokedex.core.model

/**
 * The guide layer (prompt 11): how to hunt a shiny step by step, what to build first, and how
 * to farm what you build. Curated per game family (data/curated/guides/<gameSet>.yaml), every
 * row cited, and keyed by curated ids. A guide reaches a hunt through the variants it targets;
 * the hunt and the game family are derived here, never stored, and never a slot position.
 */

enum class GuideSection { Prepare, Setup, Loop, Check, After, Mistake }

enum class Dlc { IsleOfArmor, CrownTundra }

enum class RequirementKind { Dlc, Story, Item, Service }

enum class BuildPartKind { Ability, Nature, Move, Item, Level, Evs }

enum class FarmKind { Catch, Hatch, Tm, Tr, Tutor, Shop, Reward, Trade }

enum class AdviceKind { Take, Avoid, Note }

data class GuideLocation(
    val gameSet: String,
    val id: String,
    val name: String,
    val dlc: Dlc?,
    val sourceUrl: String,
)

/** Something a guide needs: a DLC, story progress, an item or a service. [optional] helps but is not needed. */
data class Requirement(
    val gameSet: String,
    val id: String,
    val label: String,
    val kind: RequirementKind,
    val optional: Boolean,
    val detail: String?,
    val sourceUrl: String,
)

data class GuideStep(
    val section: GuideSection,
    val text: String,
    val locationId: String?,
    val sourceUrl: String,
)

data class BuildPart(val kind: BuildPartKind, val value: String, val why: String, val sourceUrl: String)

/** A Pokémon to build before a hunt, shared by every guide that asks for it. */
data class Build(
    val id: String,
    val gameSet: String,
    val variantId: VariantId,
    val title: String,
    val purpose: String,
    val parts: List<BuildPart>,
)

/** One link of a farm chain, in the order you would walk it. */
data class FarmSource(val kind: FarmKind, val locationId: String?, val detail: String, val sourceUrl: String)

/** Rental advice and similar short lines, for hunts where you cannot bring your own Pokémon. */
data class Advice(val kind: AdviceKind, val subject: String, val why: String, val sourceUrl: String)

/** A build or requirement a guide asks for, with what it is for ("catcher", "odds"). */
data class GuideLink(val refId: String, val role: String?)

/**
 * The shared loop several guides follow (a Dynamax Adventure, a soft reset). Its steps come
 * before a guide's own in each section.
 */
data class Procedure(val id: String, val gameSet: String, val title: String, val steps: List<GuideStep>)

data class Guide(
    val id: String,
    val gameSet: String,
    val title: String,
    val methodId: String,
    val procedureId: String?,
    val summary: String?,
    /** Only one of the target can ever be kept on a save file. */
    val oneInSave: Boolean,
    val targets: Set<VariantId>,
    /** The guide's own steps; [GuideBook.steps] merges in the procedure's. */
    val steps: List<GuideStep>,
    val builds: List<GuideLink>,
    val requirements: List<GuideLink>,
    val advice: List<Advice>,
)

/**
 * Every guide, indexed for lookup. Built once per dataset, like [Curation].
 */
class GuideBook(
    guides: List<Guide>,
    procedures: List<Procedure>,
    builds: List<Build>,
    requirements: List<Requirement>,
    locations: List<GuideLocation>,
    farm: Map<Pair<String, String>, List<FarmSource>>,
) {
    private val guidesById = guides.associateBy { it.id }
    private val proceduresById = procedures.associateBy { it.id }
    private val buildsByKey = builds.associateBy { it.gameSet to it.id }
    private val requirementsByKey = requirements.associateBy { it.gameSet to it.id }
    private val locationsByKey = locations.associateBy { it.gameSet to it.id }
    private val farmBySubject = farm
    private val byVariantSet: Map<Pair<VariantId, String>, List<Guide>> = guides
        .flatMap { g -> g.targets.map { (it to g.gameSet) to g } }
        .groupBy({ it.first }, { it.second })

    val guides: List<Guide> get() = guidesById.values.sortedBy { it.id }

    fun guide(id: String): Guide? = guidesById[id]

    /** The guides for one variant in one game family. */
    fun guidesFor(variant: VariantId, gameSet: String): List<Guide> = byVariantSet[variant to gameSet].orEmpty()

    /**
     * The guides for a hunt in one game: every guide targeting one of the hunt's variants, in
     * that game's family. Derived from the variants, so a guide for Zygarde 50% does not
     * claim the 10% Forme slot of the same hunt.
     */
    fun guidesFor(hunt: HuntKey, variants: Collection<Variant>, gameSet: String): List<Guide> =
        variants.filter { huntKeyOf(it) == hunt }
            .flatMap { guidesFor(it.id, gameSet) }
            .distinctBy { it.id }

    /** The procedure's steps then the guide's own, section by section, each in written order. */
    fun steps(guide: Guide): List<GuideStep> {
        val shared = guide.procedureId?.let { proceduresById[it]?.steps }.orEmpty()
        return GuideSection.entries.flatMap { section ->
            shared.filter { it.section == section } + guide.steps.filter { it.section == section }
        }
    }

    fun builds(guide: Guide): List<Pair<Build, String?>> =
        guide.builds.mapNotNull { link -> buildsByKey[guide.gameSet to link.refId]?.let { it to link.role } }

    fun requirements(guide: Guide): List<Pair<Requirement, String?>> =
        guide.requirements.mapNotNull { link ->
            requirementsByKey[guide.gameSet to link.refId]?.let { it to link.role }
        }

    fun location(gameSet: String, id: String?): GuideLocation? = id?.let { locationsByKey[gameSet to it] }

    fun farmChain(build: Build): List<FarmSource> = farmBySubject[FARM_BUILD to build.id].orEmpty()

    fun farmChain(requirement: Requirement): List<FarmSource> =
        farmBySubject[FARM_REQUIREMENT to requirement.id].orEmpty()

    /**
     * The DLCs a guide needs: the ones its requirements name, and the ones its steps' places are
     * in. Derived from the locations, so a step at the Max Lair cannot forget The Crown Tundra.
     */
    fun dlcOf(guide: Guide): Set<Dlc> {
        val fromSteps = steps(guide).mapNotNull { location(guide.gameSet, it.locationId)?.dlc }
        val fromRequirements = requirements(guide).mapNotNull { (r, _) -> dlcOfRequirement(r.id) }
        return (fromSteps + fromRequirements).toSortedSet()
    }

    companion object {
        const val FARM_BUILD = "build"
        const val FARM_REQUIREMENT = "requirement"

        private fun dlcOfRequirement(id: String): Dlc? = when (id) {
            "isle-of-armor" -> Dlc.IsleOfArmor
            "crown-tundra" -> Dlc.CrownTundra
            else -> null
        }
    }
}

/** Every build any guide of the family asks for, once each: the prep list for a game. */
fun GuideBook.buildsOf(gameSet: String): List<Build> =
    guides.filter { it.gameSet == gameSet }.flatMap { g -> builds(g).map { it.first } }
        .distinctBy { it.id }
        .sortedBy { it.title }

/** Every requirement any guide of the family names, once each: the checklist for a game. */
fun GuideBook.requirementsOf(gameSet: String): List<Requirement> =
    guides.filter { it.gameSet == gameSet }.flatMap { g -> requirements(g).map { it.first } }
        .distinctBy { it.id }
        .sortedBy { it.label }
