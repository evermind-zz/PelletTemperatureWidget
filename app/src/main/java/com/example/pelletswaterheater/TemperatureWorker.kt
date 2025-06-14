package com.example.pelletswaterheater

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        private var lastAccessTime = 0L
        private val networkMutex = Mutex()
        fun resetBackoffCount() { backoffCount = 0 }
    }
    private val backoffCount = AtomicInteger(0)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting worker execution: workId=${id}")
        val viewModel = TemperatureViewModel.getInstance()
        val url = TemperatureUtils.getStoredUrl(applicationContext) ?: "http://192.168.178.4:4321/<PASSWORD>/all"
        val maskedUrl = TemperatureUtils.maskPasswordInUrl(url)
        Log.d(TAG, "Using ViewModel instance: ${viewModel.hashCode()}")
        networkMutex.withLock {
            val whichWorker = inputData.getString(TemperatureUtils.WHICH_WORKER)
            Log.d(TAG, "worker=$whichWorker Starting work: url=$maskedUrl, backoffCount=${backoffCount.get()}")

            if (!isOnSameLocalNetwork(applicationContext, url)) {
                viewModel.updateTemperature("Wrong Network")
                viewModel.updateError("Device not on the same network as the server")
                viewModel.updateLastUpdateTime(System.currentTimeMillis())
                Log.e(TAG, "worker=$whichWorker Network mismatch")
                updateWidget()
                return@withContext Result.failure()
            }
            Log.d(TAG, "worker=$whichWorker Attempting temperature fetch: url=$maskedUrl")
            // Ensure minimum 3s between network accesses
            val currentTime = System.currentTimeMillis()
            val timeSinceLast = currentTime - lastAccessTime
            if (timeSinceLast < 3000) {
                val waitTime = 3000 - timeSinceLast
                Log.d(TAG, " worker=$whichWorker Waiting ${waitTime}ms to respect 3s network access interval")
                delay(waitTime)
            }

            repeat(3) { attempt ->
                try {
                    viewModel.updateTemperature("Loading...")
                    val jsonString = URL(url).readText()
                    lastAccessTime = System.currentTimeMillis()
                    val json = Json.parseToJsonElement(jsonString).jsonObject
                    val ambient = json["system"]?.jsonObject?.get("L_ambient")?.jsonPrimitive?.content
                    val temp = ambient?.toDoubleOrNull()?.let { (it * 0.1).toString() } ?: "Error"
                    viewModel.updateTemperature(temp)
                    viewModel.updateError(null)
                    viewModel.updateLastUpdateTime(System.currentTimeMillis())
                    backoffCount.set(0)
                    Log.d(TAG, "worker=$whichWorker Success: attempt=$attempt, temp=$temp, time=${viewModel.lastUpdateTime.value}")
                    updateWidget()
                    return@withContext Result.success(workDataOf("temperature" to temp))

                } catch (e: Exception) {
                    lastAccessTime = System.currentTimeMillis()
                    val maskedOriginalMessage = e.message?.replace(url, maskedUrl) ?: "Unknown error"
                    val maskedException = when (e) {
                        is java.io.FileNotFoundException -> java.io.FileNotFoundException(maskedOriginalMessage)
                        else -> e.javaClass.getConstructor(String::class.java).newInstance(maskedOriginalMessage)
                    }.apply { stackTrace = e.stackTrace }
                    val maskedMessage = "worker=$whichWorker Failed to fetch temperature (attempt $attempt): $maskedOriginalMessage"
                    Log.e(TAG, maskedMessage, RuntimeException(maskedMessage, maskedException))
                    if (attempt < 2) delay(5000L) // Wait 5s before retry
                }
            }

            // All retries failed
            val currentBackoff = backoffCount.incrementAndGet()
            viewModel.updateTemperature("Error")
            viewModel.updateError("Failed to fetch temperature after 3 attempts")
            viewModel.updateLastUpdateTime(System.currentTimeMillis())
            updateWidget()
            Log.e(TAG, "worker=$whichWorker All retries failed, backoffCount=$currentBackoff")
        }
        Result.failure()
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
