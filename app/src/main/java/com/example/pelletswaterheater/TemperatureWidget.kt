package com.example.pelletswaterheater

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import androidx.glance.appwidget.SizeMode
import kotlin.math.min
import kotlinx.serialization.json.Json
import java.text.DecimalFormat
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL
import java.util.concurrent.TimeUnit

class TemperatureWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode
        get() = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val width = SettingsActivity.getStoredWidgetSize(context, "width")?.toIntOrNull() ?: 100
            val height = SettingsActivity.getStoredWidgetSize(context, "height")?.toIntOrNull() ?: 100
            TemperatureWidgetContent(
                width = width.dp,
                height = height.dp,
                backgroundColor = getStoredBackgroundColor(context),
                textColor = getStoredTextColor(context)
            )
        }
    }

    @SuppressLint("StateFlowValueCalledInComposition")
    @Composable
    fun TemperatureWidgetContent(
        width: Dp = 100.dp,
        height: Dp = 100.dp,
        backgroundColor: Color = Color(0xFFF3E5F5), // Default purple
        textColor: Color = Color.Black // Default black
    ) {
        val rawTemperature = TemperatureWorker.latestTemperatureFlow.value ?: "Loading..."
        val temperature = when {
            rawTemperature == "Error" -> "Error"
            rawTemperature == "Loading..." -> "Loading..."
            else -> try {
                DecimalFormat("0.0").format(rawTemperature.toFloat()) + "°C"
            } catch (e: NumberFormatException) {
                "Error"
            }
        }
        val error = TemperatureWorker.errorFlow.value

        Column(
            modifier = GlanceModifier
                .size(width, height)
                .background(backgroundColor)
                .cornerRadius(16.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Log.d("TemperatureWidget", "Rendering: temp=$temperature, error=$error, bgColor=$backgroundColor, width=$width, height=$height")
            Row(
                modifier = GlanceModifier.fillMaxWidth().wrapContentWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = temperature,
                    style = TextStyle(fontSize = 20.sp, color = ColorProvider(textColor)),
                    modifier = GlanceModifier.wrapContentWidth()
                )
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Refresh",
                    modifier = GlanceModifier
                        .size(24.dp)
                        .clickable(actionRunCallback<RefreshWidgetAction>())
                        .padding(start = 8.dp)
                )
            }
            if (error != null) {
                Text(
                    text = error,
                    style = TextStyle(fontSize = 12.sp, color = ColorProvider(textColor)),
                    modifier = GlanceModifier.fillMaxWidth().wrapContentHeight()
                )
            }
        }
    }

    private fun getStoredBackgroundColor(context: Context): Color {
        val sharedPrefs = SettingsActivity.getSharedPrefs(context)
        val colorHex = sharedPrefs.getString("background_color", "FFF3E5F5") // Default purple
        return if (colorHex == "TRANSPARENT") Color.Transparent else Color(colorHex?.toLong(16) ?: 0xFFF3E5F5)
    }

    private fun getStoredTextColor(context: Context): Color {
        val sharedPrefs = SettingsActivity.getSharedPrefs(context)
        val colorHex = sharedPrefs.getString("text_color", "FF000000") // Default black
        return Color(colorHex?.toLong(16) ?: 0xFF000000)
    }
}

class TemperatureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TemperatureWidget()
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<TemperatureWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.SECONDS)
                .build()
        )
    }
}

class TemperatureWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val TAG = TemperatureWorker::class.java.simpleName;

    companion object {
        val latestTemperatureFlow = MutableStateFlow<String?>(null)
        val errorFlow = MutableStateFlow<String?>(null)
        private const val SERVER_IP = "192.168.178.4"
        private var failureCount = 0
        private const val INITIAL_BACKOFF_MS = 5000L // 5 seconds
        private const val MAX_BACKOFF_MS = 60000L // 60 seconds
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!isOnSameLocalNetwork(applicationContext)) {
            latestTemperatureFlow.value = "Wrong Network"
            errorFlow.value = "Device not on the same network as the server"
            return@withContext Result.failure()
        }
        applyBackoffDelay()

        try {
            val url = getStoredUrl(applicationContext) ?: "http://$SERVER_IP:4321/<PASSWORD>/all"
            val jsonString = URL(url).readText()
            val json = Json.parseToJsonElement(jsonString).jsonObject
            val ambient = json["system"]?.jsonObject?.get("L_ambient")?.jsonPrimitive?.content
            val temp = ambient?.toDoubleOrNull()?.let { (it * 0.1).toString() + "°C" } ?: "Error"
            latestTemperatureFlow.value = temp
            errorFlow.value = null
            failureCount = 0 // Reset backoff on success
            Result.success(workDataOf("temperature" to temp))
        } catch (e: Exception) {
            latestTemperatureFlow.value = "Error"
            errorFlow.value = "Failed to fetch temperature: ${e.message}"
            Log.e(TAG, e.message, e)
            failureCount++ // Increment failure count for backoff
            Result.failure()
        }
    }

    private suspend fun applyBackoffDelay() {
        if (failureCount > 0) {
            delay(min(INITIAL_BACKOFF_MS * (1L shl failureCount), MAX_BACKOFF_MS))
        }
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
