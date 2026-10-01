package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.UploadMode
import com.example.data.model.UserSessionInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "archiver_settings")

data class ArchiverSettings(
    val uploadMode: UploadMode = UploadMode.BOT,
    val apiId: String = "",
    val apiHash: String = "",
    val botToken: String = "",
    val uploadDestination: String = "",
    val botApiBaseUrl: String = "https://api.telegram.org",
    val autoDeleteLocal: Boolean = false,
    val itemsPerLot: Int = 100,
    val itemsPerAlbum: Int = 10,
    val sendLotHeaders: Boolean = true,
    val includeInstagramLink: Boolean = true,
    val lastFolderUri: String = "",
    val lastFolderName: String = "",

    // User Account Mode properties
    val userPhoneNumber: String = "",
    val userSessionString: String = "",
    val userAccountName: String = "",
    val userAccountUsername: String = "",
    val isUserSessionValid: Boolean = false,
    val userUploadDestination: String = "me", // "me" for Saved Messages, or @channel / -100...
    val userDcId: Int = 2
) {
    val isReadyForUpload: Boolean
        get() = when (uploadMode) {
            UploadMode.BOT -> botToken.isNotBlank() && uploadDestination.isNotBlank()
            UploadMode.USER_ACCOUNT -> isUserSessionValid && userUploadDestination.isNotBlank()
        }

    val activeDestination: String
        get() = when (uploadMode) {
            UploadMode.BOT -> uploadDestination
            UploadMode.USER_ACCOUNT -> if (userUploadDestination == "me") "Mensagens Salvas (me)" else userUploadDestination
        }
}

class AppPreferences(private val context: Context) {

    companion object {
        val KEY_UPLOAD_MODE = stringPreferencesKey("upload_mode")
        val KEY_API_ID = stringPreferencesKey("api_id")
        val KEY_API_HASH = stringPreferencesKey("api_hash")
        val KEY_BOT_TOKEN = stringPreferencesKey("bot_token")
        val KEY_UPLOAD_DESTINATION = stringPreferencesKey("upload_destination")
        val KEY_BOT_API_BASE_URL = stringPreferencesKey("bot_api_base_url")
        val KEY_AUTO_DELETE = booleanPreferencesKey("auto_delete")
        val KEY_ITEMS_PER_LOT = intPreferencesKey("items_per_lot")
        val KEY_ITEMS_PER_ALBUM = intPreferencesKey("items_per_album")
        val KEY_SEND_LOT_HEADERS = booleanPreferencesKey("send_lot_headers")
        val KEY_INCLUDE_IG_LINK = booleanPreferencesKey("include_ig_link")
        val KEY_LAST_FOLDER_URI = stringPreferencesKey("last_folder_uri")
        val KEY_LAST_FOLDER_NAME = stringPreferencesKey("last_folder_name")

        // User Account Mode keys
        val KEY_USER_PHONE_NUMBER = stringPreferencesKey("user_phone_number")
        val KEY_USER_SESSION_STRING = stringPreferencesKey("user_session_string")
        val KEY_USER_ACCOUNT_NAME = stringPreferencesKey("user_account_name")
        val KEY_USER_ACCOUNT_USERNAME = stringPreferencesKey("user_account_username")
        val KEY_IS_USER_SESSION_VALID = booleanPreferencesKey("is_user_session_valid")
        val KEY_USER_UPLOAD_DESTINATION = stringPreferencesKey("user_upload_destination")
        val KEY_USER_DC_ID = intPreferencesKey("user_dc_id")
    }

    val settingsFlow: Flow<ArchiverSettings> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[KEY_UPLOAD_MODE] ?: UploadMode.BOT.name
        val mode = try { UploadMode.valueOf(modeStr) } catch (_: Exception) { UploadMode.BOT }

