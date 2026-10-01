package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LogLevel
import com.example.data.model.UploadLogEntry
import com.example.data.model.UploadMode
import com.example.data.model.UploadStatus
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.InstagramPink
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSkip
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TelegramBlue
import com.example.util.InstagramParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    BackHandler { onBack() }

    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var selectedLogFilter by remember { mutableStateOf<LogLevel?>(null) }

    val filteredLogs = remember(logs, selectedLogFilter) {
        if (selectedLogFilter == null) logs
        else logs.filter { it.level == selectedLogFilter }
    }

    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            title = { Text("Cancelar Arquivamento?") },
            text = { Text("Tem certeza que deseja interromper a transferência? Os arquivos já enviados permanecerão no Telegram.") },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirmDialog = false
                        viewModel.cancelTransfer()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Sim, Cancelar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmDialog = false }) {
                    Text("Continuar Enviando")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (progress.isRunning) "Transferindo para Telegram" else "Status do Arquivamento",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                        if (settings.activeDestination.isNotBlank()) {
                            val modeText = if (settings.uploadMode == UploadMode.USER_ACCOUNT) "Conta" else "Bot"
                            Text(
                                text = "Modo $modeText • Destino: ${settings.activeDestination}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("transfer_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (progress.isRunning) {
                        IconButton(
                            onClick = { showCancelConfirmDialog = true },
                            modifier = Modifier.testTag("transfer_cancel_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancelar Transferência",
                                tint = StatusError
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Main Active Item Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (progress.isRunning) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Profile & Lot Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(InstagramPink),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = progress.currentUsername.take(1).uppercase().ifEmpty { "IG" },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Column {
                                Text(
                                    text = if (progress.currentUsername.isNotBlank()) "@${progress.currentUsername}" else "Aguardando lote...",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (progress.totalLots > 0) {
                                    Text(
                                        text = "Lote ${progress.currentLotIndex} de ${progress.totalLots} (100 itens/lote)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        // Status Badge
                        val badgeColor = when {
                            progress.isPaused -> StatusWarning
                            progress.isRunning -> TelegramBlue
                            progress.currentStatus == UploadStatus.SUCCESS -> StatusSuccess
                            else -> MaterialTheme.colorScheme.outline
                        }
                        val badgeText = when {
                            progress.isPaused -> "PAUSADO"
                            progress.isRunning -> "ENVIANDO"
                            progress.currentStatus == UploadStatus.SUCCESS -> "CONCLUÍDO"
                            else -> "PARADO"
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = badgeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = badgeText,
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Active File name
                    if (progress.currentFileName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Upload,
                                contentDescription = null,
                                tint = TelegramBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = progress.currentFileName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // File upload progress bar
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Progresso da Mídia",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "${InstagramParser.formatFileSize(progress.fileBytesUploaded)} / ${InstagramParser.formatFileSize(progress.fileBytesTotal)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress.fileProgressPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = TelegramBlue
                        )
                    }

                    // Overall session progress bar
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Progresso Geral (${progress.currentFileIndex}/${progress.totalFiles} arquivos)",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "%.0f%%".format(progress.overallProgressPercent * 100),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress.overallProgressPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = InstagramPink
                        )

                        // Live Counters: Sent, Skipped (>50MB or user), Failed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✓ ${progress.totalSuccess} enviados",
                                fontSize = 12.sp,
                                color = StatusSuccess,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "⏭ ${progress.totalSkipped} pulados",
                                fontSize = 12.sp,
                                color = StatusSkip,
                                fontWeight = FontWeight.Bold
                            )
                            if (progress.totalFailed > 0) {
                                Text(
                                    text = "✗ ${progress.totalFailed} falhas",
                                    fontSize = 12.sp,
                                    color = StatusError,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Speed & stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "${InstagramParser.formatFileSize(progress.uploadSpeedBytesPerSec)}/s",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (settings.autoDeleteLocal) {
                            Text(
                                text = "🗑️ Autodelete ativado",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusWarning,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Real-Time Control Actions: SKIP, PAUSE, CANCEL
            AnimatedVisibility(visible = progress.isRunning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // PULAR (Skip) Button
                    Button(
                        onClick = { viewModel.skipCurrentFile() },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusSkip),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("skip_file_button")
                    ) {
                        Icon(imageVector = Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pular Item", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // PAUSE / RESUME Button
                    OutlinedButton(
                        onClick = { viewModel.togglePauseTransfer() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pause_resume_button")
                    ) {
                        Icon(
                            imageVector = if (progress.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (progress.isPaused) "Continuar" else "Pausar",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // CANCEL Button
                    Button(
                        onClick = { showCancelConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("cancel_upload_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancelar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Done Summary Card (when finished)
            AnimatedVisibility(visible = !progress.isRunning && progress.currentFileIndex > 0) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = StatusSuccess,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = progress.statusMessage,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Arquivos processados: ${progress.currentFileIndex} de ${progress.totalFiles}.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = onNavigateToHistory,
                                modifier = Modifier.testTag("view_history_button")
                            ) {
                                Text("Ver Relatório Completo")
                            }
                        }
                    }
                }
            }

            // Real-Time Log Section Header & Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Log em Tempo Real (${logs.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                if (logs.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearLogs() },
                        modifier = Modifier.size(28.dp).testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "Limpar Logs",
                            tint = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Filter Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = selectedLogFilter == null,
                    onClick = { selectedLogFilter = null },
                    label = { Text("Todos", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedLogFilter == LogLevel.SUCCESS,
                    onClick = { selectedLogFilter = if (selectedLogFilter == LogLevel.SUCCESS) null else LogLevel.SUCCESS },
                    label = { Text("Sucessos", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedLogFilter == LogLevel.SKIP,
                    onClick = { selectedLogFilter = if (selectedLogFilter == LogLevel.SKIP) null else LogLevel.SKIP },
                    label = { Text("Pulados", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedLogFilter == LogLevel.ERROR,
                    onClick = { selectedLogFilter = if (selectedLogFilter == LogLevel.ERROR) null else LogLevel.ERROR },
                    label = { Text("Erros", fontSize = 11.sp) }
                )
            }

            // Terminal Log List
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhum log registrado ainda.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { log ->
                            LogEntryRow(log)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding().height(36.dp))
        }
    }
}

@Composable
fun LogEntryRow(log: UploadLogEntry) {
    val (badgeBg, badgeTextColor, label) = when (log.level) {
        LogLevel.SUCCESS -> Triple(StatusSuccess.copy(alpha = 0.15f), StatusSuccess, "OK")
        LogLevel.SKIP -> Triple(StatusSkip.copy(alpha = 0.15f), StatusSkip, "PULO")
        LogLevel.ERROR -> Triple(StatusError.copy(alpha = 0.15f), StatusError, "ERRO")
        LogLevel.WARNING -> Triple(StatusWarning.copy(alpha = 0.15f), StatusWarning, "AVISO")
        LogLevel.INFO -> Triple(TelegramBlue.copy(alpha = 0.15f), TelegramBlue, "INFO")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = log.timeFormatted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 2.dp)
        )

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = badgeBg
        ) {
            Text(
                text = label,
                color = badgeTextColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        Text(
            text = log.message,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
