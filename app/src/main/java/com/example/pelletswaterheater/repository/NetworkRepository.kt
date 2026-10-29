package com.example.pelletswaterheater.repository

import com.example.pelletswaterheater.NetworkAccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository for network operations with rate limiting and coordination.
 */
interface NetworkRepository {
    suspend fun fetchData(url: String, caller: String): Result<String>
    suspend fun fetchDataWithRetry(url: String, caller: String, maxAttempts: Int): Result<String>
}

class NetworkRepositoryImpl(
    private val networkAccess: NetworkAccess
) : NetworkRepository {

    private val networkAccessMutex = Mutex()

    override suspend fun fetchData(url: String, caller: String): Result<String> {
        return networkAccess.getDataFromServerResult(url, caller)
    }

    override suspend fun fetchDataWithRetry(url: String, caller: String, maxAttempts: Int): Result<String> {
        return networkAccessMutex.withLock {
            var attempt = 0
            while (attempt < maxAttempts) {
                try {
                    val result = networkAccess.getDataFromServerResult(url, caller)
                    if (result.isFailure) {
                        val exception = result.exceptionOrNull()
                        if (exception?.message == "network busy") {
                            Result.failure(exception)
                        } else {
                            throw exception ?: Exception("Unknown network error")
                        }
                    } else {
                        result
                    }
                } catch (e: Exception) {
                    if (attempt < maxAttempts - 1) {
                        delay(5000L)
                    }
                }
                attempt++
            }
            Result.failure(Exception("Failed to fetch after $maxAttempts attempts"))
        }
    }
}