package com.example.pelletswaterheater.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.preferences.rxjava3.PreferencesDataStoreFactory
import androidx.datastore.preferences.rxjava3.RxDataStore
import androidx.datastore.preferences.rxjava3.RxDataStoreBuilder
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesKeys
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.awaitSingle
import kotlinx.coroutines.rx3.asFlowable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PreferencesRepositoryImpl(
    private val context: Context
) : PreferencesRepository, KoinComponent {

    private val dataStore by lazy {
        val serializer = AppPreferencesSerializer(context)
        val migratedPrefs = serializer.migrate()

        RxDataStoreBuilder.create(
            context,
            "app_preferences",
            serializer
        ).build()
    }

    // Preference keys
    private val SERVER_URL_KEY = preferencesKey<String>("server_url")
    private val WIDGET_WIDTH_KEY = preferencesKey<String>("widget_width")
    private val WIDGET_HEIGHT_KEY = preferencesKey<String>("widget_height")
    private val BACKGROUND_COLOR_KEY = preferencesKey<String>("background_color")
    private val TEXT_COLOR_KEY = preferencesKey<String>("text_color")
    private val STORED_ORDER_HASH_KEY = preferencesKey<Int>("storedOrderHash")
    private val METADATA_DEFAULT_FILE_KEY = preferencesKey<String>("key_metadata_default_file")
    private val GATEWAY_FINGERPRINT_KEY = preferencesKey<String>("gateway_fingerprint")
    private val SELECTIONS_JSON_KEY = preferencesKey<String>("selections_json")
    private val LAST_REFRESH_TIME_KEY = preferencesKey<Long>("last_refresh_time")

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
        dataStore.updateData { it.edit { it[SERVER_URL_KEY] = url } }.awaitSingle()
    }

    override suspend fun setWidgetWidth(width: String) {
        dataStore.updateData { it.edit { it[WIDGET_WIDTH_KEY] = width } }.awaitSingle()
    }

    override suspend fun setWidgetHeight(height: String) {
        dataStore.updateData { it.edit { it[WIDGET_HEIGHT_KEY] = height } }.awaitSingle()
    }

    override suspend fun setBackgroundColor(color: String) {
        dataStore.updateData { it.edit { it[BACKGROUND_COLOR_KEY] = color } }.awaitSingle()
    }

    override suspend fun setTextColor(color: String) {
        dataStore.updateData { it.edit { it[TEXT_COLOR_KEY] = color } }.awaitSingle()
    }

    override suspend fun setStoredOrderHash(hash: Int) {
        dataStore.updateData { it.edit { it[STORED_ORDER_HASH_KEY] = hash } }.awaitSingle()
    }

    override suspend fun setMetadataDefaultFile(fileName: String) {
        dataStore.updateData { it.edit { it[METADATA_DEFAULT_FILE_KEY] = fileName } }.awaitSingle()
    }

    override suspend fun setGatewayFingerprint(fingerprint: String) {
        dataStore.updateData { it.edit { it[GATEWAY_FINGERPRINT_KEY] = fingerprint } }.awaitSingle()
    }

    override suspend fun setSelectionsJson(json: String) {
        dataStore.updateData { it.edit { it[SELECTIONS_JSON_KEY] = json } }.awaitSingle()
    }

    override suspend fun setLastRefreshTime(time: Long) {
        dataStore.updateData { it.edit { it[LAST_REFRESH_TIME_KEY] = time } }.awaitSingle()
    }

    override suspend fun updateAllSettings(
        url: String,
        width: String,
        height: String,
        backgroundColor: String,
        textColor: String
    ): Boolean {
        // Get current URL to detect change
        val currentUrl = dataStore.data.first().awaitSingle()[SERVER_URL_KEY]
        val urlChanged = currentUrl != url

        dataStore.updateData { prefs ->
            val edit = prefs.edit()
            edit[SERVER_URL_KEY] = url
            edit[WIDGET_WIDTH_KEY] = width
            edit[WIDGET_HEIGHT_KEY] = height
            edit[BACKGROUND_COLOR_KEY] = backgroundColor
            edit[TEXT_COLOR_KEY] = textColor
            edit
        }.awaitSingle()

        return urlChanged
    }
}