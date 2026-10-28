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
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TemperatureWidget : GlanceAppWidget(), KoinComponent {

    private val viewModel: TemperatureViewModelNew by inject()
    override val sizeMode: SizeMode
        get() = SizeMode.Exact

    @SuppressLint("StateFlowValueCalledInComposition")
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetSnapshotData = viewModel.getWidgetValues()
        provideContent {
            Log.d("TemperatureWidget", "provideGlance: glanceId=$id, viewModel=${viewModel.hashCode()}")
            //val width = TemperatureUtils.getStoredWidgetSize(context, "width")?.toIntOrNull() ?: 100
            //val height = TemperatureUtils.getStoredWidgetSize(context, "height")?.toIntOrNull() ?: 100
            val size = LocalSize.current // in DpSize (width.dp x height.dp)
            TemperatureWidgetContent(
                width = size.width,
                height = size.height,
                backgroundColor = getStoredBackgroundColor(context),
                textColor = getStoredTextColor(context),
                snapshotData = widgetSnapshotData
            )
        }
    }

    @Composable
    fun TemperatureWidgetContent(
        width: Dp = 100.dp,
        height: Dp = 100.dp,
        backgroundColor: Color = Color(0xFFF3E5F5),
        textColor: Color = Color.Black,
        snapshotData: TemperatureViewModelNew.WidgetDataWrapper
    ) {
        val widgetValues = snapshotData.snapshot // Simplified access
        val state = snapshotData.state
        val error = snapshotData.error
        val formattedTime = snapshotData.timestampSuccess

        Column(
            modifier = GlanceModifier
                .size(width, height)
                .background(backgroundColor)
                .cornerRadius(8.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start
        ) {

            Row(GlanceModifier.fillMaxWidth()) {
                // Icon column
                Column(
                    modifier = GlanceModifier.padding(end = 8.dp)
                        .clickable(actionRunCallback<RefreshWidgetAction>()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_refresh),
                            contentDescription = "Refresh",
                            modifier = GlanceModifier
                                .size(20.dp)
                                .padding(bottom = 4.dp),
                        )
                    }
                }

                // Data column
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    LazyColumn(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(widgetValues.size) { index ->
                            val wrapper = widgetValues[index]
                            Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    text = TemperatureUtils.getNameSuffix(wrapper),
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = ColorProvider(textColor),
                                        textAlign = TextAlign.Start
                                    ),
                                )
                                Text(
                                    text = TemperatureUtils.getLogicalValueWithUnit(wrapper.convertedValue),
                                    style = TextStyle(
                                        fontSize = 18.sp,
                                        color = ColorProvider(textColor),
                                        textAlign = TextAlign.Center
                                    ),
                                    modifier = GlanceModifier.padding(top = 2.dp, bottom = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Footer info
            if (formattedTime.isNotEmpty()) {
                Text(
                    text = "Last update: $formattedTime",
                    style = TextStyle(fontSize = 12.sp, color = ColorProvider(textColor)),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }

            if (state.isNotEmpty()) {
                Text(
                    text = TemperatureUtils.formattedState(state),
                    style = TextStyle(fontSize = 12.sp, color = ColorProvider(textColor)),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }

            //if (error.isNotEmpty()) {
            //    Text(
            //        text = error,
            //        style = TextStyle(fontSize = 12.sp, color = ColorProvider(textColor)),
            //        modifier = GlanceModifier
            //            .fillMaxWidth()
            //            .padding(top = 2.dp)
            //    )
            //}
            error?.let {
                Text(
                    text = it,
                    style = TextStyle(fontSize = 12.sp, color = ColorProvider(textColor)),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        //.wrapContentHeight()
                        .padding(top = 2.dp)
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

class RefreshWidgetAction : ActionCallback, KoinComponent {
    private val preferencesRepository: PreferencesRepository by inject()
    private val temperatureUtils: TemperatureUtils by inject()

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val lastRefresh = preferencesRepository.lastRefreshTime.first().await() ?: 0L
        val currentTime = System.currentTimeMillis()
        Log.d("RefreshWidgetAction", "Refresh attempt: currentTime=$currentTime, lastRefresh=$lastRefresh, delta=${currentTime - lastRefresh}ms")
        if (currentTime - lastRefresh < 5000) {
            Log.d("RefreshWidgetAction", "Refresh debounced: Too soon since last refresh")
            return
        }
        try {
            preferencesRepository.setLastRefreshTime(currentTime).await()
            temperatureUtils.forceTemperatureUpdate()
            (TemperatureWidget()).forceUpdateAll(context)
            Log.d("RefreshWidgetAction", "Refresh enqueued successfully")
        } catch (e: Exception) {
            Log.e("RefreshWidgetAction", "Refresh failed", e)
            (TemperatureWidget()).forceUpdateAll(context)
        }
    }
}
