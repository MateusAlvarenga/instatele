package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TelegramBlue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val botTestState by viewModel.botTestState.collectAsStateWithLifecycle()
    val destTestState by viewModel.destTestState.collectAsStateWithLifecycle()

    var apiId by remember(settings.apiId) { mutableStateOf(settings.apiId) }
    var apiHash by remember(settings.apiHash) { mutableStateOf(settings.apiHash) }
    var botToken by remember(settings.botToken) { mutableStateOf(settings.botToken) }
    var destination by remember(settings.uploadDestination) { mutableStateOf(settings.uploadDestination) }
    var botApiBaseUrl by remember(settings.botApiBaseUrl) { mutableStateOf(settings.botApiBaseUrl) }

    var itemsPerLot by remember(settings.itemsPerLot) { mutableIntStateOf(settings.itemsPerLot) }
    var itemsPerAlbum by remember(settings.itemsPerAlbum) { mutableFloatStateOf(settings.itemsPerAlbum.toFloat()) }
    var sendLotHeaders by remember(settings.sendLotHeaders) { mutableStateOf(settings.sendLotHeaders) }
    var includeIgLink by remember(settings.includeInstagramLink) { mutableStateOf(settings.includeInstagramLink) }
    var autoDelete by remember(settings.autoDeleteLocal) { mutableStateOf(settings.autoDeleteLocal) }

    var showTokenPassword by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações do Telegram", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("config_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveCredentials(apiId, apiHash, botToken, destination, botApiBaseUrl)
                            viewModel.updateBatchOptions(itemsPerLot, itemsPerAlbum.toInt(), sendLotHeaders, includeIgLink)
                            viewModel.updateAutoDelete(autoDelete)
                            scope.launch {
                                snackbarHostState.showSnackbar("Configurações salvas com sucesso!")
                            }
                        },
                        modifier = Modifier.testTag("save_config_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = "Salvar",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Telegram Bot & Authentication Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SmartToy,
                            contentDescription = null,
                            tint = TelegramBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Credenciais do Telegram",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Informe o Token do Bot criado no @BotFather e o destino (Canal ou Grupo) onde o bot seja administrador.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.8f)
                    )

                    // Bot Token
                    OutlinedTextField(
                        value = botToken,
                        onValueChange = { botToken = it },
                        label = { Text("Token do Bot (Obrigatório)") },
                        placeholder = { Text("123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bot_token_input"),
                        singleLine = true,
                        visualTransformation = if (showTokenPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showTokenPassword = !showTokenPassword }) {
                                Icon(
                                    imageVector = Icons.Filled.Key,
                                    contentDescription = "Mostrar/Ocultar Token"
                                )
                            }
                        }
                    )

                    // Test Bot Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.testBot(botToken, botApiBaseUrl)
                            },
                            enabled = botToken.isNotBlank() && !botTestState.isLoading,
                            modifier = Modifier.testTag("test_bot_button")
                        ) {
                            if (botTestState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Testar Token do Bot")
                        }
                    }

                    // Bot Test Feedback
                    if (botTestState.message.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (botTestState.isSuccess == true) StatusSuccess.copy(alpha = 0.15f)
                                    else StatusError.copy(alpha = 0.15f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = if (botTestState.isSuccess == true) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                contentDescription = null,
                                tint = if (botTestState.isSuccess == true) StatusSuccess else StatusError,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = botTestState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (botTestState.isSuccess == true) StatusSuccess else StatusError,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    HorizontalDivider()

                    // Destino do Upload
                    OutlinedTextField(
                        value = destination,
                        onValueChange = { destination = it },
                        label = { Text("Destino do Upload (Chat ID ou Canal)") },
                        placeholder = { Text("@meu_canal_arquivos ou -1001234567890") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_destination_input"),
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.Send, contentDescription = null)
                        }
                    )

                    // Test Destination Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.testDestination(botToken, destination, botApiBaseUrl)
                            },
                            enabled = botToken.isNotBlank() && destination.isNotBlank() && !destTestState.isLoading,
                            modifier = Modifier.testTag("test_destination_button")
                        ) {
                            if (destTestState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Verificar Destino")
                        }
                    }

                    // Destination Test Feedback
                    if (destTestState.message.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (destTestState.isSuccess == true) StatusSuccess.copy(alpha = 0.15f)
                                    else StatusError.copy(alpha = 0.15f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = if (destTestState.isSuccess == true) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                contentDescription = null,
                                tint = if (destTestState.isSuccess == true) StatusSuccess else StatusError,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = destTestState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (destTestState.isSuccess == true) StatusSuccess else StatusError,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    HorizontalDivider()

                    // Optional API ID & API HASH (Telethon / Telegram Core)
                    Text(
                        text = "Configurações MTProto / Telethon (Opcional)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = apiId,
                        onValueChange = { apiId = it },
                        label = { Text("API ID (my.telegram.org)") },
                        placeholder = { Text("Ex: 12345678") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_id_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = apiHash,
                        onValueChange = { apiHash = it },
                        label = { Text("API HASH (my.telegram.org)") },
                        placeholder = { Text("Ex: 9f8e7d6c5b4a3f2e1d0c...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_hash_input"),
                        singleLine = true
                    )

                    // Custom Bot API Server
                    OutlinedTextField(
                        value = botApiBaseUrl,
                        onValueChange = { botApiBaseUrl = it },
                        label = { Text("Servidor Bot API (Padrão: Telegram Cloud)") },
                        placeholder = { Text("https://api.telegram.org") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bot_api_url_input"),
                        singleLine = true
                    )
                    Text(
                        text = "Dica: Em servidores de Bot API locais, arquivos de até 2GB podem ser enviados sem a restrição de 50MB.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.7f)
                    )
                }
            }

            // Organization & Captions Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Agrupamento & Organização de Lotes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Items per lot (Batch of 100)
                    Text(
                        text = "Arquivos por Lote: $itemsPerLot itens",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Agrupa mensagens do mesmo perfil em lotes de 100 itens para organização e controle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.7f)
                    )

                    // Items per Telegram Album (up to 10 files)
                    Column {
                        Text(
                            text = "Limite de mídias por mensagem (Álbum): ${itemsPerAlbum.toInt()} arquivos",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = itemsPerAlbum,
                            onValueChange = { itemsPerAlbum = it },
                            valueRange = 2f..10f,
                            steps = 7,
                            modifier = Modifier.testTag("items_per_album_slider")
                        )
                        Text(
                            text = "O Telegram permite até 10 fotos/vídeos agrupados por postagem.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.7f)
                        )
                    }

                    HorizontalDivider()

                    // Send Lot Headers Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enviar cabeçalho de Lote",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Envia uma mensagem no canal identificando o início de cada lote de 100 itens.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = sendLotHeaders,
                            onCheckedChange = { sendLotHeaders = it },
                            modifier = Modifier.testTag("send_lot_headers_switch")
                        )
                    }

                    // Include Instagram link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Incluir Link direto do Instagram",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Adiciona o link https://instagram.com/usuario na caption.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = includeIgLink,
                            onCheckedChange = { includeIgLink = it },
                            modifier = Modifier.testTag("include_ig_link_switch")
                        )
                    }

                    // Auto-delete local files
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Autodelete: Excluir arquivo local",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (autoDelete) StatusError else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Exclui o arquivo do celular imediatamente após confirmação de upload.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.typography.bodySmall.color.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = autoDelete,
                            onCheckedChange = { autoDelete = it },
                            modifier = Modifier.testTag("auto_delete_switch")
                        )
                    }
                }
            }

            // Save Button
            Button(
                onClick = {
                    viewModel.saveCredentials(apiId, apiHash, botToken, destination, botApiBaseUrl)
                    viewModel.updateBatchOptions(itemsPerLot, itemsPerAlbum.toInt(), sendLotHeaders, includeIgLink)
                    viewModel.updateAutoDelete(autoDelete)
                    scope.launch {
                        snackbarHostState.showSnackbar("Configurações salvas com sucesso!")
                    }
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_config_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Salvar Configurações", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
