package net.pokedex.feature.settings

import android.text.format.DateUtils
import net.pokedex.core.model.AppError
import net.pokedex.core.model.backup.BackupDestination
import net.pokedex.core.model.backup.BackupName
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Backup state as sentences. Every string the backup screens show is decided here, so the
 * settings row, the restore list and the first-launch offer describe the same thing the
 * same way.
 */

private val WHEN: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())

internal fun formatWhen(instant: Instant): String = WHEN.format(instant)

internal fun relative(instant: Instant, now: Instant = Instant.now()): String =
    DateUtils.getRelativeTimeSpanString(instant.toEpochMilli(), now.toEpochMilli(), DateUtils.MINUTE_IN_MILLIS)
        .toString()

internal fun destinationSummary(destination: BackupDestination?): String = when {
    destination == null -> "Checking…"
    destination.folderName != null -> destination.folderName.orEmpty()
    destination.lostGrant -> "The folder can no longer be reached. Backups are going inside the app."
    else -> "Inside the app. Not safe from an uninstall."
}

/** "Automatic, 412 caught · Today 09:15". The kind comes first: that is what tells two apart. */
internal fun backupLabel(name: BackupName): String {
    val kind = when (name.kind) {
        BackupName.Kind.AUTO -> "Automatic"
        BackupName.Kind.PRE_IMPORT -> "Before a restore"
    }
    val build = if (name.prefix.endsWith("-debug")) ", debug build" else ""
    return "$kind$build, ${name.caughtCount} caught"
}

internal fun caughtLabel(count: Int): String = if (count == 1) "1 caught" else "$count caught"

internal fun recordsLabel(count: Int): String = if (count == 1) "1 record" else "$count records"

/**
 * An error as the restore screen should say it. A newer schema names both numbers, as
 * ADR 0007 requires, and says what to do about it.
 */
internal fun backupErrorTitle(error: AppError): String = when (error) {
    is AppError.ImportSchemaTooNew -> "This backup is from a newer version of the app"
    is AppError.ImportMalformed -> "This file is not a backup this app can read"
    is AppError.StorageFailure -> "The file could not be read or written"
    else -> "The backup could not be used"
}

internal fun backupErrorBody(error: AppError): String = when (error) {
    is AppError.ImportSchemaTooNew ->
        "It uses backup schema ${error.found}, and this version reads up to schema ${error.supported}. " +
            "Update the app, then restore it. Nothing has been changed."
    is AppError.ImportMalformed -> "${error.reason} (at ${error.pointer}). Nothing has been changed."
    is AppError.StorageFailure -> "${error.detail}. Nothing has been changed."
    is AppError.Unexpected -> "${error.detail}. Nothing has been changed."
    is AppError.Fatal -> "The bundled dataset could not be read, so the backup cannot be checked against it."
}
