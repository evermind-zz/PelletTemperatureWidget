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
     * Loads default metadata from assets (fallback when no cache/server available).
     *
     * @return The default metadata JSON string or null if not found.
     */
    fun loadDefaultMetadata(): String?

    /**
     * Provides the Android context for file operations.
     *
     * @return The application context.
     */
    fun getContext(): Context

    /**
     * @filename relative to metadata's dir the filename of the current metadata
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
    private val metadataDir = File(context.filesDir, "metadata")
    private val TAG = AndroidContextProvider::class.java.simpleName

    override fun saveMetadata(jsonString: String) {
        try {
            metadataFile.writeText(jsonString)
            if (!metadataDir.exists()) {
                metadataDir.mkdirs()
            }
            val versionedFile = File(metadataDir, "metadata_${System.currentTimeMillis()}.json")
            versionedFile.writeText(jsonString)
            // Keep only last 5 versions
            cleanupOldMetadataVersions()
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

    override fun loadDefaultMetadata(): String? {
        return try {
            val inputStream = context.assets.open("metadata.json")
            inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Logger.e(TAG, "Default metadata load error: $e", e)
            null
        }
    }

    override fun getContext(): Context = context

    override fun getMetadata(filename: String): String? {
        val metadataFile = File(metadataDir, filename)
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

    private fun cleanupOldMetadataVersions() {
        try {
            val files = metadataDir.listFiles { file ->
                file.isFile && file.name.startsWith("metadata_") && file.extension == "json"
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            if (files.size > 5) {
                files.drop(5).forEach { it.delete() }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Cleanup old metadata versions error: $e", e)
        }
    }
}