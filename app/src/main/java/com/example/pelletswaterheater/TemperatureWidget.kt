package com.example.pelletswaterheater

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.glance.unit.ColorProvider
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import kotlin.math.min
import kotlinx.serialization.json.Json
import java.text.DecimalFormat
import kotlinx.serialization.json.jsonObject
import com.example.pelletswaterheater.TemperatureUtils.formatTemperature
import java.util.concurrent.TimeUnit

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
            rawTemperature == "Loading..." -> "Loading..."
            else -> formatTemperature(rawTemperature)
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

    private fun getStoredBackgroundColor(context: Context): Color = TemperatureUtils.getStoredBackgroundColor(context)

    private fun getStoredTextColor(context: Context): Color = TemperatureUtils.getStoredTextColor(context)
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

