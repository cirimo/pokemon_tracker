package net.pokedex.core.model

/**
 * What changed between two versions of a preset, in terms the user cares about.
 *
 * Drives the review screen: the app never silently adopts an upstream revision, because
 * a revision can strand records and the user should see that before it happens rather
 * than discover it later. See docs/adr/0001-slot-identity.md.
 *
 * Note what is NOT here: nothing about boxes being renamed or reordered on its own.
 * Movement only matters when it changes which [CatchKey] a position demands.
 */
data class PresetDiff(
    val added: List<CatchKey>,
    val removed: List<CatchKey>,
    val moved: List<Moved>,
    /** Removed keys the user actually has a caught record for. The only lossy case. */
    val strandedRecords: List<CatchKey>,
) {
    data class Moved(
        val key: CatchKey,
        val from: Position,
        val to: Position,
    )

    data class Position(val boxIndex: Int, val slotIndex: Int)

    val isEmpty: Boolean
        get() = added.isEmpty() && removed.isEmpty() && moved.isEmpty()

    val hasLoss: Boolean get() = strandedRecords.isNotEmpty()
}

/**
 * Diff two slot lists of the same preset.
 *
 * Both lists are keyed by [CatchKey], never by position -- that is the whole point.
 * A slot that shifts from box 3 to box 4 is "moved", not "removed plus added".
 */
fun diffPresets(
    old: List<Slot>,
    new: List<Slot>,
    records: Map<CatchKey, CatchRecord> = emptyMap(),
): PresetDiff {
    val oldByKey = old.associateBy { it.catchKey }
    val newByKey = new.associateBy { it.catchKey }

    val added = newByKey.keys.filter { it !in oldByKey }
    val removed = oldByKey.keys.filter { it !in newByKey }

    val moved = buildList {
        for ((key, newSlot) in newByKey) {
            val oldSlot = oldByKey[key] ?: continue
            if (oldSlot.boxIndex != newSlot.boxIndex || oldSlot.slotIndex != newSlot.slotIndex) {
                add(
                    PresetDiff.Moved(
                        key = key,
                        from = PresetDiff.Position(oldSlot.boxIndex, oldSlot.slotIndex),
                        to = PresetDiff.Position(newSlot.boxIndex, newSlot.slotIndex),
                    ),
                )
            }
        }
    }

    val stranded = removed.filter { records[it]?.caught == true }

    return PresetDiff(
        added = added.sortedBy { it.toString() },
        removed = removed.sortedBy { it.toString() },
        moved = moved.sortedWith(compareBy({ it.to.boxIndex }, { it.to.slotIndex })),
        strandedRecords = stranded.sortedBy { it.toString() },
    )
}

/**
 * Assigns copyIndex to a preset-ordered list of variant ids.
 *
 * This is the app-side mirror of what the dataset pipeline does at build time. It lives
 * here so the two implementations can be tested against each other, and so the preset
 * diff can be computed from raw upstream JSON without a rebuild.
 */
fun assignCopyIndices(variantIdsInPresetOrder: List<VariantId>): List<CatchKey> {
    val seen = HashMap<VariantId, Int>()
    return variantIdsInPresetOrder.map { id ->
        val n = seen.getOrDefault(id, 0)
        seen[id] = n + 1
        CatchKey(id, n)
    }
}
