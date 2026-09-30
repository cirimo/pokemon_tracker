package net.pokedex.feature.dex.boxes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.pokedex.core.data.repository.CatchRepository
import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CatchRecord
import net.pokedex.core.model.DexEntry
import net.pokedex.core.model.MarkLedger
import net.pokedex.core.model.regularForAll
import net.pokedex.core.model.regularToggle

/**
 * Mark mode's writes and its undo. The rules are `:core:model`'s ([regularToggle],
 * [regularForAll], [MarkLedger]); this runs them against the database, one at a time.
 *
 * The ledger is in memory only. After process death every mark is already saved; what is
 * gone is only the way to take a session's marks back as one.
 */
internal class MarkSession(
    private val catches: CatchRepository,
    private val scope: CoroutineScope,
    private val boxEntries: (boxIndex: Int) -> List<DexEntry>,
) {
    private val ledgerState = MutableStateFlow(MarkLedger())
    val ledger: StateFlow<MarkLedger> = ledgerState

    // Taps come fast, and each reads the slot before writing it. One at a time, so two taps on
    // one slot are on-then-off rather than two reads of "off".
    private val lock = Mutex()

    fun on(event: BoxesEvent.MarkEdit) {
        when (event) {
            is BoxesEvent.MarkToggled -> write { regularToggle(event.key, it) }
            is BoxesEvent.MarkBox -> write { regularForAll(boxEntries(event.boxIndex), it) }
            BoxesEvent.UndoMarks -> scope.launch {
                lock.withLock {
                    val undo = ledgerState.value.undo
                    if (undo.isNotEmpty()) catches.setRegular(undo)
                    ledgerState.value = MarkLedger()
                }
            }
        }
    }

    /** Ends the session: its marks stay, and Undo starts again from nothing. */
    fun clear() {
        ledgerState.value = MarkLedger()
    }

    /**
     * Reads the records from the database, not the stream, so a tap right after the last one
     * sees that one's write.
     */
    private fun write(change: (Map<CatchKey, CatchRecord>) -> Map<CatchKey, Boolean>) {
        scope.launch {
            lock.withLock {
                val before = catches.allRecords().associateBy { it.key }
                val changes = change(before)
                if (changes.isEmpty()) return@withLock
                catches.setRegular(changes)
                ledgerState.value = ledgerState.value.after(before, changes)
            }
        }
    }
}
