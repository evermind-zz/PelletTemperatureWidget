package com.example.pelletswaterheater.utils

import android.content.Context
import java.io.File

/**
 * Interface for context-dependent operations, such as file storage.
 */
interface ContextProvider {
    /**
     * Saves metadata JSON to storage.
     *
     * @param jsonString The JSON string to save.
     */
    fun saveMetadata(jsonString: String)

    /**
     * Loads cached metadata from storage.
     *
     * @return The cached JSON string or null if not found.
     */
    fun loadCachedMetadata(): String?
}

/**
 * Implementation of [ContextProvider] using Android's [Context] for file operations.
 */
class AndroidContextProvider(private val context: Context) : ContextProvider {
    private val metadataFile = context.filesDir.resolve("metadata.json")

    override fun saveMetadata(jsonString: String) {
        try {
            metadataFile.writeText(jsonString)
        } catch (e: Exception) {
            AndroidLogger.e("ContextProvider", "Metadata save error: $e", e)
        }
    }

    override fun loadCachedMetadata(): String? {
        return try {
            if (metadataFile.exists()) {
                metadataFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            AndroidLogger.e("ContextProvider", "Cached metadata load error: $e", e)
            null
        }
    }
}