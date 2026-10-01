package com.example.data.repository

import android.content.Context
import com.example.data.model.ScannedMediaFile
import com.example.data.telegram.TelegramApiClient

class TelegramRepository(private val client: TelegramApiClient = TelegramApiClient()) {

    fun cancelActiveCall() {
        client.cancelCurrentCall()
    }

    suspend fun testBot(botToken: String, baseUrl: String): Result<String> {
        return client.testBot(botToken, baseUrl)
    }

    suspend fun testDestination(botToken: String, chatId: String, baseUrl: String): Result<String> {
        return client.testDestination(botToken, chatId, baseUrl)
    }

    suspend fun sendLotHeader(
        botToken: String,
        chatId: String,
        message: String,
        baseUrl: String
    ): Result<Boolean> {
        return client.sendMessage(botToken, chatId, message, baseUrl)
    }

    suspend fun sendMediaGroup(
        context: Context,
        botToken: String,
        chatId: String,
        files: List<ScannedMediaFile>,
        caption: String,
        baseUrl: String,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit,
        isCancelled: () -> Boolean,
        isSkipped: () -> Boolean
    ): Result<Boolean> {
        return client.sendMediaGroup(
            context = context,
            botToken = botToken,
            chatId = chatId,
            files = files,
            caption = caption,
            baseUrl = baseUrl,
            onProgress = onProgress,
            isCancelled = isCancelled,
            isSkipped = isSkipped
        )
    }

    suspend fun sendSingleMedia(
        context: Context,
        botToken: String,
        chatId: String,
        file: ScannedMediaFile,
        caption: String,
        baseUrl: String,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit,
        isCancelled: () -> Boolean,
        isSkipped: () -> Boolean
    ): Result<Boolean> {
        return client.sendSingleMedia(
            context = context,
            botToken = botToken,
            chatId = chatId,
            file = file,
            caption = caption,
            baseUrl = baseUrl,
            onProgress = onProgress,
            isCancelled = isCancelled,
            isSkipped = isSkipped
        )
    }
}
