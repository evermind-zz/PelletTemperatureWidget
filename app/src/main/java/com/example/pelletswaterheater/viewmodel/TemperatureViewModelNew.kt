package com.example.pelletswaterheater.viewmodel

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import java.io.FileNotFoundException

/**
 * ViewModel for managing metadata, converted values, logging, and UI selections in the Pellets Water Heater app.
 * Handles metadata fetching, value conversion, logging, and checkbox selections for DataScreen, widget, and logs.
 * Replaces TemperatureViewModel to integrate F3 UI requirements.
 */
class TemperatureViewModelNew(
    private val contextProvider: ContextProvider,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val logger: ILogger = Logger,
    private val temperatureLogger: TemperatureLogger = TemperatureLoggerCsvImpl(
        contextProvider,
        logger
    ),
    private val networkAccess: NetworkAccess = NetworkAccess(),
    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
)  {
    private val networkAccessMutex = Mutex()
    private val TAG = TemperatureViewModelNew::class.java.simpleName

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

    private val _state = MutableStateFlow<String>("state")
    private val _error = MutableStateFlow<String>("error")
    private val _lastTimestamp = MutableStateFlow<String>("timestamp")

    private val _isLoggingViable = MutableStateFlow<Boolean>(false)
    val isLoggingViable: StateFlow<Boolean> = _isLoggingViable.asStateFlow()

    //var loggingIsViable: StateFlow<Boolean> = selections
    //    .map { list -> list.any {
    //        println("EMIT HERE this is ${it.log}")
    //        it.log
    //    } }
    //    .stateIn(
    //        scope = viewModelScope,
    //        started = SharingStarted.WhileSubscribed(5_000),
    //        initialValue = false
    //    )
    val loggingIsViable = selections
        .map { list -> list.any(Selection::log) }
        .onEach { Log.d("VM", "loggingIsViable -> $it") } // easier to read
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val statsMutex = Mutex()

    init {
        loadCachedMetadata()
        viewModelScope.launch {
            selections.onEach { println("EMIT ${loggingIsViable.value} -> $it") }.collect()
        }

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
                val body = networkAccess.getDataFromServer(url, "fetchMetaData()")
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
            //val body = networkAccess.getDataFromServer(url, caller)
            val result = getDataFromNetworkWithRetry(url, caller)
            if (result.isSuccess) {
                val body = result.getOrNull()
                body?.let {
                    _lastTimestamp.value = TemperatureUtils.formatTimeStamp(System.currentTimeMillis())
                    parseDataAndUpdateValues(it, doLogging)
                }
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
                // TODO below should maybe also handled by the viewModel
                // updateWidget(whichWorker)
                return Result.success(body)
            } catch (e: Exception) {
                maskExceptionMessageAndLogIt(e, url, caller, attempt)
                if (attempt < maxAttempts - 1) delay(5000L) // Wait 5s before retry
            } finally {
                //accessTimeMutex.withLock { lastAccessTime = System.currentTimeMillis() }
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
            updateValues(valsMap, doLogging)
            updateStats(error = null, state = "Success")
            updateSelectionsIfNeeded()
        } else {
            logger.e(TAG, "Failed to parse server data values")
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
                _convertedValues.value = emptyMap()
                updateSelectionsIfNeeded()
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
    fun updateValues(rawData: Map<String, String>, doLogging: Boolean = false) {
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



        _convertedValues.value = converted
        updateSelectionsIfNeeded()
        if (doLogging) {
            // Filter converted values to include only those selected for logging
            val logValues = converted.filterKeys { key ->
                _selections.value.find { it.key == key }?.log ?: false
            }
            //if (logValues.isEmpty()) {
            //    _isLoggingViable.value = false
            //} else {
            //    _isLoggingViable.value = true
                temperatureLogger.logValues(logValues, keyOrder.filter { it in logValues })
            //}
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
    private fun updateSelectionsIfNeeded() {
        val context = contextProvider.getContext()
        val sharedPrefs = context.getSharedPreferences("pellets_prefs", Context.MODE_PRIVATE)
        val currentKeys = _convertedValues.value.keys.toList()
        val existingKeys = _selections.value.map { it.key }.toSet()
        if (currentKeys.toSet() != existingKeys) {
            _selections.value = currentKeys.map { key ->
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
    //fun getWidgetValuesOld(): WidgetDataWrapper {
    //    return WidgetDataWrapper((_convertedValues.map { values ->
    //        values.mapNotNull { (key, value) ->
    //            val selection = _selections.value.find { it.key == key } ?: return@mapNotNull null
    //            if (selection.widget) {
    //                ConvertedValueWrapper(
    //                    convertedValue = value,
    //                    section = key.substringBeforeLast("."),
    //                    showSectionName = selection.showSectionName
    //                )
    //            } else null
    //        }
    //    }),
    //            state = _state.value,
    //        error = _error.value,
    //        timestampSuccess = _lastTimestamp.value
    //}

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


    /**
     * Provides filtered [ConvertedValueWrapper] flow for logging, respecting 'log' selections.
     *
     * @return Flow of filtered values with section and display preferences.
     */
    fun getLogValues(): StateFlow<List<ConvertedValueWrapper>> {
        return _convertedValues.map { values ->
            values.mapNotNull { (key, value) ->
                val selection = _selections.value.find { it.key == key } ?: return@mapNotNull null
                if (selection.log) {
                    ConvertedValueWrapper(
                        convertedValue = value,
                        section = key.substringBeforeLast("."),
                        showSectionName = false,
                    )
                } else null
            }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())
    }

    suspend fun updateError(error: String?) {
        Log.d(TAG, "Error updated: $error")
        //statsMutex.lock {
            //_error.value = error
            //updateDisplayTemperature()
        //}
        // TODO maybe here we should also trigger updating the widget
    }

    suspend fun updateState(state: String) {
        Log.d(TAG, "State updated: $state")
        //statsMutex.lock {
            //_state.value = state
            //updateDisplayTemperature()
        //}
        // TODO maybe here we should also trigger updating the widget
    }

    suspend fun updateStats(error: String?, state: String) {
        //statsMutex.lock {

        //}
        // TODO maybe here we should also trigger updating the widget
    }
    fun getMetadataFileName(): String {
        return "" // metadataFile?.name ?: "default"
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

}
