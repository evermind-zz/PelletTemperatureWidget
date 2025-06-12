package com.example.pelletswaterheater

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TemperatureViewModel : ViewModel() {
    private val _temperature = MutableStateFlow<String?>(null)
    val temperature: StateFlow<String?> = _temperature

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _lastUpdateTime = MutableStateFlow<Long>(0L)
    val lastUpdateTime: StateFlow<Long> = _lastUpdateTime

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
        _temperature.value = temp
    }

    fun updateError(error: String?) {
        _error.value = error
    }

    fun updateLastUpdateTime(time: Long) {
        _lastUpdateTime.value = time
    }

    fun clearError() {
        _error.value = null
    }
}