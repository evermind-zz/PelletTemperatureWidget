package com.example.pelletswaterheater.repository

import com.example.pelletswaterheater.utils.ConvertedValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing converted temperature values.
 * Handles value conversion, updates, and provides filtered views for UI.
 */
interface ValueRepository {
    val convertedValues: StateFlow<Map<String, ConvertedValue>>
    val lastTimestamp: StateFlow<String>

    fun updateValues(rawData: Map<String, String>, timestamp: Long, doLogging: Boolean)
    suspend fun loadTestDataFromStorage()
    fun getCurrentTimestamp(): Long
}