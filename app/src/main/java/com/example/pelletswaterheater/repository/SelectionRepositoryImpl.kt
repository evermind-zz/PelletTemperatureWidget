package com.example.pelletswaterheater.repository

import com.example.pelletswaterheater.preferences.PreferencesRepository
import com.example.pelletswaterheater.repository.SelectionRepository.Selection
import com.example.pelletswaterheater.utils.ILogger
import com.example.pelletswaterheater.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SelectionRepositoryImpl(
    private val preferencesRepository: PreferencesRepository,
    private val logger: ILogger = Logger,
    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : SelectionRepository, KoinComponent {

    private val TAG = SelectionRepositoryImpl::class.java.simpleName
    private val json = Json { ignoreUnknownKeys = true }

    private val _selections = MutableStateFlow<List<Selection>>(emptyList())
    override val selections: StateFlow<List<Selection>> = _selections.asStateFlow()

    private suspend fun loadSelectionsFromDataStore(): List<Selection> {
        val jsonStr = preferencesRepository.selectionsJson.first() ?: "[]"
        return try {
            json.decodeFromString<List<Selection>>(jsonStr)
        } catch (e: Exception) {
            logger.e(TAG, "Failed to decode selections JSON: $e", e)
            emptyList()
        }
    }

    private suspend fun saveSelectionsToDataStore(selections: List<Selection>) {
        val jsonStr = json.encodeToString(serializer<List<Selection>>(), selections)
        preferencesRepository.setSelectionsJson(jsonStr)
    }

    override suspend fun loadSelections(): List<Selection> {
        return _selections.value
    }

    override suspend fun updateSelectionsIfNeeded(convertedKeys: Set<String>) {
        val existingKeys = _selections.value.map { it.key }.toSet()
        if (convertedKeys != existingKeys) {
            val storedSelections = loadSelectionsFromDataStore()
            val storedMap = storedSelections.associateBy { it.key }
            _selections.value = convertedKeys.map { key ->
                storedMap[key] ?: Selection(
                    key = key,
                    log = false,
                    widget = false,
                    showData = true,
                    showSectionName = false
                )
            }
        }
    }

    override fun updateSelection(
        key: String,
        log: Boolean?,
        widget: Boolean?,
        showData: Boolean?,
        showSectionName: Boolean?
    ) {
        var changedItem: Selection? = null

        _selections.update { list: List<Selection> ->
            list.map { sel: Selection ->
                if (sel.key == key) {
                    val newSel = sel.copy(
                        log = log ?: sel.log,
                        widget = widget ?: sel.widget,
                        showData = showData ?: sel.showData,
                        showSectionName = showSectionName ?: sel.showSectionName
                    )
                    if (newSel != sel) changedItem = newSel
                    newSel
                } else sel
            }
        }

        changedItem?.let { sel ->
            viewModelScope.launch { saveSelectionsToDataStore(_selections.value) }
        }
    }

    override fun toggleAllLog(checked: Boolean) {
        _selections.value = _selections.value.map { it -> it.copy(log = checked) }
        viewModelScope.launch { saveSelectionsToDataStore(_selections.value) }
    }

    override fun toggleAllWidget(checked: Boolean) {
        _selections.value = _selections.value.map { it -> it.copy(widget = checked) }
        viewModelScope.launch { saveSelectionsToDataStore(_selections.value) }
    }

    override fun toggleAllShowData(checked: Boolean) {
        _selections.value = _selections.value.map { it -> it.copy(showData = checked) }
        viewModelScope.launch { saveSelectionsToDataStore(_selections.value) }
    }

    override fun toggleAllSectionName(checked: Boolean) {
        _selections.value = _selections.value.map { it -> it.copy(showSectionName = checked) }
        viewModelScope.launch { saveSelectionsToDataStore(_selections.value) }
    }
}