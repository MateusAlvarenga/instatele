package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.InstagramProfileBatch
import com.example.data.model.MediaType
import com.example.ui.MainViewModel
import com.example.ui.theme.InstagramOrange
import com.example.ui.theme.InstagramPink
import com.example.ui.theme.InstagramPurple
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TelegramBlue
import com.example.util.InstagramParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToConfig: () -> Unit,
    onNavigateToTransfer: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scannedBatches by viewModel.scannedBatches.collectAsStateWithLifecycle()
    val selectedFolderName by viewModel.selectedFolderName.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    val totalFilesCount = remember(scannedBatches) { scannedBatches.sumOf { it.allFiles.size } }
    val totalLotsCount = remember(scannedBatches) { scannedBatches.sumOf { it.lots.size } }
    val totalBytesCount = remember(scannedBatches) { scannedBatches.sumOf { it.allFiles.sumOf { f -> f.sizeBytes } } }
    val totalOversizedCount = remember(scannedBatches) { scannedBatches.sumOf { it.oversizedFilesCount } }
    val totalValidCount = remember(scannedBatches) { scannedBatches.sumOf { it.validFilesCount } }

    val isTelegramReady = settings.botToken.isNotBlank() && settings.uploadDestination.isNotBlank()

    // SAF Folder Picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if not supported by provider
            }
            val displayName = uri.lastPathSegment?.substringAfterLast(':') ?: "Pasta Selecionada"
            viewModel.onFolderSelected(uri, displayName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(TelegramBlue, InstagramPink, InstagramPurple)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "InstaTele Archiver",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.testTag("history_nav_button")
                    ) {
                        Icon(imageVector = Icons.Filled.History, contentDescription = "Histórico")
                    }
                    IconButton(
                        onClick = onNavigateToConfig,
                        modifier = Modifier.testTag("config_nav_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Settings, contentDescription = "Configurações")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Start Archiving Bottom Action Card
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (progress.isRunning) {
                        Button(
                            onClick = onNavigateToTransfer,
                            colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("view_active_transfer_button")
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Acompanhar Transferência Ativa", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                if (isTelegramReady) {
                                    val started = viewModel.startArchiving()
                                    if (started) onNavigateToTransfer()
                                } else {
                                    onNavigateToConfig()
                                }
                            },
                            enabled = if (!isTelegramReady) true else totalValidCount > 0,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTelegramReady) TelegramBlue else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("start_archiving_button")
                        ) {
                            Icon(
                                imageVector = if (isTelegramReady) Icons.Filled.PlayArrow else Icons.Filled.Settings,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    !isTelegramReady -> "Configurar Telegram para Iniciar"
                                    totalValidCount == 0 && totalOversizedCount > 0 -> "Todos os $totalOversizedCount arquivos excedem 50 MB"
                                    totalOversizedCount > 0 -> "Iniciar ($totalValidCount prontos • $totalOversizedCount > 50MB pulados)"
                                    else -> "Iniciar Arquivamento ($totalValidCount itens • $totalLotsCount lotes)"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Telegram Destination Banner (or Warning if not configured)
            item {
                if (!isTelegramReady) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = StatusWarning.copy(alpha = 0.12f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToConfig() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = StatusWarning,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Telegram não configurado",
                                    fontWeight = FontWeight.Bold,
                                    color = StatusWarning,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Toque aqui para definir o Token do Bot e o Canal/Chat de destino antes de enviar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = StatusSuccess,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Destino configurado",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = StatusSuccess
                                )
                                Text(
                                    text = settings.uploadDestination,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            TextButton(onClick = onNavigateToConfig) {
                                Text("Alterar", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Folder Selection Section
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = null,
                                tint = TelegramBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Pasta de Origem",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (selectedFolderName.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = TelegramBlue
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedFolderName,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${scannedBatches.size} perfis detectados • $totalFilesCount mídias (${InstagramParser.formatFileSize(totalBytesCount)})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // Folder Picker Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { folderPickerLauncher.launch(null) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("choose_folder_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Filled.FolderOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (selectedFolderName.isBlank()) "Escolher Pasta" else "Trocar Pasta")
                            }

                            OutlinedButton(
                                onClick = { viewModel.loadSampleDemoFiles() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("load_demo_button")
                            ) {
                                Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Carregar Demo")
                            }
                        }

                        if (isScanning) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Escaneando arquivos da pasta e agrupando por perfil...", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Pre-Transfer Options Card (Autodelete toggle, lot rules)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Opções do Arquivamento",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        // Autodelete Option
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = if (settings.autoDeleteLocal) StatusWarning else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Autodelete após envio com sucesso",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "Remove o arquivo da memória do telefone logo após confirmação de entrega no Telegram.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.7f)
                                )
                            }
                            Switch(
                                checked = settings.autoDeleteLocal,
                                onCheckedChange = { viewModel.updateAutoDelete(it) },
                                modifier = Modifier.testTag("home_autodelete_switch")
                            )
                        }

                        HorizontalDivider()

                        // Batch info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Layers,
                                    contentDescription = null,
                                    tint = TelegramBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Regra de Lotes: ${settings.itemsPerLot} itens por lote",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "Álbuns de até ${settings.itemsPerAlbum} fotos/vídeos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Summary stats row
            if (scannedBatches.isNotEmpty()) {
                // Warning banner for oversized files > 50MB
                if (totalOversizedCount > 0) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = StatusWarning.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = StatusWarning,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "$totalOversizedCount arquivo(s) > 50 MB detectado(s)",
                                        fontWeight = FontWeight.Bold,
                                        color = StatusWarning,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "O Telegram Bot API não aceita arquivos maiores que 50 MB. O app pulará automaticamente esses arquivos para garantir que o restante do lote seja enviado com sucesso sem falhas.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatTile(
                            title = "Perfis",
                            value = "${scannedBatches.size}",
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            title = "≤ 50 MB",
                            value = "$totalValidCount",
                            modifier = Modifier.weight(1f)
                        )
                        if (totalOversizedCount > 0) {
                            StatTile(
                                title = "> 50 MB (Pula)",
                                value = "$totalOversizedCount",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        StatTile(
                            title = "Lotes",
                            value = "$totalLotsCount",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Text(
                        text = "Perfis Detectados para Arquivamento (${scannedBatches.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // List of profile cards
                items(scannedBatches, key = { it.username }) { batch ->
                    InstagramProfileCard(batch = batch, includeIgLink = settings.includeInstagramLink)
                }
            } else if (!isScanning) {
                // Empty state
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(TelegramBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Collections,
                                    contentDescription = null,
                                    tint = TelegramBlue,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Text(
                                text = "Nenhuma pasta selecionada",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Escolha a pasta com fotos e vídeos do Instagram ou toque em 'Carregar Demo' para testar com arquivos simulados.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun StatTile(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TelegramBlue
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.8f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun InstagramProfileCard(
    batch: InstagramProfileBatch,
    includeIgLink: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Profile Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(InstagramPurple, InstagramPink, InstagramOrange)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = batch.username.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Column {
                        Text(
                            text = "@${batch.username}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtext = if (batch.oversizedFilesCount > 0) {
                            "${batch.validFilesCount} válidos (${batch.oversizedFilesCount} > 50MB) • ${batch.lots.size} lote(s)"
                        } else {
                            "${batch.totalFilesCount} mídias • ${batch.lots.size} lote(s) • ${InstagramParser.formatFileSize(batch.totalSizeBytes)}"
                        }
                        Text(
                            text = subtext,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (batch.oversizedFilesCount > 0) StatusWarning else MaterialTheme.typography.bodySmall.color.copy(alpha = 0.7f)
                        )
                    }
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Expandir"
                    )
                }
            }

            // Expanded Details: Lots & Sample Captions
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider()

                    Text(
                        text = "Divisão em Lotes de 100:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    batch.lots.forEach { lot ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "📦 Lote ${lot.lotNumber} de ${lot.totalLots}",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${lot.files.size} arquivos • ${InstagramParser.formatFileSize(lot.totalSizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.7f)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = TelegramBlue.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "${(lot.files.size + 9) / 10} msgs",
                                        fontSize = 11.sp,
                                        color = TelegramBlue,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Caption Sample
                    val sampleFile = batch.allFiles.firstOrNull()?.name ?: "${batch.username}_sample.mp4"
                    val sampleCaption = InstagramParser.buildCaption(
                        username = batch.username,
                        fileName = sampleFile,
                        lotNumber = 1,
                        totalLots = batch.lots.size,
                        groupItemRange = "1-10/${batch.lots.firstOrNull()?.files?.size ?: 10}",
                        includeLink = includeIgLink
                    )

                    Text(
                        text = "Exemplo da Caption gerada no Telegram:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = sampleCaption.replace("<b>", "").replace("</b>", "")
                                .replace("<code>", "").replace("</code>", "")
                                .replace("<a href=\"", "").replace("\">", " -> ")
                                .replace("</a>", ""),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
