package net.pokedex.core.model.backup

import net.pokedex.core.model.Outcome
import java.time.Instant

/**
 * Writes backups into a [BackupFolder] and decides which old ones may go.
 *
 * Every rule here exists because a rolling set can destroy the thing it is protecting:
 *
 * - **An empty database is never auto-backed up.** After a wipe, ten empty backups would
 *   rotate every good one out.
 * - **The high-water mark is never pruned.** The newest file holding the most caught
 *   records survives any number of later writes, so a database that lost records cannot
 *   back itself up over the last copy that still has them.
 * - **Unchanged data is not written again.** Otherwise opening the app ten times keeps ten
 *   copies of one state, and "keep 10" means nothing.
 * - **Pre-import snapshots have their own pool.** Ten sessions after a bad replace must not
 *   push out the snapshot taken before it.
 * - **Only files with this build's [prefix] are ever deleted.**
 * - **Every write is read back and decoded** before anything older is pruned. A provider
 *   that truncated the file must not cost the files it was meant to replace.
 */
class BackupWriter(private val prefix: String) {

    sealed interface AutoResult {
        data class Written(val name: BackupName) : AutoResult
        data object SkippedEmpty : AutoResult
        data object SkippedUnchanged : AutoResult
    }

    fun writeAuto(folder: BackupFolder, file: BackupFile, now: Instant, keep: Int): AutoResult {
        if (file.records.isEmpty()) return AutoResult.SkippedEmpty
        val newest = own(folder, BackupName.Kind.AUTO).maxByOrNull { it.createdAt }
        if (newest != null && sameContent(folder, newest, file)) return AutoResult.SkippedUnchanged

        val name = write(folder, BackupName.Kind.AUTO, file, now)
        prune(folder, keepAuto = keep)
        return AutoResult.Written(name)
    }

    /** The copy of the current records ADR 0007 promises before any import. */
    fun writeSnapshot(folder: BackupFolder, file: BackupFile, now: Instant): BackupName {
        val name = write(folder, BackupName.Kind.PRE_IMPORT, file, now)
        prune(folder, keepAuto = null)
        return name
    }

    /**
     * Every backup in the folder this app could restore from, whichever build wrote it,
     * newest first. After a reinstall this is the list, since nothing inside the app
     * remembers what was written.
     */
    fun restorable(folder: BackupFolder): List<BackupName> =
        folder.list().mapNotNull(BackupName::parse).sortedByDescending { it.createdAt }

    private fun write(folder: BackupFolder, kind: BackupName.Kind, file: BackupFile, now: Instant): BackupName {
        val name = BackupName(prefix, kind, now, file.records.count { it.caught })
        val text = BackupCodec.encode(file)
        folder.write(name.fileName, text)
        if (folder.read(name.fileName) != text) {
            folder.delete(name.fileName)
            error("${name.fileName} did not read back as written")
        }
        return name
    }

    /**
     * Records and games, the two things a user chooses. The rest of the file (the export
     * time, the app version) changes on every write and says nothing new.
     */
    private fun sameContent(folder: BackupFolder, name: BackupName, file: BackupFile): Boolean {
        val existing = runCatching { BackupCodec.decode(folder.read(name.fileName)) }.getOrNull()
        return existing is Outcome.Ok &&
            existing.value.records == file.records &&
            existing.value.settings.myGames == file.settings.myGames
    }

    private fun prune(folder: BackupFolder, keepAuto: Int?) {
        val names = own(folder, null)
        val doomed = BackupRetention.toDelete(names, keepAuto = keepAuto, keepPreImport = KEEP_PRE_IMPORT)
        // A failed delete leaves one file too many, which is the right way to fail.
        doomed.forEach { runCatching { folder.delete(it.fileName) } }
    }

    private fun own(folder: BackupFolder, kind: BackupName.Kind?): List<BackupName> =
        folder.list().mapNotNull(BackupName::parse).filter { it.prefix == prefix && (kind == null || it.kind == kind) }

    companion object {
        const val KEEP_PRE_IMPORT = 3
    }
}

/** Which of one build's backups may be deleted. Pure, so every rule is a JVM test. */
object BackupRetention {

    /**
     * @param keepAuto how many automatic backups to keep, or null to leave them alone.
     *   Clamped to at least one: "keep 0" would delete the backup just written.
     */
    fun toDelete(names: List<BackupName>, keepAuto: Int?, keepPreImport: Int): List<BackupName> {
        val auto = names.filter { it.kind == BackupName.Kind.AUTO }.sortedByDescending { it.createdAt }
        val preImport = names.filter { it.kind == BackupName.Kind.PRE_IMPORT }.sortedByDescending { it.createdAt }

        val autoDoomed = if (keepAuto == null) {
            emptyList()
        } else {
            // sortedByDescending is stable, so among equal counts the newest comes first.
            val highWater = auto.maxByOrNull { it.caughtCount }?.let { top ->
                auto.first { it.caughtCount == top.caughtCount }
            }
            auto.drop(keepAuto.coerceAtLeast(1)).filter { it != highWater }
        }
        return autoDoomed + preImport.drop(keepPreImport.coerceAtLeast(1))
    }
}
