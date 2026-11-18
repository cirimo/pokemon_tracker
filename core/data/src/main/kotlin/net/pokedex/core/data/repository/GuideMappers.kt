package net.pokedex.core.data.repository

import net.pokedex.core.data.reference.BuildEntity
import net.pokedex.core.data.reference.BuildPartEntity
import net.pokedex.core.data.reference.FarmSourceEntity
import net.pokedex.core.data.reference.GuideAdviceEntity
import net.pokedex.core.data.reference.GuideEntity
import net.pokedex.core.data.reference.GuideLinkEntity
import net.pokedex.core.data.reference.GuideStepEntity
import net.pokedex.core.data.reference.GuideTargetEntity
import net.pokedex.core.data.reference.LocationEntity
import net.pokedex.core.data.reference.RequirementEntity
import net.pokedex.core.model.Advice
import net.pokedex.core.model.AdviceKind
import net.pokedex.core.model.Build
import net.pokedex.core.model.BuildPart
import net.pokedex.core.model.BuildPartKind
import net.pokedex.core.model.Dlc
import net.pokedex.core.model.FarmKind
import net.pokedex.core.model.FarmSource
import net.pokedex.core.model.Guide
import net.pokedex.core.model.GuideBook
import net.pokedex.core.model.GuideLink
import net.pokedex.core.model.GuideLocation
import net.pokedex.core.model.GuideSection
import net.pokedex.core.model.GuideStep
import net.pokedex.core.model.Procedure
import net.pokedex.core.model.Requirement
import net.pokedex.core.model.RequirementKind
import net.pokedex.core.model.VariantId

/**
 * The guide tables to [GuideBook]: the same membrane as Mappers.kt, kept apart because it is a
 * layer of its own. The curated vocabulary is lowercase ("crown-tundra", "loop"); an unknown
 * word cannot reach here, because the pipeline's Zod schema refuses it.
 */
internal class GuideTables(
    val guides: List<GuideEntity>,
    val targets: List<GuideTargetEntity>,
    val steps: List<GuideStepEntity>,
    val builds: List<BuildEntity>,
    val parts: List<BuildPartEntity>,
    val links: List<GuideLinkEntity>,
    val advice: List<GuideAdviceEntity>,
    val requirements: List<RequirementEntity>,
    val locations: List<LocationEntity>,
    val farm: List<FarmSourceEntity>,
)

internal fun GuideTables.toDomain(): GuideBook {
    val stepsByOwner = steps.groupBy { it.ownerId }.mapValues { (_, rows) ->
        rows.sortedBy { it.ordinal }.map { it.toDomain() }
    }
    val targetsByGuide = targets.groupBy({ it.guideId }, { VariantId(it.variantId) })
    val linksByGuide = links.groupBy { it.guideId }
    val adviceByGuide = advice.groupBy { it.guideId }
    val partsByBuild = parts.groupBy { it.buildId }
    val (procedureRows, guideRows) = guides.partition { it.kind == "procedure" }
    return GuideBook(
        guides = guideRows.map { g ->
            val linked = linksByGuide[g.id].orEmpty()
            Guide(
                id = g.id,
                gameSet = g.gameSet,
                title = g.title,
                methodId = g.methodId.orEmpty(),
                procedureId = g.procedureId,
                summary = g.summary,
                oneInSave = g.oneInSave,
                targets = targetsByGuide[g.id].orEmpty().toSet(),
                steps = stepsByOwner[g.id].orEmpty(),
                builds = linked.filter { it.kind == "build" }.map { GuideLink(it.refId, it.role) },
                requirements = linked.filter { it.kind == "requirement" }.map { GuideLink(it.refId, it.role) },
                advice = adviceByGuide[g.id].orEmpty().sortedBy { it.ordinal }.map { it.toDomain() },
            )
        },
        procedures = procedureRows.map { Procedure(it.id, it.gameSet, it.title, stepsByOwner[it.id].orEmpty()) },
        builds = builds.map { b ->
            Build(
                id = b.id,
                gameSet = b.gameSet,
                variantId = VariantId(b.variantId),
                title = b.title,
                purpose = b.purpose,
                parts = partsByBuild[b.id].orEmpty().sortedBy { it.ordinal }.map { it.toDomain() },
            )
        },
        requirements = requirements.map { it.toDomain() },
        locations = locations.map { it.toDomain() },
        farm = farm.groupBy { it.subjectKind to it.subjectId }
            .mapValues { (_, rows) -> rows.sortedBy { it.ordinal }.map { it.toDomain() } },
    )
}

private fun GuideStepEntity.toDomain() = GuideStep(
    section = GuideSection.entries.first { it.name.equals(section, ignoreCase = true) },
    text = text,
    locationId = locationId,
    sourceUrl = sourceUrl,
)

private fun BuildPartEntity.toDomain() = BuildPart(
    kind = BuildPartKind.entries.first { it.name.equals(kind, ignoreCase = true) },
    value = value,
    why = why,
    sourceUrl = sourceUrl,
)

private fun GuideAdviceEntity.toDomain() = Advice(
    kind = AdviceKind.entries.first { it.name.equals(kind, ignoreCase = true) },
    subject = subject,
    why = why,
    sourceUrl = sourceUrl,
)

private fun RequirementEntity.toDomain() = Requirement(
    gameSet = gameSet,
    id = id,
    label = label,
    kind = RequirementKind.entries.first { it.name.equals(kind, ignoreCase = true) },
    optional = optional,
    detail = detail,
    sourceUrl = sourceUrl,
)

private fun LocationEntity.toDomain() = GuideLocation(
    gameSet = gameSet,
    id = id,
    name = name,
    dlc = when (dlc) {
        "ioa" -> Dlc.IsleOfArmor
        "ct" -> Dlc.CrownTundra
        else -> null
    },
    sourceUrl = sourceUrl,
)

private fun FarmSourceEntity.toDomain() = FarmSource(
    kind = FarmKind.entries.first { it.name.equals(kind, ignoreCase = true) },
    locationId = locationId,
    detail = detail,
    sourceUrl = sourceUrl,
)
