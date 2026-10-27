package com.example.pelletswaterheater.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for managing system stats (state, error, timestamp).
 */
interface StateRepository {
    val state: StateFlow<String>
    val error: StateFlow<String?>
    val lastTimestamp: StateFlow<String>
    val systemStats: SystemStats

    data class SystemStats(
        val lastTimestamp: StateFlow<String>,
        val state: StateFlow<String>,
        val error: StateFlow<String?>
    )

    fun updateError(error: String?)
    fun updateState(state: String)
    fun updateStats(error: String?, state: String)
}

class StateRepositoryImpl(
    private val viewModelScope: CoroutineScope
) : StateRepository {

    private val _state = MutableStateFlow<String>("Start")
    override val state: StateFlow<String> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    private val _lastTimestamp = MutableStateFlow<String>("N/A")
    override val lastTimestamp: StateFlow<String> = _lastTimestamp.asStateFlow()

    override val systemStats = SystemStats(_lastTimestamp.asStateFlow(), _state.asStateFlow(), _error.asStateFlow())

    override fun updateError(error: String?) {
        _error.value = error
    }

    override fun updateState(state: String) {
        _state.value = state
    }

    override fun updateStats(error: String?, state: String) {
        _error.value = error
        _state.value = state
    }
}