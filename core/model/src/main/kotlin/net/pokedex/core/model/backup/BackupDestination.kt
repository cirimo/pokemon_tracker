package net.pokedex.core.model.backup

/**
 * Where backups are going, as settings needs to say it.
 *
 * @param folderName the picked folder's name, or null while backups stay inside the app.
 * @param lostGrant a folder was picked but the grant is gone (revoked, or the folder was
 *   deleted). Backups fell back to inside the app, and the user needs to be told, since
 *   "your backups survive an uninstall" is no longer true.
 */
data class BackupDestination(
    val folderName: String?,
    val survivesUninstall: Boolean,
    val lostGrant: Boolean,
)
