package com.example.pelletswaterheater

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pelletswaterheater.ui.theme.PelletsWaterHeaterTheme
import org.koin.android.ext.android.inject

class SettingsActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PelletsWaterHeaterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreener(viewModel)
                }
            }
        }
    }
}

@Composable
fun SettingsScreener(settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    val serverUrl by settingsViewModel.serverUrl.collectAsState()
    val widgetWidth by settingsViewModel.widgetWidth.collectAsState()
    val widgetHeight by settingsViewModel.widgetHeight.collectAsState()
    val backgroundColor by settingsViewModel.backgroundColor.collectAsState()
    val textColor by settingsViewModel.textColor.collectAsState()
    val backgroundColorSelection by settingsViewModel.backgroundColorSelection.collectAsState()
    val textColorSelection by settingsViewModel.textColorSelection.collectAsState()
    val errorMessage by settingsViewModel.errorMessage.collectAsState()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { settingsViewModel.updateServerUrl(it) },
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null
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
            isError = errorMessage != null
        )
        OutlinedTextField(
            value = widgetHeight,
            onValueChange = { settingsViewModel.updateWidgetHeight(it) },
            label = { Text("Widget Height (dp)") },
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null
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
                isError = errorMessage != null
            )
        }
        Text(text = "Text Color", style = MaterialTheme.typography.bodyLarge)
        DropdownMenu(
            selectedOption = textColorSelection,
            options = predefinedColors.map { it.first }.filter { it != "Transparent" } + "Custom",
            onOptionSelected = { settingsViewModel.updateTextColorSelection(it) }
        )
        if (textColorSelection == "Custom") {
            OutlinedTextField(
                value = textColor,
                onValueChange = { settingsViewModel.updateTextColor(it) },
                label = { Text("Text Color (Hex, e.g., FF000000)") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage != null
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
