package net.pokedex.core.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.pokedex.core.data.di.IoDispatcher
import net.pokedex.core.data.repository.SettingsRepository
import net.pokedex.core.model.Outcome
import net.pokedex.core.model.backup.BackupDestination
import net.pokedex.core.model.backup.BackupFolder
import net.pokedex.core.model.backup.BackupName
import net.pokedex.core.model.backup.FileBackupFolder
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where backups go right now.
 *
 * The folder the user picked, while the app still holds its grant. Otherwise a folder
 * inside the app, which protects against a bad import but not an uninstall, and settings
 * says so. There is always *somewhere* to write the pre-import snapshot.
 */
@Singleton
class BackupLocation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /**
     * `pokedex` for release, `pokedex-debug` for debug. Pruning is scoped to it, so the two
     * builds can share one folder without one deleting the other's files.
     */
    val prefix: String = context.packageName.removePrefix("net.").replace('.', '-')

    private val privateFolder = FileBackupFolder(File(context.filesDir, "backups"))

    suspend fun current(): BackupFolder = withContext(io) {
        grantedTree(settings.get().backupTreeUri)?.let { SafBackupFolder(context.contentResolver, it) }
            ?: privateFolder
    }

    fun observe(): Flow<BackupDestination> = settings.observe()
        .map { it.backupTreeUri }
        .distinctUntilChanged()
        .map { uri -> withContext(io) { describe(uri) } }

    /**
     * Adopts a folder from the system picker: takes a grant that outlives this process,
     * lets go of the previous folder's grant, and remembers it.
     */
    suspend fun choose(tree: Uri) = withContext(io) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val resolver = context.contentResolver
        resolver.takePersistableUriPermission(tree, flags)
        val previous = settings.get().backupTreeUri?.let(Uri::parse)
        if (previous != null && previous != tree) {
            runCatching { resolver.releasePersistableUriPermission(previous, flags) }
        }
        settings.edit { it.copy(backupTreeUri = tree.toString()) }
    }

    /** A folder picked for one look (the first-launch offer lists it before adopting it). */
    fun folderAt(tree: Uri): BackupFolder = SafBackupFolder(context.contentResolver, tree)

    /**
     * Backups in the current folder, or in [tree] if given, newest first, whichever build
     * wrote them. After a reinstall this is the restore list: nothing inside the app
     * remembers what was written.
     */
    suspend fun restorable(tree: Uri? = null): Outcome<List<BackupName>> = storage(io, "could not list backups") {
        folder(tree).list().mapNotNull(BackupName::parse).sortedByDescending { it.createdAt }
    }

    suspend fun read(name: BackupName, tree: Uri? = null): Outcome<String> =
        storage(io, "could not read ${name.fileName}") { folder(tree).read(name.fileName) }

    /** A file the user picked with the system picker, as text. Parsing is the preview's job. */
    suspend fun readDocument(uri: Uri): Outcome<String> = storage(io, "could not read the file") {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("could not open the file")
    }

    private suspend fun folder(tree: Uri?): BackupFolder = tree?.let(::folderAt) ?: current()

    private fun grantedTree(uri: String?): Uri? {
        val tree = uri?.let(Uri::parse) ?: return null
        val held = context.contentResolver.persistedUriPermissions.any {
            it.uri == tree && it.isReadPermission && it.isWritePermission
        }
        return tree.takeIf { held }
    }

    private fun describe(uri: String?): BackupDestination {
        val tree = grantedTree(uri)
            ?: return BackupDestination(folderName = null, survivesUninstall = false, lostGrant = uri != null)
        val name = runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(
                doc,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        return BackupDestination(folderName = name ?: tree.lastPathSegment, survivesUninstall = true, lostGrant = false)
    }
}
