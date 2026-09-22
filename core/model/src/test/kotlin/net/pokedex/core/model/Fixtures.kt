package net.pokedex.core.model

/** Shared test fixtures. Mirrors the real shape of grouped-balanced, in miniature. */
object Fixtures {

    fun slot(box: Int, index: Int, variant: String, copy: Int = 0) = Slot(
        presetId = PresetId("grouped-balanced"),
        boxIndex = box,
        slotIndex = index,
        variantId = VariantId(variant),
        copyIndex = copy,
    )

    fun caught(variant: String, copy: Int = 0) = CatchRecord(
        key = CatchKey(VariantId(variant), copy),
        caught = true,
        originGameId = GameId("sv-s"),
        caughtAt = 1_700_000_000_000,
        notes = null,
        favourite = false,
        priority = 0,
        updatedAt = 1_700_000_000_000,
    )

    fun records(vararg r: CatchRecord): Map<CatchKey, CatchRecord> = r.associateBy { it.key }
}
