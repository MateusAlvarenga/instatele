package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upload_records")
data class UploadRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val fileName: String,
    val uriString: String,
    val username: String,
    val sizeBytes: Long,
    val mediaType: String,
    val lotNumber: Int,
    val totalLots: Int,
    val groupIndex: Int,
    val status: String,
    val errorMessage: String? = null,
    val localDeleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "upload_sessions")
data class UploadSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val folderName: String,
    val totalFiles: Int,
    val successCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val totalBytes: Long,
    val autoDeleteEnabled: Boolean,
    val destination: String,
    val status: String,
    val startTime: Long,
    val endTime: Long? = null
)
