package net.pokedex.core.model.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The export/import file format. This is the app-loss insurance, so it is documented,
 * versioned, human-readable and deliberately boring.
 *
 * Forward compatibility contract:
 *
 *  - [schema] is a single integer, bumped only for breaking changes.
 *  - schema > CURRENT is REFUSED outright. A partial import of an unknown shape is how
 *    records get silently lost; refusing with both numbers shown is honest.
 *  - schema < CURRENT runs a chain of pure upgrade functions.
 *  - Unknown object keys are IGNORED, so a newer minor export still restores its
 *    records. That is the whole reason additive changes must not bump [schema].
 *
 * Timestamps are ISO-8601 UTC strings rather than epoch millis so the file stays
 * readable and diffable by a human with a text editor, which is the point of a backup
 * you might have to trust in five years.
 */
@Serializable
data class BackupFile(
    val schema: Int,
    val exportedAt: String,
    val app: AppInfo,
    val dataset: DatasetInfo,
    val settings: SettingsInfo,
    val records: List<RecordInfo>,
) {
    companion object {
        /** Bump ONLY for a breaking change. Additive fields must not bump this. */
        const val CURRENT_SCHEMA = 1

        /** Oldest schema we still know how to upgrade from. */
        const val OLDEST_SUPPORTED_SCHEMA = 1
    }
}

@Serializable
data class AppInfo(
    val versionName: String,
    val versionCode: Int,
)

@Serializable
data class DatasetInfo(
    val presetId: String,
    val presetVersion: Int,
    val datasetVersion: Int,
)

@Serializable
data class SettingsInfo(
    val activePresetId: String,
    val autoBackupEnabled: Boolean = true,
    val autoBackupKeepCount: Int = 10,
)

/**
 * One catch record.
 *
 * [copyIndex] defaults to 0 so a hand-written file can omit it for the 1387 variants
 * where it is always zero. Only the seven duplicated variants ever need it.
 *
 * [updatedAt] was added in M3 without a schema bump (it is additive, and older builds
 * ignore it). A merge import compares it per record; without it, restoring an old file
 * would overwrite newer catches. Missing means "older than anything local".
 */
@Serializable
data class RecordInfo(
    val variantId: String,
    val copyIndex: Int = 0,
    val caught: Boolean,
    @SerialName("originGameId") val originGameId: String? = null,
    val caughtAt: String? = null,
    val notes: String? = null,
    val favourite: Boolean = false,
    val priority: Int = 0,
    val updatedAt: String? = null,
)
