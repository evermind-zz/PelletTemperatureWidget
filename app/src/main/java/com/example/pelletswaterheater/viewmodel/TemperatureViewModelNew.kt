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
import com.example.pelletswaterheater.utils.MetadataUtils.loadCachedMetadata
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
 * ViewModel for managing metadata, converted values, logging, and UI selections
 * in the Pellets Water Heater app.  Uses a reactive pipeline so that *both* the
 * UI and the background logger consume the same stream of converted values
 * while still allowing manual refreshes that **do not** trigger logging.
 */
class TemperatureViewModelNew(
    private val contextProvider: ContextProvider,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val logger: ILogger = Logger,
    private val temperatureLogger: TemperatureLogger = TemperatureLoggerCsvImpl(contextProvider, logger),
    private val networkAccess: NetworkAccess = NetworkAccess(),
    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val networkAccessMutex = Mutex()
    private val TAG = TemperatureViewModelNew::class.java.simpleName

    // ────────────────────────────────────────────────────────────────────────────
    //  METADATA (unchanged)
    // ────────────────────────────────────────────────────────────────────────────

    private val _metadata = MutableStateFlow<MetadataJson?>(null)
    /** Flow of the current metadata state, including server and user‑merged metadata. */
    val metadata: StateFlow<MetadataJson?> = _metadata.asStateFlow()

    // ────────────────────────────────────────────────────────────────────────────
    //  Converted values wrapper (renamed)
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * A *batch* of converted values.  The same raw/converted map can be sent
     * either because the user manually refreshed (isLoggingData = false) or
     * because the logging scheduler fetched a new sample (isLoggingData = true).
     */
    data class ConvertedBatch(
        val convertedValues: Map<String, ConvertedValue> = emptyMap(),
        val isLoggingData: Boolean = false
    )

    private val _convertedValues = MutableStateFlow(ConvertedBatch())
    /** Flow of [ConvertedBatch] consumed by both UI and logger. */
    val convertedValues: StateFlow<ConvertedBatch> = _convertedValues.asStateFlow()

    // ────────────────────────────────────────────────────────────────────────────
    //  Selections & helpers
    // ────────────────────────────────────────────────────────────────────────────

    private val _selections = MutableStateFlow(emptyList<Selection>())
    val selections: StateFlow<List<Selection>> = _selections.asStateFlow()

    private val _state = MutableStateFlow("state")
    private val _error = MutableStateFlow("error")
    private val _lastTimestamp = MutableStateFlow("timestamp")

    /** True once at least one key has `log == true`.  UI can bind to this. */
    val loggingIsViable: StateFlow<Boolean> = selections
        .map { list -> list.any(Selection::log) }
        .onEach { Log.d(TAG, "loggingIsViable -> $it") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        loadCachedMetadata()
    }

    // ────────────────────────────────────────────────────────────────────────────
    //  Data classes for UI layers (unchanged)
    // ────────────────────────────────────────────────────────────────────────────

    @Serializable
    data class ConvertedValueWrapper(
        val convertedValue: ConvertedValue,
        val section: String,
        val showSectionName: Boolean
    )

    @Serializable
    data class WidgetDataWrapper(
        val snapshot: List<ConvertedValueWrapper>,
        val state: String,
        val error: String?,
        val timestampSuccess: String
    )

    @Serializable
    data class Selection(
        val key: String,
        val log: Boolean,
        val widget: Boolean,
        val showData: Boolean,
        val showSectionName: Boolean
    )

    // ────────────────────────────────────────────────────────────────────────────
    //  Network / metadata helpers (light edits where wrapper is referenced)
    // ────────────────────────────────────────────────────────────────────────────

    fun fetchMetadata(url: String = "${TemperatureUtils.getStoredUrl()}?") {
        viewModelScope.launch {
            try {
                val body = networkAccess.getDataFromServer(url, "fetchMetaData()")
                val meta = MetadataUtils.parseServerJson(body)
                if (meta != null) {
                    contextProvider.saveMetadata(body)
                    _metadata.value = meta
                    updateSelectionsIfNeeded()
                } else logger.e(TAG, "Failed to parse server metadata")
            } catch (e: Exception) {
                logger.e(TAG, "Metadata fetch error", e)
                loadCachedMetadata()
            }
        }
    }

    /** Fetches values; `doLogging = true` when invoked from the logging scheduler. */
    fun fetchValues(url: String, caller: String, doLogging: Boolean = false): Result<String> {
        viewModelScope.launch {
            val result = getDataFromNetworkWithRetry(url, caller)
            if (result.isSuccess) {
                result.getOrNull()?.let { body ->
                    _lastTimestamp.value = TemperatureUtils.formatTimeStamp(System.currentTimeMillis())
                    parseDataAndUpdateValues(body, doLogging)
                }
            }
            TemperatureUtils.appContext?.let { TemperatureWidget().forceUpdateAll(it) }
        }
        return Result.failure(Exception("failed to fetch"))
    }

    private suspend fun getDataFromNetworkWithRetry(url: String, caller: String): Result<String> = networkAccessMutex.withLock {
        val maxAttempts = 3
        repeat(maxAttempts) { attempt ->
            try {
                val body = networkAccess.getDataFromServer(url, caller)
                return Result.success(body)
            } catch (e: Exception) {
                val cleanUrl = TemperatureUtils.maskPasswordInUrl(url)
                logger.e(TAG, "worker=$caller attempt=$attempt failure for $cleanUrl", e)
                if (attempt < maxAttempts - 1) delay(5_000)
            }
        }
        return Result.failure(Exception("failed after retries"))
    }

    /**
     * Parses server JSON and pipes it to [updateValues].  Called from UI refresh or logger.
     */
    private suspend fun parseDataAndUpdateValues(body: String, doLogging: Boolean) {
        val data = MetadataUtils.parseDataValuesJson(body) ?: run {
            logger.e(TAG, "Failed to parse server data values")
            return
        }
        val flat = buildMap<String, String> {
            data.sections.forEach { (section, entryMap) ->
                entryMap.forEach { (k, v) -> put("$section.$k", v) }
            }
        }
        updateValues(flat, doLogging)
    }

    // ────────────────────────────────────────────────────────────────────────────
    //  updateValues – purely state, no direct logging side‑effect anymore
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Converts raw sensor values into engineering units and updates [_convertedValues].
     *
     * @param rawData     Map of raw values, e.g. "system.L_ambient" → "219"
     * @param doLogging   Flag indicating these data came from the periodic logger.
     */
    fun updateValues(rawData: Map<String, String>, doLogging: Boolean = false) {
        val meta = _metadata.value ?: return
        val converted = LinkedHashMap<String, ConvertedValue>() // preserves insertion order

        meta.sections.forEach { (section, fields) ->
            fields.forEach { (key, field) ->
                val fullKey = "$section.$key"
                val raw = rawData[fullKey] ?: return@forEach
                if (field is MetadataField) {
                    converted[fullKey] = ValueConverter.convert(fullKey, raw, field)
                }
            }
        }

        // Emit new batch.  Any collector can decide what to do with isLoggingData flag.
        _convertedValues.value = ConvertedBatch(converted, isLoggingData = doLogging)
        updateSelectionsIfNeeded()
    }

    // ────────────────────────────────────────────────────────────────────────────
    //  Selections utils (only change: reference wrapper.convertedValues)
    // ────────────────────────────────────────────────────────────────────────────

    private fun updateSelectionsIfNeeded() {
        val ctx = contextProvider.getContext()
        val prefs = ctx.getSharedPreferences("pellets_prefs", Context.MODE_PRIVATE)
        val currentKeys = _convertedValues.value.convertedValues.keys
        val existingKeys = _selections.value.map { it.key }.toSet()
        if (currentKeys != existingKeys) {
            _selections.value = currentKeys.map { key ->
                _selections.value.find { it.key == key } ?: Selection(
                    key = key,
                    log = prefs.getBoolean("log_$key", false),
                    widget = prefs.getBoolean("widget_$key", false),
                    showData = prefs.getBoolean("showData_$key", true),
                    showSectionName = prefs.getBoolean("showSectionName_$key", false)
                )
            }
        }
    }

    fun loadSelections(): List<Selection> {
        val ctx = contextProvider.getContext()
        val prefs = ctx.getSharedPreferences("pellets_prefs", Context.MODE_PRIVATE)
        return _convertedValues.value.convertedValues.keys.map { key ->
            Selection(
                key = key,
                log = prefs.getBoolean("log_$key", false),
                widget = prefs.getBoolean("widget_$key", false),
                showData = prefs.getBoolean("showData_$key", true),
                showSectionName = prefs.getBoolean("showSectionName_$key", false)
            )
        }
    }

    // updateSelection / toggleAll* remain unchanged – they operate on _selections only.

    // ────────────────────────────────────────────────────────────────────────────
    //  UI helpers – flows now look at wrapper.convertedValues
    // ────────────────────────────────────────────────────────────────────────────

    fun getDataScreenValues(): StateFlow<List<ConvertedValueWrapper>> =
        _convertedValues.map { batch ->
            batch.convertedValues.mapNotNull { (key, value) ->
                val sel = _selections.value.find { it.key == key } ?: return@mapNotNull null
                if (sel.showData) {
                    ConvertedValueWrapper(value, key.substringBeforeLast('.'), sel.showSectionName)
                } else null
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun getWidgetValues(): WidgetDataWrapper {
        val snapshot = _convertedValues.value.convertedValues.mapNotNull { (key, value) ->
            val sel = _selections.value.find { it.key == key } ?: return@mapNotNull null
            if (sel.widget) ConvertedValueWrapper(value, key.substringBeforeLast('.'), sel.showSectionName) else null
        }
        return WidgetDataWrapper(snapshot, _state.value, _error.value, _lastTimestamp.value)
    }

    /**
     * Flow of values to be persisted by the logger.  Emits **only** when the
     * current [ConvertedBatch] was fetched for logging (`isLoggingData == true`).
     */
    fun getLogValues(): StateFlow<List<ConvertedValueWrapper>> =
        _convertedValues.map { batch ->
            if (!batch.isLoggingData) return@map emptyList()
            batch.convertedValues.mapNotNull { (key, value) ->
                val sel = _selections.value.find { it.key == key } ?: return@mapNotNull null
                if (sel.log) ConvertedValueWrapper(value, key.substringBeforeLast('.'), showSectionName = false) else null
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Remaining stats / utility methods unchanged …
}
