package net.pokedex.core.data.user

import androidx.room.ColumnInfo
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
    /** Added in version 2. The default is what MIGRATION_1_2 writes into existing rows. */
    @ColumnInfo(defaultValue = "0") val lastBoxIndex: Int,
    /** Added in version 3, with the two below. A persisted SAF tree URI, or null. */
    val backupTreeUri: String?,
    val lastOriginGameId: String?,
    @ColumnInfo(defaultValue = "0") val restoreOfferDismissed: Boolean,
)

/**
 * A game the user owns and plays: one row each, added in version 4.
 *
 * A table rather than a column of joined ids so that ticking one game is one insert, and
 * so the set has a primary key that cannot hold the same game twice. gameId is a plain
 * id with no foreign key, like originGameId: the game list lives in the other database.
 */
@Entity(tableName = "my_game")
data class MyGameEntity(
    @PrimaryKey val gameId: String,
    /**
     * Added in version 5: where this game comes in the order the user farms their games,
     * lowest first. Equal values fall back to release order, which only the reference data
     * knows, so games chosen before the order existed all migrate to 0 and read in release
     * order until the user moves one. docs/adr/0014-game-order.md.
     */
    @ColumnInfo(defaultValue = "0") val farmOrder: Int = 0,
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
