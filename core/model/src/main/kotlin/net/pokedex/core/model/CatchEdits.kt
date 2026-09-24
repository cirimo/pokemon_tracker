package net.pokedex.core.model

/**
 * What a tap on the caught toggle, or a save in the catch sheet, does to a record.
 *
 * Pure, so the rules that decide what survives an accidental untick are JVM tests rather
 * than behaviour buried in a repository.
 */

/**
 * Marks caught or not caught, and keeps everything else.
 *
 * - Unticking keeps origin, date and notes. An accidental untick loses nothing, and the
 *   toggle, tapped again, is the undo, with no timeout to race.
 * - Re-ticking restores the original date rather than stamping a new one, for the same
 *   reason: the common re-tick is the correction of a mis-tap.
 * - [prefill] is a guess at the origin (see [prefillOrigin]). A recorded origin always beats it.
 */
fun CatchRecord.withCaught(caught: Boolean, now: Long, prefill: GameId? = null): CatchRecord = copy(
    caught = caught,
    originGameId = originGameId ?: prefill.takeIf { caught },
    caughtAt = if (caught) caughtAt ?: now else caughtAt,
    updatedAt = now,
)

/**
 * The catch sheet's save. Blank notes are stored as none, so "cleared the note" and "never
 * wrote one" are the same record and the same line in a backup diff.
 */
fun CatchRecord.withDetails(origin: GameId?, caughtAt: Long?, notes: String?, now: Long): CatchRecord = copy(
    originGameId = origin,
    caughtAt = caughtAt,
    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
    updatedAt = now,
)

/**
 * Sets the hunt priority bucket. Unchanged is returned as is, updatedAt included, so a
 * tap on the bucket already chosen is not a change a backup or a merge has to carry.
 */
fun CatchRecord.withPriority(priority: Priority, now: Long): CatchRecord =
    if (Priority.of(this.priority) == priority) this else copy(priority = priority.value, updatedAt = now)

/** Whether an uncaught record still holds something the user wrote down. */
val CatchRecord.hasDetails: Boolean
    get() = originGameId != null || caughtAt != null || notes != null

/**
 * The game to prefill when a slot is ticked: the last one the user chose, but only if this
 * variant can actually be caught shiny there.
 *
 * Catches come in runs from one game, so the last game is usually right. When it cannot be
 * right (the variant is not in that game, or is shiny-locked there), a blank is honest and
 * a prefill would be a quiet lie in the record. The prefill is always shown on screen, so a
 * wrong one within those rules still gets seen.
 */
fun prefillOrigin(last: GameId?, availability: List<GameAvailability>): GameId? =
    last?.takeIf { game -> availability.any { it.gameId == game && it.obtainable && !it.shinyLocked } }
