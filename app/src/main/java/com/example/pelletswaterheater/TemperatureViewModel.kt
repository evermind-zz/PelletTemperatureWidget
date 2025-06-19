package com.example.pelletswaterheater

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val TAG = TemperatureWorker::class.java.simpleName

class TemperatureViewModel : ViewModel() {
    private val _temperature = MutableStateFlow<String?>(null)
    val temperature: StateFlow<String?> = _temperature.asStateFlow()

    private val _lastValidTemperature = MutableStateFlow<String?>(null)
    private val _displayTemperature = MutableStateFlow("N/A")
    val displayTemperature: StateFlow<String> = _displayTemperature.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _state = MutableStateFlow<String>("Loading...")
    val state: StateFlow<String> = _state.asStateFlow()

    private val _lastUpdateTime = MutableStateFlow<Long>(0L)
    val lastUpdateTime: StateFlow<Long> = _lastUpdateTime.asStateFlow()

    init {
        Log.d(TAG, "Instance created: ${this.hashCode()}")
    }

    // Singleton for worker access
    companion object {
        private var instance: TemperatureViewModel? = null
        fun getInstance(): TemperatureViewModel {
            if (instance == null) {
                instance = TemperatureViewModel()
            }
            return instance!!
        }
    }

    fun updateTemperature(temp: String?) {
        Log.d(TAG, "Temperature updated: $temp")
        _temperature.value = temp
        if (temp != null && temp != "Error") {
            _lastValidTemperature.value = temp
            _displayTemperature.value = formatTemperature(temp)
            _lastUpdateTime.value = System.currentTimeMillis()
        }
        updateDisplayTemperature()
    }

    fun updateError(error: String?) {
        Log.d(TAG, "Error updated: $error")
        _error.value = error
        updateDisplayTemperature()
    }

    fun updateState(state: String) {
        Log.d(TAG, "State updated: $state")
        _state.value = state
        updateDisplayTemperature()
    }

    fun clearError() {
        Log.d(TAG, "Error cleared")
        _error.value = null
        updateDisplayTemperature()
    }

    private fun updateDisplayTemperature() {
        _displayTemperature.value = when {
            _lastValidTemperature.value == null && _state.value == "Loading..." -> "N/A"
            _lastValidTemperature.value != null -> formatTemperature(_lastValidTemperature.value)
            else -> "N/A"
        }
        Log.d(TAG, "Display temperature updated: ${_displayTemperature.value}")
    }

    private fun formatTemperature(rawValue: String?): String {
        return when {
            rawValue == null -> "N/A"
            else -> try {
                java.text.DecimalFormat("0.0").format(rawValue.toDouble()) + "°C"
            } catch (e: NumberFormatException) {
                Log.d(TAG, "Format error: ${e.message}", e)
                "N/A"
            }
        }
    }
}