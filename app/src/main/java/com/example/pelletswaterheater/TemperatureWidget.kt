package com.example.pelletswaterheater

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class TemperatureWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode
        get() = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val width = TemperatureUtils.getStoredWidgetSize(context, "width")?.toIntOrNull() ?: 100
            val height = TemperatureUtils.getStoredWidgetSize(context, "height")?.toIntOrNull() ?: 100
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
            rawTemperature == "Loading..." -> "Unknown"
            else -> TemperatureUtils.formatTemperature(rawTemperature)
        }
        val error = TemperatureWorker.errorFlow.value
        val lastUpdateTime = TemperatureWorker.lastUpdateTimeFlow.value
        val formattedTime = if (lastUpdateTime > 0) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(java.util.Date(lastUpdateTime))
        } else {
            ""
        }

        Column(
            modifier = GlanceModifier
                .size(width, height)
                .background(backgroundColor)
                .cornerRadius(16.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Log.d("TemperatureWidget", "Rendering: temp=$temperature, time=$formattedTime, error=$error, bgColor=$backgroundColor, width=$width, height=$height")
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Refresh",
                    modifier = GlanceModifier
                        .size(20.dp)
                        .clickable(actionRunCallback<RefreshWidgetAction>())
                        .padding(end = 4.dp)
                )
                Text(
                    text = temperature,
                    style = TextStyle(fontSize = 20.sp, color = ColorProvider(textColor)),
                    modifier = GlanceModifier.wrapContentWidth()
                )
            }
            if (formattedTime.isNotEmpty()) {
                Text(
                    text = "Last updated: $formattedTime",
                    style = TextStyle(fontSize = 12.sp, color = ColorProvider(textColor), textAlign = TextAlign.Center),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(top = 4.dp)
                )
            }
            if (error != null) {
                Text(
                    text = error,
                    style = TextStyle(fontSize = 12.sp, color = ColorProvider(textColor)),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(top = 4.dp)
                )
            }
        }
    }

    private fun getStoredBackgroundColor(context: Context): Color = TemperatureUtils.getStoredBackgroundColor(context)

    private fun getStoredTextColor(context: Context): Color = TemperatureUtils.getStoredTextColor(context)
}

class TemperatureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TemperatureWidget()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TemperatureWorker.ACTION_UPDATE_WIDGET) {
                CoroutineScope(Dispatchers.Main).launch {
                    glanceAppWidget.updateAll(context)
                    Log.d("TemperatureWidgetReceiver", "Received update broadcast, widget updated")
                }
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.registerReceiver(receiver, IntentFilter(TemperatureWorker.ACTION_UPDATE_WIDGET))
    }

    override fun onDisabled(context: Context) {
        context.unregisterReceiver(receiver)
        super.onDisabled(context)
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // Debounce: Check last refresh time
        val sharedPrefs = TemperatureUtils.getSharedPrefs(context)
        val lastRefresh = sharedPrefs.getLong("last_refresh_time", 0)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRefresh < 5000) {
            Log.d("RefreshWidgetAction", "Refresh debounced: Too soon since last refresh")
            return
        }
        sharedPrefs.edit().putLong("last_refresh_time", currentTime).apply()

        TemperatureUtils.forceTemperatureUpdate(context)
    }
}
