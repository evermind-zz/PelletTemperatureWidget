package com.example.pelletswaterheater.repository

import com.example.pelletswaterheater.preferences.PreferencesRepository
import com.example.pelletswaterheater.utils.ConvertedValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing user selections (log, widget, showData, showSectionName).
 */
interface SelectionRepository {
    val selections: StateFlow<List<Selection>>

    data class Selection(
        val key: String,
        val log: Boolean,
        val widget: Boolean,
        val showData: Boolean,
        val showSectionName: Boolean
    )

    suspend fun loadSelections(): List<Selection>
    suspend fun updateSelectionsIfNeeded(convertedKeys: Set<String>)
    fun updateSelection(key: String, log: Boolean?, widget: Boolean?, showData: Boolean?, showSectionName: Boolean?)
    fun toggleAllLog(checked: Boolean)
    fun toggleAllWidget(checked: Boolean)
    fun toggleAllShowData(checked: Boolean)
    fun toggleAllSectionName(checked: Boolean)
}