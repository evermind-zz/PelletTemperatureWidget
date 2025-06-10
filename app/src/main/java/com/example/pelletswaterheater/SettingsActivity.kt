package com.example.pelletswaterheater

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pelletswaterheater.ui.theme.PelletsWaterHeaterTheme
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
    var serverUrl by remember { mutableStateOf(getStoredUrl(context) ?: "http://192.168.178.4:4321/<PASSWORD>/all") }
    var widgetWidth by remember { mutableStateOf(getStoredWidgetSize(context, "width") ?: "100") }
    var widgetHeight by remember { mutableStateOf(getStoredWidgetSize(context, "height") ?: "100") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        else -> null
                    }
                } else {
                    errorMessage = null
                    saveSettings(context, serverUrl, widgetWidth, widgetHeight)
                    Toast.makeText(context, "settings saved", Toast.LENGTH_SHORT).show()
                    WorkManager.getInstance(context).enqueue(
                        OneTimeWorkRequestBuilder<TemperatureWorker>()
                            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.SECONDS)
                            .build()
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}

private fun getSetting(context: Context, key: String): String? {
    val sharedPrefs = getSharedPrefs(context)
    return sharedPrefs.getString(key, null)
}

private fun getSharedPrefs(context: Context): SharedPreferences {
    val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    val sharedPrefs = EncryptedSharedPreferences.create(
        "secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    return sharedPrefs
}

fun getStoredUrl(context: Context): String? {
    return getSetting(context, "server_url")
}

private fun getStoredWidgetSize(context: Context, dimension: String): String? {
    return getSetting(context, "widget_$dimension")
}

private fun saveSettings(context: Context, url: String, width: String, height: String) {
    val sharedPrefs = getSharedPrefs(context)
    with(sharedPrefs.edit()) {
        putString("server_url", url)
        putString("widget_width", width)
        putString("widget_height", height)
        apply()
    }
}
