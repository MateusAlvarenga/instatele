package com.example.data.repository

import android.content.Context
import com.example.data.model.ScannedMediaFile
import com.example.data.model.UserSessionInfo
import com.example.data.telegram.TelegramApiClient
import com.example.data.telegram.TelegramUserClient
import com.example.data.telegram.UserSignInResult

class TelegramRepository(
    private val botClient: TelegramApiClient = TelegramApiClient(),
    private val userClient: TelegramUserClient = TelegramUserClient()
) {

    fun cancelActiveCall() {
        botClient.cancelCurrentCall()
        userClient.cancelCurrentCall()
    }

    // Bot API operations
    suspend fun testBot(botToken: String, baseUrl: String): Result<String> {
        return botClient.testBot(botToken, baseUrl)
    }

    suspend fun testDestination(botToken: String, chatId: String, baseUrl: String): Result<String> {
        return botClient.testDestination(botToken, chatId, baseUrl)
    }

    suspend fun sendLotHeader(
        botToken: String,
        chatId: String,
        message: String,
        baseUrl: String
    ): Result<Boolean> {
        return botClient.sendMessage(botToken, chatId, message, baseUrl)
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
        return botClient.sendMediaGroup(
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
        return botClient.sendSingleMedia(
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

    // User Account (MTProto / Telethon) operations
    fun importUserSession(sessionString: String, accountLabel: String = ""): UserSignInResult {
        return userClient.importStringSession(sessionString, accountLabel)
    }

    suspend fun requestUserCode(
        apiId: String,
        apiHash: String,
        phoneNumber: String
    ): Result<String> {
        return userClient.requestLoginCode(apiId, apiHash, phoneNumber)
    }

    suspend fun signInWithUserCode(
        apiId: String,
        apiHash: String,
        phoneNumber: String,
        phoneCodeHash: String,
        code: String,
        hasTwoFactorEnabled: Boolean = false
    ): UserSignInResult {
        return userClient.signInWithCode(apiId, apiHash, phoneNumber, phoneCodeHash, code, hasTwoFactorEnabled)
    }

    suspend fun signInWithUserPassword(
        apiId: String,
        apiHash: String,
        phoneNumber: String,
        password: String
    ): UserSignInResult {
        return userClient.signInWithPassword(apiId, apiHash, phoneNumber, password)
    }

    suspend fun validateUserSession(
        sessionString: String,
        apiId: String,
        apiHash: String
    ): Result<UserSessionInfo> {
        return userClient.validateSession(sessionString, apiId, apiHash)
    }

    suspend fun sendUserMediaGroup(
        context: Context,
        session: UserSessionInfo,
        destination: String,
        files: List<ScannedMediaFile>,
        caption: String,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit,
        isCancelled: () -> Boolean,
        isSkipped: () -> Boolean
    ): Result<Boolean> {
        return userClient.sendUserMediaGroup(
            context = context,
            session = session,
            destination = destination,
            files = files,
            caption = caption,
            onProgress = onProgress,
            isCancelled = isCancelled,
            isSkipped = isSkipped
        )
    }

    suspend fun sendUserSingleMedia(
        context: Context,
        session: UserSessionInfo,
        destination: String,
        file: ScannedMediaFile,
        caption: String,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit,
        isCancelled: () -> Boolean,
        isSkipped: () -> Boolean
    ): Result<Boolean> {
        return userClient.sendUserSingleMedia(
            context = context,
            session = session,
            destination = destination,
            file = file,
            caption = caption,
            onProgress = onProgress,
            isCancelled = isCancelled,
            isSkipped = isSkipped
        )
    }
}
