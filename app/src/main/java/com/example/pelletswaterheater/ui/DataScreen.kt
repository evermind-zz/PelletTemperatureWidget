package com.example.pelletswaterheater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pelletswaterheater.TemperatureUtils
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew
import org.koin.java.KoinJavaComponent.inject

@Composable
fun DataScreen(
    showTestButton: Boolean = true // Pass true to show the test button
) {
    val viewModel: TemperatureViewModelNew by inject(TemperatureViewModelNew::class.java)
    val values = viewModel.getDataScreenValues().collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Scrollable content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(values.value) { value ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val displayUnit = value.convertedValue.unit?.takeIf { it.isNotBlank() } ?: ""
                    val nameSuffix = if (value.showSectionName) {
                        "${value.section}.${value.convertedValue.name}"
                    } else {
                        ""
                    }

                    // First column: logical value + unit
                    Text(
                        text = "${value.convertedValue.logical}${if (displayUnit.isNotEmpty()) " $displayUnit" else ""}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )

                    // Second column: section.name (if showSectionName)
                    Text(
                        text = nameSuffix,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        textAlign = TextAlign.Start
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
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
    }
}
