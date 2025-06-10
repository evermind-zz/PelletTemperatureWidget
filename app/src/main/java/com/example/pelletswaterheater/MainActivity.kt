package com.example.pelletswaterheater

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pelletswaterheater.ui.theme.PelletsWaterHeaterTheme
import com.example.pelletswaterheater.TemperatureUtils.formatTemperature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleTemperatureUpdate()
        setContent {
            PelletsWaterHeaterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TemperatureDisplay()
                }
            }
        }
    }

    private fun scheduleTemperatureUpdate() {
        val periodicWorkRequest = PeriodicWorkRequestBuilder<TemperatureWorker>(10, TimeUnit.MINUTES)
           .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(this).enqueue(periodicWorkRequest)
    }
}

@Composable
fun TemperatureDisplay(viewModel: TemperatureViewModel = viewModel()) {
    val temperatureState = viewModel.temperature.collectAsState().value
    val error = viewModel.error.collectAsState().value
    val context = LocalContext.current

    error?.let {
        Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        viewModel.clearError()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatTemperature(temperatureState),
            fontSize = 48.sp,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = {
                context.startActivity(Intent(context, SettingsActivity::class.java))
            }
        ) {
            Text("Open Settings")
        }
    }
}

class TemperatureViewModel : ViewModel() {
    private val _temperature = MutableStateFlow<String?>(null)
    val temperature: StateFlow<String?> = _temperature
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch {
            TemperatureWorker.latestTemperatureFlow.collect { temp ->
                _temperature.value = temp
            }
        }
        viewModelScope.launch {
            TemperatureWorker.errorFlow.collect { err ->
                _error.value = err
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
