package com.example.pelletswaterheater

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.unit.ColorProvider
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL

class TemperatureWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            TemperatureWidgetContent()
        }
    }

    @Composable
    fun TemperatureWidgetContent() {
        val temperature = TemperatureWorker.latestTemperatureFlow.value ?: "Loading..."

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFFF3E5F5))
                .cornerRadius(16.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = temperature,
                style = TextStyle(fontSize = 24.sp, color = ColorProvider(Color.Black))
            )
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                modifier = GlanceModifier
                    .size(24.dp)
                    .clickable(actionRunCallback<RefreshWidgetAction>())
            )
        }
    }
}

class TemperatureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TemperatureWidget()
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<TemperatureWorker>().build())
    }
}

class TemperatureWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val TAG = TemperatureWorker::class.java.simpleName;

    companion object {
        val latestTemperatureFlow = MutableStateFlow<String?>(null)
        val errorFlow = MutableStateFlow<String?>(null)
        private const val SERVER_IP = "192.168.178.4"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!isOnSameLocalNetwork(applicationContext)) {
            latestTemperatureFlow.value = "Wrong Network"
            errorFlow.value = "Device not on the same network as the server"
            return@withContext Result.failure()
        }

        try {
            val url = getStoredUrl(applicationContext) ?: "http://$SERVER_IP:4321/<PASSWORD>/all"
            val jsonString = URL(url).readText()
            val json = Json.parseToJsonElement(jsonString).jsonObject
            val ambient = json["system"]?.jsonObject?.get("L_ambient")?.jsonPrimitive?.content
            val temp = ambient?.toDoubleOrNull()?.let { (it * 0.1).toString() + "°C" } ?: "Error"
            latestTemperatureFlow.value = temp
            errorFlow.value = null
            Result.success(workDataOf("temperature" to temp))
        } catch (e: Exception) {
            latestTemperatureFlow.value = "Error"
            errorFlow.value = "Failed to fetch temperature: ${e.message}"
            Log.e(TAG, e.message, e)
            Result.failure()
        }.also { delay(10 * 60 * 1000L) } // 10 minutes
    }

    private fun isOnSameLocalNetwork(context: Context): Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo ?: return false
        val deviceIp = wifiInfo.ipAddress
        if (deviceIp == 0) return false

        val deviceIpString = formatIpAddress(deviceIp)
        return deviceIpString.split(".").take(3) == SERVER_IP.split(".").take(3)
    }

    private fun formatIpAddress(ip: Int): String {
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }
}