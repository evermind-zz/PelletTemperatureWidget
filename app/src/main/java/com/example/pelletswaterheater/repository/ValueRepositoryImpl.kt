package com.example.pelletswaterheater.repository

import com.example.pelletswaterheater.TemperatureWidget
import com.example.pelletswaterheater.utils.ContextProvider
import com.example.pelletswaterheater.utils.ConvertedValue
import com.example.pelletswaterheater.utils.ILogger
import com.example.pelletswaterheater.utils.Logger
import com.example.pelletswaterheater.utils.MetadataJson
import com.example.pelletswaterheater.utils.MetadataUtils
import com.example.pelletswaterheater.utils.TemperatureLogger
import com.example.pelletswaterheater.utils.TemperatureUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ValueRepositoryImpl(
    private val contextProvider: ContextProvider,
    private val logger: ILogger = Logger,
    private val temperatureLogger: TemperatureLogger,
    private val metadataRepository: MetadataRepository,
    private val temperatureUtils: TemperatureUtils,
    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : ValueRepository, KoinComponent {

    private val TAG = ValueRepositoryImpl::class.java.simpleName

    private val _convertedValues = MutableStateFlow<Map<String, ConvertedValue>>(emptyMap())
    override val convertedValues: StateFlow<Map<String, ConvertedValue>> = _convertedValues.asStateFlow()

    private val _lastTimestamp = MutableStateFlow<String>("N/A")
    override val lastTimestamp: StateFlow<String> = _lastTimestamp.asStateFlow()

    override fun updateValues(rawData: Map<String, String>, timestamp: Long, doLogging: Boolean) {
        val metadata = metadataRepository.metadata.value ?: return
        val converted = mutableMapOf<String, ConvertedValue>()
        val keyOrder = rawData.keys.toList()
        metadata.sections.forEach { (section, fields) ->
            fields.forEach { (key, field) ->
                val fullKey = "$section.$key"
                val rawValue = rawData[fullKey] ?: return@forEach
                when (field) {
                    is MetadataJson.Field -> {
                        logger.d(TAG, " updateValues() Field $key = ${field.value} (${field.unit ?: "no unit"})")
                        converted[fullKey] = com.example.pelletswaterheater.utils.ValueConverter.convert(fullKey, rawValue, field)
                    }
                    is MetadataJson.Label -> logger.d(TAG, "updateValues() Label $key = ${field.label}")
                }
            }
        }

        _convertedValues.value = converted
        _lastTimestamp.value = temperatureUtils.formatTimeStamp(timestamp)

        if (doLogging) {
            // Note: Logging selection filtering is done by the caller (ViewModel)
            // which has access to SelectionRepository
        }

        // Trigger widget update
        temperatureUtils.context?.let {
            TemperatureWidget().forceUpdateAll(it)
        }
    }

    override suspend fun loadTestDataFromStorage() {
        val file = contextProvider.getContext().filesDir.resolve("test-values.json")
        if (file.exists()) {
            val body = file.readText()
            parseAndUpdateValues(body)
        }
    }

    override fun getCurrentTimestamp(): Long = System.currentTimeMillis()

    override fun resetValues() {
        _convertedValues.value = emptyMap()
        _lastTimestamp.value = "N/A"
    }

    private fun parseAndUpdateValues(body: String) {
        val values = MetadataUtils.parseDataValuesJson(body)
        if (values != null) {
            val valsMap = mutableMapOf<String, String>()
            values.sections.forEach { (section, valueMap) ->
                valueMap.forEach { (jsonName, rawValue) ->
                    valsMap["${section}.${jsonName}"] = rawValue
                }
            }
            updateValues(valsMap, getCurrentTimestamp(), false)
        } else {
            logger.e(TAG, "Failed to parse test data values")
        }
    }
}