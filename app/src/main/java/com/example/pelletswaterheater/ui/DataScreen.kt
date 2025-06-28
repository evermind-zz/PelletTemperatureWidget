package com.example.pelletswaterheater.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.example.pelletswaterheater.TemperatureUtils
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject

@Composable
fun DataScreen() {
    val viewModel: TemperatureViewModelNew by inject(TemperatureViewModelNew::class.java)
    val values = viewModel.getDataScreenValues().collectAsState(initial = emptyList())

    LazyColumn {
        items(values.value) { value ->
            Text(
                text = if (value.showSectionName) {
                    "${value.convertedValue.logical} ${value.convertedValue.unit} ${value.section}.${value.convertedValue.name}"
                } else {
                    "${value.convertedValue.logical} ${value.convertedValue.unit}"
                }
            )
        }
        item {
            Button(onClick = {
               TemperatureUtils.forceTemperatureUpdate()

            }) {
                Text("Refresh")
            }
            Button(onClick = {
                viewModel.loadTestDataFromStorage()

            }) {
                Text("TestData")
            }
        }
    }
}