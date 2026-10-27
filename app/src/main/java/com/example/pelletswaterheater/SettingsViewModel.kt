package com.example.pelletswaterheater

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pelletswaterheater.service.LoggingService
import com.example.pelletswaterheater.ui.misc.UiEvent
import com.example.pelletswaterheater.ui.misc.UiState
import com.example.pelletswaterheater.preferences.PreferencesRepository
import com.example.pelletswaterheater.utils.ContextProvider
import com.example.pelletswaterheater.utils.ILogger
import com.example.pelletswaterheater.utils.NetworkFingerprint
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.URL
import kotlin.apply

class SettingsViewModel(
    private val contextProvider: ContextProvider,
    private val logger: ILogger,
    private val dataRepository: TemperatureViewModelNew,
    private val nnetworkFingerprint: NetworkFingerprint = NetworkFingerprint,
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val preferencesRepository: PreferencesRepository = PreferencesRepositoryImpl(contextProvider.getContext()),
    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : ViewModel() {

    companion object {
        val TAG = SettingsViewModel::class.java.simpleName
        const val DEFAULT_SERVER_URL: String = "http://192.168.178.4:4321/<PASSWORD>/all"
        const val LOGGING_INTERVAL_MIN_VAL_IN_MS = 10000
    }

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _widgetWidth = MutableStateFlow("100")
    val widgetWidth: StateFlow<String> = _widgetWidth.asStateFlow()

    private val _widgetHeight = MutableStateFlow("100")
    val widgetHeight: StateFlow<String> = _widgetHeight.asStateFlow()

    private val _backgroundColor = MutableStateFlow("FFF3E5F5")
    val backgroundColor: StateFlow<String> = _backgroundColor.asStateFlow()

    private val _textColor = MutableStateFlow("FF000000")
    val textColor: StateFlow<String> = _textColor.asStateFlow()

    private val _backgroundColorSelection = MutableStateFlow("Custom")
    val backgroundColorSelection: StateFlow<String> = _backgroundColorSelection.asStateFlow()

    private val _textColorSelection = MutableStateFlow("Custom")
    val textColorSelection: StateFlow<String> = _textColorSelection.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    private val predefinedColors = listOf(
        "Purple" to "FFF3E5F5",
        "White" to "FFFFFFFF",
        "Black" to "FF000000",
        "Transparent" to "TRANSPARENT"
    )

    private val _networkFingerprint = MutableStateFlow<String?>(null)
    val networkFingerprint: StateFlow<String?> = _networkFingerprint

    private val _rawMetadata = MutableStateFlow<String?>(null)
    val rawMetadata: StateFlow<String?> = _rawMetadata

    fun initialize(context: Context) {
        _serverUrl.value = TemperatureUtils.getStoredUrl()
        _widgetWidth.value = TemperatureUtils.getStoredWidgetSize(context, "width") ?: "100"
        _widgetHeight.value = TemperatureUtils.getStoredWidgetSize(context, "height") ?: "100"
        _backgroundColor.value = TemperatureUtils.getStoredBackgroundColorHex(context) ?: "FFF3E5F5"
        _textColor.value = TemperatureUtils.getStoredTextColorHex(context) ?: "FF000000"
        _backgroundColorSelection.value = if (_backgroundColor.value == "TRANSPARENT") "Transparent" else "Custom"
        _textColorSelection.value = "Custom"
        Log.d("SettingsViewModel", "Initialized: url=${_serverUrl.value}, width=${_widgetWidth.value}, height=${_widgetHeight.value}")
    }

    fun updateServerUrl(url: String) {
        _serverUrl.value = url
        _errorMessage.value = null
    }

    fun updateWidgetWidth(width: String) {
        _widgetWidth.value = width.filter { it.isDigit() }
        _errorMessage.value = null
    }

    fun updateWidgetHeight(height: String) {
        _widgetHeight.value = height.filter { it.isDigit() }
        _errorMessage.value = null
    }

    fun updateBackgroundColorSelection(selection: String) {
        _backgroundColorSelection.value = selection
        if (selection != "Custom") {
            _backgroundColor.value = predefinedColors.find { it.first == selection }?.second ?: "FFF3E5F5"
        }
        _errorMessage.value = null
    }

    fun updateBackgroundColor(color: String) {
        if (color.matches(Regex("^[0-9A-Fa-f]{0,8}$"))) {
            _backgroundColor.value = color
            _errorMessage.value = null
        }
    }

    fun updateTextColorSelection(selection: String) {
        _textColorSelection.value = selection
        if (selection != "Custom") {
            _textColor.value = predefinedColors.find { it.first == selection }?.second ?: "FF000000"
        }
        _errorMessage.value = null
    }

    fun updateTextColor(color: String) {
        if (color.matches(Regex("^[0-9A-Fa-f]{0,8}$"))) {
            _textColor.value = color
            _errorMessage.value = null
        }
    }

    fun testServerUrl(context: Context) {
        if (!isValidServerUrl(_serverUrl.value)) {
            _testResult.value = "Invalid URL format"
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                TemperatureUtils.pingServer(_serverUrl.value)
            }
            _testResult.value = if (result) "Server reachable" else "Server unreachable"
            Log.d("SettingsViewModel", "Test result: ${_testResult.value}")
        }

    }

    fun saveSettings(context: Context): Boolean {
        if (!validateInputs()) {
            return false
        }
        TemperatureUtils.saveSettings(
            context,
            _serverUrl.value,
            _widgetWidth.value,
            _widgetHeight.value,
            _backgroundColor.value,
            _textColor.value
        )
        TemperatureUtils.forceTemperatureUpdate()
        viewModelScope.launch {
            TemperatureWidget().forceUpdateAll(context)
        }
        return true
    }

    private fun validateInputs(): Boolean {
        _errorMessage.value = when {
            !isValidServerUrl(_serverUrl.value) -> "Invalid server URL (must start with http:// or https://)"
            _widgetWidth.value.toIntOrNull()?.let { it < 40 } == true -> "Widget width must be at least 40dp"
            _widgetHeight.value.toIntOrNull()?.let { it < 40 } == true -> "Widget height must be at least 40dp"
            !isValidHexColor(_backgroundColor.value) && _backgroundColorSelection.value == "Custom" -> "Invalid background color hex"
            !isValidHexColor(_textColor.value) && _textColorSelection.value == "Custom" -> "Invalid text color hex"
            else -> null
        }
        return _errorMessage.value == null
    }

    private fun isValidServerUrl(url: String): Boolean {
        return try {
            URL(url).toURI()
            url.startsWith("http://") || url.startsWith("https://")
        } catch (e: Exception) {
            false
        }
    }

    private fun isValidHexColor(color: String): Boolean {
        return color.matches(Regex("^[0-9A-Fa-f]{8}$")) || color == "TRANSPARENT"
    }

    private val _isLoggingEnabled = MutableStateFlow(false)
    val isLoggingEnabled: StateFlow<Boolean> = _isLoggingEnabled

    private val _showBatteryOptimizationDialog = MutableStateFlow(false)
    val showBatteryOptimizationDialog: StateFlow<Boolean> = _showBatteryOptimizationDialog

    private val _loggingInterval = MutableStateFlow(5000L) // Default: 5 seconds
    val loggingInterval: StateFlow<Long> = _loggingInterval

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    private val _uiLoggingIntervalState = MutableStateFlow(UiState(
        input = getLoggingIntervalFromPrefs().toString(),
        canSubmit = enableIntervalSetting()
    ))
    val uiLoggingIntervalState: StateFlow<UiState> = _uiLoggingIntervalState.asStateFlow()

    private fun getLoggingIntervalFromPrefs(): Int {
        return LOGGING_INTERVAL_MIN_VAL_IN_MS // TODO fetch it from config
    }


    private fun enableIntervalSetting(): Boolean {
        return getLoggingIntervalFromPrefs() >= LOGGING_INTERVAL_MIN_VAL_IN_MS
    }

    fun onLoggingIntervalInputChanged(text: String) {
        val value = text.toIntOrNull()
        val error = value == null || value < LOGGING_INTERVAL_MIN_VAL_IN_MS
        _uiLoggingIntervalState.update {
            it.copy(
                input = text,
                isError = error,
                canSubmit = !error
            )
        }
    }

    fun onLoggingIntervalSubmit() {
        if (_isLoggingEnabled.value) {
            // Restart service with new interval
            stopLogging()
            startLogging()
        }
    }

    fun isLoggingViable() : Boolean {
        if (!dataRepository.loggingIsViable.value) {
            viewModelScope.launch {
                _uiEvent.emit(UiEvent.ShowSnackbar("There are no values to log please select some values first."))
            }
            return false
        }
        return true
    }

    fun toggleLogging() {
        viewModelScope.launch {
            if (_isLoggingEnabled.value) {
                stopLogging()
            } else {
                if (!isLoggingViable())
                    return@launch
                checkBatteryOptimization()
            }
        }
    }

    private fun checkBatteryOptimization() {
        val context = contextProvider.getContext()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName
        val isIgnoringOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }

        if (isIgnoringOptimizations) {
            startLogging()
        } else {
            _showBatteryOptimizationDialog.value = true
        }
    }

    @SuppressLint("BatteryLife")
    fun onBatteryOptimizationDialogConfirmed(openSettings: Boolean) {
        hideBatteryOptimizationDialog()
        if (!openSettings) {
            startLogging()
        }
    }

    private fun startLogging() {
        if (!checkIfLoggingIsValid()) {
            return
        }
        _isLoggingEnabled.value = true
        val context = contextProvider.getContext()
        TemperatureUtils.cancelPeriodicWorker(context)
        val intent = Intent(context, LoggingService::class.java).apply {
            //putExtra(LoggingService.EXTRA_LOGGING_INTERVAL, _loggingInterval.value)
            putExtra(LoggingService.EXTRA_LOGGING_INTERVAL, _uiLoggingIntervalState.value.input.toLong())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
        logger.d(TAG, "Logging service started with interval ${_loggingInterval.value}ms")
    }

    private fun checkIfLoggingIsValid(): Boolean {
        return dataRepository.loggingIsViable.value
    }

    private fun stopLogging() {
        _isLoggingEnabled.value = false
        val context = contextProvider.getContext()
        val intent = Intent(context, LoggingService::class.java)
        context.stopService(intent)
        logger.d(TAG, "Logging service stopped")
        TemperatureUtils.schedulePeriodicTemperatureUpdate()
    }

    fun hideBatteryOptimizationDialog() {
        _showBatteryOptimizationDialog.value = false
    }

    /**
     * get data via [rawMetaData].
     */
    fun getRawMetadata() {
        viewModelScope.launch {
            _rawMetadata.update {  contextProvider.loadCachedMetadata() }
        }
    }
    fun updateNetworkFingerprint() {
        viewModelScope.launch {
            val fingerprint = nnetworkFingerprint.getGatewayFingerprint(contextProvider.getContext(), okHttpClient)
            _networkFingerprint.value = fingerprint
            logger.d(TAG, "Gateway fingerprint: $fingerprint")
            fingerprint?.let {
                viewModelScope.launch { preferencesRepository.setGatewayFingerprint(it) }
            }
        }
    }
}
