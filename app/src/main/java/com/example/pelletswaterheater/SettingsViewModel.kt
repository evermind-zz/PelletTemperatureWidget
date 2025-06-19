package com.example.pelletswaterheater

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class SettingsViewModel : ViewModel() {
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

    fun initialize(context: Context) {
        _serverUrl.value = TemperatureUtils.getStoredUrl(context)
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
        TemperatureUtils.forceTemperatureUpdate(context)
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
}
