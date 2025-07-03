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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
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
 * in the Pellets Water Heater app.
 *
 * **Key idea**: every fetch – whether initiated by the user or by the periodic
 * logging scheduler – produces a **single immutable batch** of converted
 * values, emitted through one `StateFlow`.  Consumers decide what to do with a
 * batch via the `isLoggingData` flag.
 */
class TemperatureViewModelNew(
    private val contextProvider: ContextProvider,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val logger: ILogger = Logger,
    private val temperatureLogger: TemperatureLogger = TemperatureLoggerCsvImpl(contextProvider, logger),
    private val networkAccess: NetworkAccess = NetworkAccess(),
    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    // ────────────────────────────────────────────────────────────────────────────
    //  Fields & flows
    // ────────────────────────────────────────────────────────────────────────────

    private val TAG = TemperatureViewModelNew::class.java.simpleName
    private val networkAccessMutex = Mutex()

    /*──────────────────────────── METADATA ─────────────────────────────*/
    private val _metadata = MutableStateFlow<MetadataJson?>(null)
    /** Current (merged) metadata */
    val metadata: StateFlow<MetadataJson?> = _metadata.asStateFlow()

    /*─────────────────────── CONVERTED VALUES (BATCH) ───────────────────*/
    /**
     * A batch of converted values.  `isLoggingData` is **true** only when the
     * periodic logger fetched this batch; manual UI refreshes always set it to
     * **false** so they won’t be written to CSV.
     */
    data class ConvertedBatch(
        val convertedValues: Map<String, ConvertedValue> = emptyMap(),
        val isLoggingData: Boolean = false
    )

    private val _convertedValues = MutableStateFlow(ConvertedBatch())
    /** Flow consumed by UI & logger. */
    val convertedValues: StateFlow<ConvertedBatch> = _convertedValues.asStateFlow()

    /*──────────────────────── SELECTIONS & MISC  ───────────────────────*/
    private val _selections = MutableStateFlow(emptyList<Selection>())
    val selections: StateFlow<List<Selection>> = _selections.asStateFlow()

    private val _state = MutableStateFlow("state")
    private val _error = MutableStateFlow("error")
    private val _lastTimestamp = MutableStateFlow("timestamp")

    /** Emits **true** once at least one key has `log == true`. */
    val loggingIsViable: StateFlow<Boolean> = selections
        .map { list -> list.any(Selection::log) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init { loadCachedMetadata() }

    // ────────────────────────────────────────────────────────────────────────────
    //  DATA CLASSES for UI layers (unchanged)
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

    /** User checkbox selections for UI & logger. */
    @Serializable
    data class Selection(
        val key: String,
        val log: Boolean,
        val widget: Boolean,
        val showData: Boolean,
        val showSectionName: Boolean
    )

    // ────────────────────────────────────────────────────────────────────────────
    //  NETWORK & METADATA HELPERS (wrapper‑aware tweaks only)
    // ────────────────────────────────────────────────────────────────────────────

    fun fetchMetadata(url: String = "${TemperatureUtils.getStoredUrl()}?") {
        viewModelScope.launch {
            try {
                val body = networkAccess.getDataFromServer(url, "fetchMetaData()")
                MetadataUtils.parseServerJson(body)?.let { meta ->
                    contextProvider.saveMetadata(body)
                    _metadata.value = meta
                    updateSelectionsIfNeeded()
                } ?: logger.e(TAG, "Failed to parse server metadata")
            } catch (e: Exception) {
                logger.e(TAG, "Metadata fetch error", e)
                loadCachedMetadata()
            }
        }
    }

    /**
     * Fetches sensor values.  The **logger** passes `doLogging = true`; the UI
     * passes `false`.
     */
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
        repeat(3) { attempt ->
            try { return Result.success(networkAccess.getDataFromServer(url, caller)) }
            catch (e: Exception) {
                val clean = TemperatureUtils.maskPasswordInUrl(url)
                logger.e(TAG, "worker=$caller attempt=$attempt for $clean", e)
                if (attempt < 2) delay(5_000)
            }
        }
        return Result.failure(Exception("failed after retries"))
    }

    /** Converts server JSON -> `ConvertedBatch`. */
    private suspend fun parseDataAndUpdateValues(body: String, doLogging: Boolean) {
        val tree = MetadataUtils.parseDataValuesJson(body) ?: run {
            logger.e(TAG, "Failed to parse server data values"); return }
        val flat = buildMap<String, String> {
            tree.sections.forEach { (sec, mp) -> mp.forEach { (k, v) -> put("$sec.$k", v) } }
        }
        updateValues(flat, doLogging)
    }

    // ────────────────────────────────────────────────────────────────────────────
    //  CORE: updateValues – PURE (no direct CSV call)
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * Converts raw values → engineering units and publishes a new [ConvertedBatch].
     *
     * @param rawData     map of raw strings, e.g. "system.L_ambient" → "219"
     * @param doLogging   true when the periodic logger fetched this batch.
     */
    fun updateValues(rawData: Map<String, String>, doLogging: Boolean = false) {
        val meta = _metadata.value ?: return
        val converted = LinkedHashMap<String, ConvertedValue>() // keeps order

        meta.sections.forEach { (section, fields) ->
            fields.forEach { (k, field) ->
                val raw = rawData["$section.$k"] ?: return@forEach
                if (field is MetadataField) converted["$section.$k"] = ValueConverter.convert("$section.$k", raw, field)
            }
        }

        _convertedValues.value = ConvertedBatch(converted, doLogging)
        updateSelectionsIfNeeded()
    }

    // ────────────────────────────────────────────────────────────────────────────
    //  SELECTIONS utilities (wrapper‑aware)
    // ────────────────────────────────────────────────────────────────────────────

    private fun updateSelectionsIfNeeded() {
        val ctx = contextProvider.getContext()
        val prefs = ctx.getSharedPreferences("pellets_prefs", Context.MODE_PRIVATE)
        val currentKeys = _convertedValues.value.convertedValues.keys
        val existing = _selections.value.map { it.key }.toSet()
        if (currentKeys != existing) {
            _selections.value = currentKeys.map { key ->
                _selections.value.find { it.key == key } ?: Selection(
                    key,
                    prefs.getBoolean("log_$key", false),
                    prefs.getBoolean("widget_$key", false),
                    prefs.getBoolean("showData_$key", true),
                    prefs.getBoolean("showSectionName_$key", false)
                )
            }
        }
    }

    fun loadSelections(): List<Selection> {
        val ctx = contextProvider.getContext()
        val prefs = ctx.getSharedPreferences("pellets_prefs", Context.MODE_PRIVATE)
        return _convertedValues.value.convertedValues.keys.map { key ->
            Selection(
                key,
                prefs.getBoolean("log_$key", false),
                prefs.getBoolean("widget_$key", false),
                prefs.getBoolean("showData_$key", true),
                prefs.getBoolean("showSectionName_$key", false)
            )
        }
    }

    /** Updates a single checkbox state and persists it if changed. */
    fun updateSelection(
        key: String,
        log: Boolean? = null,
        widget: Boolean? = null,
        showData: Boolean? = null,
        showSectionName: Boolean? = null
    ) {
        var changed: Selection? = null
        _selections.update { list ->
            list.map { sel ->
                if (sel.key == key) {
                    val n = sel.copy(
                        log = log ?: sel.log,
                        widget = widget ?: sel.widget,
                        showData = showData ?: sel.showData,
                        showSectionName = showSectionName ?: sel.showSectionName
                    )
                    if (n != sel) changed = n; n
                } else sel
            }
        }
        changed?.let { sel ->
            val prefs = contextProvider.getContext().getSharedPreferences("pellets_prefs", Context.MODE_PRIVATE)
            prefs.edit {
                putBoolean("log_$key", sel.log)
                putBoolean("widget_$key", sel.widget)
                putBoolean("showData_$key", sel.showData)
                putBoolean("showSectionName_$key", sel.showSectionName)
            }
        }
    }

    /* Bulk toggles – unchanged except map reference */
    fun toggleAllLog(checked: Boolean)  = bulkToggle("log_")    { it.copy(log = checked) }
    fun toggleAllWidget(checked: Boolean) = bulkToggle("widget_") { it.copy(widget = checked) }
    fun toggleAllShowData(checked: Boolean) = bulkToggle("showData_") { it.copy(showData = checked) }
    fun toggleAllSectionName(checked: Boolean) = bulkToggle("showSectionName_") { it.copy(showSectionName = checked) }

    private fun bulkToggle(prefix: String, transform: (Selection) -> Selection) {
        val ctx = contextProvider.getContext()
        val prefs = ctx.getSharedPreferences("pellets_prefs", Context.MODE_PRIVATE)
        _selections.update { list ->
            list.map { sel ->
                val n = transform(sel)
                prefs.edit { putBoolean(prefix + sel.key, n.log && prefix == "log_" ||
                        n.widget && prefix == "widget_" ||
                        n.showData && prefix == "showData_" ||
                        n.showSectionName && prefix == "showSectionName_") }
                n
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    //  UI HELPERS – all wrapper‑aware and unchanged in logic
    // ────────────────────────────────────────────────────────────────────────────

    fun getDataScreenValues(): StateFlow<List<ConvertedValueWrapper>> =
        _convertedValues.map { batch ->
            batch.convertedValues.mapNotNull { (key, v) ->
                val sel = _selections.value.find { it.key == key } ?: return@mapNotNull null
                if (sel.showData) ConvertedValueWrapper(v, key.substringBeforeLast('.'), sel.showSectionName) else null
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun getWidgetValues(): WidgetDataWrapper {
        val snap = _convertedValues.value.convertedValues.mapNotNull { (key, v) ->
            val sel = _selections.value.find { it.key == key } ?: return@mapNotNull null
            if (sel.widget) ConvertedValueWrapper(v, key.substringBeforeLast('.'), sel.showSectionName) else null
        }
        return WidgetDataWrapper(snap, _state.value, _error.value, _lastTimestamp.value)
    }

    /**
     * Emits values **only** when the batch was fetched by the logger and the
     * user flagged the key with `log == true`.  UI‑initiated batches are
     * filtered out by the `isLoggingData` flag.
     */

    //fun getLogValues(): StateFlow<List<ConvertedValueWrapper>> =
    //    _convertedValues
    //        .filter { it.isLoggingData }
    //        .map { batch ->
    //            batch.convertedValues.mapNotNull { (key, v) ->
    //                val sel = _selections.value.find { it.key == key } ?: return@mapNotNull null
    //                if (sel.log) ConvertedValueWrapper(v, key.substringBeforeLast('.'), false) else null
    //            }
    //        }
    //        .distinctUntilChanged()
    //        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** A stream for the logger: every batch, even identical ones. */
    fun getLogValues(scope: CoroutineScope) =
        combine(_convertedValues, _selections) { batch, selList ->
            // emit even if isLoggingData == false so collectors wake up
            if (!batch.isLoggingData) emptyMap()
            else batch.convertedValues.filter { (key, _) ->
                selList.any { it.key == key && it.log }
            }
        }
            .shareIn(scope, SharingStarted.Eagerly, replay = 0)   // SharedFlow: no dedup



    // ────────────────────────────────────────────────────────────────────────────
    //  Remaining stats / misc helpers (kept intact, refs unchanged)
    // ────────────────────────────────────────────────────────────────────────────

    suspend fun updateError(error: String?) { Log.d(TAG, "Error updated: $error") }
    suspend fun updateState(state: String) { Log.d(TAG, "State updated: $state") }
    suspend fun updateStats(error: String?, state: String) {}

    fun getMetadataFileName(): String = "" // placeholder
    fun getCurrentMetadataJson(): String = "{}"

    fun shareMetadata(context: Context) {}
    fun importMetadata(context: Context) {}

    // ────────────────────────────────────────────────────────────────────────────
    //  CACHE helpers (unchanged except wrapper reset)
    // ────────────────────────────────────────────────────────────────────────────

    private fun loadCachedMetadata() {
        viewModelScope.launch {
            contextProvider.loadCachedMetadata()?.let { raw ->
                MetadataUtils.parseServerJson(raw)?.let { _metadata.value = it }
            }
            updateSelectionsIfNeeded()
        }
    }

    fun resetMetadata() {
        _convertedValues.value = ConvertedBatch() // clear batch
        fetchMetadata()
    }

    fun loadUserMetadata(json: String) {
        viewModelScope.launch {
            MetadataUtils.parseUserJson(json)?.let { userMeta ->
                contextProvider.saveMetadata(json)
                _metadata.value = MetadataUtils.mergeMetadata(_metadata.value, userMeta)
                _convertedValues.value = ConvertedBatch() // reset batch
                updateSelectionsIfNeeded()
            } ?: logger.e(TAG, "Failed to parse user metadata")
        }
    }
    fun loadTestDataFromStorage() {
        viewModelScope.launch {
            val file = contextProvider.getContext().filesDir.resolve("test-values.json")
            if (file.exists()) {
                val body = file.readText()
                parseDataAndUpdateValues(body, true)
            }
        }
    }

}
