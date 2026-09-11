package com.example.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.util.Locale

data class GrantedFolder(
    val uriString: String,
    val name: String,
    val lastAccessed: Long = System.currentTimeMillis()
) {
    val uri: Uri get() = Uri.parse(uriString)
}

data class StorageMediaItem(
    val documentId: String,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isAudio: Boolean
) {
    val extension: String
        get() = name.substringAfterLast('.', "").uppercase(Locale.ROOT)

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "Unknown size"
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.getDefault(), "%.2f GB", gb)
                mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
                else -> String.format(Locale.getDefault(), "%.0f KB", kb)
            }
        }
}

object StorageFolderManager {

    private const val TAG = "StorageFolderManager"
    private const val PREFS_NAME = "storage_folder_prefs"
    private const val KEY_FOLDERS = "granted_storage_folders"

    val SUPPORTED_AUDIO_EXTENSIONS = setOf(
        "mp3", "wav", "m4a", "aac", "flac", "ogg", "opus", "wma", "alac", "aiff"
    )

    val SUPPORTED_VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "mov", "3gp", "ts", "avi", "flv", "m4v"
    )

    /**
     * Persists URI permission and saves the folder to the app's remembered storage folders.
     */
    fun registerGrantedFolder(context: Context, treeUri: Uri): GrantedFolder {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not take persistable URI permission for $treeUri: ${e.message}")
        }

        val folderName = deriveFolderName(treeUri)
        val folder = GrantedFolder(
            uriString = treeUri.toString(),
            name = folderName,
            lastAccessed = System.currentTimeMillis()
        )

        saveFolderToPrefs(context, folder)
        return folder
    }

    /**
     * Retrieves all saved storage folders that the user has granted access to.
     */
    fun getSavedFolders(context: Context): List<GrantedFolder> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawSet = prefs.getStringSet(KEY_FOLDERS, emptySet()) ?: emptySet()
        return rawSet.mapNotNull { entry ->
            val parts = entry.split("|##|")
            if (parts.size >= 2) {
                val uriStr = parts[0]
                val name = parts[1]
                val time = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                GrantedFolder(uriStr, name, time)
            } else null
        }.sortedByDescending { it.lastAccessed }
    }

    fun removeSavedFolder(context: Context, uriString: String) {
        val current = getSavedFolders(context).filter { it.uriString != uriString }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = current.map { "${it.uriString}|##|${it.name}|##|${it.lastAccessed}" }.toSet()
        prefs.edit().putStringSet(KEY_FOLDERS, serialized).apply()
    }

    private fun saveFolderToPrefs(context: Context, folder: GrantedFolder) {
        val current = getSavedFolders(context).filter { it.uriString != folder.uriString }.toMutableList()
        current.add(0, folder)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = current.take(10).map { "${it.uriString}|##|${it.name}|##|${it.lastAccessed}" }.toSet()
        prefs.edit().putStringSet(KEY_FOLDERS, serialized).apply()
    }

    private fun deriveFolderName(treeUri: Uri): String {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val segment = docId.substringAfterLast(':').substringAfterLast('/')
            if (segment.isNotBlank()) segment else "Internal Storage Folder"
        } catch (e: Exception) {
            treeUri.lastPathSegment ?: "Internal Storage Folder"
        }
    }

    /**
     * Scans the specified folder for audio and video media files.
     */
    fun scanFolderForMedia(
        context: Context,
        treeUri: Uri
    ): List<StorageMediaItem> {
        val results = mutableListOf<StorageMediaItem>()

        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val dateCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val childDocId = cursor.getString(idCol) ?: continue
                    val displayName = cursor.getString(nameCol) ?: "Media"
                    val mimeType = cursor.getString(mimeCol) ?: ""
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val date = if (dateCol != -1) cursor.getLong(dateCol) else 0L

                    // Check if it's a directory to optionally scan subfolder or skip
                    val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR

                    if (!isDirectory) {
                        val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        val isAudio = mimeType.startsWith("audio/") || SUPPORTED_AUDIO_EXTENSIONS.contains(ext)
                        val isVideo = mimeType.startsWith("video/") || SUPPORTED_VIDEO_EXTENSIONS.contains(ext)

                        if (isAudio || isVideo) {
                            val itemUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                            results.add(
                                StorageMediaItem(
                                    documentId = childDocId,
                                    uri = itemUri,
                                    name = displayName,
                                    mimeType = mimeType,
                                    sizeBytes = size,
                                    lastModified = date,
                                    isAudio = isAudio
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning storage folder $treeUri", e)
        }

        return results.sortedByDescending { it.lastModified }
    }
}
