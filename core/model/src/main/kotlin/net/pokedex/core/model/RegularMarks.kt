package net.pokedex.core.model

/**
 * Mark mode, the bulk path for regular catches: entering what HOME holds, box by box. Pure,
 * so what a tap writes and what Undo puts back are JVM tests. A change is a map of slot to
 * the regular mark it should have, which is what `CatchRepository.setRegular` writes.
 */

/**
 * A tap in mark mode: the regular one on if it was off, off if it was on. A shiny is left
 * as it is, so the same tap run across a box can never take a shiny off.
 */
fun regularToggle(key: CatchKey, records: Map<CatchKey, CatchRecord>): Map<CatchKey, Boolean> {
    val record = records[key]
    return if (record?.caught == true) emptyMap() else mapOf(key to (record?.regular != true))
}

/**
 * "Mark all of this box": every slot in it holding nothing, marked regular. Slots already
 * held, regular or shiny, are not touched, so their updatedAt does not move either.
 */
fun regularForAll(entries: Iterable<DexEntry>, records: Map<CatchKey, CatchRecord>): Map<CatchKey, Boolean> =
    entries.filter { ownershipOf(it, records) == Ownership.None }.associate { it.key to true }

/**
 * What one mark session has changed, as the value each slot had before the session first
 * touched it. [undo] is exactly that, so Undo returns every slot to where the session found
 * it, however many times it was tapped since.
 */
class MarkLedger(val undo: Map<CatchKey, Boolean> = emptyMap()) {
    /** How many slots Undo would change. */
    val changed: Int get() = undo.size

    /**
     * The ledger after [changes] were written over [before]. The first value wins, and a slot
     * tapped back to where it started is no change and leaves the ledger.
     */
    fun after(before: Map<CatchKey, CatchRecord>, changes: Map<CatchKey, Boolean>): MarkLedger {
        val originals = changes.keys.associateWith { before[it]?.regular == true } + undo
        return MarkLedger(originals.filter { (key, was) -> (changes[key] ?: (before[key]?.regular == true)) != was })
    }
}
