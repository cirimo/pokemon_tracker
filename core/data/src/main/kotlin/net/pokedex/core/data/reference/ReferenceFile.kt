package net.pokedex.core.data.reference

import android.content.Context
import org.json.JSONObject

/**
 * Which on-device file holds the reference dataset, and the removal of stale ones.
 *
 * Room copies a createFromAsset database once and then only replaces it when the schema
 * VERSION moves. A regenerated dataset with the same schema -- a curated shiny lock, a
 * corrected encounter -- would therefore never reach an installed app: the old copy stays
 * and the new asset is ignored. Naming the file after the asset's content hash makes any
 * content change a different file, which Room copies fresh. Nothing here is user data, so
 * deleting the old copies is free.
 */
internal object ReferenceFile {

    private const val MANIFEST_PATH = "dataset/dataset-manifest.json"
    private const val PREFIX = "reference-"
    private const val HASH_CHARS = 16

    /** `reference-<first 16 hex chars of the content hash>.db`. */
    fun name(context: Context): String {
        val manifest = context.assets.open(MANIFEST_PATH).bufferedReader().use { it.readText() }
        val hash = JSONObject(manifest).getString("contentHash")
        return PREFIX + hash.take(HASH_CHARS) + ".db"
    }

    /**
     * Deletes every reference copy except [keep], including the pre-M4 `reference.db`.
     * Matches on the reference prefix only, so user.db can never be caught by it.
     */
    fun deleteStale(context: Context, keep: String) {
        context.databaseList()
            .filter { (it.startsWith(PREFIX) || it == LEGACY_NAME) && it.endsWith(".db") && it != keep }
            .forEach { context.deleteDatabase(it) }
    }

    /** The fixed name every build before M4 used. */
    private const val LEGACY_NAME = "reference.db"
}
