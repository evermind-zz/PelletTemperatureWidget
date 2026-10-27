package com.example.pelletswaterheater.repository

import com.example.pelletswaterheater.utils.MetadataJson
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing metadata (server + user merged).
 * Handles fetching, caching, and merging metadata.
 */
interface MetadataRepository {
    val metadata: Flow<MetadataJson?>
    val metadataFiles: Flow<List<java.io.File>>

    suspend fun fetchMetadata(url: String): Result<MetadataJson>
    suspend fun loadCachedMetadata(): MetadataJson?
    suspend fun loadUserMetadata(jsonString: String): Result<MetadataJson>
    fun getMetadataContent(filename: String): String?
    fun getCurrentMetadataFilename(): String
    fun setCurrentMetadataFilename(filename: String)
    fun getMetadataFiles(): Flow<List<java.io.File>>
    fun deleteMetadataFile(file: java.io.File)
    fun shareMetadata(context: android.content.Context)
    fun importMetadata(context: android.content.Context)
    fun getMetadataUri(file: java.io.File): android.net.Uri?
    fun saveUserMetadata(jsonString: String)
}