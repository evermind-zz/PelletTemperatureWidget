package com.example.pelletswaterheater

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.text.DecimalFormat
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkRequest
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object TemperatureUtils {
    private const val PERIODIC_WORK_NAME = "periodic_temperature_update"
    private const val ONETIME_WORK_NAME = "onetime_temperature_update"
    const val WHICH_WORKER = "which_worker"
    private const val WORK_TAG = "temperature_update"
    private const val TAG = "TemperatureUtils"
    var appContext: Context? = null // Set in Application or MainActivity

    fun formatTemperature(rawValue: String?): String {
        return when {
            rawValue == null || rawValue == "Loading..." -> "Loading..."
            rawValue == "Error" || rawValue == "Wrong Network" -> rawValue
            else -> try {
                DecimalFormat("0.0").format(rawValue.toDouble()) + "°C"
            } catch (e: NumberFormatException) {
                "Error"
            }
        }
    }

    fun getSharedPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getStoredUrl(context: Context): String? {
        return getSharedPrefs(context).getString("server_url", null)
    }

    fun getStoredWidgetSize(context: Context, dimension: String): String? {
        return getSharedPrefs(context).getString("widget_$dimension", null)
    }

    fun getStoredBackgroundColor(context: Context): Color {
        val colorHex = getSharedPrefs(context).getString("background_color", "FFF3E5F5") // Default purple
        return if (colorHex == "TRANSPARENT") Color.Transparent else Color(colorHex?.toLong(16) ?: 0xFFF3E5F5)
    }

    fun getStoredBackgroundColorHex(context: Context): String? {
        return getSharedPrefs(context).getString("background_color", "FFF3E5F5") // Default purple
    }

    fun getStoredTextColor(context: Context): Color {
        val colorHex = getSharedPrefs(context).getString("text_color", "FF000000") // Default black
        return Color(colorHex?.toLong(16) ?: 0xFF000000)
    }

    fun getStoredTextColorHex(context: Context): String? {
        return getSharedPrefs(context).getString("text_color", "FF000000") // Default black
    }

    fun saveSettings(
        context: Context,
        url: String,
        width: String,
        height: String,
        backgroundColor: String,
        textColor: String
    ) {
        val previousUrl = getStoredUrl(context)
        with(getSharedPrefs(context).edit()) {
            putString("server_url", url)
            putString("widget_width", width)
            putString("widget_height", height)
            putString("background_color", backgroundColor)
            putString("text_color", textColor)
            apply()
        }
        // Cancel and reschedule workers on URL change
        if (previousUrl != url) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
            schedulePeriodicTemperatureUpdate(context)
            forceTemperatureUpdate(context)
        }
    }

    fun schedulePeriodicTemperatureUpdate(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG) // Cancel pending work
        enqueueWork<PeriodicWorkRequest>(
            context,
            configure = { builder -> 
                builder.setRequiresDeviceIdle(false)
                builder.setRequiresBatteryNotLow(false) // Allow in Doze mode
            },
            enqueue = { wm, request -> wm.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request as PeriodicWorkRequest) },
            logMessage = "Scheduled periodic temperature update (10 min)",
            workerName = PERIODIC_WORK_NAME
        )
    }

    fun forceTemperatureUpdate(context: Context) {
        TemperatureWorker.resetBackoffCount()
        enqueueWork<OneTimeWorkRequest>(
            context,
            configure = { /* No additional config needed */ },
            enqueue = { wm, request -> wm.enqueueUniqueWork(ONETIME_WORK_NAME, ExistingWorkPolicy.REPLACE, request as OneTimeWorkRequest) },
            logMessage = "Enqueued force temperature update",
            workerName = ONETIME_WORK_NAME
        )
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
            PeriodicWorkRequest::class -> PeriodicWorkRequestBuilder<TemperatureWorker>(10, TimeUnit.MINUTES, 5, TimeUnit.MINUTES)
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
}
