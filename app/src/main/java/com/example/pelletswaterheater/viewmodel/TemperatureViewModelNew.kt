package com.example.pelletswaterheater.viewmodel

import com.example.pelletswaterheater.TemperatureWidget
import com.example.pelletswaterheater.preferences.PreferencesRepository
import com.example.pelletswaterheater.repository.LoggingRepository
import com.example.pelletswaterheater.repository.MetadataRepository
import com.example.pelletswaterheater.repository.NetworkRepository
import com.example.pelletswaterheater.repository.SelectionRepository
import com.example.pelletswaterheater.repository.StateRepository
import com.example.pelletswaterheater.repository.ValueRepository
import com.example.pelletswaterheater.utils.ContextProvider
import com.example.pelletswaterheater.utils.ConvertedValue
import com.example.pelletswaterheater.utils.ILogger
import com.example.pelletswaterheater.utils.Logger
import com.example.pelletswaterheater.utils.MetadataJson
import com.example.pelletswaterheater.utils.TemperatureUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Simplified ViewModel that coordinates between repositories.
 * Handles UI state and delegates business logic to repositories.
 */
class TemperatureViewModelNew(
    private val contextProvider: ContextProvider,
    private val logger: ILogger = Logger,
    private val preferencesRepository: PreferencesRepository,
    private val temperatureUtils: TemperatureUtils,

    private val metadataRepository: MetadataRepository,
    private val valueRepository: ValueRepository,
    private val selectionRepository: SelectionRepository,
    private val networkRepository: NetworkRepository,
    private val loggingRepository: LoggingRepository,
    private val stateRepository: StateRepository,
    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    companion object {
        private val TAG = TemperatureViewModelNew::class.java.simpleName
    }

    // Expose metadata from MetadataRepository
    val metadata = metadataRepository.metadata

    // Expose converted values from ValueRepository
    val convertedValues = valueRepository.convertedValues
    val lastTimestamp = valueRepository.lastTimestamp

    // Expose selections from SelectionRepository
    val selections = selectionRepository.selections

    // Expose system stats from StateRepository
    val systemStats = stateRepository.systemStats

    // Derived state
    val loggingIsViable = selections
        .map { list -> list.any { it.log } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // DataScreen values - combines convertedValues and selections
    val dataScreenValues: StateFlow<List<ConvertedValueWrapper>> = combine(convertedValues, selections) { values, selections ->
        val selectionMap = selections.associateBy { it.key }
        values.mapNotNull { (key, value) ->
            val selection = selectionMap[key] ?: return@mapNotNull null
            if (selection.showData) {
                ConvertedValueWrapper(
                    convertedValue = value,
                    section = key.substringBeforeLast("."),
                    showSectionName = selection.showSectionName,
                )
            } else null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Widget values - combines convertedValues and selections
    val widgetValues: StateFlow<WidgetDataWrapper> = combine(convertedValues, selections, stateRepository.state, stateRepository.error, lastTimestamp) { values, selections, state, error, timestamp ->
        val selectionMap = selections.associateBy { it.key }
        val snapshot = values.mapNotNull { (key, value) ->
            val selection = selectionMap[key] ?: return@mapNotNull null
            if (selection.widget) {
                ConvertedValueWrapper(
                    convertedValue = value,
                    section = key.substringBeforeLast("."),
                    showSectionName = selection.showSectionName
                )
            } else null
        }
        WidgetDataWrapper(
            snapshot = snapshot,
            state = state,
            error = error,
            timestampSuccess = timestamp
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WidgetDataWrapper(emptyList(), "N/A", null, "N/A"))

    init {
        // Initialize selections when metadata is available
        metadataRepository.metadata.onEach { metadata ->
            if (metadata != null) {
                viewModelScope.launch {
                    val allKeys = metadata.sections.flatMap { (sectionKey, sectionValuesMap) ->
                        sectionValuesMap.keys.map { valuesKey -> "$sectionKey.$valuesKey" }
                    }.toSet()
                    selectionRepository.updateSelectionsIfNeeded(allKeys)
                }
            }
        }.launchIn(viewModelScope)
    }

    /**
     * Fetches metadata from the server.
     */
    fun fetchMetadata() {
        viewModelScope.launch {
            val url = temperatureUtils.getServerUrl()
            stateRepository.updateState("Loading...")
            val result = metadataRepository.fetchMetadata(url)
            if (result.isFailure) {
                val exception = result.exceptionOrNull()
                if (exception?.message == "network busy") {
                    logger.d(TAG, "Metadata fetch skipped: network busy")
                } else {
                    logger.e(TAG, "Metadata fetch error: $exception", exception)
                    metadataRepository.loadCachedMetadata()
                    stateRepository.updateStats("Failed to fetch metadata", "Error")
                }
            }
        }
    }

    /**
     * Fetches values from the server with retry logic.
     */
    suspend fun fetchValues(url: String, caller: String, doLogging: Boolean = false): Result<String> {
        stateRepository.updateState("Loading...")
        val result = networkRepository.fetchDataWithRetry(url, caller, maxAttempts = 3)
        if (result.isSuccess) {
            val body = result.getOrNull()
            body?.let {
                val timestamp = valueRepository.getCurrentTimestamp()
                valueRepository.updateValues(
                    parseDataValuesToMap(it),
                    timestamp,
                    doLogging
                )
                stateRepository.updateStats(error = null, state = "Success")
                // Log if requested
                if (doLogging) {
                    val selectedKeys = selections.value.filter { it.log }.map { it.key }.toSet()
                    loggingRepository.logValues(
                        values = valueRepository.convertedValues.value,
                        keyOrder = parseDataValuesToMap(it).keys.toList(),
                        timestamp = timestamp,
                        selectedKeys = selectedKeys
                    )
                }
            } ?: stateRepository.updateStats("Fetch failed: empty server reply", "Error")
        } else {
            val exception = result.exceptionOrNull()
            if (exception?.message == "network busy") {
                logger.d(TAG, "worker=$caller Network busy, will retry on next cycle")
            } else {
                stateRepository.updateStats(
                    error = "Failed to fetch temperature after retries",
                    state = "Error"
                )
            }
        }
        return result
    }

    private fun parseDataValuesToMap(jsonBody: String): Map<String, String> {
        val values = com.example.pelletswaterheater.utils.MetadataUtils.parseDataValuesJson(jsonBody)
        if (values == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        values.sections.forEach { (section, valueMap) ->
            valueMap.forEach { (jsonName, rawValue) ->
                map["${section}.${jsonName}"] = rawValue
            }
        }
        return map
    }

    fun loadTestDataFromStorage() {
        viewModelScope.launch {
            valueRepository.loadTestDataFromStorage()
        }
    }

    fun loadUserMetadata(jsonString: String) {
        viewModelScope.launch {
            metadataRepository.loadUserMetadata(jsonString)
        }
    }

    fun resetMetadata() {
        valueRepository.convertedValues.value = emptyMap()
        fetchMetadata()
    }

    // Selection methods - delegate to SelectionRepository
    suspend fun loadSelections(): List<SelectionRepository.Selection> {
        return selectionRepository.loadSelections()
    }

    fun updateSelection(
        key: String,
        log: Boolean? = null,
        widget: Boolean? = null,
        showData: Boolean? = null,
        showSectionName: Boolean? = null
    ) {
        selectionRepository.updateSelection(key, log, widget, showData, showSectionName)
    }

    fun toggleAllLog(checked: Boolean) {
        selectionRepository.toggleAllLog(checked)
    }

    fun toggleAllWidget(checked: Boolean) {
        selectionRepository.toggleAllWidget(checked)
    }

    fun toggleAllShowData(checked: Boolean) {
        selectionRepository.toggleAllShowData(checked)
    }

    fun toggleAllSectionName(checked: Boolean) {
        selectionRepository.toggleAllSectionName(checked)
    }

    // Metadata file methods - delegate to MetadataRepository
    fun getMetadataFileName(): String = metadataRepository.getCurrentMetadataFilename()
    fun setCurrentMetadata(filename: String) { metadataRepository.setCurrentMetadataFilename(filename) }
    fun getMetadataFiles(): kotlinx.coroutines.flow.StateFlow<List<java.io.File>> = metadataRepository.getMetadataFiles()
    fun deleteMetadataFile(file: java.io.File) { metadataRepository.deleteMetadataFile(file) }
    fun shareMetadata(context: android.content.Context) { metadataRepository.shareMetadata(context) }
    fun importMetadata(context: android.content.Context) { metadataRepository.importMetadata(context) }
    fun getMetadataUri(file: java.io.File): android.net.Uri? = (metadataRepository as? MetadataRepositoryImpl)?.getMetadataUri(file)
    fun saveUserMetadata(jsonString: String) { (metadataRepository as? MetadataRepositoryImpl)?.saveUserMetadata(jsonString) }
    fun getCurrentMetadataJson(): String? = metadataRepository.getMetadataContent(getMetadataFileName())

    // Error/State methods - delegate to StateRepository
    fun updateError(error: String?) { stateRepository.updateError(error) }
    fun updateState(state: String) { stateRepository.updateState(state) }
    fun updateStats(error: String?, state: String) { stateRepository.updateStats(error, state) }

    /**
     * Data class representing a converted value with section and display preferences for UI.
     */
    data class ConvertedValueWrapper(
        val convertedValue: ConvertedValue,
        val section: String,
        val showSectionName: Boolean,
    )

    /**
     * Data class for widget data snapshot.
     */
    data class WidgetDataWrapper(
        val snapshot: List<ConvertedValueWrapper>,
        val state: String,
        val error: String?,
        val timestampSuccess: String
    )
}