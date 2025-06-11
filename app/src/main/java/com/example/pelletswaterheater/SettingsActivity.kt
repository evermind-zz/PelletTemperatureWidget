package com.example.pelletswaterheater

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.BackoffPolicy
import androidx.work.WorkManager
import androidx.glance.appwidget.updateAll
import androidx.work.OneTimeWorkRequestBuilder
import com.example.pelletswaterheater.TemperatureUtils.getStoredBackgroundColorHex
import com.example.pelletswaterheater.TemperatureUtils.getStoredTextColorHex
import com.example.pelletswaterheater.ui.theme.PelletsWaterHeaterTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PelletsWaterHeaterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen()
                }
            }
        }
    }

}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var serverUrl by remember { mutableStateOf(TemperatureUtils.getStoredUrl(context) ?: "http://192.168.178.4:4321/<PASSWORD>/all") }
    var widgetWidth by remember { mutableStateOf(TemperatureUtils.getStoredWidgetSize(context, "width") ?: "100") }
    var widgetHeight by remember { mutableStateOf(TemperatureUtils.getStoredWidgetSize(context, "height") ?: "100") }
    var backgroundColor by remember { mutableStateOf(TemperatureUtils.getStoredBackgroundColorHex(context) ?: "FFF3E5F5") }
    var textColor by remember { mutableStateOf(TemperatureUtils.getStoredTextColorHex(context) ?: "FF000000") }
    var backgroundColorSelection by remember { mutableStateOf(if (backgroundColor == "TRANSPARENT") "Transparent" else "Custom") }
    var textColorSelection by remember { mutableStateOf("Custom") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val predefinedColors = listOf(
        "Purple" to "FFF3E5F5",
        "White" to "FFFFFFFF",
        "Black" to "FF000000",
        "Transparent" to "TRANSPARENT"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding().padding(bottom = 80.dp), // Handle keyboard and add padding
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null
        )
        Log.d("SettingsScreen", "Rendering: url=$serverUrl, width=$widgetWidth, height=$widgetHeight, bg=$backgroundColor, text=$textColor")
        OutlinedTextField(
            value = widgetWidth,
            onValueChange = { widgetWidth = it.filter { it.isDigit() } },
            label = { Text("Widget Width (dp)") },
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null
        )
        OutlinedTextField(
            value = widgetHeight,
            onValueChange = { widgetHeight = it.filter { it.isDigit() } },
            label = { Text("Widget Height (dp)") },
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null
        )
        Text(text = "Background Color", style = MaterialTheme.typography.bodyLarge)
        DropdownMenu(
            selectedOption = backgroundColorSelection,
            options = predefinedColors.map { it.first } + "Custom",
            onOptionSelected = { selected ->
                backgroundColorSelection = selected
                if (selected != "Custom") {
                    backgroundColor = predefinedColors.find { it.first == selected }?.second ?: "FFF3E5F5"
                }
            }
        )
        if (backgroundColorSelection == "Custom") {
            OutlinedTextField(
                value = backgroundColor,
                onValueChange = { if (it.matches(Regex("^[0-9A-Fa-f]{0,8}$"))) backgroundColor = it },
                label = { Text("Background Color (Hex, e.g., FFF3E5F5)") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage != null && !isValidHexColor(backgroundColor)
            )
        }
        Text(text = "Text Color", style = MaterialTheme.typography.bodyLarge)
        DropdownMenu(
            selectedOption = textColorSelection,
            options = predefinedColors.map { it.first }.filter { it != "Transparent" } + "Custom",
            onOptionSelected = { selected ->
                textColorSelection = selected
                if (selected != "Custom") {
                    textColor = predefinedColors.find { it.first == selected }?.second ?: "FF000000"
                }
            }
        )
        if (textColorSelection == "Custom") {
            OutlinedTextField(
                value = textColor,
                onValueChange = { if (it.matches(Regex("^[0-9A-Fa-f]{0,8}$"))) textColor = it },
                label = { Text("Text Color (Hex, e.g., FF000000)") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage != null && !isValidHexColor(textColor)
            )
        }
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Button(
            onClick = {
                if (serverUrl.isBlank() || widgetWidth.toIntOrNull()?.let { it < 40 } == true || widgetHeight.toIntOrNull()?.let { it < 40 } == true) {
                    errorMessage = when {
                        serverUrl.isBlank() -> "Server URL cannot be empty"
                        widgetWidth.toIntOrNull()?.let { it < 40 } == true -> "Widget width must be at least 40dp"
                        widgetHeight.toIntOrNull()?.let { it < 40 } == true -> "Widget height must be at least 40dp"
                        !isValidHexColor(backgroundColor) && backgroundColorSelection == "Custom" -> "Invalid background color hex"
                        !isValidHexColor(textColor) && textColorSelection == "Custom" -> "Invalid text color hex"
                        else -> null
                    }
                } else {
                    errorMessage = null
                    saveSettings(context, serverUrl, widgetWidth, widgetHeight, backgroundColor, textColor)
                    Toast.makeText(context, "settings saved", Toast.LENGTH_SHORT).show()
                    TemperatureUtils.enqueueTemperatureUpdate(context)
                    // Force widget update
                    CoroutineScope(Dispatchers.Main).launch {
                        TemperatureWidget().updateAll(context)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}

@Composable
fun DropdownMenu(
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.clickable { expanded = true }
                )
            }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun isValidHexColor(color: String): Boolean {
    return color.matches(Regex("^[0-9A-Fa-f]{8}$")) || color == "TRANSPARENT"
}

private fun saveSettings(
    context: Context,
    url: String,
    width: String,
    height: String,
    backgroundColor: String,
    textColor: String
) {
    TemperatureUtils.saveSettings(context, url, width, height, backgroundColor, textColor)
}
