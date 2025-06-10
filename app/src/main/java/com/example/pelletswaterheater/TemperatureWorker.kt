package com.example.pelletswaterheater

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL

class TemperatureWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val TAG = TemperatureWorker::class.java.simpleName

    companion object {
        val latestTemperatureFlow = MutableStateFlow<String?>(null)
        val errorFlow = MutableStateFlow<String?>(null)
        private const val SERVER_IP = "192.168.178.4"
        private var backoffCount = 0
        private const val INITIAL_BACKOFF_MS = 5000L // 5 seconds
        private const val MAX_BACKOFF_MS = 60000L // 60 seconds
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!isOnSameLocalNetwork(applicationContext)) {
            latestTemperatureFlow.value = "Wrong Network"
            errorFlow.value = "Device not on the same network as the server"
            Log.e(TAG, "Network mismatch")
            return@withContext Result.failure()
        }
        applyBackoffDelay()

        try {
            val url = TemperatureUtils.getStoredUrl(applicationContext) ?: "http://$SERVER_IP:4321/<PASSWORD>/all"
            val jsonString = URL(url).readText()
            val json = Json.parseToJsonElement(jsonString).jsonObject
            val ambient = json["system"]?.jsonObject?.get("L_ambient")?.jsonPrimitive?.content
            val temp = ambient?.toDoubleOrNull()?.let { (it * 0.1).toString() } ?: "Error"
            latestTemperatureFlow.value = temp
            errorFlow.value = null
            backoffCount = 0 // Reset backoff on success
            Log.d(TAG, "Success: temp=$temp")
            Result.success(workDataOf("temperature" to temp))
        } catch (e: Exception) {
            latestTemperatureFlow.value = "Error"
            errorFlow.value = "Failed to fetch temperature: ${e.message}"
            Log.e(TAG, "Error fetching temperature", e)
            backoffCount++ // Increment backoff count
            Result.failure()
        }
    }

    private suspend fun applyBackoffDelay() {
        if (backoffCount > 0) {
            val delayMs = minOf(INITIAL_BACKOFF_MS * (1L shl backoffCount), MAX_BACKOFF_MS)
            Log.d(TAG, "Applying backoff delay: $delayMs ms")
            delay(delayMs)
        }
    }

    private fun isOnSameLocalNetwork(context: Context): Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo ?: return false
        val deviceIp = wifiInfo.ipAddress
        if (deviceIp == 0) return false
        val deviceIpString = formatIpAddress(deviceIp)
        Log.d(TAG, "Device IP: $deviceIpString, Server IP: $SERVER_IP")
        return deviceIpString.split(".").take(3) == SERVER_IP.split(".").take(3)
    }

    private fun formatIpAddress(ip: Int): String {
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }
}
