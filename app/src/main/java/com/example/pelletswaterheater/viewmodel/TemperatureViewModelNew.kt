package com.example.pelletswaterheater.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pelletswaterheater.utils.ContextProvider
import com.example.pelletswaterheater.utils.ConvertedValue
import com.example.pelletswaterheater.utils.ILogger
import com.example.pelletswaterheater.utils.Logger
import com.example.pelletswaterheater.utils.MetadataJson
import com.example.pelletswaterheater.utils.MetadataUtils
import com.example.pelletswaterheater.utils.ValueConverter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

class TemperatureViewModelNew(
    private val contextProvider: ContextProvider,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val logger: ILogger = Logger
) : ViewModel() {
    private val _metadata = MutableStateFlow<MetadataJson?>(null)
    val metadata: StateFlow<MetadataJson?> = _metadata

    private val _convertedValues = MutableStateFlow<Map<String, ConvertedValue>>(emptyMap())
    val convertedValues: StateFlow<Map<String, ConvertedValue>> = _convertedValues

    init {
        loadCachedMetadata()
    }

    fun fetchMetadata(serverUrl: String) {
        viewModelScope.launch {
            val url = "$serverUrl?"
            val request = Request.Builder().url(url).build()
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        logger.e(
                            "TemperatureViewModelNew",
                            "Metadata fetch failed: ${response.code}"
                        )
                        return@launch
                    }
                    val body = response.body?.string() ?: return@launch
                    val metadata = MetadataUtils.parseServerJson(body)
                    if (metadata != null) {
                        contextProvider.saveMetadata(body)
                        _metadata.value = metadata
                    } else {
                        logger.e("TemperatureViewModelNew", "Failed to parse server metadata")
                    }
                }
            } catch (e: Exception) {
                logger.e("TemperatureViewModelNew", "Metadata fetch error: $e", e)
                loadCachedMetadata()
            }
        }
    }

    fun loadUserMetadata(jsonString: String) {
        viewModelScope.launch {
            val userMetadata = MetadataUtils.parseUserJson(jsonString)
            if (userMetadata != null) {
                contextProvider.saveMetadata(jsonString)
                _metadata.value = MetadataUtils.mergeMetadata(_metadata.value, userMetadata)
                _convertedValues.value = emptyMap() // Invalidate cache
            } else {
                logger.e("TemperatureViewModelNew", "Failed to parse user metadata")
            }
        }
    }

    fun resetMetadata(serverUrl: String) {
        _convertedValues.value = emptyMap() // Invalidate cache
        fetchMetadata(serverUrl)
    }

    fun updateValues(rawData: Map<String, String>) {
        viewModelScope.launch {
            val metadata = _metadata.value ?: return@launch
            val converted = mutableMapOf<String, ConvertedValue>()
            metadata.sections.forEach { (section, fields) ->
                fields.forEach { (key, field) ->
                    val rawValue = rawData["$section.$key"] ?: return@forEach
                    converted["$section.$key"] =
                        ValueConverter.convert("$section.$key", rawValue, field)
                }
            }
            _convertedValues.value = converted
        }
    }

    private fun loadCachedMetadata() {
        viewModelScope.launch {
            val cachedJson = contextProvider.loadCachedMetadata()
            if (cachedJson != null) {
                val cached = MetadataUtils.parseServerJson(cachedJson)
                _metadata.value = cached
            }
            if (_metadata.value == null) {
                logger.w("TemperatureViewModelNew", "No cached metadata found")
            }
        }
    }
}
