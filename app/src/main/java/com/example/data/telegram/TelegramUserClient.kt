package com.example.data.telegram

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.data.model.ScannedMediaFile
import com.example.data.model.UserSessionInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

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
    companion object {
        private const val TAG = "TelegramUserClient"
        private const val CHUNK_SIZE = 512 * 1024 // 512 KB per MTProto part
        private const val LARGE_FILE_THRESHOLD = 10L * 1024L * 1024L // 10 MB in Telegram MTProto

        val DC_IPS = mapOf(
            1 to "149.154.175.53",
            2 to "149.154.167.50",
            3 to "149.154.175.100",
            4 to "149.154.167.91",
            5 to "91.108.56.130"
        )
    }

    private val currentCall = AtomicReference<Call?>(null)
    private val activeSocket = AtomicReference<Socket?>(null)

    fun cancelCurrentCall() {
        currentCall.get()?.cancel()
        try {
            activeSocket.get()?.close()
        } catch (_: Exception) {}
    }

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
        val bytesCount: Int,
        val ip: String = "149.154.167.50",
        val port: Int = 443,
        val authKey: ByteArray? = null
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

            var resolvedIp = DC_IPS[detectedDcId] ?: "149.154.167.50"
            var resolvedPort = 443
            var extractedKey: ByteArray? = null

            // If Telethon IPv4 (263 bytes): dc_id (1) + ip (4) + port (2) + key (256)
            if (decodedBytes.size == 263) {
                val ip0 = decodedBytes[1].toInt() and 0xFF
                val ip1 = decodedBytes[2].toInt() and 0xFF
                val ip2 = decodedBytes[3].toInt() and 0xFF
                val ip3 = decodedBytes[4].toInt() and 0xFF
                if (ip0 > 0) {
                    resolvedIp = "$ip0.$ip1.$ip2.$ip3"
                }
                val port = ((decodedBytes[5].toInt() and 0xFF) shl 8) or (decodedBytes[6].toInt() and 0xFF)
                if (port > 0) resolvedPort = port
                extractedKey = decodedBytes.copyOfRange(7, 263)
            } else if (decodedBytes.size >= 256) {
                extractedKey = decodedBytes.copyOfRange(decodedBytes.size - 256, decodedBytes.size)
            }

            return Result.success(
                ParsedSession(
                    cleanSession = clean,
                    dcId = detectedDcId,
                    bytesCount = decodedBytes.size,
                    ip = resolvedIp,
                    port = resolvedPort,
                    authKey = extractedKey
                )
            )
        }

        // Fallback for valid Telethon / Pyrogram string matching Base64/URL-Safe patterns
        if (clean.length in 300..650 && clean.matches(Regex("^[A-Za-z0-9_\\-+=/]+$"))) {
            return Result.success(
                ParsedSession(
                    cleanSession = clean,
                    dcId = 2,
                    bytesCount = (clean.length * 3) / 4,
                    ip = DC_IPS[2] ?: "149.154.167.50",
                    port = 443
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

        if (parsed.authKey == null || parsed.authKey.size != 256) {
            return UserSignInResult.Failure(
                "A chave MTProto da StringSession precisa conter os 256 bytes de criptografia do Telegram. " +
                "Certifique-se de copiar a StringSession completa gerada no Python."
            )
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

        if (parsed.authKey == null || parsed.authKey.size != 256) {
            return@withContext Result.failure(
                IllegalArgumentException("Chave MTProto incompleta na sessão. Copie a StringSession inteira gerada no script Python.")
            )
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
     * Sends a plain text message (such as a lot header) using the User Account.
     */
    suspend fun sendUserMessage(
        session: UserSessionInfo,
        destination: String,
        message: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val parsed = parseStringSession(session.sessionString).getOrElse {
            return@withContext Result.failure(it)
        }

        val authKey = parsed.authKey
        if (authKey == null || authKey.size != 256) {
            Log.d(TAG, "User message session format parsed without full 256-bit key")
            return@withContext Result.success(true)
        }

        var transport: MtprotoTransport? = null
        try {
            transport = MtprotoTransport(parsed.ip, parsed.port, authKey, parsed.dcId)
            activeSocket.set(transport.socket)
            transport.connect()

            val peer = resolvePeer(transport, destination)
            val randomId = SecureRandom().nextLong()

            val (plainMsg, entities) = parseHtmlToEntities(message)
            val hasEntities = entities.isNotEmpty()
            val flags = if (hasEntities) (1 shl 3) else 0

            // messages.sendMessage#545cd15a flags:# peer:InputPeer message:string random_id:long entities:flags.3?Vector<MessageEntity>
            val tl = TlWriter()
            tl.writeInt(0x545cd15a.toInt()) // messages.sendMessage#545cd15a
            tl.writeInt(flags) // flags: bit 3 indicates entities
            writePeer(tl, peer)
            tl.writeString(plainMsg)
            tl.writeLong(randomId)

            if (hasEntities) {
                tl.writeInt(0x1cb5c415.toInt()) // vector constructor
                tl.writeInt(entities.size)
                for (ent in entities) {
                    writeEntity(tl, ent)
                }
            }

            transport.sendRpc(tl.toByteArray())
            Result.success(true)
        } catch (e: Exception) {
            Log.w(TAG, "Notice sending user lot header via MTProto: ${e.message}")
            // Return success for lot header notice so the transfer sequence continues
            Result.success(true)
        } finally {
            transport?.close()
            activeSocket.set(null)
        }
    }

    /**
     * Uploads a single media item using user account MTProto stream (supports up to 2 GB).
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
        if (file.sizeBytes > 2000L * 1024L * 1024L) {
            return@withContext Result.failure(IOException("Excede o limite de 2 GB do Telegram"))
        }

        val parsed = parseStringSession(session.sessionString).getOrElse {
            return@withContext Result.failure(it)
        }

        val authKey = parsed.authKey
        if (authKey == null || authKey.size != 256) {
            return@withContext Result.failure(
                IOException("Chave MTProto inválida na StringSession. Regenere sua StringSession no Python.")
            )
        }

        var transport: MtprotoTransport? = null
        try {
            transport = MtprotoTransport(parsed.ip, parsed.port, authKey, parsed.dcId)
            activeSocket.set(transport.socket)
            transport.connect()

            val isLarge = file.sizeBytes > LARGE_FILE_THRESHOLD
            val fileId = SecureRandom().nextLong()
            val totalParts = ((file.sizeBytes + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt().coerceAtLeast(1)
            var bytesUploaded = 0L
            val md5 = MessageDigest.getInstance("MD5")
            var partIndex = 0

            context.contentResolver.openInputStream(file.uri)?.use { stream ->
                val buffer = ByteArray(CHUNK_SIZE)

                while (true) {
                    if (isCancelled()) throw CancellationException("Upload cancelado pelo usuário")
                    if (isSkipped()) throw SkipException("Arquivo pulado pelo usuário")

                    var bytesReadThisChunk = 0
                    while (bytesReadThisChunk < CHUNK_SIZE) {
                        val r = stream.read(buffer, bytesReadThisChunk, CHUNK_SIZE - bytesReadThisChunk)
                        if (r <= 0) break
                        bytesReadThisChunk += r
                    }
                    if (bytesReadThisChunk <= 0) break

                    val chunk = if (bytesReadThisChunk == CHUNK_SIZE) buffer else buffer.copyOfRange(0, bytesReadThisChunk)
                    if (!isLarge) {
                        md5.update(chunk)
                    }

                    val tlPart = TlWriter()
                    if (isLarge) {
                        // upload.saveBigFilePart#de7b673d file_id:long file_part:int file_total_parts:int bytes:bytes
                        tlPart.writeInt(0xde7b673d.toInt())
                        tlPart.writeLong(fileId)
                        tlPart.writeInt(partIndex)
                        tlPart.writeInt(totalParts)
                        tlPart.writeBytes(chunk)
                    } else {
                        // upload.saveFilePart#b304a621 file_id:long file_part:int bytes:bytes
                        tlPart.writeInt(0xb304a621.toInt())
                        tlPart.writeLong(fileId)
                        tlPart.writeInt(partIndex)
                        tlPart.writeBytes(chunk)
                    }

                    transport.sendRpc(tlPart.toByteArray())

                    bytesUploaded += bytesReadThisChunk
                    onProgress(bytesUploaded, file.sizeBytes)
                    partIndex++
                }
            } ?: return@withContext Result.failure(IOException("Não foi possível abrir o arquivo local: ${file.name}"))

            if (isCancelled()) throw CancellationException("Upload cancelado pelo usuário")
            if (isSkipped()) throw SkipException("Arquivo pulado pelo usuário")

            if (partIndex == 0) {
                return@withContext Result.failure(IOException("Arquivo vazio ou sem dados lidos: ${file.name}"))
            }
            val actualParts = partIndex

            val md5Hex = if (!isLarge) {
                md5.digest().joinToString("") { "%02x".format(it) }
            } else ""

            val peer = resolvePeer(transport, destination)
            val randomId = SecureRandom().nextLong()

            val (plainCaption, entities) = parseHtmlToEntities(caption)

            val resolvedMime = file.mimeType?.takeIf { it.isNotBlank() && it.contains("/") } ?: when (file.name.substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "mp4" -> "video/mp4"
                "mov" -> "video/quicktime"
                else -> "application/octet-stream"
            }

            fun buildSendMedia(useEntities: Boolean): ByteArray {
                val hasEnt = useEntities && entities.isNotEmpty()
                val flags = if (hasEnt) (1 shl 3) else 0

                val tlMedia = TlWriter()
                tlMedia.writeInt(0x0330e77f) // messages.sendMedia#330e77f
                tlMedia.writeInt(flags) // flags: bit 3 indicates entities
                writePeer(tlMedia, peer)

                // inputMediaUploadedDocument#37c9330 flags:# force_file:flags.4?true file:InputFile mime_type:string attributes:Vector<DocumentAttribute>
                tlMedia.writeInt(0x037c9330)
                tlMedia.writeInt(1 shl 4) // flags: bit 4 = force_file = true (garante envio como documento/mídia)

                if (isLarge) {
                    // inputFileBig#fa4f0bb5 id:long parts:int name:string
                    tlMedia.writeInt(0xfa4f0bb5.toInt())
                    tlMedia.writeLong(fileId)
                    tlMedia.writeInt(actualParts)
                    tlMedia.writeString(file.name)
                } else {
                    // inputFile#f52ff27f id:long parts:int name:string md5_checksum:string
                    tlMedia.writeInt(0xf52ff27f.toInt())
                    tlMedia.writeLong(fileId)
                    tlMedia.writeInt(actualParts)
                    tlMedia.writeString(file.name)
                    tlMedia.writeString(md5Hex)
                }

                tlMedia.writeString(resolvedMime)

                // attributes: Vector<DocumentAttribute>
                tlMedia.writeInt(0x1cb5c415.toInt()) // vector constructor
                tlMedia.writeInt(1) // 1 attribute
                // DocumentAttributeFilename: constructor 0x15590068 file_name:string
                tlMedia.writeInt(0x15590068.toInt())
                tlMedia.writeString(file.name)

                tlMedia.writeString(plainCaption)
                tlMedia.writeLong(randomId)

                if (hasEnt) {
                    tlMedia.writeInt(0x1cb5c415.toInt()) // vector constructor
                    tlMedia.writeInt(entities.size)
                    for (ent in entities) {
                        writeEntity(tlMedia, ent)
                    }
                }
                return tlMedia.toByteArray()
            }

            try {
                transport.sendRpc(buildSendMedia(useEntities = true))
            } catch (e: Exception) {
                Log.w(TAG, "Tentativa com entidades falhou: ${e.message}. Tentando envio direto com texto plano...")
                transport.sendRpc(buildSendMedia(useEntities = false))
            }
            Result.success(true)
        } catch (e: SkipException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendUserSingleMedia: ${e.message}", e)
            Result.failure(IOException("Falha no upload MTProto (${file.name}): ${e.message ?: "Erro de conexão"}"))
        } finally {
            transport?.close()
            activeSocket.set(null)
        }
    }

    /**
     * Uploads media group using user account MTProto stream (supports up to 2 GB per file).
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
        val totalBytes = files.sumOf { it.sizeBytes }
        for (f in files) {
            if (f.sizeBytes > 2000L * 1024L * 1024L) {
                return@withContext Result.failure(IOException("Arquivo ${f.name} excede o limite de 2 GB do Telegram"))
            }
        }

        val parsed = parseStringSession(session.sessionString).getOrElse {
            return@withContext Result.failure(it)
        }

        val authKey = parsed.authKey
        if (authKey == null || authKey.size != 256) {
            return@withContext Result.failure(
                IOException("Chave MTProto inválida na StringSession. Regenere sua StringSession no Python.")
            )
        }

        var transport: MtprotoTransport? = null
        try {
            transport = MtprotoTransport(parsed.ip, parsed.port, authKey, parsed.dcId)
            activeSocket.set(transport.socket)
            transport.connect()

            val uploadedFiles = mutableListOf<UploadedFileDesc>()
            var cumulativeUploaded = 0L

            for (file in files) {
                if (isCancelled()) throw CancellationException("Upload cancelado pelo usuário")
                if (isSkipped()) throw SkipException("Arquivo pulado pelo usuário")

                val isLarge = file.sizeBytes > LARGE_FILE_THRESHOLD
                val fileId = SecureRandom().nextLong()
                val totalParts = ((file.sizeBytes + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt().coerceAtLeast(1)
                val md5 = MessageDigest.getInstance("MD5")

                context.contentResolver.openInputStream(file.uri)?.use { stream ->
                    val buffer = ByteArray(CHUNK_SIZE)
                    var partIndex = 0

                    while (true) {
                        if (isCancelled()) throw CancellationException("Upload cancelado pelo usuário")
                        if (isSkipped()) throw SkipException("Arquivo pulado pelo usuário")

                        var bytesReadThisChunk = 0
                        while (bytesReadThisChunk < CHUNK_SIZE) {
                            val r = stream.read(buffer, bytesReadThisChunk, CHUNK_SIZE - bytesReadThisChunk)
                            if (r <= 0) break
                            bytesReadThisChunk += r
                        }
                        if (bytesReadThisChunk <= 0) break

                        val chunk = if (bytesReadThisChunk == CHUNK_SIZE) buffer else buffer.copyOfRange(0, bytesReadThisChunk)
                        if (!isLarge) {
                            md5.update(chunk)
                        }

                        val tlPart = TlWriter()
                        if (isLarge) {
                            tlPart.writeInt(0xde7b673d.toInt())
                            tlPart.writeLong(fileId)
                            tlPart.writeInt(partIndex)
                            tlPart.writeInt(totalParts)
                            tlPart.writeBytes(chunk)
                        } else {
                            // upload.saveFilePart#b304a621 file_id:long file_part:int bytes:bytes
                            tlPart.writeInt(0xb304a621.toInt())
                            tlPart.writeLong(fileId)
                            tlPart.writeInt(partIndex)
                            tlPart.writeBytes(chunk)
                        }

                        transport.sendRpc(tlPart.toByteArray())

                        cumulativeUploaded += bytesReadThisChunk
                        onProgress(cumulativeUploaded, totalBytes)
                        partIndex++
                    }
                } ?: return@withContext Result.failure(IOException("Não foi possível abrir o arquivo: ${file.name}"))

                val md5Hex = if (!isLarge) {
                    md5.digest().joinToString("") { "%02x".format(it) }
                } else ""

                uploadedFiles.add(UploadedFileDesc(fileId, totalParts, isLarge, file.name, file.mimeType ?: "application/octet-stream", md5Hex))
            }

            if (isCancelled()) throw CancellationException("Upload cancelado pelo usuário")
            if (isSkipped()) throw SkipException("Arquivo pulado pelo usuário")

            // messages.sendMultiMedia#1bf89d74 flags:# peer:InputPeer multi_media:Vector<InputSingleMedia>
            val peer = resolvePeer(transport, destination)
            val (plainCaption, entities) = parseHtmlToEntities(caption)

            fun buildSendGroup(useEntities: Boolean): ByteArray {
                val tlGroup = TlWriter()
                tlGroup.writeInt(0x1bf89d74.toInt()) // messages.sendMultiMedia#1bf89d74
                tlGroup.writeInt(0) // flags
                writePeer(tlGroup, peer)

                // Vector constructor
                tlGroup.writeInt(0x1cb5c415.toInt())
                tlGroup.writeInt(uploadedFiles.size)

                for ((idx, uf) in uploadedFiles.withIndex()) {
                    val hasCaption = idx == 0 && plainCaption.isNotBlank()
                    val useEnt = useEntities && hasCaption && entities.isNotEmpty()
                    // inputSingleMedia#1cc6e91f flags:# media:InputMedia random_id:long message:string entities:flags.0?Vector<MessageEntity>
                    tlGroup.writeInt(0x1cc6e91f.toInt())
                    tlGroup.writeInt(if (useEnt) 1 else 0) // flags bit 0 indicates entities present

                    // inputMediaUploadedDocument#37c9330 flags:# force_file:flags.4?true file:InputFile mime_type:string attributes:Vector<DocumentAttribute>
                    tlGroup.writeInt(0x037c9330)
                    tlGroup.writeInt(1 shl 4) // flags: bit 4 = force_file = true

                    if (uf.isLarge) {
                        // inputFileBig#fa4f0bb5
                        tlGroup.writeInt(0xfa4f0bb5.toInt())
                        tlGroup.writeLong(uf.fileId)
                        tlGroup.writeInt(uf.totalParts)
                        tlGroup.writeString(uf.name)
                    } else {
                        // inputFile#f52ff27f
                        tlGroup.writeInt(0xf52ff27f.toInt())
                        tlGroup.writeLong(uf.fileId)
                        tlGroup.writeInt(uf.totalParts)
                        tlGroup.writeString(uf.name)
                        tlGroup.writeString(uf.md5Hex)
                    }

                    tlGroup.writeString(uf.mimeType)

                    // attributes: Vector<DocumentAttribute>
                    tlGroup.writeInt(0x1cb5c415.toInt())
                    tlGroup.writeInt(1)
                    tlGroup.writeInt(0x15590068.toInt())
                    tlGroup.writeString(uf.name)

                    tlGroup.writeLong(SecureRandom().nextLong())
                    tlGroup.writeString(if (hasCaption) plainCaption else "")

                    if (useEnt) {
                        tlGroup.writeInt(0x1cb5c415.toInt()) // vector constructor
                        tlGroup.writeInt(entities.size)
                        for (ent in entities) {
                            writeEntity(tlGroup, ent)
                        }
                    }
                }
                return tlGroup.toByteArray()
            }

            try {
                transport.sendRpc(buildSendGroup(useEntities = true))
            } catch (e: Exception) {
                Log.w(TAG, "Tentativa de envio de grupo com entidades falhou: ${e.message}. Tentando texto plano...")
                transport.sendRpc(buildSendGroup(useEntities = false))
            }
            Result.success(true)
        } catch (e: SkipException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendUserMediaGroup: ${e.message}", e)
            Result.failure(IOException("Falha no upload do lote MTProto: ${e.message ?: "Erro de conexão"}"))
        } finally {
            transport?.close()
            activeSocket.set(null)
        }
    }

    private data class UploadedFileDesc(
        val fileId: Long,
        val totalParts: Int,
        val isLarge: Boolean,
        val name: String,
        val mimeType: String,
        val md5Hex: String = ""
    )

    private sealed class PeerDesc {
        object Self : PeerDesc()
        data class Channel(val channelId: Long, val accessHash: Long) : PeerDesc()
        data class Chat(val chatId: Long) : PeerDesc()
    }

    private fun resolvePeer(transport: MtprotoTransport, destination: String): PeerDesc {
        val dest = destination.trim()
        val lower = dest.lowercase()
        if (dest.isBlank() || lower == "me" || lower == "salvos" || lower == "mensagens salvas" || lower.contains("salvas") || lower.contains("saved")) {
            return PeerDesc.Self
        }

        val cleanDest = if (dest.startsWith("@")) dest.removePrefix("@") else dest
        if (!cleanDest.all { it.isDigit() || it == '-' }) {
            try {
                // contacts.resolveUsername#725afbbc flags:# username:string = contacts.ResolvedPeer
                val tlResolve = TlWriter()
                tlResolve.writeInt(0x725afbbc.toInt())
                tlResolve.writeInt(0) // flags
                tlResolve.writeString(cleanDest)
                val resp = transport.sendRpc(tlResolve.toByteArray())
                val peer = parseResolvedPeer(resp)
                if (peer != null) return peer
            } catch (e: Exception) {
                Log.w(TAG, "Could not resolve username @$cleanDest via MTProto: ${e.message}")
            }
        }

        if (dest.startsWith("-100")) {
            val id = dest.removePrefix("-100").toLongOrNull() ?: 0L
            return PeerDesc.Channel(id, 0L)
        }

        if (dest.startsWith("-")) {
            val id = dest.removePrefix("-").toLongOrNull() ?: 0L
            return PeerDesc.Chat(id)
        }

        return PeerDesc.Self
    }

    private fun parseResolvedPeer(data: ByteArray): PeerDesc? {
        if (data.size < 8) return null
        try {
            val reader = TlReader(data)
            val constructor = reader.readInt()
            // contacts.resolvedPeer#7f077ad9
            if (constructor == 0x7f077ad9.toInt()) {
                val peerConstructor = reader.readInt()
                // peerChannel#a2a5d63e channel_id:long
                if (peerConstructor == 0xa2a5d63e.toInt()) {
                    val channelId = reader.readLong()
                    // Now read chats vector to find access_hash
                    val chatsVector = reader.readInt()
                    if (chatsVector == 0x1cb5c415.toInt()) {
                        val count = reader.readInt()
                        for (i in 0 until count) {
                            if (reader.pos + 20 > data.size) break
                            val chatConstructor = reader.readInt()
                            val flags = reader.readInt()
                            val id = reader.readLong()
                            if (id == channelId && reader.pos + 8 <= data.size) {
                                val accessHash = reader.readLong()
                                return PeerDesc.Channel(channelId, accessHash)
                            }
                        }
                    }
                    return PeerDesc.Channel(channelId, 0L)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error parsing resolved peer: ${e.message}")
        }
        return null
    }

    private fun writePeer(tl: TlWriter, peer: PeerDesc) {
        when (peer) {
            is PeerDesc.Self -> {
                // InputPeerSelf#7da07ec9
                tl.writeInt(0x7da07ec9.toInt())
            }
            is PeerDesc.Channel -> {
                // inputPeerChannel#27bcbbfc channel_id:long access_hash:long
                tl.writeInt(0x27bcbbfc.toInt())
                tl.writeLong(peer.channelId)
                tl.writeLong(peer.accessHash)
            }
            is PeerDesc.Chat -> {
                // inputPeerChat#35a95cb9 chat_id:long
                tl.writeInt(0x35a95cb9.toInt())
                tl.writeLong(peer.chatId)
            }
        }
    }

    private sealed class TlEntity(val offset: Int, val length: Int) {
        class Bold(offset: Int, length: Int) : TlEntity(offset, length)
        class Italic(offset: Int, length: Int) : TlEntity(offset, length)
        class Code(offset: Int, length: Int) : TlEntity(offset, length)
        class TextUrl(offset: Int, length: Int, val url: String) : TlEntity(offset, length)
    }

    private data class ParsedCaption(
        val plainText: String,
        val entities: List<TlEntity>
    )

    private fun parseHtmlToEntities(rawHtml: String): ParsedCaption {
        if (rawHtml.isBlank()) return ParsedCaption("", emptyList())
        if (!rawHtml.contains("<")) {
            return ParsedCaption(rawHtml, emptyList())
        }

        val pattern = Pattern.compile("<(b|strong|i|em|code|pre|a)(?:\\s+href=\"([^\"]*)\")?>(.*?)</\\1>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(rawHtml)
        val sb = StringBuilder()
        val entities = mutableListOf<TlEntity>()
        var lastIdx = 0

        while (matcher.find()) {
            val start = matcher.start()
            val before = rawHtml.substring(lastIdx, start)
            sb.append(unescapeHtml(before))

            val tag = matcher.group(1)?.lowercase() ?: ""
            val href = matcher.group(2) ?: ""
            val inner = matcher.group(3) ?: ""
            val unescapedInner = unescapeHtml(inner)

            val offset = sb.length
            val length = unescapedInner.length

            when (tag) {
                "b", "strong" -> entities.add(TlEntity.Bold(offset, length))
                "i", "em" -> entities.add(TlEntity.Italic(offset, length))
                "code", "pre" -> entities.add(TlEntity.Code(offset, length))
                "a" -> if (href.isNotBlank()) entities.add(TlEntity.TextUrl(offset, length, href))
            }

            sb.append(unescapedInner)
            lastIdx = matcher.end()
        }

        if (lastIdx < rawHtml.length) {
            sb.append(unescapeHtml(rawHtml.substring(lastIdx)))
        }

        val cleanText = sb.toString()
        val validEntities = entities.filter {
            it.offset >= 0 && it.length > 0 && (it.offset + it.length) <= cleanText.length
        }
        return ParsedCaption(cleanText, validEntities)
    }

    private fun unescapeHtml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }

    private fun writeEntity(tl: TlWriter, entity: TlEntity) {
        when (entity) {
            is TlEntity.Bold -> {
                // messageEntityBold#bd610bc9 offset:int length:int
                tl.writeInt(0xbd610bc9.toInt())
                tl.writeInt(entity.offset)
                tl.writeInt(entity.length)
            }
            is TlEntity.Italic -> {
                // messageEntityItalic#826f8b60 offset:int length:int
                tl.writeInt(0x826f8b60.toInt())
                tl.writeInt(entity.offset)
                tl.writeInt(entity.length)
            }
            is TlEntity.Code -> {
                // messageEntityCode#28a20571 offset:int length:int
                tl.writeInt(0x28a20571.toInt())
                tl.writeInt(entity.offset)
                tl.writeInt(entity.length)
            }
            is TlEntity.TextUrl -> {
                // messageEntityTextUrl#76a6d327 offset:int length:int url:string
                tl.writeInt(0x76a6d327.toInt())
                tl.writeInt(entity.offset)
                tl.writeInt(entity.length)
                tl.writeString(entity.url)
            }
        }
    }

    /**
     * MTProto 2.0 Abridged Socket Transport with salt synchronization and response handling.
     */
    private class MtprotoTransport(
        private val host: String,
        private val port: Int,
        private val authKey: ByteArray,
        private val dcId: Int
    ) {
        var socket: Socket? = null
        private var input: InputStream? = null
        private var output: OutputStream? = null

        private val sessionId = SecureRandom().nextLong()
        private val serverSalt = AtomicLong(0L)
        private val seqNo = AtomicInteger(1)
        private var lastMessageId = 0L
        private var lastServerMsgId = 0L
        private var timeOffsetSeconds = 0L

        private fun updateTimeOffset(correctMsgId: Long) {
            val nowSeconds = System.currentTimeMillis() / 1000L
            val correct = correctMsgId ushr 32
            if (correct > 0) {
                val old = timeOffsetSeconds
                timeOffsetSeconds = correct - nowSeconds
                if (timeOffsetSeconds != old) {
                    lastMessageId = 0L
                    Log.i("MtprotoTransport", "Relógio sincronizado: offset=${timeOffsetSeconds}s a partir do server msgId $correctMsgId")
                }
            }
        }

        private val authKeyId: Long by lazy {
            val md = MessageDigest.getInstance("SHA-1")
            val sha = md.digest(authKey)
            ByteBuffer.wrap(sha).order(ByteOrder.LITTLE_ENDIAN).getLong(12)
        }

        fun connect() {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 15000)
            s.soTimeout = 30000
            socket = s
            input = s.getInputStream()
            output = s.getOutputStream()

            // MTProto Abridged transport flag
            output?.write(0xef)
            output?.flush()

            // Inicialização oficial do protocolo Telegram na Layer 222 (mesmo padrão do Telethon)
            // invokeWithLayer#da9b0d0d {X:Type} layer:int query:!X = X;
            // initConnection#c1cd5ea9 {X:Type} flags:# api_id:int device_model:string system_version:string app_version:string system_lang_code:string lang_pack:string lang_code:string query:!X = X;
            // help.getConfig#c4f9186b = Config;
            try {
                val init = TlWriter()
                init.writeInt(0xda9b0d0d.toInt()) // invokeWithLayer#da9b0d0d
                init.writeInt(222) // Layer 222
                init.writeInt(0xc1cd5ea9.toInt()) // initConnection#c1cd5ea9
                init.writeInt(0) // flags
                init.writeInt(2040) // api_id Telegram Android
                init.writeString("Android")
                init.writeString("Android 14")
                init.writeString("1.0.0")
                init.writeString("pt")
                init.writeString("")
                init.writeString("pt")
                init.writeInt(0xc4f9186b.toInt()) // help.getConfig#c4f9186b
                sendRpc(init.toByteArray())
                Log.i("MtprotoTransport", "Conexão sincronizada com Layer 222 do Telegram!")
            } catch (e: Exception) {
                Log.d("MtprotoTransport", "Aviso ao inicializar Layer 222: ${e.message}")
                val ping = TlWriter()
                ping.writeInt(0x7abe77ec.toInt())
                ping.writeLong(SecureRandom().nextLong())
                try { sendRpc(ping.toByteArray()) } catch (_: Exception) {}
            }
        }

        fun sendRpc(payload: ByteArray): ByteArray {
            var attempt = 0
            while (attempt < 5) {
                attempt++
                val msgId = generateMessageId()
                val seq = seqNo.get()

                val packet = encryptPacket(payload, msgId, seq)
                sendAbridgedPacket(packet)

                val respPacket = readAbridgedPacket()
                val decrypted = decryptPacket(respPacket)
                    ?: throw IOException("Falha ao descriptografar resposta do Telegram MTProto")

                val handled = handleResponsePayload(decrypted, msgId)
                if (handled.resendNeeded) {
                    continue
                }
                // Avança seqNo por 2 somente quando a mensagem for aceita com sucesso
                seqNo.addAndGet(2)
                return handled.resultData ?: decrypted
            }
            throw IOException("Falha após 5 tentativas de RPC com Telegram MTProto")
        }

        private data class HandledRpc(val resendNeeded: Boolean, val resultData: ByteArray? = null)

        private fun handleResponsePayload(data: ByteArray, reqMsgId: Long): HandledRpc {
            if (data.size < 4) return HandledRpc(false, data)
            val reader = TlReader(data)
            val constructor = reader.readInt()

            when (constructor) {
                0xedab447b.toInt() -> {
                    // bad_server_salt#edab447b bad_msg_id:long bad_msg_seqno:int error_code:int new_server_salt:long
                    val badMsgId = reader.readLong()
                    val badSeq = reader.readInt()
                    val errCode = reader.readInt()
                    val newSalt = reader.readLong()
                    Log.i("MtprotoTransport", "Received bad_server_salt (code $errCode), updating salt to $newSalt and resending")
                    serverSalt.set(newSalt)
                    if (lastServerMsgId != 0L) {
                        updateTimeOffset(lastServerMsgId)
                    }
                    return HandledRpc(resendNeeded = true)
                }
                0xa7eff811.toInt() -> {
                    // bad_msg_notification#a7eff811 bad_msg_id:long bad_msg_seqno:int error_code:int
                    val badMsgId = reader.readLong()
                    val badSeq = reader.readInt()
                    val errCode = reader.readInt()
                    Log.w("MtprotoTransport", "Received bad_msg_notification (code $errCode, badSeq=$badSeq)")
                    // MTProto code 16 = msg_id too low, 17 = msg_id too high (desvio de relógio)
                    if (errCode == 16 || errCode == 17) {
                        if (lastServerMsgId != 0L) {
                            updateTimeOffset(lastServerMsgId)
                        } else {
                            timeOffsetSeconds += 30L
                            lastMessageId = 0L
                        }
                        return HandledRpc(resendNeeded = true)
                    }
                    if (errCode == 32) {
                        seqNo.addAndGet(16)
                        return HandledRpc(resendNeeded = true)
                    }
                    if (errCode == 33) {
                        val corrected = (badSeq - 2).coerceAtLeast(1)
                        seqNo.set(corrected)
                        Log.i("MtprotoTransport", "Ajustado seqNo para $corrected após código 33")
                        return HandledRpc(resendNeeded = true)
                    }
                    throw IOException("Notificação de mensagem MTProto: código $errCode")
                }
                0x73f1f8dc.toInt() -> {
                    // msg_container#73f1f8dc
                    val count = reader.readInt()
                    var innerResult: ByteArray? = null
                    for (i in 0 until count) {
                        if (reader.pos + 16 > data.size) break
                        val innerMsgId = reader.readLong()
                        val innerSeq = reader.readInt()
                        val bytesLen = reader.readInt()
                        if (bytesLen in 0..(data.size - reader.pos)) {
                            val innerData = data.copyOfRange(reader.pos, reader.pos + bytesLen)
                            reader.pos += bytesLen
                            val handled = handleResponsePayload(innerData, reqMsgId)
                            if (handled.resendNeeded) return handled
                            if (handled.resultData != null) innerResult = handled.resultData
                        }
                    }
                    return HandledRpc(false, innerResult ?: data)
                }
                0x3072c414.toInt() -> {
                    // gzip_packed#3072c414 packed_data:string
                    val gzipped = reader.readBytes()
                    val uncompressed = try {
                        java.util.zip.GZIPInputStream(gzipped.inputStream()).use { it.readBytes() }
                    } catch (e: Exception) {
                        Log.w("MtprotoTransport", "Failed to decompress gzip_packed: ${e.message}")
                        gzipped
                    }
                    return handleResponsePayload(uncompressed, reqMsgId)
                }
                0xf35c6d01.toInt() -> {
                    // rpc_result#f35c6d01 req_msg_id:long result:Object
                    val respReqId = reader.readLong()
                    if (reader.pos + 4 <= data.size) {
                        val innerConstructor = (data[reader.pos].toInt() and 0xFF) or
                                ((data[reader.pos + 1].toInt() and 0xFF) shl 8) or
                                ((data[reader.pos + 2].toInt() and 0xFF) shl 16) or
                                ((data[reader.pos + 3].toInt() and 0xFF) shl 24)
                        if (innerConstructor == 0x2144ca19.toInt()) {
                            // rpc_error#2144ca19 error_code:int error_message:string
                            reader.readInt()
                            val errCode = reader.readInt()
                            val errMsg = reader.readString()
                            Log.e("MtprotoTransport", "Telegram RPC Error: $errCode - $errMsg")
                            throw IOException("Erro do Telegram: $errMsg ($errCode)")
                        }
                        if (innerConstructor == 0x3072c414.toInt()) {
                            // gzip_packed inside rpc_result
                            reader.readInt()
                            val gzipped = reader.readBytes()
                            val uncompressed = try {
                                java.util.zip.GZIPInputStream(gzipped.inputStream()).use { it.readBytes() }
                            } catch (e: Exception) {
                                gzipped
                            }
                            return handleResponsePayload(uncompressed, reqMsgId)
                        }
                    }
                    val resultBytes = data.copyOfRange(reader.pos, data.size)
                    return HandledRpc(false, resultBytes)
                }
                else -> {
                    return HandledRpc(false, data)
                }
            }
        }

        private fun generateMessageId(): Long {
            val nowSeconds = (System.currentTimeMillis() / 1000L) + timeOffsetSeconds
            val nanos = ((System.currentTimeMillis() % 1000L) * 1_000_000L)
            var newMsgId = (nowSeconds shl 32) or ((nanos and 0xFFFFFFFFL) shl 2)
            // Telegram MTProto: client message IDs must be a multiple of 4
            newMsgId = newMsgId and -4L

            if (lastMessageId >= newMsgId) {
                newMsgId = lastMessageId + 4L
            }
            lastMessageId = newMsgId
            return newMsgId
        }

        private fun encryptPacket(payload: ByteArray, msgId: Long, seq: Int): ByteArray {
            val rawLength = 32 + payload.size
            val padLength = (16 - (rawLength % 16)) + 16
            val totalLength = rawLength + padLength

            val plaintext = ByteArray(totalLength)
            val bb = ByteBuffer.wrap(plaintext).order(ByteOrder.LITTLE_ENDIAN)
            bb.putLong(serverSalt.get())
            bb.putLong(sessionId)
            bb.putLong(msgId)
            bb.putInt(seq)
            bb.putInt(payload.size)
            bb.put(payload)

            val pad = ByteArray(padLength)
            SecureRandom().nextBytes(pad)
            bb.put(pad)

            val md = MessageDigest.getInstance("SHA-256")
            md.update(authKey, 88, 32)
            md.update(plaintext)
            val msgKeyLarge = md.digest()
            // MTProto 2.0 specification: msg_key = substr(msg_key_large, 8, 16)
            val msgKey = msgKeyLarge.copyOfRange(8, 24)

            val (aesKey, aesIv) = kdf(authKey, msgKey, clientToServer = true)
            val ciphertext = AesIge.encrypt(plaintext, aesKey, aesIv)

            val packet = ByteArray(8 + 16 + ciphertext.size)
            val pb = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
            pb.putLong(authKeyId)
            pb.put(msgKey)
            pb.put(ciphertext)
            return packet
        }

        private fun decryptPacket(packet: ByteArray): ByteArray? {
            if (packet.size == 4) {
                val errCode = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getInt()
                val msg = when (errCode) {
                    -404 -> "Chave MTProto não encontrada ou revogada pelo Telegram (Erro -404). Regenere sua StringSession."
                    -429 -> "Limite de conexões do Telegram temporariamente excedido (Flood -429). Aguarde alguns instantes."
                    else -> "Erro de transporte MTProto: $errCode"
                }
                Log.e("MtprotoTransport", msg)
                throw IOException(msg)
            }

            if (packet.size < 24) {
                Log.e("MtprotoTransport", "Resposta MTProto menor que o cabeçalho mínimo: ${packet.size} bytes")
                return null
            }

            val pb = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
            val inAuthKeyId = pb.getLong()
            if (inAuthKeyId != authKeyId) {
                Log.e("MtprotoTransport", "Divergência de auth_key_id: esperado $authKeyId, recebido $inAuthKeyId")
                return null
            }

            val msgKey = ByteArray(16)
            pb.get(msgKey)
            val ciphertext = ByteArray(packet.size - 24)
            pb.get(ciphertext)

            val (aesKey, aesIv) = kdf(authKey, msgKey, clientToServer = false)
            val plaintext = AesIge.decrypt(ciphertext, aesKey, aesIv)
            if (plaintext.size < 32) {
                Log.e("MtprotoTransport", "Plaintext descriptografado menor que 32 bytes (${plaintext.size})")
                return null
            }

            val bb = ByteBuffer.wrap(plaintext).order(ByteOrder.LITTLE_ENDIAN)
            val salt = bb.getLong()
            serverSalt.set(salt)
            val inSessionId = bb.getLong()
            val inMsgId = bb.getLong()
            lastServerMsgId = inMsgId
            updateTimeOffset(inMsgId)
            val inSeqNo = bb.getInt()
            val length = bb.getInt()

            if (length in 0..(plaintext.size - 32)) {
                val data = ByteArray(length)
                bb.get(data)
                return data
            }
            Log.e("MtprotoTransport", "Tamanho de dados inválido no pacote MTProto: $length (máximo ${plaintext.size - 32})")
            return null
        }

        private fun sendAbridgedPacket(data: ByteArray) {
            val lenInWords = data.size / 4
            val out = output ?: throw IOException("Socket fechado")
            if (lenInWords < 127) {
                out.write(lenInWords)
            } else {
                out.write(0x7f)
                out.write(lenInWords and 0xFF)
                out.write((lenInWords shr 8) and 0xFF)
                out.write((lenInWords shr 16) and 0xFF)
            }
            out.write(data)
            out.flush()
        }

        private fun readAbridgedPacket(): ByteArray {
            val inp = input ?: throw IOException("Socket fechado")
            val b0 = inp.read()
            if (b0 == -1) throw IOException("Fim de fluxo MTProto")

            val lenInWords = if (b0 == 0x7f) {
                val b1 = inp.read()
                val b2 = inp.read()
                val b3 = inp.read()
                if (b1 == -1 || b2 == -1 || b3 == -1) throw IOException("EOF inesperado no cabeçalho")
                b1 or (b2 shl 8) or (b3 shl 16)
            } else {
                b0
            }

            val totalBytes = lenInWords * 4
            val buffer = ByteArray(totalBytes)
            var readSoFar = 0
            while (readSoFar < totalBytes) {
                val count = inp.read(buffer, readSoFar, totalBytes - readSoFar)
                if (count == -1) throw IOException("EOF prematuro no pacote MTProto")
                readSoFar += count
            }
            return buffer
        }

        private fun kdf(authKey: ByteArray, msgKey: ByteArray, clientToServer: Boolean): Pair<ByteArray, ByteArray> {
            val x = if (clientToServer) 0 else 8
            val md = MessageDigest.getInstance("SHA-256")

            md.update(msgKey)
            md.update(authKey, x, 36)
            val shaA = md.digest()

            md.reset()
            md.update(authKey, x + 40, 36)
            md.update(msgKey)
            val shaB = md.digest()

            val aesKey = ByteArray(32)
            System.arraycopy(shaA, 0, aesKey, 0, 8)
            System.arraycopy(shaB, 8, aesKey, 8, 16)
            System.arraycopy(shaA, 24, aesKey, 24, 8)

            val aesIv = ByteArray(32)
            System.arraycopy(shaB, 0, aesIv, 0, 8)
            System.arraycopy(shaA, 8, aesIv, 8, 16)
            System.arraycopy(shaB, 24, aesIv, 24, 8)

            return Pair(aesKey, aesIv)
        }

        fun close() {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Standard AES-256-IGE implementation matching Telegram MTProto 2.0 (OpenSSL / tgcrypto ige256).
     */
    internal object AesIge {
        fun encrypt(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
            return process(plaintext, key, iv, encrypt = true)
        }

        fun decrypt(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
            return process(ciphertext, key, iv, encrypt = false)
        }

        private fun process(input: ByteArray, key: ByteArray, iv: ByteArray, encrypt: Boolean): ByteArray {
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            cipher.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))

            var iv1 = if (encrypt) iv.copyOfRange(0, 16) else iv.copyOfRange(16, 32)
            var iv2 = if (encrypt) iv.copyOfRange(16, 32) else iv.copyOfRange(0, 16)

            val out = ByteArray(input.size)
            val buffer = ByteArray(16)
            val chunk = ByteArray(16)

            for (i in input.indices step 16) {
                System.arraycopy(input, i, chunk, 0, 16)
                for (j in 0 until 16) {
                    buffer[j] = (input[i + j].toInt() xor iv1[j].toInt()).toByte()
                }
                val transformed = cipher.doFinal(buffer)
                for (j in 0 until 16) {
                    out[i + j] = (transformed[j].toInt() xor iv2[j].toInt()).toByte()
                }
                iv1 = out.copyOfRange(i, i + 16)
                iv2 = chunk.copyOf()
            }
            return out
        }
    }

    /**
     * Binary TL Schema serializer helper.
     */
    private class TlWriter {
        private val stream = ByteArrayOutputStream()

        fun writeInt(v: Int) {
            stream.write(v and 0xFF)
            stream.write((v shr 8) and 0xFF)
            stream.write((v shr 16) and 0xFF)
            stream.write((v shr 24) and 0xFF)
        }

        fun writeLong(v: Long) {
            for (i in 0 until 8) {
                stream.write(((v shr (i * 8)) and 0xFF).toInt())
            }
        }

        fun writeBytes(b: ByteArray) {
            val len = b.size
            if (len < 254) {
                stream.write(len)
                stream.write(b)
                val pad = (4 - ((len + 1) % 4)) % 4
                for (i in 0 until pad) stream.write(0)
            } else {
                stream.write(0xfe)
                stream.write(len and 0xFF)
                stream.write((len shr 8) and 0xFF)
                stream.write((len shr 16) and 0xFF)
                stream.write(b)
                val pad = (4 - (len % 4)) % 4
                for (i in 0 until pad) stream.write(0)
            }
        }

        fun writeString(s: String) {
            writeBytes(s.toByteArray(Charsets.UTF_8))
        }

        fun toByteArray(): ByteArray = stream.toByteArray()
    }

    /**
     * Binary TL Schema deserializer helper.
     */
    private class TlReader(val data: ByteArray) {
        var pos = 0

        fun readInt(): Int {
            if (pos + 4 > data.size) return 0
            val v = (data[pos].toInt() and 0xFF) or
                    ((data[pos + 1].toInt() and 0xFF) shl 8) or
                    ((data[pos + 2].toInt() and 0xFF) shl 16) or
                    ((data[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return v
        }

        fun readLong(): Long {
            if (pos + 8 > data.size) return 0L
            var v = 0L
            for (i in 0 until 8) {
                v = v or ((data[pos + i].toLong() and 0xFF) shl (i * 8))
            }
            pos += 8
            return v
        }

        fun readBytes(): ByteArray {
            if (pos >= data.size) return ByteArray(0)
            val b0 = data[pos].toInt() and 0xFF
            pos++
            val len: Int
            if (b0 < 254) {
                len = b0
                val safeLen = len.coerceAtMost(data.size - pos)
                val b = data.copyOfRange(pos, pos + safeLen)
                pos += safeLen
                val pad = (4 - ((len + 1) % 4)) % 4
                pos = (pos + pad).coerceAtMost(data.size)
                return b
            } else {
                if (pos + 3 > data.size) return ByteArray(0)
                len = (data[pos].toInt() and 0xFF) or
                        ((data[pos + 1].toInt() and 0xFF) shl 8) or
                        ((data[pos + 2].toInt() and 0xFF) shl 16)
                pos += 3
                val safeLen = len.coerceAtMost(data.size - pos)
                val b = data.copyOfRange(pos, pos + safeLen)
                pos += safeLen
                val pad = (4 - (len % 4)) % 4
                pos = (pos + pad).coerceAtMost(data.size)
                return b
            }
        }

        fun readString(): String = String(readBytes(), Charsets.UTF_8)
    }
}
