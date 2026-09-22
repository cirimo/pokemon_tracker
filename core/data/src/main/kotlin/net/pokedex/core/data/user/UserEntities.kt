package net.pokedex.core.data.user

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User-data tables. These live in user.db and are SACRED: no dataset regeneration,
 * no destructive migration, no drop-and-recreate. Ever.
 *
 * Note the absence of foreign keys. catch_record deliberately does not reference
 * variant or game, because those live in a different database file and because a
 * record must outlive the reference row that used to explain it.
 */
@Entity(
    tableName = "catch_record",
    primaryKeys = ["variantId", "copyIndex"],
    indices = [Index(value = ["caught"]), Index(value = ["favourite"])],
)
data class CatchRecordEntity(
    val variantId: String,
    /** 0 for almost everything; 1 for the second copy of the seven duplicated variants. */
    val copyIndex: Int,
    val caught: Boolean,
    /** Plain id, not a validated reference -- see the class comment. */
    val originGameId: String?,
    val caughtAt: Long?,
    val notes: String?,
    val favourite: Boolean,
    val priority: Int,
    val updatedAt: Long,
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int,
    val activePresetId: String,
    val lastSeenPresetVersion: Int,
    val lastSeenDatasetVersion: Int,
    val autoBackupEnabled: Boolean,
    val autoBackupKeepCount: Int,
)

/** One row per rolling local backup written, so the UI can offer a restore list. */
@Entity(tableName = "backup_log")
data class BackupLogEntity(
    @PrimaryKey val fileName: String,
    val createdAt: Long,
    val recordCount: Int,
    val sizeBytes: Long,
    val reason: String,
)
