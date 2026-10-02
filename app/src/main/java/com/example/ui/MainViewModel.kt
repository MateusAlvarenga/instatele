package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.AppPreferences
import com.example.data.local.ArchiverSettings
import com.example.data.local.UploadRecordEntity
import com.example.data.local.UploadSessionEntity
import com.example.data.model.InstagramProfileBatch
import com.example.data.model.LiveUploadProgress
import com.example.data.model.UploadLogEntry
import com.example.data.model.UploadMode
import com.example.data.model.UserSessionInfo
import com.example.data.repository.TelegramRepository
import com.example.data.repository.UploadRepository
import com.example.data.telegram.UserSignInResult
import com.example.service.UploadManager
import com.example.util.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,
    TRANSFER,
    CONFIG,
    HISTORY
}

data class ConnectionTestState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean? = null,
    val message: String = ""
)

enum class UserLoginStep {
    IDLE,
    AWAITING_CODE,
    AUTHENTICATED
}

data class UserLoginState(
    val isLoading: Boolean = false,
    val step: UserLoginStep = UserLoginStep.IDLE,
    val phoneCodeHash: String = "",
    val phone: String = "",
    val requiresTwoFactor: Boolean = false,
    val twoFactorHint: String = "",
    val errorMessage: String = "",
    val successMessage: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AppPreferences(application)
    private val database = AppDatabase.getInstance(application)
    private val uploadRepository = UploadRepository(database.uploadDao())
    private val telegramRepository = TelegramRepository()

    val uploadManager = UploadManager(
        context = application,
        telegramRepository = telegramRepository,
        uploadRepository = uploadRepository
    )

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    val settings: StateFlow<ArchiverSettings> = preferences.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ArchiverSettings()
    )

    val progress: StateFlow<LiveUploadProgress> = uploadManager.progress
    val logs: StateFlow<List<UploadLogEntry>> = uploadManager.logs

    val allRecords: StateFlow<List<UploadRecordEntity>> = uploadRepository.allRecordsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allSessions: StateFlow<List<UploadSessionEntity>> = uploadRepository.allSessionsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalSuccessCount: StateFlow<Int> = uploadRepository.totalSuccessCountFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    private val _scannedBatches = MutableStateFlow<List<InstagramProfileBatch>>(emptyList())
    val scannedBatches: StateFlow<List<InstagramProfileBatch>> = _scannedBatches.asStateFlow()

    private val _selectedFolderName = MutableStateFlow("")
    val selectedFolderName: StateFlow<String> = _selectedFolderName.asStateFlow()

    private val _selectedFolderUri = MutableStateFlow<Uri?>(null)
    val selectedFolderUri: StateFlow<Uri?> = _selectedFolderUri.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _botTestState = MutableStateFlow(ConnectionTestState())
    val botTestState: StateFlow<ConnectionTestState> = _botTestState.asStateFlow()

    private val _destTestState = MutableStateFlow(ConnectionTestState())
    val destTestState: StateFlow<ConnectionTestState> = _destTestState.asStateFlow()

    private val _userLoginState = MutableStateFlow(UserLoginState())
    val userLoginState: StateFlow<UserLoginState> = _userLoginState.asStateFlow()

    private val _sessionValidationState = MutableStateFlow(ConnectionTestState())
    val sessionValidationState: StateFlow<ConnectionTestState> = _sessionValidationState.asStateFlow()

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun onFolderSelected(uri: Uri, displayName: String) {
        _selectedFolderUri.value = uri
        _selectedFolderName.value = displayName
        viewModelScope.launch {
            preferences.saveLastFolder(uri.toString(), displayName)
            scanSelectedFolder(uri)
        }
    }

    fun scanSelectedFolder(uri: Uri? = _selectedFolderUri.value) {
        if (uri == null) return
        _isScanning.value = true
        viewModelScope.launch {
            val batches = FileUtils.scanDirectoryTree(
                context = getApplication(),
                treeUri = uri,
                itemsPerLot = settings.value.itemsPerLot
            )
            _scannedBatches.value = batches
            _isScanning.value = false
        }
    }

    fun loadSampleDemoFiles() {
        _isScanning.value = true
        viewModelScope.launch {
            _selectedFolderName.value = "Pasta_Instagram_Downloads (Demo)"
            _selectedFolderUri.value = Uri.parse("content://demo.archiver/instagram")
            val batches = FileUtils.createSampleDemoFiles(getApplication())
            _scannedBatches.value = batches
            _isScanning.value = false
        }
    }

    fun saveCredentials(
        apiId: String,
        apiHash: String,
        botToken: String,
        destination: String,
        baseUrl: String
    ) {
        viewModelScope.launch {
            preferences.saveCredentials(apiId, apiHash, botToken, destination, baseUrl)
        }
    }

    fun updateAutoDelete(enabled: Boolean) {
        viewModelScope.launch {
            preferences.updateAutoDelete(enabled)
        }
    }

    fun updateBatchOptions(
        itemsPerLot: Int,
        itemsPerAlbum: Int,
        sendLotHeaders: Boolean,
        includeIgLink: Boolean
    ) {
        viewModelScope.launch {
            preferences.updateBatchOptions(itemsPerLot, itemsPerAlbum, sendLotHeaders, includeIgLink)
            // Re-group current files if loaded
            if (_scannedBatches.value.isNotEmpty()) {
                val allFiles = _scannedBatches.value.flatMap { it.allFiles }
                _scannedBatches.value = FileUtils.groupAndLotFiles(allFiles, itemsPerLot)
            }
        }
    }

    fun testBot(botToken: String, baseUrl: String) {
        _botTestState.value = ConnectionTestState(isLoading = true)
        viewModelScope.launch {
            val result = telegramRepository.testBot(botToken, baseUrl)
            if (result.isSuccess) {
                _botTestState.value = ConnectionTestState(
                    isLoading = false,
                    isSuccess = true,
                    message = result.getOrNull() ?: "Conexão OK!"
                )
            } else {
                _botTestState.value = ConnectionTestState(
                    isLoading = false,
                    isSuccess = false,
                    message = result.exceptionOrNull()?.message ?: "Falha ao validar bot"
                )
            }
        }
    }

    fun testDestination(botToken: String, chatId: String, baseUrl: String) {
        _destTestState.value = ConnectionTestState(isLoading = true)
        viewModelScope.launch {
            val result = telegramRepository.testDestination(botToken, chatId, baseUrl)
            if (result.isSuccess) {
                _destTestState.value = ConnectionTestState(
                    isLoading = false,
                    isSuccess = true,
                    message = result.getOrNull() ?: "Destino OK!"
                )
            } else {
                _destTestState.value = ConnectionTestState(
                    isLoading = false,
                    isSuccess = false,
                    message = result.exceptionOrNull()?.message ?: "Falha ao validar destino"
                )
            }
        }
    }

    fun switchUploadMode(mode: UploadMode) {
        viewModelScope.launch {
            preferences.setUploadMode(mode)
        }
    }

    fun importUserSession(sessionString: String, accountLabel: String = "") {
        _userLoginState.value = UserLoginState(isLoading = true)
        val result = telegramRepository.importUserSession(sessionString, accountLabel)
        viewModelScope.launch {
            handleSignInResult(result, accountLabel)
        }
    }

    fun requestUserLoginCode(apiId: String, apiHash: String, phone: String) {
        if (apiId.isBlank() || apiHash.isBlank()) {
            _userLoginState.value = UserLoginState(
                errorMessage = "API ID e API HASH são obrigatórios para login com conta de usuário."
            )
            return
        }
        val cleanPhone = phone.trim()
        if (cleanPhone.length < 8) {
            _userLoginState.value = UserLoginState(
                errorMessage = "Informe o telefone com DDI (ex: +5511999999999)"
            )
            return
        }

        _userLoginState.value = UserLoginState(isLoading = true, phone = cleanPhone)

        viewModelScope.launch {
            val result = telegramRepository.requestUserCode(apiId, apiHash, cleanPhone)
            if (result.isSuccess) {
                val codeHash = result.getOrNull() ?: ""
                _userLoginState.value = UserLoginState(
                    isLoading = false,
                    step = UserLoginStep.AWAITING_CODE,
                    phone = cleanPhone,
                    phoneCodeHash = codeHash,
                    successMessage = "Código de confirmação enviado para seu Telegram ou SMS!"
                )
            } else {
                _userLoginState.value = UserLoginState(
                    isLoading = false,
                    step = UserLoginStep.IDLE,
                    phone = cleanPhone,
                    errorMessage = result.exceptionOrNull()?.message ?: "Falha ao solicitar código."
                )
            }
        }
    }

    fun confirmUserLoginCode(
        apiId: String,
        apiHash: String,
        phone: String,
        code: String,
        password: String = ""
    ) {
        val curr = _userLoginState.value
        _userLoginState.value = curr.copy(isLoading = true, errorMessage = "")

        viewModelScope.launch {
            if (curr.requiresTwoFactor && password.isNotBlank()) {
                val result = telegramRepository.signInWithUserPassword(apiId, apiHash, phone, password)
                handleSignInResult(result, phone)
            } else {
                val result = telegramRepository.signInWithUserCode(
                    apiId = apiId,
                    apiHash = apiHash,
                    phoneNumber = phone,
                    phoneCodeHash = curr.phoneCodeHash,
                    code = code,
                    hasTwoFactorEnabled = false
                )
                handleSignInResult(result, phone)
            }
        }
    }

    private suspend fun handleSignInResult(result: UserSignInResult, phone: String) {
        when (result) {
            is UserSignInResult.Success -> {
                val session = result.session
                preferences.saveUserSession(
                    phone = session.phoneNumber.ifBlank { phone },
                    sessionString = session.sessionString,
                    accountName = session.firstName,
                    username = session.username,
                    isValid = true,
                    dcId = session.dcId
                )
                _userLoginState.value = UserLoginState(
                    step = UserLoginStep.AUTHENTICATED,
                    successMessage = "Conectado com sucesso como ${session.firstName} (@${session.username})!"
                )
            }
            is UserSignInResult.RequiresTwoFactor -> {
                _userLoginState.value = _userLoginState.value.copy(
                    isLoading = false,
                    requiresTwoFactor = true,
                    twoFactorHint = result.hint,
                    errorMessage = "Esta conta possui verificação em duas etapas. Digite sua senha."
                )
            }
            is UserSignInResult.Failure -> {
                _userLoginState.value = _userLoginState.value.copy(
                    isLoading = false,
                    errorMessage = result.error
                )
            }
        }
    }

    fun validateCurrentSession() {
        val currSettings = settings.value
        if (currSettings.userSessionString.isBlank()) {
            _sessionValidationState.value = ConnectionTestState(
                isLoading = false,
                isSuccess = false,
                message = "Nenhuma sessão ativa encontrada. Crie uma nova sessão abaixo."
            )
            return
        }

        _sessionValidationState.value = ConnectionTestState(isLoading = true)

        viewModelScope.launch {
            val result = telegramRepository.validateUserSession(
                sessionString = currSettings.userSessionString,
                apiId = currSettings.apiId,
                apiHash = currSettings.apiHash
            )

            if (result.isSuccess) {
                preferences.updateUserSessionValidity(true)
                _sessionValidationState.value = ConnectionTestState(
                    isLoading = false,
                    isSuccess = true,
                    message = "Sessão válida e ativa! Conectado como ${currSettings.userAccountName.ifBlank { currSettings.userPhoneNumber }}."
                )
            } else {
                preferences.updateUserSessionValidity(false)
                _sessionValidationState.value = ConnectionTestState(
                    isLoading = false,
                    isSuccess = false,
                    message = "A sessão expirou ou foi encerrada no Telegram. Crie uma nova sessão."
                )
            }
        }
    }

    fun logoutUserSession() {
        viewModelScope.launch {
            preferences.clearUserSession()
            _userLoginState.value = UserLoginState()
            _sessionValidationState.value = ConnectionTestState(
                isLoading = false,
                isSuccess = null,
                message = "Sessão encerrada com sucesso."
            )
        }
    }

    fun saveUserDestination(destination: String) {
        viewModelScope.launch {
            preferences.saveUserDestination(destination)
        }
    }

    fun resetUserLoginState() {
        _userLoginState.value = UserLoginState()
    }

    fun startArchiving(): Boolean {
        val currentSettings = settings.value

        if (!currentSettings.isReadyForUpload) {
            return false
        }

        if (_scannedBatches.value.isEmpty()) {
            return false
        }

        uploadManager.startUpload(
            batches = _scannedBatches.value,
            settings = currentSettings,
            folderName = _selectedFolderName.value.ifBlank { "Arquivos Instagram" }
        )
        _currentScreen.value = AppScreen.TRANSFER
        return true
    }

    fun skipCurrentFile() {
        uploadManager.skipCurrent()
    }

    fun togglePauseTransfer() {
        uploadManager.togglePause()
    }

    fun cancelTransfer() {
        uploadManager.cancelUpload()
    }

    fun clearLogs() {
        uploadManager.clearLogs()
    }

    fun clearHistory() {
        viewModelScope.launch {
            uploadRepository.clearHistory()
        }
    }
}
