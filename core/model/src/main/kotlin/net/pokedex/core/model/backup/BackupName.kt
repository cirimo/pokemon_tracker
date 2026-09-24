package net.pokedex.core.model.backup

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * What a backup file's name says about it, so the folder can be pruned and listed without
 * opening every file.
 *
 * `pokedex-auto-20260924T071500Z-c412.json`: who wrote it, why, when, and how many caught
 * records it holds. That also makes it legible in a file manager.
 *
 * - The prefix is per build (`pokedex`, `pokedex-debug`). Pruning only touches files with
 *   the build's own prefix, so a debug build pointed at the same folder can never rotate
 *   away the release build's backups.
 * - The time has no colons. Several storage providers reject them.
 */
data class BackupName(
    val prefix: String,
    val kind: Kind,
    val createdAt: Instant,
    val caughtCount: Int,
) {
    enum class Kind(val tag: String) {
        /** Written by the app after changes, or daily. Rolls. */
        AUTO("auto"),

        /** Written before an import touches anything. Kept apart from the rolling set. */
        PRE_IMPORT("preimport"),
    }

    val fileName: String get() = "$prefix-${kind.tag}-${STAMP.format(createdAt)}-c$caughtCount.json"

    companion object {
        private val STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

        private val PATTERN =
            Regex("""^(?<prefix>.+)-(?<kind>auto|preimport)-(?<stamp>\d{8}T\d{6}Z)-c(?<caught>\d+)\.json$""")

        /** Null for anything this app did not name, including `x (1).json` from a provider. */
        fun parse(fileName: String): BackupName? {
            val match = PATTERN.matchEntire(fileName) ?: return null
            fun group(name: String) = match.groups[name]!!.value
            val createdAt = runCatching { Instant.from(STAMP.parse(group("stamp"))) }.getOrNull() ?: return null
            return BackupName(
                prefix = group("prefix"),
                kind = Kind.entries.first { it.tag == group("kind") },
                createdAt = createdAt,
                caughtCount = group("caught").toIntOrNull() ?: return null,
            )
        }
    }
}
