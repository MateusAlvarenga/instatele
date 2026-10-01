package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "archiver_settings")

data class ArchiverSettings(
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
    val lastFolderName: String = ""
)

class AppPreferences(private val context: Context) {

    companion object {
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
    }

    val settingsFlow: Flow<ArchiverSettings> = context.dataStore.data.map { prefs ->
        ArchiverSettings(
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
            lastFolderName = prefs[KEY_LAST_FOLDER_NAME] ?: ""
        )
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
