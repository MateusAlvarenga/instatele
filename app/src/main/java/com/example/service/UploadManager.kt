package com.example.service

import android.content.Context
import android.util.Log
import com.example.data.local.ArchiverSettings
import com.example.data.local.UploadRecordEntity
import com.example.data.local.UploadSessionEntity
import com.example.data.model.InstagramProfileBatch
import com.example.data.model.LiveUploadProgress
import com.example.data.model.LogLevel
import com.example.data.model.ScannedMediaFile
import com.example.data.model.TELEGRAM_BOT_API_MAX_FILE_SIZE
import com.example.data.model.UploadLogEntry
import com.example.data.model.UploadStatus
import com.example.data.repository.TelegramRepository
import com.example.data.repository.UploadRepository
import com.example.data.telegram.SkipException
import com.example.util.FileUtils
import com.example.util.InstagramParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class UploadManager(
    private val context: Context,
    private val telegramRepository: TelegramRepository,
    private val uploadRepository: UploadRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var uploadJob: Job? = null

    private val isCancelledFlag = AtomicBoolean(false)
    private val isSkippedFlag = AtomicBoolean(false)
    private val isPausedFlag = AtomicBoolean(false)

    private val _progress = MutableStateFlow(LiveUploadProgress())
    val progress: StateFlow<LiveUploadProgress> = _progress.asStateFlow()

    private val _logs = MutableStateFlow<List<UploadLogEntry>>(emptyList())
    val logs: StateFlow<List<UploadLogEntry>> = _logs.asStateFlow()

    private var lastSpeedCheckTime = 0L
    private var lastBytesCount = 0L
    private var currentSpeedBps = 0L

    fun startUpload(
        batches: List<InstagramProfileBatch>,
        settings: ArchiverSettings,
        folderName: String
    ) {
        if (_progress.value.isRunning) return

        uploadJob?.cancel()
        isCancelledFlag.set(false)
        isSkippedFlag.set(false)
        isPausedFlag.set(false)

        val totalFiles = batches.sumOf { it.allFiles.size }
        val totalBytes = batches.sumOf { it.allFiles.sumOf { f -> f.sizeBytes } }
        val totalLots = batches.sumOf { it.lots.size }

        _progress.value = LiveUploadProgress(
            isRunning = true,
            isPaused = false,
            totalFiles = totalFiles,
            totalBytesTotal = totalBytes,
            totalLots = totalLots,
            statusMessage = "Iniciando transferência...",
            currentStatus = UploadStatus.PENDING
        )

        addLog(LogLevel.INFO, "Iniciando sessão de arquivamento. Total: $totalFiles arquivos em $totalLots lote(s).")

        uploadJob = scope.launch {
            val sessionId = UUID.randomUUID().toString()
            var successCount = 0
            var skippedCount = 0
            var failedCount = 0
            var currentFileCounter = 0
            var cumulativeBytesUploaded = 0L

            uploadRepository.insertSession(
                UploadSessionEntity(
                    sessionId = sessionId,
                    folderName = folderName,
                    totalFiles = totalFiles,
                    successCount = 0,
                    skippedCount = 0,
                    failedCount = 0,
                    totalBytes = totalBytes,
                    autoDeleteEnabled = settings.autoDeleteLocal,
                    destination = settings.uploadDestination,
                    status = "RUNNING",
                    startTime = System.currentTimeMillis()
                )
            )

            try {
                var currentLotOverallIndex = 0

                for (batch in batches) {
                    if (isCancelledFlag.get()) break

                    addLog(
                        LogLevel.INFO,
                        "Processando perfil @${batch.username} (${batch.totalFilesCount} arquivos)",
                        username = batch.username
                    )

                    for (lot in batch.lots) {
                        if (isCancelledFlag.get()) break
                        currentLotOverallIndex++

                        // Telegram Bot API limit: 50MB on standard api.telegram.org, 2GB on custom local server
                        val maxAllowedBytes = if (settings.botApiBaseUrl.contains("api.telegram.org", ignoreCase = true)) {
                            TELEGRAM_BOT_API_MAX_FILE_SIZE // 50 MB
                        } else {
                            2000L * 1024L * 1024L // 2 GB
                        }

                        // 1. Separate valid files from oversized files
                        val validFiles = mutableListOf<ScannedMediaFile>()
                        val oversizedFiles = mutableListOf<ScannedMediaFile>()

                        for (f in lot.files) {
                            if (f.sizeBytes > maxAllowedBytes) {
                                oversizedFiles.add(f)
                            } else {
                                validFiles.add(f)
                            }
                        }

                        // 2. Automatically skip oversized files without attempting upload
                        for (oversized in oversizedFiles) {
                            if (isCancelledFlag.get()) break
                            currentFileCounter++
                            skippedCount++

                            val sizeFormatted = InstagramParser.formatFileSize(oversized.sizeBytes)
                            val maxFormatted = InstagramParser.formatFileSize(maxAllowedBytes)
                            val reason = "Excede o limite de $maxFormatted do Telegram Bot API ($sizeFormatted)"

                            uploadRepository.insertRecord(
                                UploadRecordEntity(
                                    sessionId = sessionId,
                                    fileName = oversized.name,
                                    uriString = oversized.uri.toString(),
                                    username = batch.username,
                                    sizeBytes = oversized.sizeBytes,
                                    mediaType = oversized.mediaType.name,
                                    lotNumber = lot.lotNumber,
                                    totalLots = lot.totalLots,
                                    groupIndex = 0,
                                    status = "SKIPPED",
                                    errorMessage = reason
                                )
                            )

                            addLog(
                                LogLevel.SKIP,
                                "⏭ Arquivo '${oversized.name}' ($sizeFormatted) pulado: excede o limite de $maxFormatted do Telegram Bot API.",
                                username = batch.username,
                                fileName = oversized.name
                            )

                            _progress.update { curr ->
                                curr.copy(
                                    currentFileName = oversized.name,
                                    currentUsername = batch.username,
                                    currentFileIndex = currentFileCounter,
                                    totalSkipped = skippedCount,
                                    totalSuccess = successCount,
                                    totalFailed = failedCount,
                                    currentStatus = UploadStatus.SKIPPED,
                                    statusMessage = "Pulado: ${oversized.name} ($sizeFormatted > $maxFormatted)"
                                )
                            }
                        }

                        // If no valid files in this lot, proceed to next lot
                        if (validFiles.isEmpty()) {
                            addLog(
                                LogLevel.WARNING,
                                "Lote ${lot.lotNumber}/${lot.totalLots} de @${batch.username} finalizado: todos os arquivos excediam o limite de 50 MB.",
                                username = batch.username
                            )
                            continue
                        }

                        // Optional lot header message
                        if (settings.sendLotHeaders && settings.botToken.isNotBlank() && settings.uploadDestination.isNotBlank()) {
                            val headerMsg = InstagramParser.buildLotHeaderMessage(
                                username = batch.username,
                                lotNumber = lot.lotNumber,
                                totalLots = lot.totalLots,
                                itemCountInLot = validFiles.size,
                                totalUserFiles = batch.totalFilesCount,
                                lotTotalSizeBytes = validFiles.sumOf { it.sizeBytes }
                            )
                            telegramRepository.sendLotHeader(
                                botToken = settings.botToken,
                                chatId = settings.uploadDestination,
                                message = headerMsg,
                                baseUrl = settings.botApiBaseUrl
                            )
                            addLog(
                                LogLevel.INFO,
                                "Cabeçalho enviado: Lote ${lot.lotNumber}/${lot.totalLots} de @${batch.username}",
                                username = batch.username
                            )
                        }

                        // Chunk only valid files into media groups (Telegram max 10 files per message)
                        val albumLimit = settings.itemsPerAlbum.coerceIn(2, 10)
                        val groups = validFiles.chunked(albumLimit)

                        for ((groupIndex, groupFiles) in groups.withIndex()) {
                            if (isCancelledFlag.get()) break

                            // Handle Pause
                            while (isPausedFlag.get() && !isCancelledFlag.get()) {
                                delay(500)
                            }

                            // Reset skip flag for this group
                            isSkippedFlag.set(false)

                            val groupTotalBytes = groupFiles.sumOf { it.sizeBytes }
                            val startRange = groupIndex * albumLimit + 1
                            val endRange = startRange + groupFiles.size - 1
                            val rangeStr = "$startRange-$endRange/${validFiles.size}"

                            val primaryFileName = groupFiles.first().name

                            _progress.update {
                                it.copy(
                                    currentFileName = primaryFileName,
                                    currentUsername = batch.username,
                                    currentFileIndex = currentFileCounter,
                                    currentLotIndex = currentLotOverallIndex,
                                    currentGroupIndex = groupIndex + 1,
                                    totalGroupsInLot = groups.size,
                                    fileBytesUploaded = 0L,
                                    fileBytesTotal = groupTotalBytes,
                                    totalSuccess = successCount,
                                    totalSkipped = skippedCount,
                                    totalFailed = failedCount,
                                    currentStatus = UploadStatus.UPLOADING,
                                    statusMessage = "Enviando @${batch.username} (Lote ${lot.lotNumber}/${lot.totalLots} • Grupo ${groupIndex + 1}/${groups.size})"
                                )
                            }

                            // Build caption for this message group
                            val caption = InstagramParser.buildCaption(
                                username = batch.username,
                                fileName = primaryFileName,
                                lotNumber = lot.lotNumber,
                                totalLots = lot.totalLots,
                                groupItemRange = rangeStr,
                                includeLink = settings.includeInstagramLink
                            )

                            // Measure upload progress and speed
                            lastSpeedCheckTime = System.currentTimeMillis()
                            lastBytesCount = 0L
                            currentSpeedBps = 0L

                            var groupUploadedBytes = 0L

                            val result: Result<Boolean> = try {
                                if (groupFiles.size > 1) {
                                    telegramRepository.sendMediaGroup(
                                        context = context,
                                        botToken = settings.botToken,
                                        chatId = settings.uploadDestination,
                                        files = groupFiles,
                                        caption = caption,
                                        baseUrl = settings.botApiBaseUrl,
                                        onProgress = { written, total ->
                                            groupUploadedBytes = written
                                            calculateSpeed(written)
                                            _progress.update { curr ->
                                                curr.copy(
                                                    fileBytesUploaded = written,
                                                    fileBytesTotal = if (total > 0) total else groupTotalBytes,
                                                    totalBytesUploaded = cumulativeBytesUploaded + written,
                                                    uploadSpeedBytesPerSec = currentSpeedBps
                                                )
                                            }
                                        },
                                        isCancelled = { isCancelledFlag.get() },
                                        isSkipped = { isSkippedFlag.get() }
                                    )
                                } else {
                                    val singleFile = groupFiles.first()
                                    telegramRepository.sendSingleMedia(
                                        context = context,
                                        botToken = settings.botToken,
                                        chatId = settings.uploadDestination,
                                        file = singleFile,
                                        caption = caption,
                                        baseUrl = settings.botApiBaseUrl,
                                        onProgress = { written, total ->
                                            groupUploadedBytes = written
                                            calculateSpeed(written)
                                            _progress.update { curr ->
                                                curr.copy(
                                                    fileBytesUploaded = written,
                                                    fileBytesTotal = if (total > 0) total else singleFile.sizeBytes,
                                                    totalBytesUploaded = cumulativeBytesUploaded + written,
                                                    uploadSpeedBytesPerSec = currentSpeedBps
                                                )
                                            }
                                        },
                                        isCancelled = { isCancelledFlag.get() },
                                        isSkipped = { isSkippedFlag.get() }
                                    )
                                }
                            } catch (e: SkipException) {
                                Result.failure(e)
                            } catch (e: Exception) {
                                Result.failure(e)
                            }

                            // Evaluate result
                            if (result.isSuccess) {
                                currentFileCounter += groupFiles.size
                                successCount += groupFiles.size
                                cumulativeBytesUploaded += groupTotalBytes

                                for (file in groupFiles) {
                                    var deleted = false
                                    if (settings.autoDeleteLocal) {
                                        deleted = FileUtils.deleteFile(context, file.uri)
                                        if (deleted) {
                                            addLog(
                                                LogLevel.INFO,
                                                "🗑️ Arquivo local apagado: ${file.name}",
                                                username = batch.username,
                                                fileName = file.name
                                            )
                                        }
                                    }

                                    uploadRepository.insertRecord(
                                        UploadRecordEntity(
                                            sessionId = sessionId,
                                            fileName = file.name,
                                            uriString = file.uri.toString(),
                                            username = batch.username,
                                            sizeBytes = file.sizeBytes,
                                            mediaType = file.mediaType.name,
                                            lotNumber = lot.lotNumber,
                                            totalLots = lot.totalLots,
                                            groupIndex = groupIndex + 1,
                                            status = "SUCCESS",
                                            localDeleted = deleted
                                        )
                                    )
                                }

                                addLog(
                                    LogLevel.SUCCESS,
                                    "✓ Enviado (${groupFiles.size} itens) para @${batch.username} [Lote ${lot.lotNumber}]",
                                    username = batch.username,
                                    fileName = primaryFileName
                                )
                            } else {
                                val exception = result.exceptionOrNull()
                                val isSkip = isSkippedFlag.get() || exception is SkipException
                                val isCancel = isCancelledFlag.get() || exception is CancellationException

                                if (isCancel) {
                                    break
                                } else if (isSkip) {
                                    currentFileCounter += groupFiles.size
                                    skippedCount += groupFiles.size
                                    for (file in groupFiles) {
                                        uploadRepository.insertRecord(
                                            UploadRecordEntity(
                                                sessionId = sessionId,
                                                fileName = file.name,
                                                uriString = file.uri.toString(),
                                                username = batch.username,
                                                sizeBytes = file.sizeBytes,
                                                mediaType = file.mediaType.name,
                                                lotNumber = lot.lotNumber,
                                                totalLots = lot.totalLots,
                                                groupIndex = groupIndex + 1,
                                                status = "SKIPPED",
                                                errorMessage = "Pulado pelo usuário"
                                            )
                                        )
                                    }
                                    addLog(
                                        LogLevel.SKIP,
                                        "⏭ Arquivo(s) pulado(s) pelo usuário: $primaryFileName",
                                        username = batch.username,
                                        fileName = primaryFileName
                                    )
                                } else if (groupFiles.size > 1) {
                                    // Fallback: the album failed, so try sending each file individually
                                    // so one large/problematic file does not fail the whole group or lot!
                                    addLog(
                                        LogLevel.WARNING,
                                        "Álbum de ${groupFiles.size} itens rejeitado (${exception?.message}). Enviando itens individualmente para não perder o lote...",
                                        username = batch.username
                                    )

                                    for (singleFile in groupFiles) {
                                        if (isCancelledFlag.get()) break
                                        if (isSkippedFlag.get()) {
                                            currentFileCounter++
                                            skippedCount++
                                            uploadRepository.insertRecord(
                                                UploadRecordEntity(
                                                    sessionId = sessionId,
                                                    fileName = singleFile.name,
                                                    uriString = singleFile.uri.toString(),
                                                    username = batch.username,
                                                    sizeBytes = singleFile.sizeBytes,
                                                    mediaType = singleFile.mediaType.name,
                                                    lotNumber = lot.lotNumber,
                                                    totalLots = lot.totalLots,
                                                    groupIndex = groupIndex + 1,
                                                    status = "SKIPPED",
                                                    errorMessage = "Pulado pelo usuário"
                                                )
                                            )
                                            continue
                                        }

                                        if (singleFile.sizeBytes > maxAllowedBytes) {
                                            currentFileCounter++
                                            skippedCount++
                                            val sStr = InstagramParser.formatFileSize(singleFile.sizeBytes)
                                            uploadRepository.insertRecord(
                                                UploadRecordEntity(
                                                    sessionId = sessionId,
                                                    fileName = singleFile.name,
                                                    uriString = singleFile.uri.toString(),
                                                    username = batch.username,
                                                    sizeBytes = singleFile.sizeBytes,
                                                    mediaType = singleFile.mediaType.name,
                                                    lotNumber = lot.lotNumber,
                                                    totalLots = lot.totalLots,
                                                    groupIndex = groupIndex + 1,
                                                    status = "SKIPPED",
                                                    errorMessage = "Excede limite de 50MB do Telegram Bot API ($sStr)"
                                                )
                                            )
                                            addLog(LogLevel.SKIP, "⏭ '${singleFile.name}' ($sStr) pulado: excede 50MB.", username = batch.username, fileName = singleFile.name)
                                            continue
                                        }

                                        val singleCaption = InstagramParser.buildCaption(
                                            username = batch.username,
                                            fileName = singleFile.name,
                                            lotNumber = lot.lotNumber,
                                            totalLots = lot.totalLots,
                                            includeLink = settings.includeInstagramLink
                                        )

                                        val singleResult = telegramRepository.sendSingleMedia(
                                            context = context,
                                            botToken = settings.botToken,
                                            chatId = settings.uploadDestination,
                                            file = singleFile,
                                            caption = singleCaption,
                                            baseUrl = settings.botApiBaseUrl,
                                            onProgress = { written, _ ->
                                                calculateSpeed(written)
                                                _progress.update { curr ->
                                                    curr.copy(
                                                        fileBytesUploaded = written,
                                                        fileBytesTotal = singleFile.sizeBytes,
                                                        uploadSpeedBytesPerSec = currentSpeedBps
                                                    )
                                                }
                                            },
                                            isCancelled = { isCancelledFlag.get() },
                                            isSkipped = { isSkippedFlag.get() }
                                        )

                                        currentFileCounter++
                                        if (singleResult.isSuccess) {
                                            successCount++
                                            cumulativeBytesUploaded += singleFile.sizeBytes
                                            var deleted = false
                                            if (settings.autoDeleteLocal) {
                                                deleted = FileUtils.deleteFile(context, singleFile.uri)
                                            }
                                            uploadRepository.insertRecord(
                                                UploadRecordEntity(
                                                    sessionId = sessionId,
                                                    fileName = singleFile.name,
                                                    uriString = singleFile.uri.toString(),
                                                    username = batch.username,
                                                    sizeBytes = singleFile.sizeBytes,
                                                    mediaType = singleFile.mediaType.name,
                                                    lotNumber = lot.lotNumber,
                                                    totalLots = lot.totalLots,
                                                    groupIndex = groupIndex + 1,
                                                    status = "SUCCESS",
                                                    localDeleted = deleted
                                                )
                                            )
                                            addLog(LogLevel.SUCCESS, "✓ Enviado individualmente: ${singleFile.name}", username = batch.username, fileName = singleFile.name)
                                        } else {
                                            val singleEx = singleResult.exceptionOrNull()
                                            val isSingleSkip = isSkippedFlag.get() || singleEx is SkipException
                                            val isSizeErr = singleEx?.message?.contains("too big", ignoreCase = true) == true ||
                                                    singleEx?.message?.contains("Too Large", ignoreCase = true) == true

                                            if (isSingleSkip || isSizeErr) {
                                                skippedCount++
                                                val skipMsg = if (isSingleSkip) "Pulado pelo usuário" else "Excede limite de 50MB do Telegram"
                                                uploadRepository.insertRecord(
                                                    UploadRecordEntity(
                                                        sessionId = sessionId,
                                                        fileName = singleFile.name,
                                                        uriString = singleFile.uri.toString(),
                                                        username = batch.username,
                                                        sizeBytes = singleFile.sizeBytes,
                                                        mediaType = singleFile.mediaType.name,
                                                        lotNumber = lot.lotNumber,
                                                        totalLots = lot.totalLots,
                                                        groupIndex = groupIndex + 1,
                                                        status = "SKIPPED",
                                                        errorMessage = skipMsg
                                                    )
                                                )
                                                addLog(LogLevel.SKIP, "⏭ ${singleFile.name} pulado: $skipMsg", username = batch.username, fileName = singleFile.name)
                                            } else {
                                                failedCount++
                                                uploadRepository.insertRecord(
                                                    UploadRecordEntity(
                                                        sessionId = sessionId,
                                                        fileName = singleFile.name,
                                                        uriString = singleFile.uri.toString(),
                                                        username = batch.username,
                                                        sizeBytes = singleFile.sizeBytes,
                                                        mediaType = singleFile.mediaType.name,
                                                        lotNumber = lot.lotNumber,
                                                        totalLots = lot.totalLots,
                                                        groupIndex = groupIndex + 1,
                                                        status = "FAILED",
                                                        errorMessage = singleEx?.message ?: "Falha no envio"
                                                    )
                                                )
                                                addLog(LogLevel.ERROR, "✗ Falha ao enviar ${singleFile.name}: ${singleEx?.message}", username = batch.username, fileName = singleFile.name)
                                            }
                                        }
                                        delay(800)
                                    }
                                } else {
                                    // Single file failed
                                    currentFileCounter += groupFiles.size
                                    val err = exception?.message ?: "Erro desconhecido"
                                    val isSizeErr = err.contains("too big", ignoreCase = true) || err.contains("Too Large", ignoreCase = true)

                                    if (isSizeErr) {
                                        skippedCount += groupFiles.size
                                        for (file in groupFiles) {
                                            uploadRepository.insertRecord(
                                                UploadRecordEntity(
                                                    sessionId = sessionId,
                                                    fileName = file.name,
                                                    uriString = file.uri.toString(),
                                                    username = batch.username,
                                                    sizeBytes = file.sizeBytes,
                                                    mediaType = file.mediaType.name,
                                                    lotNumber = lot.lotNumber,
                                                    totalLots = lot.totalLots,
                                                    groupIndex = groupIndex + 1,
                                                    status = "SKIPPED",
                                                    errorMessage = "Excede limite de 50MB do Telegram"
                                                )
                                            )
                                        }
                                        addLog(
                                            LogLevel.SKIP,
                                            "⏭ Arquivo $primaryFileName pulado: excede o limite de 50MB do Telegram.",
                                            username = batch.username,
                                            fileName = primaryFileName
                                        )
                                    } else {
                                        failedCount += groupFiles.size
                                        for (file in groupFiles) {
                                            uploadRepository.insertRecord(
                                                UploadRecordEntity(
                                                    sessionId = sessionId,
                                                    fileName = file.name,
                                                    uriString = file.uri.toString(),
                                                    username = batch.username,
                                                    sizeBytes = file.sizeBytes,
                                                    mediaType = file.mediaType.name,
                                                    lotNumber = lot.lotNumber,
                                                    totalLots = lot.totalLots,
                                                    groupIndex = groupIndex + 1,
                                                    status = "FAILED",
                                                    errorMessage = err
                                                )
                                            )
                                        }

                                        addLog(
                                            LogLevel.ERROR,
                                            "✗ Falha ao enviar $primaryFileName: $err",
                                            username = batch.username,
                                            fileName = primaryFileName
                                        )
                                    }
                                }
                            }

                            // Slight delay between Telegram requests to avoid hitting rate limits
                            delay(1200)
                        }
                    }
                }

                val finalStatus = if (isCancelledFlag.get()) "CANCELLED" else "COMPLETED"
                uploadRepository.updateSession(
                    UploadSessionEntity(
                        sessionId = sessionId,
                        folderName = folderName,
                        totalFiles = totalFiles,
                        successCount = successCount,
                        skippedCount = skippedCount,
                        failedCount = failedCount,
                        totalBytes = totalBytes,
                        autoDeleteEnabled = settings.autoDeleteLocal,
                        destination = settings.uploadDestination,
                        status = finalStatus,
                        startTime = System.currentTimeMillis(),
                        endTime = System.currentTimeMillis()
                    )
                )

                _progress.update {
                    it.copy(
                        isRunning = false,
                        isPaused = false,
                        currentFileIndex = currentFileCounter,
                        currentStatus = if (isCancelledFlag.get()) UploadStatus.FAILED else UploadStatus.SUCCESS,
                        statusMessage = if (isCancelledFlag.get()) "Processo cancelado pelo usuário." else "Transferência concluída!"
                    )
                }

                addLog(
                    LogLevel.INFO,
                    "Processo finalizado. Enviados: $successCount, Pulados: $skippedCount, Falhos: $failedCount."
                )

            } catch (e: CancellationException) {
                _progress.update {
                    it.copy(
                        isRunning = false,
                        isPaused = false,
                        statusMessage = "Transferência cancelada."
                    )
                }
                addLog(LogLevel.WARNING, "Transferência cancelada pelo usuário.")
            } catch (e: Exception) {
                Log.e("UploadManager", "Erro fatal na transferência", e)
                _progress.update {
                    it.copy(
                        isRunning = false,
                        isPaused = false,
                        statusMessage = "Erro fatal: ${e.message}"
                    )
                }
                addLog(LogLevel.ERROR, "Erro fatal: ${e.message}")
            }
        }
    }

    fun skipCurrent() {
        if (!_progress.value.isRunning) return
        isSkippedFlag.set(true)
        telegramRepository.cancelActiveCall()
        addLog(LogLevel.SKIP, "Solicitação para pular item atual enviada...")
    }

    fun cancelUpload() {
        if (!_progress.value.isRunning) return
        isCancelledFlag.set(true)
        telegramRepository.cancelActiveCall()
        uploadJob?.cancel()
        _progress.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                statusMessage = "Cancelado."
            )
        }
        addLog(LogLevel.WARNING, "Cancelamento solicitado.")
    }

    fun togglePause() {
        val next = !isPausedFlag.get()
        isPausedFlag.set(next)
        _progress.update {
            it.copy(
                isPaused = next,
                statusMessage = if (next) "Transferência pausada." else "Retomando transferência..."
            )
        }
        addLog(LogLevel.INFO, if (next) "Transferência pausada." else "Transferência retomada.")
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun addLog(level: LogLevel, message: String, username: String? = null, fileName: String? = null) {
        val entry = UploadLogEntry(
            level = level,
            message = message,
            username = username,
            fileName = fileName
        )
        _logs.update { (listOf(entry) + it).take(200) }
    }

    private fun calculateSpeed(bytesWritten: Long) {
        val now = System.currentTimeMillis()
        val dt = now - lastSpeedCheckTime
        if (dt >= 800) {
            val db = bytesWritten - lastBytesCount
            if (db > 0 && dt > 0) {
                currentSpeedBps = (db * 1000L) / dt
            }
            lastSpeedCheckTime = now
            lastBytesCount = bytesWritten
        }
    }
}
