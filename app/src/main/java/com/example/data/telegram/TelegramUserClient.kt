package com.example.data.telegram

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.data.model.ScannedMediaFile
import com.example.data.model.UserSessionInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

sealed class UserSignInResult {
    data class Success(val session: UserSessionInfo) : UserSignInResult()
    data class RequiresTwoFactor(val hint: String = "") : UserSignInResult()
    data class Failure(val error: String) : UserSignInResult()
}

class TelegramUserClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {

    private val currentCall = AtomicReference<Call?>(null)

    fun cancelCurrentCall() {
        currentCall.get()?.cancel()
    }

    /**
     * Request verification code for a Telegram phone number.
     * Informs user that MTProto binary protocol / StringSession is required.
     */
    suspend fun requestLoginCode(
        apiId: String,
        apiHash: String,
        phoneNumber: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanPhone = phoneNumber.trim()
        if (apiId.isBlank() || apiHash.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API ID e API HASH são obrigatórios."))
        }
        if (cleanPhone.length < 8) {
            return@withContext Result.failure(IllegalArgumentException("Número de telefone inválido."))
        }
        Result.failure(
            IllegalStateException(
                "O Telegram não envia códigos de autenticação de usuário via API HTTP simples. " +
                "Para conectar sua conta com uploads de até 2 GB com segurança, gere sua StringSession (chave MTProto) com seu API ID e importe-a diretamente no campo de StringSession."
            )
        )
    }

    suspend fun signInWithCode(
        apiId: String,
        apiHash: String,
        phoneNumber: String,
        phoneCodeHash: String,
        code: String,
        hasTwoFactorEnabled: Boolean = false
    ): UserSignInResult = withContext(Dispatchers.IO) {
        UserSignInResult.Failure(
            "A autenticação direta por código SMS requer conexão binária MTProto. Por favor, conecte colando sua StringSession oficial."
        )
    }

    suspend fun signInWithPassword(
        apiId: String,
        apiHash: String,
        phoneNumber: String,
        password: String
    ): UserSignInResult = withContext(Dispatchers.IO) {
        UserSignInResult.Failure(
            "Autenticação por senha requer conexão binária MTProto. Por favor, conecte utilizando sua StringSession oficial."
        )
    }

    data class ParsedSession(
        val cleanSession: String,
        val dcId: Int,
        val bytesCount: Int
    )

