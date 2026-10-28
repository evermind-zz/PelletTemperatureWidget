package com.example.pelletswaterheater.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.pelletswaterheater.NetworkAccess
import com.example.pelletswaterheater.preferences.PreferencesRepository
import com.example.pelletswaterheater.utils.ContextProvider
import com.example.pelletswaterheater.utils.ILogger
import com.example.pelletswaterheater.utils.Logger
import com.example.pelletswaterheater.utils.MetadataJson
import com.example.pelletswaterheater.utils.MetadataUtils
import com.example.pelletswaterheater.utils.TemperatureUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class MetadataRepositoryImpl(
    private val contextProvider: ContextProvider,
    private val logger: ILogger = Logger,
    private val preferencesRepository: PreferencesRepository,
    private val temperatureUtils: TemperatureUtils,
    private val networkAccess: NetworkAccess = NetworkAccess(),
    private val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : MetadataRepository, KoinComponent {

    private val TAG = MetadataRepositoryImpl::class.java.simpleName

    private val _metadata = MutableStateFlow<MetadataJson?>(null)
    override val metadata: StateFlow<MetadataJson?> = _metadata.asStateFlow()

    private val _metadataFiles = MutableStateFlow<List<File>>(emptyList())
    override val metadataFiles: StateFlow<List<File>> = _metadataFiles.asStateFlow()

    init {
        loadCachedMetadata()
    }

    override suspend fun fetchMetadata(url: String): Result<MetadataJson> {
        val fullUrl = if (url.endsWith("?")) url else "$url?"
        return try {
            val result = networkAccess.getDataFromServerResult(fullUrl, "fetchMetadata()")
            if (result.isFailure) {
                val exception = result.exceptionOrNull()
                if (exception?.message == "network busy") {
                    logger.d(TAG, "Metadata fetch skipped: network busy")
                    Result.failure(exception)
                } else {
                    throw exception ?: Exception("Unknown network error")
                }
            } else {
                val body = result.getOrThrow()
                val metadata = MetadataUtils.parseServerJson(body)
                if (metadata != null) {
                    contextProvider.saveMetadata(body)
                    _metadata.value = metadata
                    Result.success(metadata)
                } else {
                    logger.e(TAG, "Failed to parse server metadata")
                    Result.failure(Exception("Failed to parse server metadata"))
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Metadata fetch error: $e", e)
            loadCachedMetadata()
            Result.failure(e)
        }
    }

    override suspend fun loadCachedMetadata(): MetadataJson? {
        var cachedJson = getCurrentMetadataContent()
        if (cachedJson != null) {
            val cached = MetadataUtils.parseServerJson(cachedJson)
            _metadata.value = cached
            return cached
        }
        logger.w(TAG, "No cached metadata found, trying default from assets")
        cachedJson = contextProvider.loadDefaultMetadata()
        if (cachedJson != null) {
            val cached = MetadataUtils.parseServerJson(cachedJson)
            _metadata.value = cached
            return cached
        }
        logger.e(TAG, "No default metadata found in assets")
        return null
    }

    override suspend fun loadUserMetadata(jsonString: String): Result<MetadataJson> {
        return try {
            val userMetadata = MetadataUtils.parseUserJson(jsonString)
            if (userMetadata != null) {
                contextProvider.saveMetadata(jsonString)
                _metadata.value = MetadataUtils.mergeMetadata(_metadata.value, userMetadata)
                Result.success(userMetadata)
            } else {
                logger.e(TAG, "Failed to parse user metadata")
                Result.failure(Exception("Failed to parse user metadata"))
            }
        } catch (e: Exception) {
            logger.e(TAG, "User metadata load error: $e", e)
            Result.failure(e)
        }
    }

    override fun getMetadataContent(filename: String): String? {
        return contextProvider.getMetadata(filename)
    }

    override fun getCurrentMetadataFilename(): String {
        return temperatureUtils.getMetadataDefaultFile()
    }

    override fun setCurrentMetadataFilename(filename: String) {
        temperatureUtils.setMetadataDefaultFile(filename)
    }

    override fun getMetadataFiles(): StateFlow<List<File>> {
        val metadataDir = File(contextProvider.getContext().filesDir, "")
        _metadataFiles.update {
            metadataDir.listFiles { file ->
                file.isFile && file.extension == "json"
            }?.toList() ?: emptyList()
        }
        return metadataFiles
    }

    override fun deleteMetadataFile(file: File) {
        val context = contextProvider.getContext()
        val target = File(context.filesDir, file.name)
        if (target.exists()) {
            target.delete()
            getMetadataFiles() // refresh
        }
    }

    override fun shareMetadata(context: Context) {
        // TODO: Implement share intent logic
    }

    override fun importMetadata(context: Context) {
        // TODO: Implement file picker logic
    }

    override fun getMetadataUri(file: File): Uri? {
        return try {
            val context = contextProvider.getContext()
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            logger.e(TAG, "Failed to get metadata URI: $e", e)
            null
        }
    }

    override fun saveUserMetadata(jsonString: String) {
        viewModelScope.launch { loadUserMetadata(jsonString) }
    }

    private fun getCurrentMetadataContent(): String? {
        val currentMetadataFilename = temperatureUtils.getMetadataDefaultFile()
        return contextProvider.getMetadata(currentMetadataFilename)
    }
}