package com.example.pelletswaterheater

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URL

class TemperatureWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    companion object {
        private val TAG = TemperatureWorker::class.java.simpleName
        private var backoffCount = 0
        fun resetBackoffCount() { backoffCount = 0 }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val viewModel = TemperatureViewModel.getInstance()
        val url = TemperatureUtils.getStoredUrl(applicationContext) ?: "http://192.168.178.4:4321/<PASSWORD>/all"

        if (!isOnSameLocalNetwork(applicationContext, url)) {
            viewModel.updateTemperature("Wrong Network")
            viewModel.updateError("Device not on the same network as the server")
            viewModel.updateLastUpdateTime(System.currentTimeMillis())
            Log.e(TAG, "Network mismatch")
            updateWidget()
            return@withContext Result.failure()
        }

        try {
            val jsonString = URL(url).readText()
            val json = Json.parseToJsonElement(jsonString).jsonObject
            val ambient = json["system"]?.jsonObject?.get("L_ambient")?.jsonPrimitive?.content
            val temp = ambient?.toDoubleOrNull()?.let { (it * 0.1).toString() } ?: "Error"
            viewModel.updateTemperature(temp)
            viewModel.updateError(null)
            viewModel.updateLastUpdateTime(System.currentTimeMillis())
            backoffCount = 0 // Reset backoff on success
            Log.d(TAG, "Success: temp=$temp, time=${viewModel.lastUpdateTime.value}")
            updateWidget()
            Result.success(workDataOf("temperature" to temp))
        } catch (e: Exception) {
            val maskedUrl = TemperatureUtils.maskPasswordInUrl(url)
            // Mask password in original exception message
            val maskedOriginalMessage = e.message?.replace(url, maskedUrl) ?: "Unknown error"
            // Create new exception with masked message
            val maskedException = when (e) {
                is java.io.FileNotFoundException -> java.io.FileNotFoundException(maskedOriginalMessage)
                else -> e.javaClass.getConstructor(String::class.java).newInstance(maskedOriginalMessage)
            }.apply { stackTrace = e.stackTrace } // Preserve stack trace
            val maskedMessage = "Failed to fetch temperature: $maskedOriginalMessage"
            viewModel.updateTemperature("Error")
            viewModel.updateError(maskedMessage)
            viewModel.updateLastUpdateTime(System.currentTimeMillis())
            Log.e(TAG, maskedMessage, RuntimeException(maskedMessage, maskedException))
            backoffCount++ // Increment backoff count
            updateWidget()
            Result.failure()
        }
    }

    private suspend fun updateWidget() {
        TemperatureWidget().updateAll(applicationContext)
        Log.d(TAG, "Widget update triggered")
    }

    private fun isOnSameLocalNetwork(context: Context, url: String): Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo ?: return false
        val deviceIp = wifiInfo.ipAddress
        if (deviceIp == 0) return false
        val deviceIpString = formatIpAddress(deviceIp)
        val serverIp = URI(url).host // we expect always a IP no hostname
        Log.d(TAG, "Device IP: $deviceIpString, Server IP: $serverIp")
        return deviceIpString.split(".").take(3) == serverIp.split(".").take(3)
    }

    private fun formatIpAddress(ip: Int): String {
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }
}
