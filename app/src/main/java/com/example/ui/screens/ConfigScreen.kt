package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UploadMode
import com.example.ui.MainViewModel
import com.example.ui.UserLoginStep
import com.example.ui.theme.InstagramPurple
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
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
    val userLoginState by viewModel.userLoginState.collectAsStateWithLifecycle()
    val sessionValidationState by viewModel.sessionValidationState.collectAsStateWithLifecycle()

    var selectedMode by remember(settings.uploadMode) { mutableStateOf(settings.uploadMode) }

    // Bot mode fields
    var botToken by remember(settings.botToken) { mutableStateOf(settings.botToken) }
    var destination by remember(settings.uploadDestination) { mutableStateOf(settings.uploadDestination) }
    var botApiBaseUrl by remember(settings.botApiBaseUrl) { mutableStateOf(settings.botApiBaseUrl) }

    // User Account mode fields
    var apiId by remember(settings.apiId) { mutableStateOf(settings.apiId) }
    var apiHash by remember(settings.apiHash) { mutableStateOf(settings.apiHash) }
    var userPhone by remember(settings.userPhoneNumber) { mutableStateOf(settings.userPhoneNumber) }
    var userDestination by remember(settings.userUploadDestination) { mutableStateOf(settings.userUploadDestination) }
    var stringSessionInput by remember { mutableStateOf("") }
    var showHelpTutorial by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    // General Batch & Caption Options
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
                title = { Text("Configurações de Envio", fontWeight = FontWeight.SemiBold) },
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
                            viewModel.switchUploadMode(selectedMode)
                            viewModel.saveCredentials(apiId, apiHash, botToken, destination, botApiBaseUrl)
                            viewModel.saveUserDestination(userDestination)
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
            // Mode Selector: Bot vs User Account
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Método de Upload para o Telegram",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    TabRow(
                        selectedTabIndex = if (selectedMode == UploadMode.BOT) 0 else 1,
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = selectedMode == UploadMode.BOT,
                            onClick = {
                                selectedMode = UploadMode.BOT
                                viewModel.switchUploadMode(UploadMode.BOT)
                            },
                            text = { Text("🤖 Via Bot (Padrão)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                            icon = { Icon(Icons.Filled.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedMode == UploadMode.USER_ACCOUNT,
                            onClick = {
                                selectedMode = UploadMode.USER_ACCOUNT
                                viewModel.switchUploadMode(UploadMode.USER_ACCOUNT)
                            },
                            text = { Text("👤 Conta Própria (2 GB)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                            icon = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }

            // MODE 1: BOT CONFIGURATION
            if (selectedMode == UploadMode.BOT) {
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
                                text = "Configuração do Bot do Telegram",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "No modo Bot, arquivos de até 50 MB são enviados para o Canal ou Grupo indicado. Arquivos maiores que 50 MB serão automaticamente pulados para garantir a integridade do lote.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

                        // Destino do Upload (Bot)
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

                        // Bot API Server
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
                            text = "Servidor oficial da nuvem do Telegram (limite de 50 MB por mídia/requisição).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // MODE 2: USER ACCOUNT CONFIGURATION
            if (selectedMode == UploadMode.USER_ACCOUNT) {
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
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = InstagramPurple,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Conta de Usuário (Telethon / MTProto)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Faça upload diretamente pela sua própria conta do Telegram. Suporta arquivos de até 2 GB (sem a limitação de 50 MB do bot) e permite arquivar tanto em Canais/Grupos quanto nas suas 'Mensagens Salvas'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Destination for User Account
                        Text(
                            text = "Destino do Upload no Telegram",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = userDestination == "me",
                                onClick = { userDestination = "me" },
                                label = { Text("Mensagens Salvas (me)") },
                                leadingIcon = { Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            FilterChip(
                                selected = userDestination != "me",
                                onClick = { if (userDestination == "me") userDestination = "@meu_canal" },
                                label = { Text("Canal / Grupo") },
                                leadingIcon = { Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }

                        if (userDestination != "me") {
                            OutlinedTextField(
                                value = userDestination,
                                onValueChange = { userDestination = it },
                                label = { Text("Identificador do Canal ou Chat") },
                                placeholder = { Text("@meu_canal_backup ou -100123456789") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("user_destination_input"),
                                singleLine = true
                            )
                        }

                        HorizontalDivider()

                        // API ID and API HASH
                        Text(
                            text = "Credenciais MTProto (my.telegram.org)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        OutlinedTextField(
                            value = apiId,
                            onValueChange = { apiId = it },
                            label = { Text("API ID (Obrigatório)") },
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
                            label = { Text("API HASH (Obrigatório)") },
                            placeholder = { Text("Ex: 9f8e7d6c5b4a3f2e1d0c...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_hash_input"),
                            singleLine = true
                        )

                        HorizontalDivider()

                        // ACTIVE SESSION STATUS OR NEW LOGIN FLOW
                        val hasActiveSession = settings.isUserSessionValid && settings.userSessionString.isNotBlank()

                        if (hasActiveSession) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = StatusSuccess.copy(alpha = 0.12f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.VerifiedUser,
                                            contentDescription = null,
                                            tint = StatusSuccess,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Sessão Conectada e Ativa",
                                                fontWeight = FontWeight.Bold,
                                                color = StatusSuccess,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "Conta: ${settings.userAccountName.ifBlank { "Usuário Telegram" }} (${settings.userPhoneNumber})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    // Session action buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.validateCurrentSession() },
                                            enabled = !sessionValidationState.isLoading,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (sessionValidationState.isLoading) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                            } else {
                                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text("Validar Sessão", fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { viewModel.logoutUserSession() },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Encerrar Sessão", fontSize = 12.sp)
                                        }
                                    }

                                    // Session Validation Feedback
                                    if (sessionValidationState.message.isNotEmpty()) {
                                        Text(
                                            text = sessionValidationState.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (sessionValidationState.isSuccess == true) StatusSuccess else StatusError,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        } else {
                            // Genuine User StringSession Connection Card
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Link,
                                            contentDescription = null,
                                            tint = InstagramPurple,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Conectar Sessão MTProto (StringSession)",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "Permite enviar arquivos de até 2 GB pela sua conta pessoal",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Para sua segurança e privacidade, a autenticação de usuário no Telegram utiliza criptografia de chave de autorização MTProto (StringSession oficial do Telethon/Pyrogram). Cole sua StringSession gerada com seu API ID e HASH:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    OutlinedTextField(
                                        value = stringSessionInput,
                                        onValueChange = { stringSessionInput = it },
                                        label = { Text("Cole aqui sua StringSession (Base64)") },
                                        placeholder = { Text("Ex: 1BJWap1wBu05FA... (chave MTProto completa de 264+ bytes)") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("string_session_input"),
                                        minLines = 3,
                                        maxLines = 5
                                    )

                                    OutlinedTextField(
                                        value = userPhone,
                                        onValueChange = { userPhone = it },
                                        label = { Text("Identificador ou Telefone (Opcional)") },
                                        placeholder = { Text("Ex: +55 11 99999-9999 ou Minha Conta") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("user_phone_input"),
                                        singleLine = true
                                    )

                                    Button(
                                        onClick = {
                                            viewModel.importUserSession(stringSessionInput, userPhone)
                                        },
                                        enabled = stringSessionInput.isNotBlank() && !userLoginState.isLoading,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("import_session_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = InstagramPurple)
                                    ) {
                                        if (userLoginState.isLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                        } else {
                                            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text("Validar e Conectar Sessão", fontWeight = FontWeight.Bold)
                                    }

                                    // Session Error Feedback
                                    if (userLoginState.errorMessage.isNotEmpty()) {
                                        Card(
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = StatusError.copy(alpha = 0.12f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Filled.Error, contentDescription = null, tint = StatusError, modifier = Modifier.size(20.dp))
                                                Text(
                                                    text = userLoginState.errorMessage,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = StatusError,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    // Session Success Feedback
                                    if (userLoginState.successMessage.isNotEmpty()) {
                                        Card(
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = StatusSuccess.copy(alpha = 0.12f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(20.dp))
                                                Text(
                                                    text = userLoginState.successMessage,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = StatusSuccess,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider()

                                    // Assistant Card: How to generate StringSession
                                    val currentApiId = if (apiId.isNotBlank()) apiId.trim() else "SEU_API_ID"
                                    val currentApiHash = if (apiHash.isNotBlank()) apiHash.trim() else "SEU_API_HASH"
                                    val pythonSnippet = """python -c "from telethon.sync import TelegramClient; from telethon.sessions import StringSession; client=TelegramClient(StringSession(), $currentApiId, '$currentApiHash'); client.start(); print('\nSUA STRING SESSION:\n', client.session.save())""""

                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { showHelpTutorial = !showHelpTutorial },
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Code,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        text = "Como gerar sua StringSession oficial em 1 comando",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Icon(
                                                    imageVector = if (showHelpTutorial) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            AnimatedVisibility(visible = showHelpTutorial) {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(
                                                        text = "O Telegram não envia códigos por APIs HTTP web (apenas para bots). As contas de usuário utilizam o protocolo MTProto oficial. Para gerar sua chave segura em segundos:",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )

                                                    Text(
                                                        text = "1. Execute este comando no terminal do seu computador ou no app Termux:",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold
                                                    )

                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                                            .padding(10.dp)
                                                    ) {
                                                        Text(
                                                            text = pythonSnippet,
                                                            color = Color(0xFF64FFDA),
                                                            fontSize = 11.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            clipboardManager.setText(AnnotatedString(pythonSnippet))
                                                            scope.launch {
                                                                snackbarHostState.showSnackbar("Comando copiado! Cole no terminal do seu PC ou Termux.")
                                                            }
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Copiar Comando Python", fontSize = 12.sp)
                                                    }

                                                    Text(
                                                        text = "2. O Telegram oficial solicitará seu número e enviará o código de verificação real para o seu app oficial do Telegram.\n3. Copie a StringSession impressa no terminal e cole no campo acima.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    viewModel.switchUploadMode(selectedMode)
                    viewModel.saveCredentials(apiId, apiHash, botToken, destination, botApiBaseUrl)
                    viewModel.saveUserDestination(userDestination)
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

            Spacer(modifier = Modifier.navigationBarsPadding().height(48.dp))
        }
    }
}
