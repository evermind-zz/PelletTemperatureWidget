package com.example.pelletswaterheater.repository

import android.content.Context
import android.net.Uri
import com.example.pelletswaterheater.utils.MetadataJson
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing metadata (server + user merged).
 * Handles fetching, caching, and merging metadata.
 */
interface MetadataRepository {
    val metadata: StateFlow<MetadataJson?>
    val metadataFiles: StateFlow<List<java.io.File>>

    suspend fun fetchMetadata(url: String): Result<MetadataJson>
    suspend fun loadCachedMetadata(): MetadataJson?
    suspend fun loadUserMetadata(jsonString: String): Result<MetadataJson>
    suspend fun getMetadataContent(filename: String): String?
    suspend fun getCurrentMetadataFilename(): String
    suspend fun setCurrentMetadataFilename(filename: String)
    fun deleteMetadataFile(file: java.io.File)
    fun shareMetadata(context: Context)
    fun importMetadata(context: Context)
    fun getMetadataUri(file: java.io.File): Uri?
    fun saveUserMetadata(jsonString: String)
}