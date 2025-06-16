package com.example.pelletswaterheater

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.glance.appwidget.GlanceAppWidgetManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TemperatureWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode
        get() = SizeMode.Exact

    @SuppressLint("StateFlowValueCalledInComposition")
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val viewModel = TemperatureViewModel.getInstance()
            Log.d("TemperatureWidget", "provideGlance: glanceId=$id, viewModel=${viewModel.hashCode()}, temp=${viewModel.temperature.value}")
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
        val viewModel = TemperatureViewModel.getInstance()
        val rawTemperature by viewModel.temperature.collectAsState()
        val error by viewModel.error.collectAsState()
        val lastUpdateTime by viewModel.lastUpdateTime.collectAsState()
        val state by viewModel.state.collectAsState()
        val temperature = TemperatureUtils.formatTemperature(rawTemperature)
        val formattedTime = if (lastUpdateTime > 0) {
            TemperatureUtils.formatTimeStamp(lastUpdateTime)
        } else {
            ""
        }

        Column(
            modifier = GlanceModifier
                .size(width, height)
                .background(backgroundColor)
                .cornerRadius(8.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Log.d("TemperatureWidget", "Rendering: temp=$temperature, rawTemp=$rawTemperature, time=$formattedTime, error=$error, bgColor=$backgroundColor, width=$width, height=$height")
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
                    text = temperature.toString(),
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
            if (state != null) {
                Text(
                    text = TemperatureUtils.formattedState(state),
                    style = TextStyle(fontSize = 12.sp, color = ColorProvider(textColor)),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(top = 4.dp)
                )
            }
            if (error != null) {
                Text(
                    text = error!!,
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

    suspend fun forceUpdateAll(context: Context) {
        try {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(TemperatureWidget::class.java)
            Log.d("TemperatureWidget", "forceUpdateAll: Found ${glanceIds.size} widget instances")
            glanceIds.forEach { glanceId ->
                update(context, glanceId)
                Log.d("TemperatureWidget", "Updated glanceId=$glanceId")
            }
            if (glanceIds.isEmpty()) {
                Log.w("TemperatureWidget", "No widget instances found, retrying after 1s")
                CoroutineScope(Dispatchers.Main).launch {
                    delay(1000)
                    forceUpdateAll(context)
                    Log.d("TemperatureWidget", "Fallback retry triggered")
                }
            }
        } catch (e: Exception) {
            Log.e("TemperatureWidget", "Failed to force update widgets", e)
            CoroutineScope(Dispatchers.Main).launch {
                delay(1000)
                forceUpdateAll(context)
                Log.d("TemperatureWidget", "Retry after failure triggered")
            }
        }
    }
}

class TemperatureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TemperatureWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CoroutineScope(Dispatchers.Main).launch {
            (glanceAppWidget as TemperatureWidget).forceUpdateAll(context)
            Log.d("TemperatureWidgetReceiver", "onEnabled: Widget update triggered")
        }
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val sharedPrefs = TemperatureUtils.getSharedPrefs(context)
        val lastRefresh = sharedPrefs.getLong("last_refresh_time", 0)
        val currentTime = System.currentTimeMillis()
        Log.d("RefreshWidgetAction", "Refresh attempt: currentTime=$currentTime, lastRefresh=$lastRefresh, delta=${currentTime - lastRefresh}ms")
        if (currentTime - lastRefresh < 5000) {
            Log.d("RefreshWidgetAction", "Refresh debounced: Too soon since last refresh")
            return
        }
        try {
            sharedPrefs.edit().putLong("last_refresh_time", currentTime).apply()
            TemperatureUtils.forceTemperatureUpdate(context)
            (TemperatureWidget()).forceUpdateAll(context)
            Log.d("RefreshWidgetAction", "Refresh enqueued successfully")
        } catch (e: Exception) {
            Log.e("RefreshWidgetAction", "Refresh failed", e)
            (TemperatureWidget()).forceUpdateAll(context)
        }
    }
}
