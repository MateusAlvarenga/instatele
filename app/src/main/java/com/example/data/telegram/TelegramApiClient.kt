package com.example.data.telegram

import android.content.Context
import android.net.Uri
import com.example.data.model.MediaType
import com.example.data.model.ScannedMediaFile
import com.example.util.InstagramParser
import kotlinx.coroutines.delay
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class TelegramApiClient(
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

    suspend fun testBot(botToken: String, baseUrl: String): Result<String> {
        val cleanToken = botToken.trim()
        val url = normalizeBaseUrl(baseUrl) + "/bot$cleanToken/getMe"
        val request = Request.Builder().url(url).get().build()

        return executeRequest(request) { json ->
            val result = json.optJSONObject("result")
            val username = result?.optString("username") ?: "Bot"
            val firstName = result?.optString("first_name") ?: ""
            "Conectado com sucesso como: $firstName (@$username)"
        }
    }

    suspend fun testDestination(botToken: String, chatId: String, baseUrl: String): Result<String> {
        val cleanToken = botToken.trim()
        val cleanChatId = chatId.trim()
        val url = normalizeBaseUrl(baseUrl) + "/bot$cleanToken/getChat"

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", cleanChatId)
            .build()

        val request = Request.Builder().url(url).post(body).build()

        return executeRequest(request) { json ->
            val result = json.optJSONObject("result")
            val title = result?.optString("title")
            val username = result?.optString("username")
            val name = title ?: (if (!username.isNullOrEmpty()) "@$username" else cleanChatId)
            "Destino confirmado: $name"
        }
    }

    suspend fun sendMessage(
        botToken: String,
        chatId: String,
        text: String,
        baseUrl: String
    ): Result<Boolean> {
        val cleanToken = botToken.trim()
        val cleanChatId = chatId.trim()
        val url = normalizeBaseUrl(baseUrl) + "/bot$cleanToken/sendMessage"

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", cleanChatId)
            .addFormDataPart("text", text)
            .addFormDataPart("parse_mode", "HTML")
            .addFormDataPart("disable_web_page_preview", "true")
            .build()

        val request = Request.Builder().url(url).post(body).build()

        return executeRequest(request) { true }
    }

    /**
     * Sends an album of 2 to 10 media files using sendMediaGroup.
     */
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
        val cleanToken = botToken.trim()
        val cleanChatId = chatId.trim()
        val url = normalizeBaseUrl(baseUrl) + "/bot$cleanToken/sendMediaGroup"

        val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        multipartBuilder.addFormDataPart("chat_id", cleanChatId)

        val mediaArray = JSONArray()

        files.forEachIndexed { index, file ->
            val attachKey = "file$index"
            val mediaObj = JSONObject()
            val mediaTypeStr = when (file.mediaType) {
                MediaType.PHOTO -> "photo"
                MediaType.VIDEO -> "video"
                MediaType.DOCUMENT -> "document"
            }
            mediaObj.put("type", mediaTypeStr)
            mediaObj.put("media", "attach://$attachKey")

            // Add caption to the first item of the album
            if (index == 0) {
                mediaObj.put("caption", caption)
                mediaObj.put("parse_mode", "HTML")
            }

            if (file.mediaType == MediaType.VIDEO) {
                mediaObj.put("supports_streaming", true)
            }

            mediaArray.put(mediaObj)

            val partBody = createUriRequestBody(context, file.uri, file.mimeType)
            multipartBuilder.addFormDataPart(attachKey, file.name, partBody)
        }

        multipartBuilder.addFormDataPart("media", mediaArray.toString())

        val rawBody = multipartBuilder.build()
        val countedBody = CountingRequestBody(
            delegate = rawBody,
            onProgress = onProgress,
            isCancelled = isCancelled,
            isSkipped = isSkipped
        )

        val request = Request.Builder().url(url).post(countedBody).build()
        return executeWithRetry(request, isCancelled = isCancelled, isSkipped = isSkipped) { true }
    }

    /**
     * Sends a single media file (photo, video, or document).
     */
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
        val cleanToken = botToken.trim()
        val cleanChatId = chatId.trim()
        val endpoint = when (file.mediaType) {
            MediaType.PHOTO -> "sendPhoto"
            MediaType.VIDEO -> "sendVideo"
            MediaType.DOCUMENT -> "sendDocument"
        }
        val fileField = when (file.mediaType) {
            MediaType.PHOTO -> "photo"
            MediaType.VIDEO -> "video"
            MediaType.DOCUMENT -> "document"
        }

        val url = normalizeBaseUrl(baseUrl) + "/bot$cleanToken/$endpoint"

        val filePartBody = createUriRequestBody(context, file.uri, file.mimeType)

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", cleanChatId)
            .addFormDataPart("caption", caption)
            .addFormDataPart("parse_mode", "HTML")
            .addFormDataPart(fileField, file.name, filePartBody)

        if (file.mediaType == MediaType.VIDEO) {
            multipartBuilder.addFormDataPart("supports_streaming", "true")
        }

        val rawBody = multipartBuilder.build()
        val countedBody = CountingRequestBody(
            delegate = rawBody,
            onProgress = onProgress,
            isCancelled = isCancelled,
            isSkipped = isSkipped
        )

        val request = Request.Builder().url(url).post(countedBody).build()
        return executeWithRetry(request, isCancelled = isCancelled, isSkipped = isSkipped) { true }
    }

    private suspend fun <T> executeWithRetry(
        request: Request,
        isCancelled: () -> Boolean,
        isSkipped: () -> Boolean,
        maxRetries: Int = 3,
        parseSuccess: (JSONObject) -> T
    ): Result<T> {
        var currentAttempt = 0
        while (currentAttempt <= maxRetries) {
            if (isCancelled()) {
                return Result.failure(CancellationException("Upload cancelado pelo usuário"))
            }
            if (isSkipped()) {
                return Result.failure(SkipException("Arquivo pulado pelo usuário"))
            }

            try {
                val call = client.newCall(request)
                currentCall.set(call)
                val response = call.execute()
                val bodyStr = response.body?.string().orEmpty()

                if (response.code == 429) {
                    // Flood wait
                    val json = try { JSONObject(bodyStr) } catch (e: Exception) { null }
                    val waitSecs = json?.optJSONObject("parameters")?.optInt("retry_after", 15) ?: 15
                    delay((waitSecs * 1000L).coerceAtMost(60_000L))
                    currentAttempt++
                    continue
                }

                if (!response.isSuccessful) {
                    val errorDesc = parseErrorDescription(bodyStr) ?: "HTTP ${response.code} ${response.message}".trim()
                    val isSizeLimit = response.code == 413 ||
                            errorDesc.contains("too big", ignoreCase = true) ||
                            errorDesc.contains("too large", ignoreCase = true) ||
                            errorDesc.contains("file_parts_invalid", ignoreCase = true) ||
                            bodyStr.contains("413", ignoreCase = true) ||
                            bodyStr.contains("Payload Too Large", ignoreCase = true) ||
                            bodyStr.contains("Entity Too Large", ignoreCase = true) ||
                            bodyStr.contains("file is too big", ignoreCase = true)

                    if (isSizeLimit) {
                        return Result.failure(IOException("EXCEDE_LIMITE_50MB: $errorDesc"))
                    }
                    return Result.failure(IOException(errorDesc))
                }

                val json = JSONObject(bodyStr)
                if (json.optBoolean("ok", false)) {
                    return Result.success(parseSuccess(json))
                } else {
                    val desc = json.optString("description", "Erro desconhecido do Telegram")
                    val isSizeLimit = desc.contains("too big", ignoreCase = true) ||
                            desc.contains("too large", ignoreCase = true) ||
                            desc.contains("file_parts_invalid", ignoreCase = true) ||
                            desc.contains("413", ignoreCase = true)
                    if (isSizeLimit) {
                        return Result.failure(IOException("EXCEDE_LIMITE_50MB: $desc"))
                    }
                    return Result.failure(IOException(desc))
                }
            } catch (e: SkipException) {
                return Result.failure(e)
            } catch (e: CancellationException) {
                return Result.failure(e)
            } catch (e: IOException) {
                if (isCancelled()) {
                    return Result.failure(CancellationException("Upload cancelado pelo usuário"))
                }
                if (isSkipped()) {
                    return Result.failure(SkipException("Arquivo pulado pelo usuário"))
                }
                if (currentAttempt < maxRetries) {
                    currentAttempt++
                    delay(2000L * currentAttempt)
                } else {
                    return Result.failure(e)
                }
            } finally {
                currentCall.set(null)
            }
        }
        return Result.failure(IOException("Limite de tentativas excedido."))
    }

    private fun <T> executeRequest(
        request: Request,
        parseSuccess: (JSONObject) -> T
    ): Result<T> {
        return try {
            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errorDesc = parseErrorDescription(bodyStr) ?: "HTTP ${response.code} ${response.message}"
                return Result.failure(IOException(errorDesc))
            }
            val json = JSONObject(bodyStr)
            if (json.optBoolean("ok", false)) {
                Result.success(parseSuccess(json))
            } else {
                val desc = json.optString("description", "Erro na resposta do Telegram")
                Result.failure(IOException(desc))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorDescription(responseBody: String): String? {
        return try {
            val json = JSONObject(responseBody)
            json.optString("description").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizeBaseUrl(url: String): String {
        var trimmed = url.trim()
        if (trimmed.isEmpty()) trimmed = "https://api.telegram.org"
        return trimmed.removeSuffix("/")
    }

    private fun createUriRequestBody(context: Context, uri: Uri, mimeType: String?): RequestBody {
        val mediaType = (mimeType ?: "application/octet-stream").toMediaTypeOrNull()
        return object : RequestBody() {
            override fun contentType(): okhttp3.MediaType? = mediaType

            override fun contentLength(): Long {
                return try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use {
                        it.statSize
                    } ?: -1L
                } catch (e: Exception) {
                    -1L
                }
            }

            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    sink.writeAll(inputStream.source())
                } ?: throw IOException("Não foi possível ler o arquivo: $uri")
            }
        }
    }
}
