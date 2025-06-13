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
import java.util.concurrent.TimeUnit

object TemperatureUtils {
    private const val WORK_NAME = "temperature_update"
    private const val WORK_TAG = "temperature_update"
    private const val TAG = "TemperatureUtils"

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
        with(getSharedPrefs(context).edit()) {
            putString("server_url", url)
            putString("widget_width", width)
            putString("widget_height", height)
            putString("background_color", backgroundColor)
            putString("text_color", textColor)
            apply()
        }
    }

    fun schedulePeriodicTemperatureUpdate(context: Context) {
        enqueueWork<PeriodicWorkRequest>(
            context,
            configure = { /* No additional config needed */ },
            enqueue = { wm, request -> wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request as PeriodicWorkRequest) },
            logMessage = "Scheduled periodic temperature update"
        )
    }

    fun forceTemperatureUpdate(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG) // Cancel pending work
        // Reset backoff count for immediate retry
        TemperatureWorker.resetBackoffCount()
        enqueueWork<OneTimeWorkRequest>(
            context,
            configure = { /* No additional config needed */ },
            enqueue = { wm, request -> wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request as OneTimeWorkRequest) },
            logMessage = "Enqueued force temperature update"
        )
    }

    private inline fun <reified T : WorkRequest> enqueueWork(
        context: Context,
        configure: (WorkRequest.Builder<*, *>) -> Unit,
        enqueue: (WorkManager, WorkRequest) -> Unit,
        logMessage: String
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val builder = when (T::class) {
            PeriodicWorkRequest::class -> PeriodicWorkRequestBuilder<TemperatureWorker>(10, TimeUnit.MINUTES)
            OneTimeWorkRequest::class -> OneTimeWorkRequestBuilder<TemperatureWorker>()
            else -> throw IllegalArgumentException("Unsupported work request type")
        }
        builder.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .addTag(WORK_TAG)
        configure(builder)
        val workRequest = builder.build()
        enqueue(WorkManager.getInstance(context), workRequest)
        Log.d(TAG, logMessage)
    }

    fun maskPasswordInUrl(url: String): String {
        return url.replace(Regex("(https?://[^/]+/)[^/]+(/.*)"), "$1****$2")
    }
}
