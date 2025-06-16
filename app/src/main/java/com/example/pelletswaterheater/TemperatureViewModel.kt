package com.example.pelletswaterheater

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import android.util.Log
import kotlinx.coroutines.flow.StateFlow

private val TAG = TemperatureWorker::class.java.simpleName

class TemperatureViewModel : ViewModel() {
    private val _temperature = MutableStateFlow<String?>(null)
    val temperature: StateFlow<String?> = _temperature

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _state = MutableStateFlow<String?>("Loading...")
    val state: StateFlow<String?> = _state

    init {
        Log.d("TemperatureViewModel", "Instance created: ${this.hashCode()}")
    }

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
        Log.d("TemperatureViewModel", "Temperature updated: $temp")
        _temperature.value = temp
    }

    fun updateError(error: String?) {
        Log.d("TemperatureViewModel", "Error updated: $error")
        _error.value = error
    }

    fun updateLastUpdateTime(time: Long) {
        Log.d("TemperatureViewModel", "LastUpdateTime updated: $time")
        _lastUpdateTime.value = time
    }

    fun clearError() {
        Log.d("TemperatureViewModel", "Error cleared")
        _error.value = null
    }

    fun updateState(state: String) {
        Log.d(TAG, "State updated: $state")
        _state.value = state

    }
}