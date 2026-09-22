package net.pokedex.core.model

/**
 * Completion, derived. Never stored.
 *
 * A stored counter is a second source of truth that drifts the moment anything writes a
 * record without going through the increment path. These functions are cheap enough
 * that there is no reason to cache: the whole active preset is ~1394 slots.
 */
data class Progress(
    val caught: Int,
    val total: Int,
) {
    val remaining: Int get() = total - caught
    val fraction: Float get() = if (total == 0) 0f else caught.toFloat() / total
    val isComplete: Boolean get() = total > 0 && caught == total

    operator fun plus(other: Progress) = Progress(caught + other.caught, total + other.total)

    companion object {
        val ZERO = Progress(0, 0)
    }
}

/**
 * Progress over an arbitrary set of slots.
 *
 * Takes a map rather than a list because the seven duplicated variants in
 * grouped-balanced mean two distinct slots can share a [VariantId] but never a
 * [CatchKey] -- so lookup must be by key, and counting distinct variants would
 * undercount by seven.
 */
fun progressOf(slots: Iterable<Slot>, records: Map<CatchKey, CatchRecord>): Progress {
    var caught = 0
    var total = 0
    for (slot in slots) {
        total++
        if (records[slot.catchKey]?.caught == true) caught++
    }
    return Progress(caught, total)
}

fun progressByBox(
    slots: Iterable<Slot>,
    records: Map<CatchKey, CatchRecord>,
): Map<Int, Progress> {
    val out = LinkedHashMap<Int, Progress>()
    for (slot in slots) {
        val current = out[slot.boxIndex] ?: Progress.ZERO
        val isCaught = records[slot.catchKey]?.caught == true
        out[slot.boxIndex] = Progress(
            caught = current.caught + if (isCaught) 1 else 0,
            total = current.total + 1,
        )
    }
    return out
}

/**
 * Records the user holds that no slot in the active preset claims.
 *
 * These are never deleted. If upstream drops a variant, you still own the Pokemon --
 * it just has nowhere to sit in this preset. The UI surfaces them rather than the app
 * quietly discarding them.
 */
fun orphanedRecords(
    slots: Iterable<Slot>,
    records: Map<CatchKey, CatchRecord>,
): List<CatchRecord> {
    val claimed = slots.mapTo(HashSet()) { it.catchKey }
    return records.values.filter { it.key !in claimed && it.caught }
}
