package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.UploadRecordEntity
import com.example.data.local.UploadSessionEntity
import com.example.ui.MainViewModel
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSkip
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TelegramBlue
import com.example.util.InstagramParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()
    val totalSuccessCount by viewModel.totalSuccessCount.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Limpar Histórico") },
            text = { Text("Tem certeza que deseja apagar todos os registros de sessões e uploads passados?") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        viewModel.clearHistory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Limpar Tudo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico de Envios", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("history_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (allSessions.isNotEmpty() || allRecords.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(imageVector = Icons.Filled.DeleteSweep, contentDescription = "Limpar Histórico")
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
        ) {
            // Stats summary card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${allSessions.size}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = TelegramBlue
                        )
                        Text("Sessões", style = MaterialTheme.typography.bodySmall)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalSuccessCount",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = StatusSuccess
                        )
                        Text("Enviados", style = MaterialTheme.typography.bodySmall)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val totalDeleted = allRecords.count { it.localDeleted }
                        Text(
                            text = "$totalDeleted",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Locais Excluídos", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Arquivos Recentes (${allRecords.size})") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Sessões (${allSessions.size})") }
                )
            }

            if (selectedTabIndex == 0) {
                if (allRecords.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Nenhum arquivo enviado ainda.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allRecords, key = { it.id }) { record ->
                            RecordItemCard(record)
                        }
                    }
                }
            } else {
                if (allSessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Nenhuma sessão de arquivamento gravada.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(allSessions, key = { it.sessionId }) { session ->
                            SessionItemCard(session)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordItemCard(record: UploadRecordEntity) {
    val (statusColor, statusIcon, statusLabel) = when (record.status) {
        "SUCCESS" -> Triple(StatusSuccess, Icons.Filled.CheckCircle, "Enviado")
        "SKIPPED" -> Triple(StatusSkip, Icons.Filled.SkipNext, "Pulado")
        else -> Triple(StatusError, Icons.Filled.Error, "Falha")
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.fileName,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "@${record.username}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TelegramBlue
                    )
                    Text(
                        text = "• Lote ${record.lotNumber}/${record.totalLots}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• ${InstagramParser.formatFileSize(record.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (record.localDeleted) {
                    Text(
                        text = "🗑️ Arquivo local apagado pelo Autodelete",
                        fontSize = 11.sp,
                        color = StatusSuccess
                    )
                }

                if (!record.errorMessage.isNullOrBlank()) {
                    Text(
                        text = "Erro: ${record.errorMessage}",
                        fontSize = 11.sp,
                        color = StatusError
                    )
                }
            }
        }
    }
}

@Composable
fun SessionItemCard(session: UploadSessionEntity) {
    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(session.startTime))

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.folderName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (session.status == "COMPLETED") StatusSuccess.copy(alpha = 0.15f) else StatusError.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (session.status == "COMPLETED") "CONCLUÍDO" else session.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (session.status == "COMPLETED") StatusSuccess else StatusError,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = "Destino: ${session.destination} • Data: $dateStr",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.7f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("✓ ${session.successCount} enviados", fontSize = 12.sp, color = StatusSuccess)
                Text("⏭ ${session.skippedCount} pulados", fontSize = 12.sp, color = StatusSkip)
                Text("✗ ${session.failedCount} falhas", fontSize = 12.sp, color = StatusError)
                Text("💾 ${InstagramParser.formatFileSize(session.totalBytes)}", fontSize = 12.sp)
            }
        }
    }
}