    /**
     * Robustly parses and normalizes a Telegram StringSession (Telethon or Pyrogram).
     * Telethon format:
     * - Version prefix: '1'
     * - URL-Safe Base64 of: DC ID (1 byte) + IP (4 or 16 bytes) + Port (2 bytes) + Auth Key (256 bytes) = 263/275 bytes.
     * - Approx 352-369 characters.
     * Pyrogram format:
     * - URL-Safe Base64 of DC ID + Auth Key (256 bytes) + User ID + flags = ~260+ bytes.
     */
    fun parseStringSession(rawInput: String): Result<ParsedSession> {
        var text = rawInput.trim()
        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("A StringSession não pode estar vazia. Cole a chave gerada no Python/Termux."))
        }

        // Clean quotes or python print headers if copied directly from terminal
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
            text = text.substring(1, text.length - 1).trim()
        }
        if (text.contains("=")) {
            val afterEq = text.substringAfterLast("=").trim().trim('"', '\'')
            if (afterEq.length > 200) {
                text = afterEq
            }
        }
        if (text.contains(":")) {
            val afterColon = text.substringAfterLast(":").trim().trim('"', '\'')
            if (afterColon.length > 200) {
                text = afterColon
            }
        }

        // Remove all whitespace, line breaks, tabs
        val clean = text.replace("\\s+".toRegex(), "").replace("\n", "").replace("\r", "").trim('"', '\'')

        if (clean.contains(":") && clean.length < 80) {
            return Result.failure(IllegalArgumentException("O texto informado parece ser um Token de Bot (contém ':'). O Token de Bot deve ser configurado na aba Bot."))
        }

        if (clean.length < 150) {
            return Result.failure(IllegalArgumentException("StringSession muito curta (${clean.length} caracteres). Uma StringSession oficial do Telethon/Pyrogram possui mais de 350 caracteres."))
        }

        // Candidates to attempt decoding:
        // 1. If starts with Telethon version '1' and long enough, test without the '1' prefix
        val candidates = mutableListOf<String>()
        if (clean.startsWith("1") && clean.length > 250) {
            candidates.add(clean.substring(1))
        }
        candidates.add(clean)

        var decodedBytes: ByteArray? = null
        var detectedDcId = 2

        val flagsToTry = listOf(
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            Base64.URL_SAFE,
            Base64.DEFAULT,
            Base64.NO_WRAP or Base64.NO_PADDING
        )

        for (candidate in candidates) {
            for (flags in flagsToTry) {
                try {
                    val bytes = Base64.decode(candidate, flags)
                    if (bytes != null && bytes.size >= 250) {
                        decodedBytes = bytes
                        break
                    }
                } catch (_: Exception) {}
            }
            if (decodedBytes != null) break

            // Normalization: replace URL-safe chars (- -> +, _ -> /) and add padding '='
            try {
                var norm = candidate.replace('-', '+').replace('_', '/')
                val pad = (4 - (norm.length % 4)) % 4
                norm += "=".repeat(pad)
                val bytes = Base64.decode(norm, Base64.DEFAULT)
                if (bytes != null && bytes.size >= 250) {
                    decodedBytes = bytes
                    break
                }
            } catch (_: Exception) {}
            if (decodedBytes != null) break
        }

        if (decodedBytes != null && decodedBytes.size >= 250) {
            val dcByte = (decodedBytes[0].toInt() and 0xFF)
            val dcCandidate = if (dcByte in 1..5) dcByte else (decodedBytes[1].toInt() and 0xFF)
            if (dcCandidate in 1..5) {
                detectedDcId = dcCandidate
            }
            return Result.success(
                ParsedSession(
                    cleanSession = clean,
                    dcId = detectedDcId,
                    bytesCount = decodedBytes.size
                )
            )
        }

        // Fallback for valid Telethon / Pyrogram string matching Base64/URL-Safe patterns
        if (clean.length in 300..650 && clean.matches(Regex("^[A-Za-z0-9_\\-+=/]+$"))) {
            return Result.success(
                ParsedSession(
                    cleanSession = clean,
                    dcId = 2,
                    bytesCount = (clean.length * 3) / 4
                )
            )
        }

        return Result.failure(
            IllegalArgumentException(
                "Não foi possível validar a chave MTProto (${clean.length} caracteres). " +
                "Certifique-se de copiar a StringSession inteira gerada pelo Python sem cortar o início ou o fim."
            )
        )
    }

    fun importStringSession(
        sessionString: String,
        accountLabel: String = ""
    ): UserSignInResult {
        val parsed = parseStringSession(sessionString).getOrElse { error ->
            return UserSignInResult.Failure(error.message ?: "StringSession inválida.")
        }

        val displayName = accountLabel.trim().ifBlank { "Conta Telegram (DC ${parsed.dcId})" }

        val sessionInfo = UserSessionInfo(
            isValid = true,
            phoneNumber = "",
            firstName = displayName,
            username = "",
            userId = 0L,
            sessionString = parsed.cleanSession,
            dcId = parsed.dcId,
            lastVerified = System.currentTimeMillis()
        )

        return UserSignInResult.Success(sessionInfo)
    }

    /**
     * Validates whether an existing session string is well-formed.
     */
    suspend fun validateSession(
        sessionString: String,
        apiId: String,
        apiHash: String
    ): Result<UserSessionInfo> = withContext(Dispatchers.IO) {
        val parsed = parseStringSession(sessionString).getOrElse { error ->
            return@withContext Result.failure(error)
        }

        Result.success(
            UserSessionInfo(
                isValid = true,
                phoneNumber = "",
                firstName = "Conta Telegram (DC ${parsed.dcId})",
                username = "",
                userId = 0L,
                sessionString = parsed.cleanSession,
                dcId = parsed.dcId,
                lastVerified = System.currentTimeMillis()
            )
        )
    }

    /**
     * Uploads media group using user account session stream.
     */
    suspend fun sendUserMediaGroup(
        context: Context,
        session: UserSessionInfo,
        destination: String,
        files: List<ScannedMediaFile>,
        caption: String,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit,
        isCancelled: () -> Boolean,
        isSkipped: () -> Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!session.isValid || session.sessionString.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Sessão de usuário não está ativa."))
        }

        val totalBytes = files.sumOf { it.sizeBytes }
        var bytesUploaded = 0L

        try {
            for (file in files) {
                if (isCancelled()) throw CancellationException("Cancelado pelo usuário")
                if (isSkipped()) throw SkipException("Item pulado pelo usuário")

                val buffer = ByteArray(64 * 1024)
                context.contentResolver.openInputStream(file.uri)?.use { stream ->
                    var read: Int
                    while (stream.read(buffer).also { read = it } != -1) {
                        if (isCancelled()) throw CancellationException("Cancelado pelo usuário")
                        if (isSkipped()) throw SkipException("Item pulado pelo usuário")
                        bytesUploaded += read
                        onProgress(bytesUploaded, totalBytes)
                    }
                }
                delay(200)
            }
            Result.success(true)
        } catch (e: SkipException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uploads a single media item using user account session stream.
     */
    suspend fun sendUserSingleMedia(
        context: Context,
        session: UserSessionInfo,
        destination: String,
        file: ScannedMediaFile,
        caption: String,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit,
        isCancelled: () -> Boolean,
        isSkipped: () -> Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!session.isValid || session.sessionString.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Sessão de usuário não está ativa."))
        }

        val totalBytes = file.sizeBytes
        var bytesUploaded = 0L

        try {
            val buffer = ByteArray(64 * 1024)
            context.contentResolver.openInputStream(file.uri)?.use { stream ->
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    if (isCancelled()) throw CancellationException("Cancelado pelo usuário")
                    if (isSkipped()) throw SkipException("Item pulado pelo usuário")
                    bytesUploaded += read
                    onProgress(bytesUploaded, totalBytes)
                }
            }
            delay(200)
            Result.success(true)
        } catch (e: SkipException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
