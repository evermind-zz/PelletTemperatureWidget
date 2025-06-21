package com.example.pelletswaterheater.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pelletswaterheater.utils.ContextProvider
import com.example.pelletswaterheater.utils.ConvertedValue
import com.example.pelletswaterheater.utils.ILogger
import com.example.pelletswaterheater.utils.Logger
import com.example.pelletswaterheater.utils.MetadataJson
import com.example.pelletswaterheater.utils.MetadataUtils
import com.example.pelletswaterheater.utils.TemperatureLogger
import com.example.pelletswaterheater.utils.TemperatureLoggerImpl
import com.example.pelletswaterheater.utils.ValueConverter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * ViewModel for managing metadata, converted values, and logging, with injectable dependencies.
 */
class TemperatureViewModelNew(
    private val contextProvider: ContextProvider,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val logger: ILogger = Logger,
    private val temperatureLogger: TemperatureLogger = TemperatureLoggerImpl(contextProvider, logger)
) : ViewModel() {
    private val TAG = TemperatureViewModelNew::class.java.simpleName

    private val _metadata = MutableStateFlow<MetadataJson?>(null)
    /**
     * Flow of the current metadata state.
     */
    val metadata: StateFlow<MetadataJson?> = _metadata

    private val _convertedValues = MutableStateFlow<Map<String, ConvertedValue>>(emptyMap())
    /**
     * Flow of converted values (e.g., "system.L_ambient" to converted value).
     */
    val convertedValues: StateFlow<Map<String, ConvertedValue>> = _convertedValues

    init {
        loadCachedMetadata()
    }

    /**
     * Fetches metadata from the server and saves it to storage.
     *
     * @param serverUrl The URL of the server to fetch metadata from.
     */
    fun fetchMetadata(serverUrl: String) {
        viewModelScope.launch {
            val url = "$serverUrl?"
            val request = Request.Builder().url(url).build()
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        logger.e(TAG, "Metadata fetch failed: ${response.code}")
                        return@launch
                    }
                    val body = response.body?.string() ?: return@launch
                    val metadata = MetadataUtils.parseServerJson(body)
                    if (metadata != null) {
                        contextProvider.saveMetadata(body)
                        _metadata.value = metadata
                    } else {
                        logger.e(TAG, "Failed to parse server metadata")
                    }
                }
            } catch (e: Exception) {
                logger.e(TAG, "Metadata fetch error: $e", e)
                loadCachedMetadata()
            }
        }
    }

    /**
     * Loads user-provided metadata JSON and merges it with existing metadata.
     *
     * @param jsonString The user-provided JSON string.
     */
    fun loadUserMetadata(jsonString: String) {
        viewModelScope.launch {
            val userMetadata = MetadataUtils.parseUserJson(jsonString)
            if (userMetadata != null) {
                contextProvider.saveMetadata(jsonString)
                _metadata.value = MetadataUtils.mergeMetadata(_metadata.value, userMetadata)
                _convertedValues.value = emptyMap() // Invalidate cache
            } else {
                logger.e(TAG, "Failed to parse user metadata")
            }
        }
    }

    /**
     * Resets converted values and fetches fresh metadata from the server.
     *
     * @param serverUrl The URL of the server to fetch metadata from.
     */
    fun resetMetadata(serverUrl: String) {
        _convertedValues.value = emptyMap() // Invalidate cache
        fetchMetadata(serverUrl)
    }

    /**
     * Updates converted values based on raw data and logs them.
     *
     * @param rawData A map of raw values (e.g., "system.L_ambient" to "219").
     */
    fun updateValues(rawData: Map<String, String>) {
        viewModelScope.launch {
            val metadata = _metadata.value ?: return@launch
            val converted = mutableMapOf<String, ConvertedValue>()
            val keyOrder = rawData.keys.toList() // Preserve server order
            metadata.sections.forEach { (section, fields) ->
                fields.forEach { (key, field) ->
                    val fullKey = "$section.$key"
                    val rawValue = rawData[fullKey] ?: return@forEach
                    converted[fullKey] = ValueConverter.convert(fullKey, rawValue, field)
                }
            }
            _convertedValues.value = converted
            temperatureLogger.logValues(converted, keyOrder)
        }
    }

    /**
     * Loads cached metadata from storage.
     */
    private fun loadCachedMetadata() {
        viewModelScope.launch {
            val cachedJson = contextProvider.loadCachedMetadata()
            if (cachedJson != null) {
                val cached = MetadataUtils.parseServerJson(cachedJson)
                _metadata.value = cached
            }
            if (_metadata.value == null) {
                logger.w(TAG, "No cached metadata found")
            }
        }
    }
}