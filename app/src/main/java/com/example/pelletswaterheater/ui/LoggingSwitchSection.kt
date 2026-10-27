package com.example.pelletswaterheater.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.pelletswaterheater.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun LoggingSwitchSection(
    settingsViewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val isLoggingEnabled by settingsViewModel.isLoggingEnabled.collectAsState()
    val showBatteryOptimizationDialog by settingsViewModel.showBatteryOptimizationDialog.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.Companion.CenterVertically) {
        Switch(
            checked = isLoggingEnabled,
            onCheckedChange = { settingsViewModel.toggleLogging() }
        )
        Spacer(Modifier.Companion.width(8.dp))
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
}

@Composable
public fun ShowBatteryOptimizeDialog(
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

