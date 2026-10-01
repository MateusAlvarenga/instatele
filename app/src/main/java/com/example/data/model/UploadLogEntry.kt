package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    SKIP
}

data class UploadLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val message: String,
    val username: String? = null,
    val fileName: String? = null
) {
    val timeFormatted: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
