package com.example.pelletswaterheater.viewmodel

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.example.pelletswaterheater.NetworkAccess
import com.example.pelletswaterheater.TemperatureUtils
import com.example.pelletswaterheater.TemperatureWidget
import com.example.pelletswaterheater.utils.ContextProvider
import com.example.pelletswaterheater.utils.ConvertedValue
import com.example.pelletswaterheater.utils.ILogger
import com.example.pelletswaterheater.utils.Logger
import com.example.pelletswaterheater.utils.MetadataField
import com.example.pelletswaterheater.utils.MetadataJson
import com.example.pelletswaterheater.utils.MetadataLabel
import com.example.pelletswaterheater.utils.MetadataUtils
import com.example.pelletswaterheater.utils.TemperatureLogger
import com.example.pelletswaterheater.utils.TemperatureLoggerCsvImpl
import com.example.pelletswaterheater.utils.ValueConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileNotFoundException

/**
 * ViewModel for managing metadata, converted values, logging, and UI selections in the Pellets Water Heater app.
 * Handles metadata fetching, value conversion, logging, and checkbox selections for DataScreen, widget, and logs.
 * Replaces TemperatureViewModel to integrate F3 UI requirements.
 */
class TemperatureViewModelNew(
    private val contextProvider: ContextProvider,
    private val logger: ILogger = Logger,

    private val temperatureLogger: TemperatureLogger = TemperatureLoggerCsvImpl(
        contextProvider,
        logger
    ),
    private val networkAccess: NetworkAccess = NetworkAccess(),
    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    companion object {
        private val TAG = TemperatureViewModelNew::class.java.simpleName
    }

    /* use it only in debug builds
    private val _convertedValues: MutableStateFlow<ConvertedBatch> =
        if (BuildConfig.DEBUG) DebugStateFlow(MutableStateFlow(ConvertedBatch()))
        else MutableStateFlow(ConvertedBatch())

     */


    private val networkAccessMutex = Mutex()

    private val _metadata = MutableStateFlow<MetadataJson?>(null)
    /**
     * Flow of the current metadata state, including server and user-merged metadata.
     */
    val metadata: StateFlow<MetadataJson?> = _metadata.asStateFlow()

    private val _convertedValues = MutableStateFlow<Map<String, ConvertedValue>>(emptyMap())
    /**
     * Flow of converted values (e.g., "system.L_ambient" to [ConvertedValue]) for UI and logging.
     */
    val convertedValues: StateFlow<Map<String, ConvertedValue>> = _convertedValues.asStateFlow()

    private val _selections = MutableStateFlow<List<Selection>>(emptyList())
    /**
     * Flow of checkbox selections for logging, widget display, DataScreen, and section/name visibility.
     */
    val selections: StateFlow<List<Selection>> = _selections.asStateFlow()

    private val _state = MutableStateFlow<String>("Start")
    private val _error = MutableStateFlow<String?>(null)
    private val _lastTimestamp = MutableStateFlow<String>("N/A")

    @Serializable
    data class SystemStats(
        val lastTimestamp : StateFlow<String>,
        val state: StateFlow<String>,
        val error : StateFlow<String?>
    )
    val systemStats = SystemStats(_lastTimestamp.asStateFlow(), _state.asStateFlow(), _error.asStateFlow())

    val loggingIsViable = selections
        .map { list -> list.any(Selection::log) }
        /* DBG .onEach { Log.d("VM", "loggingIsViable -> $it") } // easier to read */
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val statsMutex = Mutex()

    init {
        loadCachedMetadata()
        // DBG viewModelScope.launch {
        // DBG     selections.onEach {
        // DBG         println("EMIT ${loggingIsViable.value} -> $it") }.collect()
        // DBG }

    }

    /**
     * Data class representing a converted value with section and display preferences for UI.
     *
     * @property convertedValue The converted value from [ValueConverter].
     * @property section The section name (e.g., "system", "hk1") for UI grouping.
     * @property showSectionName Whether to display section and name in UI.
     */
    @Serializable
    data class ConvertedValueWrapper(
        val convertedValue: ConvertedValue,
        val section: String,
        val showSectionName: Boolean,
    )

    @Serializable
    data class WidgetDataWrapper(
        val snapshot: List<ConvertedValueWrapper>,
        val state: String,
        val error: String?,
        val timestampSuccess: String
    )

    /**
     * Data class representing user selections for UI and logging.
     *
     * @property key The field key (e.g., "system.L_ambient").
     * @property log Whether to include in CSV logs.
     * @property widget Whether to display on the widget.
     * @property showData Whether to display on DataScreen.
     * @property showSectionName Whether to show section and name in UI.
     */
    @Serializable
    data class Selection(
        val key: String,
        val log: Boolean,
        val widget: Boolean,
        val showData: Boolean,
        val showSectionName: Boolean
    )

    /**
     * Fetches metadata from the server and saves it to storage.
     */
    fun fetchMetadata(url: String = "${TemperatureUtils.getStoredUrl()}?") {  // append ? to get the metadata
        viewModelScope.launch {
            try {
                val body = networkAccess.getDataFromServer(url, "fetchMetadata()")
                val metadata = MetadataUtils.parseServerJson(body)
                if (metadata != null) {
                    contextProvider.saveMetadata(body)
                    _metadata.value = metadata
                    updateSelectionsIfNeeded()
                } else {
                    logger.e(TAG, "Failed to parse server metadata")
                }
            } catch (e: Exception) {
                logger.e(TAG, "Metadata fetch error: $e", e)
                loadCachedMetadata()
            }
        }
    }

    /**
     * Fetches metadata from the server and saves it to storage.
     *
     * Should be called by the worker
     * @param url for server aka http://<IP:PORT>all
     * @param caller eg which worker called, only for interval logging used
     * @param doLogging log via [TemperatureLogger]
     *
     */
    fun fetchValues(url: String, caller: String, doLogging: Boolean = false) : Result<String> {
        viewModelScope.launch {
            val result = getDataFromNetworkWithRetry(url, caller)
            if (result.isSuccess) {
                val body = result.getOrNull()
                body?.let {
                    val timestamp = getCurrentTimestamp()
                    _lastTimestamp.value = TemperatureUtils.formatTimeStamp(timestamp)
                    parseDataAndUpdateValues(it, timestamp, doLogging)
                } ?: updateStats("Fetch failed: empty server reply", "Error" )
            }
            TemperatureUtils.appContext?.let {
                TemperatureWidget().forceUpdateAll(it)
            }
        }
        return Result.failure(Exception("failed to fetch"))
    }

    // remove the password from the url inside the exception message
    private fun maskExceptionMessageAndLogIt(
        e: Exception,
        url: String,
        whichWorker: String?,
        attempt: Int
    ) {
        val refreshedMaskedUrl = TemperatureUtils.maskPasswordInUrl(url)
        val maskedOriginalMessage = e.message?.replace(url, refreshedMaskedUrl) ?: "Unknown error"
        val maskedException = when (e) {
            is FileNotFoundException -> FileNotFoundException(maskedOriginalMessage)
            else -> e.javaClass.getConstructor(String::class.java)
                .newInstance(maskedOriginalMessage)
        }.apply { stackTrace = e.stackTrace }
        val maskedMessage =
            "worker=$whichWorker Failed to fetch temperature (attempt $attempt): $maskedOriginalMessage"
        logger.e(TAG, maskedMessage, RuntimeException(maskedMessage, maskedException))
    }

    suspend fun getDataFromNetworkWithRetry(url: String, caller: String): Result<String> = networkAccessMutex.withLock {
        val maxAttempts = 3
        repeat(maxAttempts) { attempt ->
            try {
                updateState("Loading...")
                val body = networkAccess.getDataFromServer(url, caller)
                logger.d(TAG, "worker=$caller Success: attempt=$attempt")
                return Result.success(body)
            } catch (e: Exception) {
                maskExceptionMessageAndLogIt(e, url, caller, attempt)
                if (attempt < maxAttempts - 1) delay(5000L) // Wait 5s before retry
            }
        }
        updateStats(error = "Failed to fetch temperature after $maxAttempts attempts", state = "Error")
        return Result.failure(Exception("failed to fetch"))
    }

    fun loadTestDataFromStorage() {
        viewModelScope.launch {
            val file = contextProvider.getContext().filesDir.resolve("test-values.json")
            if (file.exists()) {
                val body = file.readText()
                parseDataAndUpdateValues(body)
            }
        }
    }

    private suspend fun TemperatureViewModelNew.parseDataAndUpdateValues(
        body: String,
        timestamp: Long = getCurrentTimestamp(),
        doLogging: Boolean = false
    ) {
        val values = MetadataUtils.parseDataValuesJson(body)
        if (values != null) {
            val valsMap = mutableMapOf<String, String>()
            values.sections.forEach { (section, valueMap) ->
                valueMap.forEach { (jsonName, rawValue) ->
                    valsMap["${section}.${jsonName}"] = rawValue
                }
            }
            updateValues(valsMap, timestamp, doLogging)
            updateStats(error = null, state = "Success")
        } else {
            val errMsg = "Failed to parse server data values"
            updateStats(errMsg, "parsing failed")
            logger.e(TAG, errMsg)
        }
    }

    private fun getCurrentTimestamp(): Long = System.currentTimeMillis()

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
                wrapUpdateSelectionAndConvertedValuesUpdate(emptyMap())
            } else {
                logger.e(TAG, "Failed to parse user metadata")
            }
        }
    }

    /**
     * Resets converted values and fetches fresh metadata from the server.
     *
     */
    fun resetMetadata() {
        _convertedValues.value = emptyMap()
        fetchMetadata()
    }

    /**
     * Updates converted values based on raw data and logs selected values.
     *
     * @param rawData A map of raw values (e.g., "system.L_ambient" to "219").
     */
    fun updateValues(rawData: Map<String, String>, timestamp: Long = getCurrentTimestamp(), doLogging: Boolean = false) {
        val metadata = _metadata.value ?: return
        val converted = mutableMapOf<String, ConvertedValue>()
        val keyOrder = rawData.keys.toList()
        metadata.sections.forEach { (section, fields) ->
            fields.forEach { (key, field) ->
                val fullKey = "$section.$key"
                val rawValue = rawData[fullKey] ?: return@forEach
                when (field) {
                    is MetadataField -> {
                        logger.d(TAG, " updateValues() Field $key = ${field.value} (${field.unit ?: "no unit"})")
                        converted[fullKey] = ValueConverter.convert(fullKey, rawValue, field)
                    }
                    is MetadataLabel -> logger.d(TAG, "updateValues() Label $key = ${field.label}")
                }
            }
        }

        wrapUpdateSelectionAndConvertedValuesUpdate(converted)

        if (doLogging) {
            // Filter converted values to include only those selected for logging
            val logValues = converted.filterKeys { key ->
                _selections.value.find { it.key == key }?.log ?: false
            }
            temperatureLogger.logValues(logValues, keyOrder.filter { it in logValues }, timestamp)
        }
    }

    /**
     * Wrap [updateSelectionsIfNeeded] and [_convertedValues].
     *
     * First update [_selections] than set new values to [_convertedValues]
     * -> as [_convertedValues] depends on [_selections]
     *
     * even tough it would be good to call loadSelections() calling on init may fail
     */
    private fun wrapUpdateSelectionAndConvertedValuesUpdate(converted: Map<String, ConvertedValue>) {
        updateSelectionsIfNeeded(converted)
        _convertedValues.value = converted
    }

    private fun getCurrentMetadataContent(): String? {
        val currentMetadataFilename = TemperatureUtils.getCurrentMetadataFile()
        return contextProvider.getMetadata(currentMetadataFilename)
    }

    fun getMetadataContent(filename: String): String? {
        return contextProvider.getMetadata(filename)
    }

    /**
     * Loads cached metadata from storage.
     */
    private fun loadCachedMetadata() {
        viewModelScope.launch {
            //val cachedJson = contextProvider.loadCachedMetadata()
            val cachedJson = getCurrentMetadataContent()
            if (cachedJson != null) {
                val cached = MetadataUtils.parseServerJson(cachedJson)
                _metadata.value = cached
                updateSelectionsIfNeeded()
            }
            if (_metadata.value == null) {
                logger.w(TAG, "No cached metadata found")
            }
        }
    }

    /**
     * Updates selections to match current converted values, loading from SharedPreferences.
     */
    private fun updateSelectionsIfNeeded(
        converted: Map<String, ConvertedValue> = _convertedValues.value
    ) {
        val context = contextProvider.getContext()
        val sharedPrefs = context.getSharedPreferences("pellets_prefs", Context.MODE_PRIVATE)
        val currentKeys = converted.keys
        val existingKeys = _selections.value.map { it.key }.toSet()

        // use the _metadata's inner sections values keys if there are no currentKeys available
        var availableCurrentKeys: Set<String> = if (currentKeys.isEmpty() && _metadata.value != null) {
            _metadata.value!!
                .sections.flatMap { (sectionKey, sectionValuesMap) ->
                    // create a List<String> like "system.L_ambient"
                    sectionValuesMap.keys.map { valuesKey -> "$sectionKey.$valuesKey" }
                }
                .toSet()
        } else {
            currentKeys
        }
        if (availableCurrentKeys != existingKeys) {
            _selections.value = availableCurrentKeys.map { key ->
                _selections.value.find { it.key == key } ?: Selection(
                    key = key,
                    log = sharedPrefs.getBoolean("log_$key", false),
                    widget = sharedPrefs.getBoolean("widget_$key", false),
                    showData = sharedPrefs.getBoolean("showData_$key", true),
                    showSectionName = sharedPrefs.getBoolean("showSectionName_$key", false)
                )
            }
        }
    }

    /**
     * Loads checkbox selections from SharedPreferences.
     *
     * @return List of [Selection] objects for all current values.
     */
    fun loadSelections(): List<Selection> {
        val context = contextProvider.getContext()
        val sharedPrefs = context.getSharedPreferences("pellets_prefs", Context.MODE_PRIVATE)
        return _convertedValues.value.keys.map { key ->
            Selection(
                key = key,
                log = sharedPrefs.getBoolean("log_$key", false),
                widget = sharedPrefs.getBoolean("widget_$key", false),
                showData = sharedPrefs.getBoolean("showData_$key", true),
                showSectionName = sharedPrefs.getBoolean("showSectionName_$key", false)
            )
        }
    }

    /**
     * Updates a single selection for a specific key.
     *
     * @param key The value key to update.
     * @param log Whether to log the value (optional).
     * @param widget Whether to show on widget (optional).
     * @param showData Whether to show on DataScreen (optional).
     * @param showSectionName Whether to show section and name in UI (optional).
     */
    fun updateSelectionOld(
        key: String,
        log: Boolean? = null,
        widget: Boolean? = null,
        showData: Boolean? = null,
        showSectionName: Boolean? = null
    ) {
        val context = contextProvider.getContext()
        val sharedPrefs = context.getSharedPreferences("pellets_prefs", Context.MODE_PRIVATE)
        _selections.value = _selections.value.map { sel ->
            if (sel.key == key) {
                val newSel = sel.copy(
                    log = log ?: sel.log,
                    widget = widget ?: sel.widget,
                    showData = showData ?: sel.showData,
                    showSectionName = showSectionName ?: sel.showSectionName
                )
                sharedPrefs.edit {
                    putBoolean("log_$key", newSel.log)
                    putBoolean("widget_$key", newSel.widget)
                    putBoolean("showData_$key", newSel.showData)
                    putBoolean("showSectionName_$key", newSel.showSectionName)
                }
                newSel
            } else sel
        }
    }

    /**
     * Updates a single selection for a specific key.
     *
     * @param key The value key to update.
     * @param log Whether to log the value (optional).
     * @param widget Whether to show on widget (optional).
     * @param showData Whether to show on DataScreen (optional).
     * @param showSectionName Whether to show section and name in UI (optional).
     */
    fun updateSelection(
        key: String,
        log: Boolean? = null,
        widget: Boolean? = null,
        showData: Boolean? = null,
        showSectionName: Boolean? = null
    ) {
        // 1) Atomically update _selections and capture the new Selection if it changed
        var changedItem: Selection? = null          // will stay null if nothing really changed

        _selections.update { list ->
            list.map { sel ->
                if (sel.key == key) {
                    val newSel = sel.copy(
                        log = log ?: sel.log,
                        widget = widget ?: sel.widget,
                        showData = showData ?: sel.showData,
                        showSectionName = showSectionName ?: sel.showSectionName
                    )
                    if (newSel != sel) changedItem = newSel   // mark that we need to persist
                    newSel
                } else sel
            }
        }
        // If at least one property differs, the *new list* is not equal to the old one,
        // so MutableStateFlow emits automatically.

        // 2) Persist only when a change really happened
        changedItem?.let { sel ->
            val prefs = contextProvider
                .getContext()
                .getSharedPreferences("pellets_prefs", Context.MODE_PRIVATE)

            prefs.edit {
                putBoolean("log_$key",            sel.log)
                putBoolean("widget_$key",         sel.widget)
                putBoolean("showData_$key",       sel.showData)
                putBoolean("showSectionName_$key", sel.showSectionName)
            }
        }
    }

    /**
     * Toggles the 'log' selection for all values.
     *
     * @param checked Whether to enable logging for all values.
     */
    fun toggleAllLog(checked: Boolean) {
        val context = contextProvider.getContext()
        val sharedPrefs = context.getSharedPreferences("pellets_prefs", Context.MODE_PRIVATE)
        _selections.value = _selections.value.map {
            val newSel = it.copy(log = checked)
            sharedPrefs.edit { putBoolean("log_${it.key}", checked) }
            newSel
        }
    }

    /**
     * Toggles the 'widget' selection for all values.
     *
     * @param checked Whether to show all values on the widget.
     */
    fun toggleAllWidget(checked: Boolean) {
        val context = contextProvider.getContext()
        val sharedPrefs = context.getSharedPreferences("pellets_prefs", Context.MODE_PRIVATE)
        _selections.value = _selections.value.map {
            val newSel = it.copy(widget = checked)
            sharedPrefs.edit { putBoolean("widget_${it.key}", checked) }
            newSel
        }
    }

    /**
     * Toggles the 'showData' selection for all values.
     *
     * @param checked Whether to show all values on DataScreen.
     */
    fun toggleAllShowData(checked: Boolean) {
        val context = contextProvider.getContext()
        val sharedPrefs = context.getSharedPreferences("pellets_prefs", Context.MODE_PRIVATE)
        _selections.value = _selections.value.map {
            val newSel = it.copy(showData = checked)
            sharedPrefs.edit { putBoolean("showData_${it.key}", checked) }
            newSel
        }
    }

    /**
     * Toggles the 'showSectionName' selection for all values.
     *
     * @param checked Whether to show section and name for all values in UI.
     */
    fun toggleAllSectionName(checked: Boolean) {
        val context = contextProvider.getContext()
        val sharedPrefs = context.getSharedPreferences("pellets_prefs", Context.MODE_PRIVATE)
        _selections.value = _selections.value.map {
            val newSel = it.copy(showSectionName = checked)
            sharedPrefs.edit { putBoolean("showSectionName_${it.key}", checked) }
            newSel
        }
    }

    /**
     * Provides filtered [ConvertedValueWrapper] flow for DataScreen, respecting 'showData' and 'showSectionName' selections.
     *
     * @return Flow of filtered values with section and display preferences.
     */
    fun getDataScreenValues(): StateFlow<List<ConvertedValueWrapper>> {
        return _convertedValues.map { values ->
            values.mapNotNull { (key, value) ->
                val selection = _selections.value.find { it.key == key } ?: return@mapNotNull null
                if (selection.showData) {
                    ConvertedValueWrapper(
                        convertedValue = value,
                        section = key.substringBeforeLast("."),
                        showSectionName = selection.showSectionName,
                    )
                } else null
            }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())
    }

    /**
     * Provides filtered [ConvertedValueWrapper] flow for the widget, respecting 'widget' and 'showSectionName' selections.
     *
     * @return Flow of filtered values with section and display preferences.
     */
    fun getWidgetValues(): WidgetDataWrapper {
        val snapshot = _convertedValues.value.mapNotNull { (key, value) ->
            val selection = _selections.value.find { it.key == key } ?: return@mapNotNull null
            if (selection.widget) {
                ConvertedValueWrapper(
                    convertedValue = value,
                    section = key.substringBeforeLast("."),
                    showSectionName = selection.showSectionName
                )
            } else null
        }

        return WidgetDataWrapper(
            snapshot = snapshot,
            state = _state.value,
            error = _error.value,
            timestampSuccess = _lastTimestamp.value
        )
    }

    fun updateError(error: String?) {
        logger.d(TAG, "Error updated: $error")
        _error.update { error }
    }

    fun updateState(state: String) {
        logger.d(TAG, "State updated: $state")
        _state.update { state }
    }

    fun updateStats(error: String?, state: String) {
        _error.update { error }
        _state.update { state }
    }

    /////////// metadata section
    private val _metadataFiles = MutableStateFlow<List<File>>(emptyList())
    /**
     * Flow of the stored logfiles.
     */
    private val metadataFiles: StateFlow<List<File>> = _metadataFiles.asStateFlow()

    fun getMetadataFileName(): String {
        return TemperatureUtils.getCurrentMetadataFile()
    }

    fun getCurrentMetadataJson(): String {
        return try {
            //val raw = metadataFile?.readText() ?: "{}"
            //val obj = JSONObject(raw)
            //obj.toString(4) // pretty print with indent
            ""
        } catch (e: Exception) {
            "{}"
        }
    }

    // Dummy examples
    fun shareMetadata(context: Context) { /* share intent logic */ }

    fun importMetadata(context: Context) { /* file picker logic */ }

    fun getMetadataFiles() : StateFlow<List<File>> {
        val metadataDir = File(contextProvider.getContext().filesDir, "")
        _metadataFiles.update {
            metadataDir.listFiles() { file ->
                file.isFile && file.extension == "json"
            } ?.toList() ?: emptyList()
        }
        return metadataFiles
    }

    fun saveUserMetadata(string: kotlin.String) {}
    fun getMetadataUri(file: java.io.File) : Uri? {
        return null
    }


    fun deleteMetadataFile(file: File) {
        val context = contextProvider.getContext()
        val logDir = File(context.filesDir, "TODO")
        val target = File(logDir, file.name)
        if (target.exists()) {
            target.delete()
            // updateMetadataFiles()
        }
    }

    fun setCurrentMetadata(filename: String) {
        TemperatureUtils.setCurrentMetadataFile(filename)
    }



}
