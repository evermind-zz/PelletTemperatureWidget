package com.example.pelletswaterheater.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PreferencesRepositoryImpl(
    private val context: Context
) : PreferencesRepository, KoinComponent {

    private val dataStore: DataStore<Preferences> = DataStoreFactory.create<Preferences>(
        serializer = PreferencesSerializer as androidx.datastore.core.Serializer<Preferences>,
        scope = CoroutineScope(Dispatchers.IO),
        produceFile = { context.filesDir.resolve("app_preferences.preferences_pb") }
    )

    // Preference keys
    private val SERVER_URL_KEY = stringPreferencesKey("server_url")
    private val WIDGET_WIDTH_KEY = stringPreferencesKey("widget_width")
    private val WIDGET_HEIGHT_KEY = stringPreferencesKey("widget_height")
    private val BACKGROUND_COLOR_KEY = stringPreferencesKey("background_color")
    private val TEXT_COLOR_KEY = stringPreferencesKey("text_color")
    private val STORED_ORDER_HASH_KEY = intPreferencesKey("storedOrderHash")
    private val METADATA_DEFAULT_FILE_KEY = stringPreferencesKey("key_metadata_default_file")
    private val GATEWAY_FINGERPRINT_KEY = stringPreferencesKey("gateway_fingerprint")
    private val SELECTIONS_JSON_KEY = stringPreferencesKey("selections_json")
    private val LAST_REFRESH_TIME_KEY = longPreferencesKey("last_refresh_time")

    // Public flows
    override val serverUrl: Flow<String?> = dataStore.data
        .map { it[SERVER_URL_KEY] }
        .distinctUntilChanged()

    override val widgetWidth: Flow<String?> = dataStore.data
        .map { it[WIDGET_WIDTH_KEY] }
        .distinctUntilChanged()

    override val widgetHeight: Flow<String?> = dataStore.data
        .map { it[WIDGET_HEIGHT_KEY] }
        .distinctUntilChanged()

    override val backgroundColor: Flow<String?> = dataStore.data
        .map { it[BACKGROUND_COLOR_KEY] }
        .distinctUntilChanged()

    override val textColor: Flow<String?> = dataStore.data
        .map { it[TEXT_COLOR_KEY] }
        .distinctUntilChanged()

    override val storedOrderHash: Flow<Int?> = dataStore.data
        .map { it[STORED_ORDER_HASH_KEY] }
        .distinctUntilChanged()

    override val metadataDefaultFile: Flow<String?> = dataStore.data
        .map { it[METADATA_DEFAULT_FILE_KEY] }
        .distinctUntilChanged()

    override val gatewayFingerprint: Flow<String?> = dataStore.data
        .map { it[GATEWAY_FINGERPRINT_KEY] }
        .distinctUntilChanged()

    override val selectionsJson: Flow<String?> = dataStore.data
        .map { it[SELECTIONS_JSON_KEY] }
        .distinctUntilChanged()

    override val lastRefreshTime: Flow<Long?> = dataStore.data
        .map { it[LAST_REFRESH_TIME_KEY] }
        .distinctUntilChanged()

    override suspend fun setServerUrl(url: String) {
        dataStore.edit { it[SERVER_URL_KEY] = url }
    }

    override suspend fun setWidgetWidth(width: String) {
        dataStore.edit { it[WIDGET_WIDTH_KEY] = width }
    }

    override suspend fun setWidgetHeight(height: String) {
        dataStore.edit { it[WIDGET_HEIGHT_KEY] = height }
    }

    override suspend fun setBackgroundColor(color: String) {
        dataStore.edit { it[BACKGROUND_COLOR_KEY] = color }
    }

    override suspend fun setTextColor(color: String) {
        dataStore.edit { it[TEXT_COLOR_KEY] = color }
    }

    override suspend fun setStoredOrderHash(hash: Int) {
        dataStore.edit { it[STORED_ORDER_HASH_KEY] = hash }
    }

    override suspend fun setMetadataDefaultFile(fileName: String) {
        dataStore.edit { it[METADATA_DEFAULT_FILE_KEY] = fileName }
    }

    override suspend fun setGatewayFingerprint(fingerprint: String) {
        dataStore.edit { it[GATEWAY_FINGERPRINT_KEY] = fingerprint }
    }

    override suspend fun setSelectionsJson(json: String) {
        dataStore.edit { it[SELECTIONS_JSON_KEY] = json }
    }

    override suspend fun setLastRefreshTime(time: Long) {
        dataStore.edit { it[LAST_REFRESH_TIME_KEY] = time }
    }

    override suspend fun updateAllSettings(
        url: String,
        width: String,
        height: String,
        backgroundColor: String,
        textColor: String
    ): Boolean {
        val currentUrl = dataStore.data.first()[SERVER_URL_KEY]
        val urlChanged = currentUrl != url

        dataStore.edit {
            it[SERVER_URL_KEY] = url
            it[WIDGET_WIDTH_KEY] = width
            it[WIDGET_HEIGHT_KEY] = height
            it[BACKGROUND_COLOR_KEY] = backgroundColor
            it[TEXT_COLOR_KEY] = textColor
        }

        return urlChanged
    }
}