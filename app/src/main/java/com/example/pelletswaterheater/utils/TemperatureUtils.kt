package com.example.pelletswaterheater.utils

import android.content.Context
import android.graphics.Color
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pelletswaterheater.TemperatureWorker
import com.example.pelletswaterheater.preferences.PreferencesRepository
import com.example.pelletswaterheater.utils.ConvertedValue
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew.ConvertedValueWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first as flowFirst
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Utility functions for temperature app settings and operations.
 * Uses PreferencesRepository for DataStore-based persistence.
 * Static methods in companion object are for backward compatibility and UI access.
 */
class TemperatureUtils private constructor(
    private val preferencesRepository: PreferencesRepository,
    private val context: Context
) {

    companion object {
        private const val DEFAULT_SERVER_URL: String = "http://192.168.178.4:4321/<PASSWORD>/all"
        private const val WORK_NAME = "temperature_worker"
        const val WHICH_WORKER = "which_worker"
        private var INSTANCE: TemperatureUtils? = null
        var appContext: Context? = null

        fun getInstance(preferencesRepository: PreferencesRepository, context: Context): TemperatureUtils {
            if (INSTANCE == null) {
                INSTANCE = TemperatureUtils(preferencesRepository, context)
            }
            return INSTANCE!!
        }

        // Static convenience methods for backward compatibility
        @Suppress("UNUSED_PARAMETER")
        fun getStoredUrl(): String {
            return DEFAULT_SERVER_URL
        }

        @Suppress("UNUSED_PARAMETER")
        fun getStoredWidgetSize(context: Context, dimension: String): String? = "100"

        @Suppress("UNUSED_PARAMETER")
        fun getStoredBackgroundColorHex(context: Context): String? = "FFF3E5F5"

        @Suppress("UNUSED_PARAMETER")
        fun getStoredTextColorHex(context: Context): String? = "FF000000"

        fun saveSettings(
            context: Context,
            url: String,
            width: String,
            height: String,
            backgroundColor: String,
            textColor: String
        ) {
            // Deprecated - use PreferencesRepository directly
        }

        fun forceTemperatureUpdate() {
            // Try to trigger via WorkManager if context available
            appContext?.let { ctx ->
                val workRequest: OneTimeWorkRequest = OneTimeWorkRequestBuilder<TemperatureWorker>()
                    .addTag(WORK_NAME)
                    .build()
                WorkManager.getInstance(ctx).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }
        }

        @Suppress("UNUSED_PARAMETER")
        fun pingServer(url: String): Boolean {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(2, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder().url(url).head().build()
                val response = client.newCall(request).execute()
                return response.isSuccessful
            } catch (e: Exception) {
                return false
            }
        }

        @Suppress("UNUSED_PARAMETER")
        fun cancelPeriodicWorker(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        @Suppress("UNUSED_PARAMETER")
        fun schedulePeriodicTemperatureUpdate() {}

        fun getCurrentMetadataFile(): String = "metadata.json"

        fun setCurrentMetadataFile(filename: String) {}

        fun maskPasswordInUrl(url: String): String {
            return url.replace(Regex("://([^:]+):([^@]+)@"), "://\$1:****@")
        }

        fun getStoredLoggerHeaderHash(): Int = 0

        fun setStoredLoggerHeaderHash(context: Context, hash: Int) {}

        fun getNameSuffix(wrapper: ConvertedValueWrapper): String {
            return wrapper.convertedValue.unit?.let { " $it" } ?: ""
        }

        fun getLogicalValueWithUnit(value: ConvertedValue): String {
            return "${value.logical}${value.unit?.let { " $it" } ?: ""}"
        }

        fun formattedState(state: String): String {
            return when (state) {
                "Success" -> "OK"
                "Error" -> "ERROR"
                "Loading..." -> "LOADING"
                else -> state
            }
        }

        fun formatTimeStamp(timestamp: Long): String {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        }

        fun getStoredBackgroundColor(context: Context): Int {
            val hex = "FFF3E5F5"
            return try {
                Color.parseColor("#$hex")
            } catch (e: Exception) {
                Color.WHITE
            }
        }

        fun getStoredTextColor(context: Context): Int {
            val hex = "FF000000"
            return try {
                Color.parseColor("#$hex")
            } catch (e: Exception) {
                Color.BLACK
            }
        }
    }

    // Instance methods using PreferencesRepository (DataStore)
    suspend fun getServerUrl(): String {
        return preferencesRepository.serverUrl.flowFirst() ?: DEFAULT_SERVER_URL
    }

    suspend fun setServerUrl(url: String) {
        preferencesRepository.setServerUrl(url)
    }

    suspend fun getWidgetWidth(): String {
        return preferencesRepository.widgetWidth.flowFirst() ?: "100"
    }

    suspend fun setWidgetWidth(width: String) {
        preferencesRepository.setWidgetWidth(width)
    }

    suspend fun getWidgetHeight(): String {
        return preferencesRepository.widgetHeight.flowFirst() ?: "100"
    }

    suspend fun setWidgetHeight(height: String) {
        preferencesRepository.setWidgetHeight(height)
    }

    suspend fun getBackgroundColor(): String {
        return preferencesRepository.backgroundColor.flowFirst() ?: "FFF3E5F5"
    }

    suspend fun setBackgroundColor(color: String) {
        preferencesRepository.setBackgroundColor(color)
    }

    suspend fun getTextColor(): String {
        return preferencesRepository.textColor.flowFirst() ?: "FF000000"
    }

    suspend fun setTextColor(color: String) {
        preferencesRepository.setTextColor(color)
    }

    suspend fun getMetadataDefaultFile(): String {
        return preferencesRepository.metadataDefaultFile.flowFirst() ?: "metadata.json"
    }

    suspend fun setMetadataDefaultFile(filename: String) {
        preferencesRepository.setMetadataDefaultFile(filename)
    }

    suspend fun getGatewayFingerprint(): String? {
        return preferencesRepository.gatewayFingerprint.flowFirst()
    }

    suspend fun setGatewayFingerprint(fingerprint: String) {
        preferencesRepository.setGatewayFingerprint(fingerprint)
    }

    suspend fun getSelectionsJson(): String? {
        return preferencesRepository.selectionsJson.flowFirst()
    }

    suspend fun setSelectionsJson(json: String) {
        preferencesRepository.setSelectionsJson(json)
    }

    suspend fun getStoredOrderHash(): Int {
        return preferencesRepository.storedOrderHash.flowFirst() ?: 0
    }

    suspend fun setStoredOrderHash(hash: Int) {
        preferencesRepository.setStoredOrderHash(hash)
    }

    suspend fun getLastRefreshTime(): Long? {
        return preferencesRepository.lastRefreshTime.flowFirst()
    }

    suspend fun setLastRefreshTime(time: Long) {
        preferencesRepository.setLastRefreshTime(time)
    }

    suspend fun updateAllSettings(
        url: String,
        width: String,
        height: String,
        backgroundColor: String,
        textColor: String
    ): Boolean {
        return preferencesRepository.updateAllSettings(url, width, height, backgroundColor, textColor)
    }

    fun pingServerAsync(url: String): Boolean {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).head().build()
            val response = client.newCall(request).execute()
            return response.isSuccessful
        } catch (e: Exception) {
            return false
        }
    }

    fun schedulePeriodicWork(loggingIntervalMs: Long) {
        val workRequest = PeriodicWorkRequestBuilder<TemperatureWorker>(loggingIntervalMs, TimeUnit.MILLISECONDS)
            .setInitialDelay(0, TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancelPeriodicWork() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun forceTemperatureUpdate() {
        val workRequest: OneTimeWorkRequest = OneTimeWorkRequestBuilder<TemperatureWorker>()
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    // Instance method delegating to companion for Koin injection
    fun maskPasswordInUrl(url: String): String = maskPasswordInUrl(url)

    fun getStoredLoggerHeaderHash(): Int = getStoredLoggerHeaderHash()

    fun setStoredLoggerHeaderHash(hash: Int) { setStoredLoggerHeaderHash(context, hash) }

    fun getStoredBackgroundColor(): Int = getStoredBackgroundColor(context)

    fun getStoredTextColor(): Int = getStoredTextColor(context)

    fun getNameSuffix(wrapper: ConvertedValueWrapper): String = getNameSuffix(wrapper)

    fun getLogicalValueWithUnit(value: ConvertedValue): String = getLogicalValueWithUnit(value)

    fun formattedState(state: String): String = formattedState(state)

}