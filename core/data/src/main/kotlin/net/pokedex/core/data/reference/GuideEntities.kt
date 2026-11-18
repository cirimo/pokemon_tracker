package net.pokedex.core.data.reference

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The guide layer (prompt 11): step-by-step tutorials for a hunt, the builds to prepare for
 * it and how to farm them. Curated in data/curated/guides/<gameSet>.yaml and built into
 * reference.db like every other curated table, so it is versioned, validated and cited row
 * by row. The ADR on the guide layer records why it has this shape.
 *
 * Everything is keyed by curated string ids, never by a slot position, and nothing points
 * at user data. A guide reaches a hunt through the variants it targets; the HuntKey and the
 * game are derived in :core:model.
 *
 * `gameSet` is upstream's family id ("swsh"): a guide holds for both versions of a pair,
 * and the version-exclusive rows in game_availability say where it cannot be followed.
 */

/** A place a guide step or farm source sends you. Its DLC is what makes a step need the DLC. */
@Entity(tableName = "location", primaryKeys = ["gameSet", "id"])
data class LocationEntity(
    val gameSet: String,
    val id: String,
    val name: String,
    /** `ioa`, `ct` or null for the base game. */
    val dlc: String?,
    val sourceUrl: String,
)

/** Something a guide needs before it can be followed: a DLC, story progress, an item, a service. */
@Entity(tableName = "requirement", primaryKeys = ["gameSet", "id"])
data class RequirementEntity(
    val gameSet: String,
    val id: String,
    val label: String,
    /** `dlc`, `story`, `item` or `service`. */
    val kind: String,
    /** Helps but is not needed, like the Shiny Charm for a Dynamax Adventure. */
    val optional: Boolean,
    val detail: String?,
    val sourceUrl: String,
)

/**
 * A guide or a procedure. A procedure is the shared loop (a Dynamax Adventure, a soft reset)
 * with no targets of its own; a guide targets variants and may follow one procedure, whose
 * steps come before its own in each section.
 */
@Entity(tableName = "guide", indices = [Index(value = ["gameSet"])])
data class GuideEntity(
    @PrimaryKey val id: String,
    val gameSet: String,
    /** `guide` or `procedure`. */
    val kind: String,
    val title: String,
    /** The encounter method the guide hunts by; null on a procedure. */
    val methodId: String?,
    val procedureId: String?,
    val summary: String?,
    /** Only one of the target can ever be kept on a save file. */
    val oneInSave: Boolean,
)

@Entity(tableName = "guide_target", primaryKeys = ["guideId", "variantId"], indices = [Index(value = ["variantId"])])
data class GuideTargetEntity(
    val guideId: String,
    val variantId: String,
)

/** One step of a guide or a procedure, in its section, in order. */
@Entity(tableName = "guide_step", primaryKeys = ["ownerId", "ordinal"])
data class GuideStepEntity(
    val ownerId: String,
    val ordinal: Int,
    /** `prepare`, `setup`, `loop`, `check`, `after` or `mistake`. */
    val section: String,
    val text: String,
    val locationId: String?,
    val sourceUrl: String,
)

/** A Pokémon to build before a hunt. Shared: one catcher serves every reset legendary. */
@Entity(tableName = "build", indices = [Index(value = ["gameSet"])])
data class BuildEntity(
    @PrimaryKey val id: String,
    val gameSet: String,
    val variantId: String,
    val title: String,
    val purpose: String,
)

/** One piece of a build (ability, nature, a move, item, level or EVs) and why it is there. */
@Entity(tableName = "build_part", primaryKeys = ["buildId", "ordinal"])
data class BuildPartEntity(
    val buildId: String,
    val ordinal: Int,
    /** `ability`, `nature`, `move`, `item`, `level` or `evs`. */
    val kind: String,
    val value: String,
    val why: String,
    val sourceUrl: String,
)

/** A guide's builds and requirements. */
@Entity(tableName = "guide_link", primaryKeys = ["guideId", "kind", "refId"])
data class GuideLinkEntity(
    val guideId: String,
    /** `build` or `requirement`. */
    val kind: String,
    val refId: String,
    val role: String?,
)

/** Rental advice and other short take/avoid lines, for hunts where you cannot bring your own. */
@Entity(tableName = "guide_advice", primaryKeys = ["guideId", "ordinal"])
data class GuideAdviceEntity(
    val guideId: String,
    val ordinal: Int,
    /** `take`, `avoid` or `note`. */
    val kind: String,
    val subject: String,
    val why: String,
    val sourceUrl: String,
)

/** How to get a build or a requirement: one link of its farm chain. */
@Entity(tableName = "farm_source", primaryKeys = ["subjectKind", "subjectId", "ordinal"])
data class FarmSourceEntity(
    /** `build` or `requirement`. */
    val subjectKind: String,
    val subjectId: String,
    val ordinal: Int,
    /** `catch`, `hatch`, `tm`, `tr`, `tutor`, `shop`, `reward` or `trade`. */
    val kind: String,
    val locationId: String?,
    val detail: String,
    val sourceUrl: String,
)
