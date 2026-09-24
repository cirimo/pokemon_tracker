package net.pokedex.core.model

/**
 * User-data domain types. Sacred: a dataset regeneration must never touch these, and
 * nothing here carries a foreign key into reference data.
 *
 * [originGameId] is intentionally a plain id, not a validated reference. If a future
 * dataset drops a game, the record still remembers where the Pokemon came from.
 */
data class CatchRecord(
    val key: CatchKey,
    val caught: Boolean,
    val originGameId: GameId?,
    val caughtAt: Long?,
    val notes: String?,
    val favourite: Boolean,
    /** 0 means unprioritised. Higher sorts earlier in the hunt queue. */
    val priority: Int,
    val updatedAt: Long,
) {
    companion object {
        fun empty(key: CatchKey, now: Long): CatchRecord = CatchRecord(
            key = key,
            caught = false,
            originGameId = null,
            caughtAt = null,
            notes = null,
            favourite = false,
            priority = 0,
            updatedAt = now,
        )
    }
}

data class UserSettings(
    val activePresetId: PresetId,
    val lastSeenPresetVersion: Int,
    val lastSeenDatasetVersion: Int,
    val autoBackupEnabled: Boolean,
    val autoBackupKeepCount: Int,
    /**
     * The box the pager was last on, so the app reopens where you left it -- mid-hunt, that
     * is usually the box you are filling. A view preference, not a record: if a preset
     * revision moves boxes this lands you somewhere slightly wrong, which costs a swipe.
     * It is deliberately not part of a backup.
     */
    val lastBoxIndex: Int = 0,
    /**
     * The folder automatic backups are written to: a Storage Access Framework tree URI the
     * user granted once. Null until they pick one. Device-specific, so never in a backup --
     * a restored URI would point at a grant the new install does not hold.
     */
    val backupTreeUri: String? = null,
    /**
     * The game the last catch was recorded in. The catch sheet prefills it, because catches
     * come in runs from one game, but only where the variant can actually be shiny there.
     */
    val lastOriginGameId: GameId? = null,
    /** "Start fresh" on the first-launch restore offer. An uninstall resets it, which is right. */
    val restoreOfferDismissed: Boolean = false,
) {
    companion object {
        val DEFAULT = UserSettings(
            activePresetId = PresetId("grouped-balanced"),
            lastSeenPresetVersion = 0,
            lastSeenDatasetVersion = 0,
            autoBackupEnabled = true,
            autoBackupKeepCount = 10,
        )
    }
}
