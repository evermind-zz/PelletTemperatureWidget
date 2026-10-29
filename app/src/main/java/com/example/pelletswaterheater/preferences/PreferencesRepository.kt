package com.example.pelletswaterheater.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for type-safe preferences access.
 * Abstracts DataStore implementation details.
 */
interface PreferencesRepository {
    val serverUrl: Flow<String?>
    val widgetWidth: Flow<String?>
    val widgetHeight: Flow<String?>
    val backgroundColor: Flow<String?>
    val textColor: Flow<String?>
    val storedOrderHash: Flow<Int?>
    val metadataDefaultFile: Flow<String?>
    val gatewayFingerprint: Flow<String?>
    val selectionsJson: Flow<String?>
    val lastRefreshTime: Flow<Long?>

    suspend fun setServerUrl(url: String)
    suspend fun setWidgetWidth(width: String)
    suspend fun setWidgetHeight(height: String)
    suspend fun setBackgroundColor(color: String)
    suspend fun setTextColor(color: String)
    suspend fun setStoredOrderHash(hash: Int)
    suspend fun setMetadataDefaultFile(fileName: String)
    suspend fun setGatewayFingerprint(fingerprint: String)
    suspend fun setSelectionsJson(json: String)
    suspend fun setLastRefreshTime(time: Long)

    /**
     * Updates multiple settings atomically.
     * Returns true if URL changed (triggers worker reschedule).
     */
    suspend fun updateAllSettings(
        url: String,
        width: String,
        height: String,
        backgroundColor: String,
        textColor: String
    ): Boolean
}