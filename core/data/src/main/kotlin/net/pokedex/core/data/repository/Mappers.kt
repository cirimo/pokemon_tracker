package net.pokedex.core.data.repository

import net.pokedex.core.data.reference.BoxEntity
import net.pokedex.core.data.reference.DatasetMetaEntity
import net.pokedex.core.data.reference.DexPresetEntity
import net.pokedex.core.data.reference.GameAvailabilityEntity
import net.pokedex.core.data.reference.GameEntity
import net.pokedex.core.data.reference.SlotEntity
import net.pokedex.core.data.reference.SpeciesEntity
import net.pokedex.core.data.reference.VariantEntity
import net.pokedex.core.data.user.CatchRecordEntity
import net.pokedex.core.data.user.UserSettingsEntity
import net.pokedex.core.model.Box
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CatchRecord
import net.pokedex.core.model.DatasetMeta
import net.pokedex.core.model.DexPreset
import net.pokedex.core.model.Game
import net.pokedex.core.model.GameAvailability
import net.pokedex.core.model.GameId
import net.pokedex.core.model.PresetId
import net.pokedex.core.model.Slot
import net.pokedex.core.model.Species
import net.pokedex.core.model.UserSettings
import net.pokedex.core.model.Variant
import net.pokedex.core.model.VariantId

/**
 * Entity to domain, in one place.
 *
 * This file is the membrane: Room entities never leave :core:data, so every repository
 * return type is a :core:model type. It looks like boilerplate and it is what keeps the
 * feature modules from depending on the storage shape.
 */

internal fun SpeciesEntity.toDomain() = Species(
    dexNum = dexNum,
    name = name,
    generation = generation,
    region = region,
    isLegendary = isLegendary,
    isMythical = isMythical,
    isBaby = isBaby,
    isUltraBeast = isUltraBeast,
    isParadox = isParadox,
)

internal fun VariantEntity.toDomain() = Variant(
    id = VariantId(id),
    nid = nid,
    dexNum = dexNum,
    formId = formId,
    displayName = displayName,
    formName = formName,
    type1 = type1,
    type2 = type2,
    isDefault = isDefault,
    isForm = isForm,
    isCosmeticForm = isCosmeticForm,
    isFemaleForm = isFemaleForm,
    isRegional = isRegional,
    isBattleOnlyForm = isBattleOnlyForm,
    baseSpeciesId = baseSpeciesId?.let(::VariantId),
    evolvesFromId = evolvesFromId?.let(::VariantId),
    evolveCondition = evolveCondition,
    shinyReleased = shinyReleased,
    spriteFile = spriteFile,
)

internal fun DexPresetEntity.toDomain() = DexPreset(
    id = PresetId(id),
    name = name,
    description = description,
    sourceVersion = sourceVersion,
    presetVersion = presetVersion,
    boxCount = boxCount,
    filledSlotCount = filledSlotCount,
)

internal fun BoxEntity.toDomain() = Box(
    presetId = PresetId(presetId),
    boxIndex = boxIndex,
    name = name,
    slotCount = slotCount,
    filledSlotCount = filledSlotCount,
)

internal fun SlotEntity.toDomain() = Slot(
    presetId = PresetId(presetId),
    boxIndex = boxIndex,
    slotIndex = slotIndex,
    variantId = VariantId(variantId),
    copyIndex = copyIndex,
)

internal fun GameEntity.toDomain() = Game(
    id = GameId(id),
    name = name,
    gameSet = gameSet,
    generation = generation,
    releaseDate = releaseDate,
    region = region,
    originMark = originMark,
    supportsShiny = supportsShiny,
    sortOrder = sortOrder,
)

internal fun GameAvailabilityEntity.toDomain() = GameAvailability(
    variantId = VariantId(variantId),
    gameId = GameId(gameId),
    obtainable = obtainable,
    eventOnly = eventOnly,
    storable = storable,
    transferOnly = transferOnly,
    shinyLocked = shinyLocked,
    shinyLockReason = shinyLockReason,
)

internal fun DatasetMetaEntity.toDomain() = DatasetMeta(
    datasetVersion = datasetVersion,
    upstreamTag = upstreamTag,
    presetVersion = presetVersion,
    builtAt = builtAt,
    contentHash = contentHash,
)

internal fun CatchRecordEntity.toDomain() = CatchRecord(
    key = CatchKey(VariantId(variantId), copyIndex),
    caught = caught,
    originGameId = originGameId?.let(::GameId),
    caughtAt = caughtAt,
    notes = notes,
    favourite = favourite,
    priority = priority,
    updatedAt = updatedAt,
)

internal fun CatchRecord.toEntity() = CatchRecordEntity(
    variantId = key.variantId.value,
    copyIndex = key.copyIndex,
    caught = caught,
    originGameId = originGameId?.value,
    caughtAt = caughtAt,
    notes = notes,
    favourite = favourite,
    priority = priority,
    updatedAt = updatedAt,
)

internal fun UserSettingsEntity.toDomain() = UserSettings(
    activePresetId = PresetId(activePresetId),
    lastSeenPresetVersion = lastSeenPresetVersion,
    lastSeenDatasetVersion = lastSeenDatasetVersion,
    autoBackupEnabled = autoBackupEnabled,
    autoBackupKeepCount = autoBackupKeepCount,
    lastBoxIndex = lastBoxIndex,
    backupTreeUri = backupTreeUri,
    lastOriginGameId = lastOriginGameId?.let(::GameId),
    restoreOfferDismissed = restoreOfferDismissed,
)

internal fun UserSettings.toEntity() = UserSettingsEntity(
    id = 1,
    activePresetId = activePresetId.value,
    lastSeenPresetVersion = lastSeenPresetVersion,
    lastSeenDatasetVersion = lastSeenDatasetVersion,
    autoBackupEnabled = autoBackupEnabled,
    autoBackupKeepCount = autoBackupKeepCount,
    lastBoxIndex = lastBoxIndex,
    backupTreeUri = backupTreeUri,
    lastOriginGameId = lastOriginGameId?.value,
    restoreOfferDismissed = restoreOfferDismissed,
)
