package com.example.pelletswaterheater

import com.example.pelletswaterheater.utils.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mozilla.universalchardet.UniversalDetector
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicLong

class NetworkAccess(

) : KoinComponent {

    companion object {
        private val TAG = this::class.java.simpleName
        private const val MIN_INTERVAL_MS = 3000L
    }
    private val httpClient: OkHttpClient by inject()
    private val networkMutex = Mutex()
    private val lastAccessTime = AtomicLong(0L)

    suspend fun getDataFromServer(url: String, caller: String): String {
        // Rate limiting: wait outside the mutex to avoid blocking other callers
        val waitTime = calculateWaitTime()
        if (waitTime > 0) {
            Logger.d(TAG, "worker=$caller Waiting ${waitTime}ms to respect 3s network access interval")
            delay(waitTime)
        }

        // Actual network call inside mutex
        return networkMutex.withLock {
            try {
                Logger.d(TAG, "worker=$caller Requesting now. Thread ${Thread.currentThread().name}")
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return@withLock detectAndConvertInputEncodingToUT8(response.body?.bytes())
                    } else {
                        throw RuntimeException(response.message)
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "worker=$caller Network error: ${e.message}", e)
                throw e
            } finally {
                lastAccessTime.set(System.currentTimeMillis())
            }
        }
    }

    private fun calculateWaitTime(): Long {
        val currentTime = System.currentTimeMillis()
        val lastTime = lastAccessTime.get()
        val timeSinceLast = currentTime - lastTime
        return if (timeSinceLast < MIN_INTERVAL_MS) MIN_INTERVAL_MS - timeSinceLast else 0
    }

    fun detectAndConvertInputEncodingToUT8(input: ByteArray?) : String {
        return input?.let {
            val detector = UniversalDetector(null)
            detector.handleData(input)
            detector.dataEnd()

            val detectedCharset = detector.detectedCharset ?: "UTF-8"
            Logger.d(TAG, "detected charset: $detectedCharset")
            input.toString(Charset.forName(detectedCharset))
        } ?: ""
    }
}