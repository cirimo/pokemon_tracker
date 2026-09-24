package net.pokedex.core.model.backup

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A flat folder of backup files. Blocking: call it off the main thread.
 *
 * Two implementations. The real one is a Storage Access Framework tree in :core:data,
 * which lives outside the app's sandbox and so survives an uninstall. [FileBackupFolder]
 * is the fallback when no folder has been picked yet, and what the JVM tests use.
 */
interface BackupFolder {

    /** False for the app-private fallback, which an uninstall deletes with everything else. */
    val survivesUninstall: Boolean

    /** File names directly in the folder, in no particular order. */
    fun list(): List<String>

    fun read(name: String): String

    /** Creates [name], or replaces it if it already exists. */
    fun write(name: String, text: String)

    fun delete(name: String)
}

/** A plain directory. Inside the app it is the fallback; in tests it is a temp dir. */
class FileBackupFolder(
    private val dir: File,
    override val survivesUninstall: Boolean = false,
) : BackupFolder {

    override fun list(): List<String> = dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()

    override fun read(name: String): String = File(dir, name).readText()

    override fun write(name: String, text: String) {
        dir.mkdirs()
        // Written aside and renamed, so a crash mid-write leaves the old file or none,
        // never half of one under a name that parses.
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        Files.move(tmp.toPath(), File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    override fun delete(name: String) {
        File(dir, name).delete()
    }
}
