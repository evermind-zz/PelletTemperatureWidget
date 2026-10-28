package com.example.pelletswaterheater.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pelletswaterheater.utils.TemperatureUtils
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew
import org.koin.compose.koinInject

/**
 * Composable for the Value Selection Screen in the Pellets Water Heater app.
 * Displays converted values grouped by section (e.g., "system", "hk1")
 * with checkboxes for log, widget, showData, and showSectionName.
 *
 * @param viewModel The [TemperatureViewModelNew] instance, injected via Koin.
 * @param modifier The modifier for the composable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
@Preview
fun ValueSelectionScreen(
    modifier: Modifier = Modifier,
    viewModel: TemperatureViewModelNew = koinInject()
) {
    val temperatureUtils: TemperatureUtils = koinInject()
    val values = viewModel.convertedValues.collectAsState(initial = emptyMap())
    val selections = viewModel.selections.collectAsState(initial = emptyList())
    val groupedValues = values.value.entries.groupBy { it.key.substringBeforeLast(".") }
    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }

    Column(modifier = modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp)
    ) {

        StatsBarWrapper(viewModel)
        // Fixed Global Header (Column Titles + Global Checkboxes + Hint + Divider)
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(top = 12.dp, bottom = 4.dp)
        ) {
            // Column titles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Log",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Widget",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Data",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Section + Name",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
            }

            // Global checkboxes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = selections.value.all { it.log },
                    onCheckedChange = { viewModel.toggleAllLog(it) },
                    modifier = Modifier.weight(1f)
                )
                Checkbox(
                    checked = selections.value.all { it.widget },
                    onCheckedChange = { viewModel.toggleAllWidget(it) },
                    modifier = Modifier.weight(1f)
                )
                Checkbox(
                    checked = selections.value.all { it.showData },
                    onCheckedChange = { viewModel.toggleAllShowData(it) },
                    modifier = Modifier.weight(1f)
                )
                Checkbox(
                    checked = selections.value.all { it.showSectionName },
                    onCheckedChange = { viewModel.toggleAllSectionName(it) },
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "Above: Select all | Below: Per entry control",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            //Divider(thickness = 1.dp)
        }

        // Scrollable content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
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
                        Divider(thickness = 0.5.dp)
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
                        Divider(thickness = 0.5.dp)
                    }
                }

                if (expandedSections[section] != false) {
                    items(entries) { entry ->
                        val key = entry.key
                        val value = entry.value
                        val selection = selections.value.find { it.key == key } ?: return@items
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, bottom = 6.dp)
                        ) {
                            Text(
                                text = "${value.logical} ${value.unit} (${section}.${
                                    key.substringAfterLast(
                                        "."
                                    )
                                } ${value.name})",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = selection.log,
                                    onCheckedChange = { viewModel.updateSelection(key, log = it) },
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = selection.widget,
                                    onCheckedChange = { viewModel.updateSelection(key, widget = it) },
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = selection.showData,
                                    onCheckedChange = { viewModel.updateSelection(key, showData = it) },
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = selection.showSectionName,
                                    onCheckedChange = { viewModel.updateSelection( key, showSectionName = it ) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

        }
        Divider(thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { temperatureUtils.forceTemperatureUpdate() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Refresh")
            }
        }
    }
}
