package com.example.pelletswaterheater.preferences

import androidx.datastore.core.Serializer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesKeys
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey

/**
 * Schema definition for all app preferences migrated from EncryptedSharedPreferences.
 * Keys match the original SharedPreferences keys for seamless migration.
 */
object AppPreferencesSchema {
    // Server URL
    val SERVER_URL = stringPreferencesKey("server_url")

    // Widget dimensions
    val WIDGET_WIDTH = stringPreferencesKey("widget_width")
    val WIDGET_HEIGHT = stringPreferencesKey("widget_height")

    // Widget colors
    val BACKGROUND_COLOR = stringPreferencesKey("background_color")
    val TEXT_COLOR = stringPreferencesKey("text_color")

    // Logger
    val STORED_ORDER_HASH = intPreferencesKey("storedOrderHash")

    // Metadata file
    val METADATA_DEFAULT_FILE = stringPreferencesKey("key_metadata_default_file")

    // Network fingerprint
    val GATEWAY_FINGERPRINT = stringPreferencesKey("gateway_fingerprint")

    // Selections stored as JSON string (dynamic keys)
    val SELECTIONS_JSON = stringPreferencesKey("selections_json")

    // Widget refresh debounce
    val LAST_REFRESH_TIME = longPreferencesKey("last_refresh_time")

    // Future: add more preferences here as needed
}

/**
 * Data class representing all app preferences.
 * This is the type-safe interface for preferences access.
 */
data class AppPreferences(
    val serverUrl: String? = null,
    val widgetWidth: String? = null,
    val widgetHeight: String? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
    val storedOrderHash: Int? = null,
    val metadataDefaultFile: String? = null,
    val gatewayFingerprint: String? = null,
    val selectionsJson: String? = null,
    val lastRefreshTime: Long? = null
) {
    companion object {
        fun fromPreferences(prefs: Preferences): AppPreferences {
            return AppPreferences(
                serverUrl = prefs[AppPreferencesSchema.SERVER_URL],
                widgetWidth = prefs[AppPreferencesSchema.WIDGET_WIDTH],
                widgetHeight = prefs[AppPreferencesSchema.WIDGET_HEIGHT],
                backgroundColor = prefs[AppPreferencesSchema.BACKGROUND_COLOR],
                textColor = prefs[AppPreferencesSchema.TEXT_COLOR],
                storedOrderHash = prefs[AppPreferencesSchema.STORED_ORDER_HASH],
                metadataDefaultFile = prefs[AppPreferencesSchema.METADATA_DEFAULT_FILE],
                gatewayFingerprint = prefs[AppPreferencesSchema.GATEWAY_FINGERPRINT],
                selectionsJson = prefs[AppPreferencesSchema.SELECTIONS_JSON],
                lastRefreshTime = prefs[AppPreferencesSchema.LAST_REFRESH_TIME]
            )
        }

        fun toMutablePreferences(prefs: AppPreferences): Preferences =
            mutablePreferencesOf(
                prefs.serverUrl?.let { AppPreferencesSchema.SERVER_URL to it },
                prefs.widgetWidth?.let { AppPreferencesSchema.WIDGET_WIDTH to it },
                prefs.widgetHeight?.let { AppPreferencesSchema.WIDGET_HEIGHT to it },
                prefs.backgroundColor?.let { AppPreferencesSchema.BACKGROUND_COLOR to it },
                prefs.textColor?.let { AppPreferencesSchema.TEXT_COLOR to it },
                prefs.storedOrderHash?.let { AppPreferencesSchema.STORED_ORDER_HASH to it },
                prefs.metadataDefaultFile?.let { AppPreferencesSchema.METADATA_DEFAULT_FILE to it },
                prefs.gatewayFingerprint?.let { AppPreferencesSchema.GATEWAY_FINGERPRINT to it },
                prefs.selectionsJson?.let { AppPreferencesSchema.SELECTIONS_JSON to it },
                prefs.lastRefreshTime?.let { AppPreferencesSchema.LAST_REFRESH_TIME to it }
            ).filterValues { it != null } as Preferences
    }
}

/**
 * Serializer for migrating from EncryptedSharedPreferences to DataStore Preferences.
 * Handles the migration of existing encrypted prefs to the new DataStore.
 */
class AppPreferencesSerializer(
    private val context: android.content.Context
) : Serializer<AppPreferences> {
    private val masterKeyAlias by lazy { androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC) }
    private val encryptedPrefs by lazy {
        androidx.security.crypto.EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKeyAlias,
            context,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override val defaultValue: AppPreferences = AppPreferences()

    override suspend fun readFrom(input: java.io.InputStream): AppPreferences {
        // This is called for new DataStore file reads (after migration)
        return AppPreferences.fromPreferences(
            androidx.datastore.preferences.core.PreferencesSerializer.deserialize(input)
        )
    }

    override suspend fun writeTo(t: AppPreferences, output: java.io.OutputStream) {
        val prefs = AppPreferences.toMutablePreferences(t)
        androidx.datastore.preferences.core.PreferencesSerializer.serialize(prefs, output)
    }

    /**
     * Migrates from EncryptedSharedPreferences to DataStore.
     * Called once during first DataStore creation.
     */
    fun migrate(): AppPreferences {
        return AppPreferences(
            serverUrl = encryptedPrefs.getString("server_url", null),
            widgetWidth = encryptedPrefs.getString("widget_width", null),
            widgetHeight = encryptedPrefs.getString("widget_height", null),
            backgroundColor = encryptedPrefs.getString("background_color", null),
            textColor = encryptedPrefs.getString("text_color", null),
            storedOrderHash = encryptedPrefs.getInt("storedOrderHash", -1).takeIf { it != -1 },
            metadataDefaultFile = encryptedPrefs.getString("key_metadata_default_file", null),
            gatewayFingerprint = encryptedPrefs.getString("gateway_fingerprint", null),
            selectionsJson = encryptedPrefs.getString("selections_json", null),
            lastRefreshTime = encryptedPrefs.getLong("last_refresh_time", 0).takeIf { it != 0 }
        )
    }
}