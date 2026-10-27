package com.example.pelletswaterheater.repository

import com.example.pelletswaterheater.utils.ConvertedValue
import com.example.pelletswaterheater.utils.TemperatureLogger
import kotlinx.coroutines.CoroutineScope

/**
 * Repository for coordinating temperature logging.
 */
interface LoggingRepository {
    suspend fun logValues(
        values: Map<String, ConvertedValue>,
        keyOrder: List<String>,
        timestamp: Long,
        selectedKeys: Set<String>
    )
}

class LoggingRepositoryImpl(
    private val temperatureLogger: TemperatureLogger,
    private val viewModelScope: CoroutineScope
) : LoggingRepository {

    override suspend fun logValues(
        values: Map<String, ConvertedValue>,
        keyOrder: List<String>,
        timestamp: Long,
        selectedKeys: Set<String>
    ) {
        viewModelScope.launch {
            val logValues = values.filterKeys { key ->
                selectedKeys.contains(key)
            }
            val logKeyOrder = keyOrder.filter { it in logValues }
            temperatureLogger.logValues(logValues, logKeyOrder, timestamp)
        }
    }
}