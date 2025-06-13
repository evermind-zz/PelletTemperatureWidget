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
import java.util.concurrent.TimeUnit

object TemperatureUtils {
    private const val WORK_NAME = "temperature_update"
    private const val WORK_TAG = "temperature_update"
    private const val TAG = "TemperatureUtils"

    // Formatting and SharedPreferences methods unchanged
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
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC) // Unchanged
        return EncryptedSharedPreferences.create(
            "secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    // Other SharedPreferences methods (getStoredUrl, getStoredWidgetSize, etc.) unchanged

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

    fun getStoredTextColorHex(context: Context): String? {
        return getSharedPrefs(context).getString("text_color", "FF000000") // Default black
    }

    fun schedulePeriodicTemperatureUpdate(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = PeriodicWorkRequestBuilder<TemperatureWorker>(10, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        Log.d(TAG, "Scheduled periodic temperature update")
    }

    fun forceTemperatureUpdate(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG) // Cancel pending work
        // Reset backoff count for immediate retry
        TemperatureWorker.resetBackoffCount()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<TemperatureWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        Log.d(TAG, "Enqueued force temperature update")
    }

    fun maskPasswordInUrl(url: String): String {
        return url.replace(Regex("(https?://[^/]+/)[^/]+(/.*)"), "$1****$2")
    }
}
