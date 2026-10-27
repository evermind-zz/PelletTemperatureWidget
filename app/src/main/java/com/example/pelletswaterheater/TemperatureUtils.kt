package com.example.pelletswaterheater

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.example.pelletswaterheater.preferences.PreferencesRepository
import com.example.pelletswaterheater.utils.ConvertedValue
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew.ConvertedValueWrapper
import java.net.URL
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object Settings {
    const val KEY_METADATA_DEFAULT_FILE = "key_metadata_default_file"
    const val METADATA_DEFAULT_FILE = "metadata_en.json"
}

object TemperatureUtils : KoinComponent {
    private const val PERIODIC_WORK_NAME = "periodic_temperature_update"
    private const val ONETIME_WORK_NAME = "onetime_temperature_update"
    const val WHICH_WORKER = "which_worker"
    private const val WORK_TAG = "temperature_update"
    private const val TAG = "TemperatureUtils"
    var appContext: Context? = null // Set in Application or MainActivity

    private val preferencesRepository: PreferencesRepository by inject()

    fun formatTemperature(rawValue: String?): String {
        return when {
            rawValue == null || rawValue == "Unknown" -> "Unknown"
            else -> try {
                DecimalFormat("0.0").format(rawValue.toDouble()) + "°C"
            } catch (e: NumberFormatException) {
                Log.d(TAG, e.message, e)
                "Error"
            }
        }
    }

    fun getLogicalValueWithUnit(convertedValue: ConvertedValue): String {
        val displayUnit = convertedValue.unit?.takeIf { it.isNotBlank() } ?: ""
        return "${convertedValue.logical}${if (displayUnit.isNotEmpty()) " $displayUnit" else ""}"
    }

    fun getNameSuffix(value: ConvertedValueWrapper): String {
        val nameSuffix = if (value.showSectionName) {
            "${value.section}.${value.convertedValue.name}"
        } else {
            ""
        }
        return nameSuffix
    }

    fun formatTimeStamp(lastUpdateTime: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastUpdateTime))

    fun formattedState(state: String?) =
        "State: $state"

    // Synchronous getters for backward compatibility (blocking)
    fun getStoredUrl(): String {
        return runBlocking { preferencesRepository.serverUrl.first() } ?: SettingsViewModel.DEFAULT_SERVER_URL
    }

    fun getStoredLoggerHeaderHash(): Int {
        return runBlocking { preferencesRepository.storedOrderHash.first() } ?: -1
    }

    fun setStoredLoggerHeaderHash(context: Context, hash: Int) {
        runBlocking { preferencesRepository.setStoredOrderHash(hash) }
    }

    fun getStoredWidgetSize(context: Context, dimension: String): String? {
        return when (dimension) {
            "width" -> runBlocking { preferencesRepository.widgetWidth.first() }
            "height" -> runBlocking { preferencesRepository.widgetHeight.first() }
            else -> null
        }
    }

    fun getStoredBackgroundColor(context: Context): Color {
        val colorHex = runBlocking { preferencesRepository.backgroundColor.first() } ?: "FFF3E5F5"
        return if (colorHex == "TRANSPARENT") Color.Transparent else Color(colorHex.toLong(16))
    }

    fun getStoredBackgroundColorHex(context: Context): String? {
        return runBlocking { preferencesRepository.backgroundColor.first() }
    }

    fun getStoredTextColor(context: Context): Color {
        val colorHex = runBlocking { preferencesRepository.textColor.first() } ?: "FF000000"
        return Color(colorHex.toLong(16))
    }

    fun getStoredTextColorHex(context: Context): String? {
        return runBlocking { preferencesRepository.textColor.first() }
    }

    fun saveSettings(
        context: Context,
        url: String,
        width: String,
        height: String,
        backgroundColor: String,
        textColor: String
    ) {
        val urlChanged = runBlocking { preferencesRepository.updateAllSettings(url, width, height, backgroundColor, textColor) }
        // Cancel and reschedule workers on URL change
        if (urlChanged) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
            schedulePeriodicTemperatureUpdate()
            forceTemperatureUpdate()
        }
    }

    fun cancelPeriodicWorker(context: Context = appContext!!) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG) // Cancel pending work
    }

    fun schedulePeriodicTemperatureUpdate(context: Context = appContext!!) {
        cancelPeriodicWorker(context)
        enqueueWork<PeriodicWorkRequest>(
            context,
            configure = { builder ->
                builder.setRequiresDeviceIdle(false)
                builder.setRequiresBatteryNotLow(false) // Allow in Doze mode
            },
            enqueue = { wm, request ->
                wm.enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request as PeriodicWorkRequest
                )
            },
            logMessage = "Scheduled periodic temperature update (10 min)",
            workerName = PERIODIC_WORK_NAME
        )
    }

    fun forceTemperatureUpdate(context: Context = appContext!!) {
        TemperatureWorker.resetBackoffCount()
        enqueueWork<OneTimeWorkRequest>(
            context,
            configure = { /* No additional config needed */ },
            enqueue = { wm, request ->
                wm.enqueueUniqueWork(
                    ONETIME_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request as OneTimeWorkRequest
                )
            },
            logMessage = "Enqueued force temperature update",
            workerName = ONETIME_WORK_NAME
        )
    }

    fun pingServer(url: String): Boolean {
        return try {
            URL(url).openConnection().apply { connectTimeout = 1000 }.connect()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ping failed: ${e.message}")
            false
        }
    }

    private inline fun <reified T : WorkRequest> enqueueWork(
        context: Context,
        configure: (Constraints.Builder) -> Unit,
        enqueue: (WorkManager, WorkRequest) -> Unit,
        logMessage: String,
        workerName: String
    ) {
        val constraintsBuilder = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
        configure(constraintsBuilder)

        val builder = when (T::class) {
            PeriodicWorkRequest::class -> PeriodicWorkRequestBuilder<TemperatureWorker>(
                10,
                TimeUnit.MINUTES,
                5,
                TimeUnit.MINUTES
            )

            OneTimeWorkRequest::class -> OneTimeWorkRequestBuilder<TemperatureWorker>()
            else -> throw IllegalArgumentException("Unsupported work request type")
        }
        builder.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.SECONDS)
            .setConstraints(constraintsBuilder.build())
            .addTag(WORK_TAG)
            .setInputData(workDataOf(WHICH_WORKER to workerName))
        val workRequest = builder.build()
        enqueue(WorkManager.getInstance(context), workRequest)
        Log.d(TAG, logMessage)
    }

    fun maskPasswordInUrl(url: String): String {
        return url.replace(Regex("(https?://[^/]+/)[^/]+(/.*)"), "$1****$2")
    }

    fun setCurrentMetadataFile(string: String) {
        runBlocking { preferencesRepository.setMetadataDefaultFile(Settings.METADATA_DEFAULT_FILE) }
    }

    fun getCurrentMetadataFile(): String {
        return runBlocking { preferencesRepository.metadataDefaultFile.first() } ?: Settings.METADATA_DEFAULT_FILE
    }
}