package net.pokedex.core.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import net.pokedex.core.model.backup.BackupFolder

/**
 * A folder the user picked through the Storage Access Framework.
 *
 * The files belong to the user, not to the app, which is the entire point: an uninstall
 * takes the grant with it but leaves every file. The same code runs on API 26 and 35.
 *
 * Plain DocumentsContract rather than androidx DocumentFile: it is four calls, and
 * DocumentFile would be one more library pinned against the toolchain set.
 */
internal class SafBackupFolder(
    private val resolver: ContentResolver,
    private val tree: Uri,
) : BackupFolder {

    override val survivesUninstall: Boolean = true

    private val treeDocumentId: String = DocumentsContract.getTreeDocumentId(tree)

    override fun list(): List<String> = children().keys.toList()

    override fun read(name: String): String {
        val uri = children()[name] ?: error("$name is not in the backup folder")
        return resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("could not open $name")
    }

    override fun write(name: String, text: String) {
        // Delete and recreate rather than truncating in place: "wt" is not a mode every
        // provider honours, and a provider that ignores the "t" leaves a longer old tail
        // after the new JSON.
        children()[name]?.let { DocumentsContract.deleteDocument(resolver, it) }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeDocumentId)
        val uri = DocumentsContract.createDocument(resolver, parent, MIME_JSON, name)
            ?: error("the backup folder refused to create $name")
        resolver.openOutputStream(uri, "w")?.use { it.write(text.encodeToByteArray()) }
            ?: error("could not write $name")
    }

    override fun delete(name: String) {
        children()[name]?.let { DocumentsContract.deleteDocument(resolver, it) }
    }

    private fun children(): Map<String, Uri> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeDocumentId)
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val out = LinkedHashMap<String, Uri>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(2) == Document.MIME_TYPE_DIR) continue
                out[cursor.getString(1)] = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
            }
        }
        return out
    }

    companion object {
        const val MIME_JSON = "application/json"
    }
}
