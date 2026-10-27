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

class NetworkAccess(

) : KoinComponent {

    companion object {
        private val TAG = this::class.java.simpleName
    }
    private val httpClient: OkHttpClient by inject()
    private val networkMutex = Mutex()
    private var lastAccessTime = 0L

    suspend fun getDataFromServer(url: String, caller: String): String {
        networkMutex.withLock {
            // Ensure minimum 3s between network accesses
            val currentTime = System.currentTimeMillis()
            val timeSinceLast = currentTime - lastAccessTime
            Logger.d(TAG, "timegab ${this} currentTime $currentTime timeSinceLast $timeSinceLast")
            if (timeSinceLast < 3000) {
                val waitTime = 3000 - timeSinceLast
                Logger.d(TAG, "worker=$caller Waiting ${waitTime}ms to respect 3s network access interval")
                delay(waitTime) // we need to sleep in the mutex as the server needs some time
            }
            try {
                Logger.d(TAG, "worker=$caller Requesting now. Thread ${Thread.currentThread().name}")
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return detectAndConvertInputEncodingToUT8(response.body?.bytes())
                    } else {
                        throw RuntimeException(response.message)
                    }
                }
            } catch (e: Exception) {
                println(e.message)
                throw e
            } finally {
                lastAccessTime = System.currentTimeMillis()
            }
        }
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