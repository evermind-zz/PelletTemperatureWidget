package com.example.pelletswaterheater

import android.content.Context
import android.net.wifi.WifiManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pelletswaterheater.utils.ILogger
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.FileNotFoundException
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

class TemperatureWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) , KoinComponent {

    companion object {
        private val TAG = TemperatureWorker::class.java.simpleName
        private var backoffCount = 0
        //private val accessTimeMutex = Mutex()
        private var lastAccessTime = 0L
        //private val networkMutex = Mutex()
        fun resetBackoffCount() { backoffCount = 0 }
    }
    private val backoffCount = AtomicInteger(0)
    private val viewModel: TemperatureViewModelNew by inject()
    private val logger: ILogger by inject()
    private val temperatureUtils: TemperatureUtils by inject()
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val whichWorker = inputData.getString(TemperatureUtils.WHICH_WORKER) ?: tags.toString()
        try {
            logger.d(TAG, "Starting worker execution: workId=${id}")
            var url = temperatureUtils.getServerUrl()
            val maskedUrl = temperatureUtils.maskPasswordInUrl(url)
            logger.d(TAG, "worker=$whichWorker Starting work: url=$maskedUrl, backoffCount=${backoffCount.get()}")

            // Check max retries
            if (backoffCount.get() > 5) {
                logger.e(TAG, "worker=$whichWorker Max retries exceeded")

                //  NO local mutex here networkMutex.withLock {
                    viewModel.updateStats(error ="Max retries exceeded", state ="Error")
                    //viewModel.updateError("Max retries exceeded")
                    //viewModel.updateState("Error")
                //  NO local mutex here }
                updateWidget(whichWorker)
                return@withContext Result.failure()
            }

            if (!isOnSameLocalNetwork(applicationContext, url)) {
                //  NO local mutex here networkMutex.withLock {
                    viewModel.updateStats(error ="Device not on the same network as the server", state ="Wrong Network")
                    //viewModel.updateState("Wrong Network")
                    //viewModel.updateError("Device not on the same network as the server")
                // NO local mutex here}
                logger.e(TAG, "worker=$whichWorker Network mismatch")
                updateWidget(whichWorker)
                return@withContext Result.failure()
            }
            logger.d(TAG, "worker=$whichWorker Attempting temperature fetch: url=$maskedUrl")

            // Re-read URL to avoid stale value
            url = temperatureUtils.getServerUrl()
            val refreshedMaskedUrl = temperatureUtils.maskPasswordInUrl(url)

                logger.d(TAG, "worker=$whichWorker Refreshed URL: $refreshedMaskedUrl")

            val result = viewModel.fetchValues(url, whichWorker, false)
            if (result.isSuccess) {
                backoffCount.set(0)
                return@withContext Result.success()
            } else if (result.exceptionOrNull()?.message == "network busy") {
                // Network busy - don't count as retry failure, just reschedule
                logger.d(TAG, "worker=$whichWorker Network busy, will retry on next periodic run")
                return@withContext Result.retry()
            }

                // All retries failed
                val currentBackoff = backoffCount.incrementAndGet()
                //viewModel.updateStats(error = "Failed to fetch temperature after $maxAttempts attempts", state = "Error")
                //viewModel.updateState("Error")
                //viewModel.updateError("Failed to fetch temperature after $maxAttempts attempts")
                logger.e(TAG, "worker=$whichWorker All retries failed, backoffCount=$currentBackoff")
                updateWidget(whichWorker)
            //}
            Result.failure()
        } catch (e: Exception) {
            logger.e(TAG, "worker=${whichWorker} Unexpected error in doWork", e)
            //  NO local mutex here networkMutex.withLock {
                viewModel.updateStats(error = "Worker failed: ${e.message}", state = "Error")
                //viewModel.updateError("Worker failed: ${e.message}")
                //viewModel.updateState("Error")
            //  NO local mutex here }
            updateWidget(whichWorker)
            Result.failure()
        }
    }

    // remove the password from the url inside the exception message
    private fun maskExceptionMessageAndLogIt(
        e: Exception,
        url: String,
        whichWorker: String?,
        attempt: Int
    ) {
        val refreshedMaskedUrl = temperatureUtils.maskPasswordInUrl(url)
        val maskedOriginalMessage = e.message?.replace(url, refreshedMaskedUrl) ?: "Unknown error"
        val maskedException = when (e) {
            is FileNotFoundException -> FileNotFoundException(maskedOriginalMessage)
            else -> e.javaClass.getConstructor(String::class.java)
                .newInstance(maskedOriginalMessage)
        }.apply { stackTrace = e.stackTrace }
        val maskedMessage =
            "worker=$whichWorker Failed to fetch temperature (attempt $attempt): $maskedOriginalMessage"
        logger.e(TAG, maskedMessage, RuntimeException(maskedMessage, maskedException))
    }

    private suspend fun updateWidget(whichWorker: String?) {
        try {
            TemperatureWidget().forceUpdateAll(applicationContext)
            logger.d(TAG, "worker=$whichWorker, Widget update triggered")
        } catch (e: Exception) {
            logger.e(TAG, "worker=$whichWorker, Widget update failed", e)
        }
    }

    private fun isOnSameLocalNetwork(context: Context, url: String): Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo ?: return false
        val deviceIp = wifiInfo.ipAddress
        if (deviceIp == 0) return false
        val deviceIpString = formatIpAddress(deviceIp)
        val serverIp = URI(url).host // we expect always a IP no hostname
        logger.d(TAG, "Device IP: $deviceIpString, Server IP: $serverIp")
        return deviceIpString.split(".").take(3) == serverIp.split(".").take(3)
    }

    private fun formatIpAddress(ip: Int): String {
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }
}
