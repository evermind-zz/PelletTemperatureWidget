package com.example.pelletswaterheater.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.pelletswaterheater.SettingsViewModel
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
@Preview
fun SettingsScreen(
    viewModel: TemperatureViewModelNew = koinInject(),
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var metadataJson by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }

    val serverUrl by settingsViewModel.serverUrl.collectAsState()
    val widgetWidth by settingsViewModel.widgetWidth.collectAsState()
    val widgetHeight by settingsViewModel.widgetHeight.collectAsState()
    val backgroundColor by settingsViewModel.backgroundColor.collectAsState()
    val textColor by settingsViewModel.textColor.collectAsState()
    val backgroundColorSelection by settingsViewModel.backgroundColorSelection.collectAsState()
    val textColorSelection by settingsViewModel.textColorSelection.collectAsState()
    val errorMessage2 by settingsViewModel.errorMessage.collectAsState()
    val testResult by settingsViewModel.testResult.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    val predefinedColors = listOf(
        "Purple" to "FFF3E5F5",
        "White" to "FFFFFFFF",
        "Black" to "FF000000",
        "Transparent" to "TRANSPARENT"
    )
    val isLoggingEnabled by settingsViewModel.isLoggingEnabled.collectAsState()
    val showBatteryOptimizationDialog by settingsViewModel.showBatteryOptimizationDialog.collectAsState()
    val loggingInterval by settingsViewModel.loggingInterval.collectAsState()
    var intervalInput by remember { mutableStateOf(loggingInterval.toString()) }

    val snackbarHostState = remember { SnackbarHostState() }

    val launcher = createManagedLauncher(context, viewModel)

    LaunchedEffect(Unit) {
        settingsViewModel.initialize(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .imePadding()
    ) {
        val context = LocalContext.current

        // SECTION 1: Metadata Controls
        Text("Metadata File", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        Text("Current File: ${viewModel.getMetadataFileName()}")
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    metadataJson = viewModel.getCurrentMetadataJson()
                    showEditDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(4.dp)
            ) {
                Text("Edit")
            }
            Button(
                onClick = { viewModel.shareMetadata(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(4.dp)
            ) {
                Text("Share")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { viewModel.resetMetadata() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(4.dp)

            ) {
                Text("Reset to Default")
            }
            Button(
                onClick = {
                    launcher.launch("application/json")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(4.dp)
            ) {
                Text("Import New")
            }
        }

        Spacer(Modifier.height(24.dp))

        // SECTION 2: Logging Toggle
        Text("Logging", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            //Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = intervalInput,
                onValueChange = { intervalInput = it },
                label = { Text("Interval in milliseconds") },
                modifier = Modifier
                    .weight(1f)
                    .padding(4.dp)
            )
            Button(
                onClick = {
                    val interval = intervalInput.toLongOrNull() ?: 5000L
                    settingsViewModel.setLoggingInterval(interval)
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(4.dp)
            ) {
                Text("Set Interval")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = isLoggingEnabled,
                onCheckedChange = { settingsViewModel.toggleLogging() }
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isLoggingEnabled) "Logging is ON" else "Logging is OFF")
        }

        if (showBatteryOptimizationDialog) {
            ShowBatteryOptimizeDialog(
                LocalContext.current,
                settingsViewModel,
                snackbarHostState,
                coroutineScope
            )
        }

        Spacer(Modifier.height(32.dp))

        // SECTION 3: Server & Appearance
        Text("Server Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

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
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("Widget Appearance", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        /* we do not set the widget size anymore - but leave for later (delete or reuse:))
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
        */

        Text("Background Color", style = MaterialTheme.typography.bodyLarge)
        DropdownMenuContent(
            selectedOption = backgroundColorSelection,
            options = predefinedColors.map { it.first } + "Custom",
            onOptionSelected = { settingsViewModel.updateBackgroundColorSelection(it) }
        )
        if (backgroundColorSelection == "Custom") {
            OutlinedTextField(
                value = backgroundColor,
                onValueChange = { settingsViewModel.updateBackgroundColor(it) },
                label = { Text("Hex Background Color") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text("Text Color", style = MaterialTheme.typography.bodyLarge)
        DropdownMenuContent(
            selectedOption = textColorSelection,
            options = predefinedColors.map { it.first }
                .filter { it != "Transparent" } + "Custom",
            onOptionSelected = { settingsViewModel.updateTextColorSelection(it) }
        )
        if (textColorSelection == "Custom") {
            OutlinedTextField(
                value = textColor,
                onValueChange = { settingsViewModel.updateTextColor(it) },
                label = { Text("Hex Text Color") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (settingsViewModel.saveSettings(context)) {
                    Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Settings")
        }
    }

    // METADATA EDIT DIALOG
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Metadata JSON") },
            text = {
                Column {
                    OutlinedTextField(
                        value = metadataJson,
                        onValueChange = {
                            metadataJson = it
                            editError = null
                        },
                        label = { Text("Metadata") },
                        isError = editError != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                    editError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        viewModel.loadUserMetadata(metadataJson)
                        showEditDialog = false
                    } catch (e: Exception) {
                        editError = "Invalid JSON: ${e.message}"
                    }
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ShowBatteryOptimizeDialog(
    context: Context,
    settingsViewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope
) {
    AlertDialog(
        onDismissRequest = { settingsViewModel.hideBatteryOptimizationDialog() },
        title = { Text("Disable Battery Optimization") },
        text = {
            Text(
                "To ensure reliable logging, please disable battery optimization for this app. " +
                        "Android may kill the logging service if optimization is enabled."
            )
        },
        confirmButton = {
            Button(onClick = {
                settingsViewModel.onBatteryOptimizationDialogConfirmed(true)
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    ContextCompat.startActivity(context, intent, null)
                } catch (e: ActivityNotFoundException) {
                    // Show snackbar if system blocks or intent fails
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Could not open battery optimization settings. Please open it manually.",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            Button(onClick = { settingsViewModel.onBatteryOptimizationDialogConfirmed(false) }) {
                Text("Continue Anyway")
            }
        }
    )
}

@Composable
private fun createManagedLauncher(
    context: Context,
    viewModel: TemperatureViewModelNew
): ManagedActivityResultLauncher<String, Uri?> {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    val content =
                        inputStream?.bufferedReader()?.use { reader -> reader.readText() } ?: ""
                    viewModel.loadUserMetadata(content)
                    Toast.makeText(context, "Metadata loaded successfully", Toast.LENGTH_SHORT)
                        .show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Failed to load metadata: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    return launcher
}

@Composable
fun DropdownMenuContent(
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
