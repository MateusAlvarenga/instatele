package com.example.data.model

import android.net.Uri

const val TELEGRAM_BOT_API_MAX_FILE_SIZE = 50L * 1024L * 1024L // 50 MB (52,428,800 bytes)

enum class MediaType {
    PHOTO,
    VIDEO,
    DOCUMENT;

    companion object {
        fun fromMimeOrExtension(name: String, mimeType: String?): MediaType {
            val lower = name.lowercase()
            return when {
                mimeType?.startsWith("image/") == true ||
                        lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                        lower.endsWith(".png") || lower.endsWith(".webp") -> PHOTO

                mimeType?.startsWith("video/") == true ||
                        lower.endsWith(".mp4") || lower.endsWith(".mov") ||
                        lower.endsWith(".m4v") || lower.endsWith(".mkv") ||
                        lower.endsWith(".webm") -> VIDEO

                else -> DOCUMENT
            }
        }
    }
}

enum class UploadStatus {
    PENDING,
    UPLOADING,
    SUCCESS,
    SKIPPED,
    FAILED
}

data class ScannedMediaFile(
    val id: String,
    val name: String,
    val uri: Uri,
    val sizeBytes: Long,
    val mimeType: String?,
    val mediaType: MediaType,
    val instagramUsername: String,
    val isDeletable: Boolean = true
) {
    val isOversizedForStandardBot: Boolean get() = sizeBytes > TELEGRAM_BOT_API_MAX_FILE_SIZE
}

data class MediaLot(
    val lotNumber: Int,
    val totalLots: Int,
    val username: String,
    val files: List<ScannedMediaFile>
) {
    val totalSizeBytes: Long get() = files.sumOf { it.sizeBytes }
    val validFilesCount: Int get() = files.count { !it.isOversizedForStandardBot }
    val oversizedFilesCount: Int get() = files.count { it.isOversizedForStandardBot }

    fun validCount(maxBytes: Long): Int = files.count { it.sizeBytes <= maxBytes }
    fun oversizedCount(maxBytes: Long): Int = files.count { it.sizeBytes > maxBytes }
}

data class InstagramProfileBatch(
    val username: String,
    val allFiles: List<ScannedMediaFile>,
    val lots: List<MediaLot>
) {
    val totalFilesCount: Int get() = allFiles.size
    val totalSizeBytes: Long get() = allFiles.sumOf { it.sizeBytes }
    val validFilesCount: Int get() = allFiles.count { !it.isOversizedForStandardBot }
    val oversizedFilesCount: Int get() = allFiles.count { it.isOversizedForStandardBot }

    fun validCount(maxBytes: Long): Int = allFiles.count { it.sizeBytes <= maxBytes }
    fun oversizedCount(maxBytes: Long): Int = allFiles.count { it.sizeBytes > maxBytes }
}

data class LiveUploadProgress(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentFileName: String = "",
    val currentUsername: String = "",
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val currentLotIndex: Int = 0,
    val totalLots: Int = 0,
    val currentGroupIndex: Int = 0,
    val totalGroupsInLot: Int = 0,
    val fileBytesUploaded: Long = 0L,
    val fileBytesTotal: Long = 0L,
    val totalBytesUploaded: Long = 0L,
    val totalBytesTotal: Long = 0L,
    val totalSuccess: Int = 0,
    val totalSkipped: Int = 0,
    val totalFailed: Int = 0,
    val uploadSpeedBytesPerSec: Long = 0L,
    val statusMessage: String = "",
    val currentStatus: UploadStatus = UploadStatus.PENDING
) {
    val fileProgressPercent: Float
        get() = if (fileBytesTotal > 0) (fileBytesUploaded.toFloat() / fileBytesTotal).coerceIn(0f, 1f) else 0f

    val overallProgressPercent: Float
        get() = if (totalFiles > 0) (currentFileIndex.toFloat() / totalFiles).coerceIn(0f, 1f) else 0f
}
