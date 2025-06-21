package com.example.pelletswaterheater.utils

import android.content.Context

interface ContextProvider {
    fun saveMetadata(jsonString: String)
    fun loadCachedMetadata(): String?
}

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
