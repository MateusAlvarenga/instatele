package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.example.data.model.InstagramProfileBatch
import com.example.data.model.MediaLot
import com.example.data.model.MediaType
import com.example.data.model.ScannedMediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FileUtils {

    private const val TAG = "FileUtils"

    private val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
    private val SUPPORTED_VIDEO_EXTENSIONS = setOf("mp4", "mov", "m4v", "mkv", "webm", "3gp")

    suspend fun scanDirectoryTree(
        context: Context,
        treeUri: Uri,
        itemsPerLot: Int = 100
    ): List<InstagramProfileBatch> = withContext(Dispatchers.IO) {
        val scannedFiles = mutableListOf<ScannedMediaFile>()

        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_FLAGS
            )

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val flagsIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)

                while (cursor.moveToNext()) {
                    val childDocId = cursor.getString(idIdx)
                    val name = cursor.getString(nameIdx) ?: continue
                    val mime = cursor.getString(mimeIdx)
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val flags = if (flagsIdx >= 0 && !cursor.isNull(flagsIdx)) cursor.getInt(flagsIdx) else 0

                    // Check if it's a directory
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        continue
                    }

                    // Check if it's a supported media file
                    val extension = name.substringAfterLast('.', "").lowercase()
                    val isImage = mime?.startsWith("image/") == true || SUPPORTED_IMAGE_EXTENSIONS.contains(extension)
                    val isVideo = mime?.startsWith("video/") == true || SUPPORTED_VIDEO_EXTENSIONS.contains(extension)

                    if (isImage || isVideo) {
                        val fileDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                        var resolvedSize = size
                        if (resolvedSize <= 0L) {
                            try {
                                context.contentResolver.openFileDescriptor(fileDocUri, "r")?.use { pfd ->
                                    if (pfd.statSize > 0L) resolvedSize = pfd.statSize
                                }
                            } catch (_: Exception) {}
                        }
                        if (resolvedSize <= 0L) {
                            try {
                                context.contentResolver.openInputStream(fileDocUri)?.use { stream ->
                                    val avail = stream.available().toLong()
                                    if (avail > 0L) resolvedSize = avail
                                }
                            } catch (_: Exception) {}
                        }
                        val mediaType = if (isImage) MediaType.PHOTO else MediaType.VIDEO
                        val username = InstagramParser.extractUsername(name)
                        val canDelete = (flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE) != 0

                        scannedFiles.add(
                            ScannedMediaFile(
                                id = childDocId,
                                name = name,
                                uri = fileDocUri,
                                sizeBytes = resolvedSize,
                                mimeType = mime,
                                mediaType = mediaType,
                                instagramUsername = username,
                                isDeletable = canDelete
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning directory tree: ${e.message}", e)
        }

        groupAndLotFiles(scannedFiles, itemsPerLot)
    }

    /**
     * Groups files by Instagram username and splits into lots of [itemsPerLot] (default 100).
     */
    fun groupAndLotFiles(
        files: List<ScannedMediaFile>,
        itemsPerLot: Int = 100
    ): List<InstagramProfileBatch> {
        val groupedByUser = files.groupBy { it.instagramUsername }

        return groupedByUser.map { (username, userFiles) ->
            // Sort files alphabetically to preserve sequence
            val sorted = userFiles.sortedBy { it.name.lowercase() }
            val chunked = sorted.chunked(itemsPerLot.coerceAtLeast(1))
            val totalLots = chunked.size

            val lots = chunked.mapIndexed { index, lotFiles ->
                MediaLot(
                    lotNumber = index + 1,
                    totalLots = totalLots,
                    username = username,
                    files = lotFiles
                )
            }

            InstagramProfileBatch(
                username = username,
                allFiles = sorted,
                lots = lots
            )
        }.sortedByDescending { it.allFiles.size }
    }

    /**
     * Deletes a file via DocumentsContract or ContentResolver
     */
    suspend fun deleteFile(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (e: Exception) {
            try {
                context.contentResolver.delete(uri, null, null) > 0
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to delete file: ${ex.message}")
                false
            }
        }
    }

    /**
     * Creates demo files for simulation and testing without requiring real local files.
     */
    fun createSampleDemoFiles(context: Context): List<InstagramProfileBatch> {
        val dummyUsers = listOf(
            "eumariaemanuellyoficial",
            "fotografia_brasil",
            "viagens_incriveis"
        )

        val files = mutableListOf<ScannedMediaFile>()
        var globalId = 1

        dummyUsers.forEach { user ->
            val count = if (user == "eumariaemanuellyoficial") 125 else if (user == "fotografia_brasil") 32 else 12
            for (i in 1..count) {
                val isVid = i % 3 == 0
                val ext = if (isVid) "mp4" else "jpg"
                val mediaType = if (isVid) MediaType.VIDEO else MediaType.PHOTO
                val idNum = 3944008708912500000L + globalId
                val name = "${user}_${idNum}.$ext"
                // Simulate a couple of oversized files > 50MB
                val size = when {
                    i == 5 || i == 55 -> (65_000_000L..95_000_000L).random() // > 50MB
                    isVid -> (8_000_000L..35_000_000L).random()
                    else -> (800_000L..4_500_000L).random()
                }

                files.add(
                    ScannedMediaFile(
                        id = "demo_$globalId",
                        name = name,
                        uri = Uri.parse("content://demo.archiver/$name"),
                        sizeBytes = size,
                        mimeType = if (isVid) "video/mp4" else "image/jpeg",
                        mediaType = mediaType,
                        instagramUsername = user,
                        isDeletable = false
                    )
                )
                globalId++
            }
        }

        return groupAndLotFiles(files, itemsPerLot = 100)
    }
}
