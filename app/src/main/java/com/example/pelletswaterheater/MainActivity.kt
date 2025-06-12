package com.example.pelletswaterheater

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reinitializeWidget()
        TemperatureUtils.enqueueTemperatureUpdate(this)
        TemperatureUtils.schedulePeriodicTemperatureUpdate(this)
        CoroutineScope(Dispatchers.Main).launch {
            TemperatureWidget().updateAll(this@MainActivity)
        }
        setContent {
            TemperatureDisplay()
        }
    }

    private fun reinitializeWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, TemperatureWidgetReceiver::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (widgetIds.isNotEmpty()) {
            appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, 0)
            CoroutineScope(Dispatchers.Main).launch {
                TemperatureWidget().updateAll(this@MainActivity)
            }
        }
    }
}

@Composable
fun TemperatureDisplay(viewModel: TemperatureViewModel = TemperatureViewModel.getInstance()) {
    val temperatureState by viewModel.temperature.collectAsState()
    val error by viewModel.error.collectAsState()
    val lastUpdateTime by viewModel.lastUpdateTime.collectAsState()
    val context = LocalContext.current

    error?.let {
        Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        viewModel.clearError()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = temperatureState?.let { TemperatureUtils.formatTemperature(it) } ?: "Unknown",
            fontSize = 24.sp
        )
        if (lastUpdateTime > 0) {
            Text(
                text = "Last updated: ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(lastUpdateTime))}",
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                TemperatureUtils.forceTemperatureUpdate(context)
            }
        ) {
            Text("Refresh")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { context.startActivity(android.content.Intent(context, SettingsActivity::class.java)) }) {
            Text("Settings")
        }
    }
}
