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
    val columnWidth = 72.dp

    val expandedSections = remember { mutableStateMapOf<String, Boolean>() }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {

        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Log", modifier = Modifier.width(columnWidth), textAlign = TextAlign.Center, fontSize = 12.sp)
            Text("Widget", modifier = Modifier.width(columnWidth), textAlign = TextAlign.Center, fontSize = 12.sp)
            Text("Data", modifier = Modifier.width(columnWidth), textAlign = TextAlign.Center, fontSize = 12.sp)
            Text("Section+Name", modifier = Modifier.weight(1f), textAlign = TextAlign.Start, fontSize = 12.sp)
        }

        // Select-All Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selections.value.all { it.log },
                onCheckedChange = { viewModel.toggleAllLog(it) },
                modifier = Modifier.width(columnWidth)
            )
            Checkbox(
                checked = selections.value.all { it.widget },
                onCheckedChange = { viewModel.toggleAllWidget(it) },
                modifier = Modifier.width(columnWidth)
            )
            Checkbox(
                checked = selections.value.all { it.showData },
                onCheckedChange = { viewModel.toggleAllShowData(it) },
                modifier = Modifier.width(columnWidth)
            )
            Checkbox(
                checked = selections.value.all { it.showSectionName },
                onCheckedChange = { viewModel.toggleAllSectionName(it) },
                modifier = Modifier.width(columnWidth)
            )
        }

        // Hint Text Below Checkboxes
        Text(
            text = "✔ in header: select all visible below\n✔ per item: toggles specific option for each entry",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )

        Divider()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groupedValues.forEach { (section, entries) ->

                // Sticky Header for Section
                stickyHeader {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(vertical = 4.dp)
                            .clickable {
                                expandedSections[section] = !(expandedSections[section] ?: true)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (expandedSections[section] != false) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = section.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // List Items for Each Entry
                if (expandedSections[section] != false) {
                    items(entries) { entry ->
                        val key = entry.key
                        val value = entry.value
                        val selection = selections.value.find { it.key == key } ?: return@items

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${value.logical} ${value.unit} (${section}.${key.substringAfterLast(".")} ${value.name})",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                                fontSize = 12.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = selection.log,
                                    onCheckedChange = { viewModel.updateSelection(key, log = it) },
                                    modifier = Modifier.width(columnWidth)
                                )
                                Checkbox(
                                    checked = selection.widget,
                                    onCheckedChange = { viewModel.updateSelection(key, widget = it) },
                                    modifier = Modifier.width(columnWidth)
                                )
                                Checkbox(
                                    checked = selection.showData,
                                    onCheckedChange = { viewModel.updateSelection(key, showData = it) },
                                    modifier = Modifier.width(columnWidth)
                                )
                                Checkbox(
                                    checked = selection.showSectionName,
                                    onCheckedChange = { viewModel.updateSelection(key, showSectionName = it) },
                                    modifier = Modifier.width(columnWidth)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        TemperatureUtils.appContext?.let {
                            TemperatureUtils.forceTemperatureUpdate(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh")
                }
            }
        }
    }
}
