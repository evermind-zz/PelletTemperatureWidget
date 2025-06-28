package com.example.pelletswaterheater.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pelletswaterheater.SettingsViewModel
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
@Preview
fun SettingsScreen(viewModel: TemperatureViewModelNew = koinInject(),
                   settingsViewModel: SettingsViewModel = koinViewModel()) {
    var jsonInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

     Column(
             modifier = Modifier
                         .fillMaxSize()
                         .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                         .imePadding()
                 ) {

         // Section 1: JSON Input
         Text("User JSON")
         TextField(
             value = jsonInput,
             onValueChange = { jsonInput = it },
             modifier = Modifier.fillMaxWidth(),
             isError = errorMessage.isNotEmpty(),
             supportingText = { if (errorMessage.isNotEmpty()) Text(errorMessage) }
         )
         Button(onClick = {
             try {
                 viewModel.loadUserMetadata(jsonInput)
                 errorMessage = ""
                 jsonInput = ""
             } catch (e: Exception) {
                 errorMessage = "Invalid JSON: ${e.message}"
             }
         }) {
             Text("Apply")
         }
         Button(onClick = {
             viewModel.resetMetadata()
             jsonInput = ""
             errorMessage = ""
         }) {
             Text("Reset")
         }
         Spacer(Modifier.height(32.dp))

         // Section 2: Logging Controls
         Text("Settings", style = MaterialTheme.typography.titleLarge)
         Spacer(Modifier.height(16.dp))
         Button(onClick = { viewModel.toggleAllLog(true) }) {
             Text("Enable All Logging")
         }
         Spacer(Modifier.height(8.dp))
         Button(onClick = { viewModel.toggleAllLog(false) }) {
             Text("Disable All Logging")
         }

         val context = LocalContext.current
         val serverUrl by settingsViewModel.serverUrl.collectAsState()
         val widgetWidth by settingsViewModel.widgetWidth.collectAsState()
         val widgetHeight by settingsViewModel.widgetHeight.collectAsState()
         val backgroundColor by settingsViewModel.backgroundColor.collectAsState()
         val textColor by settingsViewModel.textColor.collectAsState()
         val backgroundColorSelection by settingsViewModel.backgroundColorSelection.collectAsState()
         val textColorSelection by settingsViewModel.textColorSelection.collectAsState()
         val errorMessage2 by settingsViewModel.errorMessage.collectAsState()
         val testResult by settingsViewModel.testResult.collectAsState()
         val predefinedColors = listOf(
             "Purple" to "FFF3E5F5",
             "White" to "FFFFFFFF",
             "Black" to "FF000000",
             "Transparent" to "TRANSPARENT"
         )

         LaunchedEffect(Unit) {
             settingsViewModel.initialize(context)
         }

         Spacer(Modifier.height(32.dp))

         // Section 3: Settings
         Text(
             text = "Settings",
             style = MaterialTheme.typography.headlineMedium
         )
         OutlinedTextField(
             value = serverUrl,
             onValueChange = { settingsViewModel.updateServerUrl(it) },
             label = { Text("Server URL") },
             modifier = Modifier.fillMaxWidth(),
             isError = errorMessage2 != null
         )
         Button(
             onClick = { settingsViewModel.testServerUrl(context) },
             modifier = Modifier.fillMaxWidth()
         ) {
             Text("Test URL")
         }
         testResult?.let {
             Text(
                 text = it,
                 color = if (it == "Server reachable") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                 style = MaterialTheme.typography.bodySmall,
                 textAlign = TextAlign.Center,
             )
         }
         OutlinedTextField(
             value = widgetWidth,
             onValueChange = { settingsViewModel.updateWidgetWidth(it) },
             label = { Text("Widget Width (dp)") },
             modifier = Modifier.fillMaxWidth(),
             isError = errorMessage2 != null
         )
         OutlinedTextField(
             value = widgetHeight,
             onValueChange = { settingsViewModel.updateWidgetHeight(it) },
             label = { Text("Widget Height (dp)") },
             modifier = Modifier.fillMaxWidth(),
             isError = errorMessage2 != null
         )
         Text(text = "Background Color", style = MaterialTheme.typography.bodyLarge)
         DropdownMenu(
             selectedOption = backgroundColorSelection,
             options = predefinedColors.map { it.first } + "Custom",
             onOptionSelected = { settingsViewModel.updateBackgroundColorSelection(it) }
         )
         if (backgroundColorSelection == "Custom") {
             OutlinedTextField(
                 value = backgroundColor,
                 onValueChange = { settingsViewModel.updateBackgroundColor(it) },
                 label = { Text("Background Color (Hex, e.g., FFF3E5F5)") },
                 modifier = Modifier.fillMaxWidth(),
                 isError = errorMessage2 != null
             )
         }
         Text(text = "Text Color", style = MaterialTheme.typography.bodyLarge)
         DropdownMenu(
             selectedOption = textColorSelection,
             options = predefinedColors.map { it.first }
                 .filter { it != "Transparent" } + "Custom",
             onOptionSelected = { settingsViewModel.updateTextColorSelection(it) }
         )
         if (textColorSelection == "Custom") {
             OutlinedTextField(
                 value = textColor,
                 onValueChange = { settingsViewModel.updateTextColor(it) },
                 label = { Text("Text Color (Hex, e.g., FF000000)") },
                 modifier = Modifier.fillMaxWidth(),
                 isError = errorMessage2 != null
             )
         }
         errorMessage2?.let {
             Text(
                 text = it,
                 color = MaterialTheme.colorScheme.error,
                 style = MaterialTheme.typography.bodySmall
             )
         }
         Button(
             onClick = {
                 if (settingsViewModel.saveSettings(context)) {
                     Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                 }
             },
             modifier = Modifier.fillMaxWidth(),
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
        androidx.compose.material3.DropdownMenu(
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
