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
import androidx.compose.ui.unit.sp
import com.example.pelletswaterheater.ui.theme.PelletsWaterHeaterTheme

class SettingsActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PelletsWaterHeaterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(viewModel)
                }
            }
        }
    }

    companion object {
        val DEFAULT_SERVER_URL: String = "http://192.168.178.4:4321/<PASSWORD>/all"
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val serverUrl by viewModel.serverUrl.collectAsState()
    val widgetWidth by viewModel.widgetWidth.collectAsState()
    val widgetHeight by viewModel.widgetHeight.collectAsState()
    val backgroundColor by viewModel.backgroundColor.collectAsState()
    val textColor by viewModel.textColor.collectAsState()
    val backgroundColorSelection by viewModel.backgroundColorSelection.collectAsState()
    val textColorSelection by viewModel.textColorSelection.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val predefinedColors = listOf(
        "Purple" to "FFF3E5F5",
        "White" to "FFFFFFFF",
        "Black" to "FF000000",
        "Transparent" to "TRANSPARENT"
    )

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
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
            onValueChange = { viewModel.updateServerUrl(it) },
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null
        )
        Button(
            onClick = { viewModel.testServerUrl(context) },
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
            onValueChange = { viewModel.updateWidgetWidth(it) },
            label = { Text("Widget Width (dp)") },
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null
        )
        OutlinedTextField(
            value = widgetHeight,
            onValueChange = { viewModel.updateWidgetHeight(it) },
            label = { Text("Widget Height (dp)") },
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null
        )
        Text(text = "Background Color", style = MaterialTheme.typography.bodyLarge)
        DropdownMenu(
            selectedOption = backgroundColorSelection,
            options = predefinedColors.map { it.first } + "Custom",
            onOptionSelected = { viewModel.updateBackgroundColorSelection(it) }
        )
        if (backgroundColorSelection == "Custom") {
            OutlinedTextField(
                value = backgroundColor,
                onValueChange = { viewModel.updateBackgroundColor(it) },
                label = { Text("Background Color (Hex, e.g., FFF3E5F5)") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage != null
            )
        }
        Text(text = "Text Color", style = MaterialTheme.typography.bodyLarge)
        DropdownMenu(
            selectedOption = textColorSelection,
            options = predefinedColors.map { it.first }.filter { it != "Transparent" } + "Custom",
            onOptionSelected = { viewModel.updateTextColorSelection(it) }
        )
        if (textColorSelection == "Custom") {
            OutlinedTextField(
                value = textColor,
                onValueChange = { viewModel.updateTextColor(it) },
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
                if (viewModel.saveSettings(context)) {
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
