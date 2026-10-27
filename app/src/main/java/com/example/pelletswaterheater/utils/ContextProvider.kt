package com.example.pelletswaterheater.utils

import android.content.Context
import java.io.File

/**
 * Interface for context-dependent operations, such as file storage and context access.
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

    /**
     * Provides the Android context for file operations.
     *
     * @return The application context.
     */
    fun getContext(): Context

    /**
     * @filename relative to matadata's dir the filename of the current metadata
     *
     * @return the content of the current metadata
     */
    fun getMetadata(filename: String): String?
}

/**
 * Implementation of [ContextProvider] using Android's [Context] for file operations.
 */
class AndroidContextProvider(private val context: Context) : ContextProvider {
    private val metadataFile = context.filesDir.resolve("metadata.json")
    private val TAG = AndroidContextProvider::class.java.simpleName

    override fun saveMetadata(jsonString: String) {
        try {
            metadataFile.writeText(jsonString)
        } catch (e: Exception) {
            Logger.e(TAG, "Metadata save error: $e", e)
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
            Logger.e(TAG, "Cached metadata load error: $e", e)
            null
        }
    }

    override fun getContext(): Context = context

    override fun getMetadata(filename: String): String? {
        val metadataFile = File(context.filesDir, "metadata/${filename}")
        return try {
            if (metadataFile.exists()) {
                metadataFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Cached metadata load error: $e", e)
            null
        }
    }
}