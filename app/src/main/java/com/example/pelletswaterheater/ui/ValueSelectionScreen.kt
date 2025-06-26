package com.example.pelletswaterheater.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pelletswaterheater.TemperatureUtils
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew
import org.koin.androidx.compose.koinViewModel
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

/**
 * Composable for the Value Selection Screen in the Pellets Water Heater app.
 * Displays converted values grouped by section (e.g., "system", "hk1") with checkboxes for log, widget, showData, and showSectionName.
 *
 * @param viewModel The [TemperatureViewModelNew] instance, injected via Koin.
 * @param modifier The modifier for the composable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
@Preview
fun ValueSelectionScreen(
    viewModel: TemperatureViewModelNew = koinInject(),
    modifier: Modifier = Modifier
) {
    val values = viewModel.convertedValues.collectAsState(initial = emptyMap())
    val selections = viewModel.selections.collectAsState(initial = emptyList())
    val groupedValues = values.value.entries.groupBy { it.key.substringBeforeLast(".") }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        // Fixed header
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(top = 12.dp, bottom = 4.dp)
        ) {
            // Header titles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Log",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Widget",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Data",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Section + Name",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }

            // Header checkboxes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
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

            // Optional explanation text under the checkboxes
            Text(
                text = "Above: Select all | Below: Select individually",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 8.dp)
            )
        }

        // List of fields
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            groupedValues.forEach { (section, entries) ->
                item {
                    Text(
                        text = section.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                    )
                }
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
                            text = "${value.logical} ${value.unit} (${section}.${key.substringAfterLast(".")} ${value.name})",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
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
                                onCheckedChange = { viewModel.updateSelection(key, showSectionName = it) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Refresh button at bottom
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            TemperatureUtils.appContext?.let {
                                TemperatureUtils.forceTemperatureUpdate(it)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Refresh")
                    }
                }
            }
        }
    }
}