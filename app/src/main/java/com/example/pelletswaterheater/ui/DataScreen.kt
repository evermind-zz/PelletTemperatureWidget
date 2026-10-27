package com.example.pelletswaterheater.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pelletswaterheater.SettingsViewModel
import com.example.pelletswaterheater.TemperatureUtils
import com.example.pelletswaterheater.ui.misc.UiEvent
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DataScreen(
    snackbarHostState: SnackbarHostState,
    showTestButton: Boolean = true // Pass true to show the test button
) {
    val viewModel: TemperatureViewModelNew = koinInject()
    val settingsViewModel: SettingsViewModel = koinViewModel()
    val values = viewModel.dataScreenValues.collectAsState(initial = emptyList())
    val groupedValues = values.value.groupBy { it.section }
    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        settingsViewModel.initialize(context)

        // Collect the event and show snackbar
        settingsViewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        StatsBarWrapper(viewModel)
        // Scrollable content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            groupedValues.forEach { (section, entries) ->

                stickyHeader {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .clickable {
                                expandedSections[section] = !(expandedSections[section] ?: true)
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        HorizontalDivider(thickness = 0.5.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (expandedSections[section] != false) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = section.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 4.dp)
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }

                if (expandedSections[section] != false) {
                    items(entries) { value ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = TemperatureUtils.getLogicalValueWithUnit(value.convertedValue),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp)
                            )

                            if (value.showSectionName) {
                                Text(
                                    text = TemperatureUtils.getNameSuffix(value),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                    textAlign = TextAlign.Start
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // Refresh button
        Button(
            onClick = { TemperatureUtils.forceTemperatureUpdate() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh")
        }
        // Optional TestData button
        if (showTestButton) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.loadTestDataFromStorage() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("TestData")
            }
        }

        LoggingSwitchSection(
            settingsViewModel,
            snackbarHostState,
        )
    }
}

