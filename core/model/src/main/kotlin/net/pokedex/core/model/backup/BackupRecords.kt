package net.pokedex.core.model.backup

import net.pokedex.core.model.CatchKey
import net.pokedex.core.model.CatchRecord
import net.pokedex.core.model.GameId
import net.pokedex.core.model.VariantId
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Records to and from the file, and what a merge import keeps.
 *
 * Here rather than in BackupRepository so the rules that decide whether a record survives
 * an import are JVM-tested in milliseconds.
 */

/**
 * The `updatedAt` a record from the file gets when the file does not say.
 *
 * Epoch zero means "older than anything you have", so a merge never lets a record of
 * unknown age overwrite one the app wrote. Every file this app writes carries the real
 * value; only a hand-written file omits it.
 */
const val UNKNOWN_UPDATED_AT = 0L

fun CatchRecord.toRecordInfo() = RecordInfo(
    variantId = key.variantId.value,
    copyIndex = key.copyIndex,
    caught = caught,
    originGameId = originGameId?.value,
    caughtAt = caughtAt?.let(::isoInstant),
    notes = notes,
    favourite = favourite,
    priority = priority,
    updatedAt = isoInstant(updatedAt),
)

fun RecordInfo.toCatchRecord() = CatchRecord(
    key = CatchKey(VariantId(variantId), copyIndex),
    caught = caught,
    originGameId = originGameId?.let(::GameId),
    caughtAt = caughtAt?.let(::parseInstant),
    notes = notes,
    favourite = favourite,
    priority = priority,
    updatedAt = updatedAt?.let(::parseInstant) ?: UNKNOWN_UPDATED_AT,
)

/**
 * The records a merge import must write: per key, whichever side was changed last.
 *
 * "Incoming always wins" was the M2 rule, and it lost data. Restoring last week's file
 * over tonight's session would untick every catch made since. Ties go to the local
 * record, because nothing is gained by rewriting a row with what it already holds.
 */
fun mergeRecords(
    local: Map<CatchKey, CatchRecord>,
    incoming: List<CatchRecord>,
): List<CatchRecord> = incoming.filter { theirs ->
    val ours = local[theirs.key]
    ours == null || theirs.updatedAt > ours.updatedAt
}

internal fun isoInstant(epochMillis: Long): String =
    DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis))

/** A timestamp a text editor mangled is dropped, not fatal: the record still restores. */
private fun parseInstant(text: String): Long? =
    runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
