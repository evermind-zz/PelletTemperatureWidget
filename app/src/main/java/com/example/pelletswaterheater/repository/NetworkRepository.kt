package com.example.pelletswaterheater.repository

import com.example.pelletswaterheater.NetworkAccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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

    private val networkAccessMutex = kotlinx.coroutines.sync.Mutex()

    override suspend fun fetchData(url: String, caller: String): Result<String> {
        return networkAccess.getDataFromServerResult(url, caller)
    }

    override suspend fun fetchDataWithRetry(url: String, caller: String, maxAttempts: Int = 3): Result<String> = networkAccessMutex.withLock {
        repeat(maxAttempts) { attempt ->
            try {
                val result = networkAccess.getDataFromServerResult(url, caller)
                if (result.isFailure) {
                    val exception = result.exceptionOrNull()
                    if (exception?.message == "network busy") {
                        return Result.failure(exception)
                    }
                    throw exception ?: Exception("Unknown network error")
                }
                return result
            } catch (e: Exception) {
                if (attempt < maxAttempts - 1) {
                    kotlinx.coroutines.delay(5000L)
                }
            }
        }
        Result.failure(Exception("Failed to fetch after $maxAttempts attempts"))
    }
}