        ArchiverSettings(
            uploadMode = mode,
            apiId = prefs[KEY_API_ID] ?: "",
            apiHash = prefs[KEY_API_HASH] ?: "",
            botToken = prefs[KEY_BOT_TOKEN] ?: "",
            uploadDestination = prefs[KEY_UPLOAD_DESTINATION] ?: "",
            botApiBaseUrl = prefs[KEY_BOT_API_BASE_URL] ?: "https://api.telegram.org",
            autoDeleteLocal = prefs[KEY_AUTO_DELETE] ?: false,
            itemsPerLot = prefs[KEY_ITEMS_PER_LOT] ?: 100,
            itemsPerAlbum = (prefs[KEY_ITEMS_PER_ALBUM] ?: 10).coerceIn(2, 10),
            sendLotHeaders = prefs[KEY_SEND_LOT_HEADERS] ?: true,
            includeInstagramLink = prefs[KEY_INCLUDE_IG_LINK] ?: true,
            lastFolderUri = prefs[KEY_LAST_FOLDER_URI] ?: "",
            lastFolderName = prefs[KEY_LAST_FOLDER_NAME] ?: "",

            userPhoneNumber = prefs[KEY_USER_PHONE_NUMBER] ?: "",
            userSessionString = prefs[KEY_USER_SESSION_STRING] ?: "",
            userAccountName = prefs[KEY_USER_ACCOUNT_NAME] ?: "",
            userAccountUsername = prefs[KEY_USER_ACCOUNT_USERNAME] ?: "",
            isUserSessionValid = prefs[KEY_IS_USER_SESSION_VALID] ?: false,
            userUploadDestination = prefs[KEY_USER_UPLOAD_DESTINATION] ?: "me",
            userDcId = prefs[KEY_USER_DC_ID] ?: 2
        )
    }

    suspend fun setUploadMode(mode: UploadMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_UPLOAD_MODE] = mode.name
        }
    }

    suspend fun saveCredentials(
        apiId: String,
        apiHash: String,
        botToken: String,
        destination: String,
        botApiBaseUrl: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_API_ID] = apiId.trim()
            prefs[KEY_API_HASH] = apiHash.trim()
            prefs[KEY_BOT_TOKEN] = botToken.trim()
            prefs[KEY_UPLOAD_DESTINATION] = destination.trim()
            prefs[KEY_BOT_API_BASE_URL] = botApiBaseUrl.trim().ifEmpty { "https://api.telegram.org" }
        }
    }

    suspend fun saveUserSession(
        phone: String,
        sessionString: String,
        accountName: String,
        username: String,
        isValid: Boolean,
        dcId: Int = 2
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_PHONE_NUMBER] = phone.trim()
            prefs[KEY_USER_SESSION_STRING] = sessionString.trim()
            prefs[KEY_USER_ACCOUNT_NAME] = accountName.trim()
            prefs[KEY_USER_ACCOUNT_USERNAME] = username.trim()
            prefs[KEY_IS_USER_SESSION_VALID] = isValid
            prefs[KEY_USER_DC_ID] = dcId
        }
    }

    suspend fun updateUserSessionValidity(isValid: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_USER_SESSION_VALID] = isValid
        }
    }

    suspend fun clearUserSession() {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_SESSION_STRING] = ""
            prefs[KEY_IS_USER_SESSION_VALID] = false
            prefs[KEY_USER_ACCOUNT_NAME] = ""
            prefs[KEY_USER_ACCOUNT_USERNAME] = ""
        }
    }

    suspend fun saveUserDestination(destination: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_UPLOAD_DESTINATION] = destination.trim()
        }
    }

    suspend fun updateAutoDelete(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_DELETE] = enabled
        }
    }

    suspend fun updateBatchOptions(
        itemsPerLot: Int,
        itemsPerAlbum: Int,
        sendLotHeaders: Boolean,
        includeIgLink: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ITEMS_PER_LOT] = itemsPerLot
            prefs[KEY_ITEMS_PER_ALBUM] = itemsPerAlbum.coerceIn(2, 10)
            prefs[KEY_SEND_LOT_HEADERS] = sendLotHeaders
            prefs[KEY_INCLUDE_IG_LINK] = includeIgLink
        }
    }

    suspend fun saveLastFolder(uri: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_FOLDER_URI] = uri
            prefs[KEY_LAST_FOLDER_NAME] = name
        }
    }
}
