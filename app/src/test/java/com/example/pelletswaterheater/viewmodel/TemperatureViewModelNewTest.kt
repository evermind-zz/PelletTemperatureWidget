package com.example.pelletswaterheater.viewmodel

import android.content.Context
import android.content.SharedPreferences
import com.example.pelletswaterheater.NetworkAccess
import com.example.pelletswaterheater.preferences.PreferencesRepository
import com.example.pelletswaterheater.repository.LoggingRepository
import com.example.pelletswaterheater.repository.MetadataRepository
import com.example.pelletswaterheater.repository.NetworkRepository
import com.example.pelletswaterheater.repository.SelectionRepository
import com.example.pelletswaterheater.repository.StateRepository
import com.example.pelletswaterheater.repository.ValueRepository
import com.example.pelletswaterheater.utils.ContextProvider
import com.example.pelletswaterheater.utils.ConvertedValue
import com.example.pelletswaterheater.utils.ILogger
import com.example.pelletswaterheater.utils.MetadataField
import com.example.pelletswaterheater.utils.MetadataJson
import com.example.pelletswaterheater.utils.MetadataLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.io.File
import kotlin.Result
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TemperatureViewModelNewTest : KoinTest {
    private lateinit var server: MockWebServer
    private lateinit var viewModel: TemperatureViewModelNew
    private lateinit var mockContext: Context
    private lateinit var contextProvider: ContextProvider
    private lateinit var tempDir: File
    private lateinit var logger: ILogger
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // Fake repository implementations
    private lateinit var fakeMetadataRepository: MetadataRepository
    private lateinit var fakeValueRepository: ValueRepository
    private lateinit var fakeSelectionRepository: SelectionRepository
    private lateinit var fakeNetworkRepository: NetworkRepository
    private lateinit var fakeLoggingRepository: LoggingRepository
    private lateinit var fakeStateRepository: StateRepository
    private lateinit var fakePreferencesRepository: PreferencesRepository

    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpAll(): Unit {
            startKoin {
                modules(
                    module {
                        single { NetworkAccess() }
                        single { OkHttpClient() }
                    }
                )
            }
        }

        @JvmStatic
        @AfterAll
        fun tearDownAll(): Unit {
            stopKoin()
        }
    }

    @BeforeEach
    fun setup(@TempDir tempDir: File) {
        this.tempDir = tempDir
        Dispatchers.setMain(testDispatcher)
        server = MockWebServer()
        server.start()

        logger = mock<ILogger> {
            on { w(any(), any()) } doAnswer { /* suppress or log */ }
            on { i(any(), any()) } doAnswer { /* suppress or log */ }
            on { e(any(), any(), anyOrNull()) } doAnswer { /* suppress or log */ }
        }

        mockContext = mock<Context> {
            on { filesDir } doAnswer { tempDir }
            on { packageName } doAnswer { "com.example.pelletswaterheater" }
            on { getSharedPreferences(any(), any()) } doAnswer {
                mock<SharedPreferences> {
                    on { getBoolean(any(), any()) }  doAnswer { false }
                }
            }
        }

        contextProvider = mock<ContextProvider> {
            on { loadCachedMetadata() } doAnswer { null }
            on { saveMetadata(any()) } doAnswer { }
            on { getContext() } doAnswer { mockContext }
        }

        // Fake MetadataRepository
        val metadataFlow = MutableStateFlow<MetadataJson?>(null)
        fakeMetadataRepository = object : MetadataRepository {
            override val metadata: StateFlow<MetadataJson?> = metadataFlow
            override suspend fun fetchMetadata(url: String): Result<MetadataJson> = Result.success(MetadataJson())
            override suspend fun loadCachedMetadata(): MetadataJson? = null
            override suspend fun loadUserMetadata(jsonString: String): Result<MetadataJson> = Result.success(MetadataJson())
            override fun getCurrentMetadataFilename(): String = "metadata.json"
            override fun setCurrentMetadataFilename(filename: String) {}
            override val metadataFiles: StateFlow<List<File>> = MutableStateFlow(emptyList()).asStateFlow()
            override fun deleteMetadataFile(file: File) {}
            override fun shareMetadata(context: Context) {}
            override fun importMetadata(context: Context) {}
            override fun getMetadataUri(file: File): android.net.Uri? = null
            override fun saveUserMetadata(jsonString: String) {}
            override fun getMetadataContent(filename: String): String? = null
        }

        // Fake ValueRepository
        val convertedValuesFlow = MutableStateFlow<Map<String, ConvertedValue>>(emptyMap())
        val lastTimestampFlow = MutableStateFlow<String>("")
        fakeValueRepository = object : ValueRepository {
            override val convertedValues: StateFlow<Map<String, ConvertedValue>> = convertedValuesFlow.asStateFlow()
            override val lastTimestamp: StateFlow<String> = lastTimestampFlow.asStateFlow()
            override fun updateValues(rawData: Map<String, String>, timestamp: String, doLogging: Boolean) {}
            override suspend fun loadTestDataFromStorage() {}
            override fun getCurrentTimestamp(): String = java.time.Instant.now().toString()
        }

        // Fake SelectionRepository
        val selectionsFlow = MutableStateFlow<List<SelectionRepository.Selection>>(emptyList())
        fakeSelectionRepository = object : SelectionRepository {
            override val selections: StateFlow<List<Selection>> = selectionsFlow.asStateFlow()
            override suspend fun loadSelections(): List<Selection> = emptyList()
            override fun updateSelection(key: String, log: Boolean?, widget: Boolean?, showData: Boolean?, showSectionName: Boolean?) {}
            override fun toggleAllLog(checked: Boolean) {}
            override fun toggleAllWidget(checked: Boolean) {}
            override fun toggleAllShowData(checked: Boolean) {}
            override fun toggleAllSectionName(checked: Boolean) {}
            override fun updateSelectionsIfNeeded(allKeys: Set<String>) {}
        }

        // Fake NetworkRepository
        fakeNetworkRepository = object : NetworkRepository {
            override suspend fun fetchDataWithRetry(url: String, caller: String, maxAttempts: Int): Result<String> = Result.success("{}")
        }

        // Fake LoggingRepository
        fakeLoggingRepository = object : LoggingRepository {
            override fun logValues(values: Map<String, ConvertedValue>, keyOrder: List<String>, timestamp: String, selectedKeys: Set<String>) {}
        }

        // Fake StateRepository
        val systemStatsFlow = MutableStateFlow<StateRepository.SystemStats>(StateRepository.SystemStats("N/A", null, "N/A"))
        fakeStateRepository = object : StateRepository {
            override val systemStats: StateFlow<SystemStats> = systemStatsFlow.asStateFlow()
            override fun updateState(state: String) {}
            override fun updateError(error: String?) {}
            override fun updateStats(error: String?, state: String) {}
        }

        // Fake PreferencesRepository
        fakePreferencesRepository = object : PreferencesRepository {
            override val serverUrl: StateFlow<String> = MutableStateFlow("").asStateFlow()
            override val widgetWidth: StateFlow<String> = MutableStateFlow("").asStateFlow()
            override val widgetHeight: StateFlow<String> = MutableStateFlow("").asStateFlow()
            override val backgroundColor: StateFlow<String> = MutableStateFlow("").asStateFlow()
            override val textColor: StateFlow<String> = MutableStateFlow("").asStateFlow()
            override val loggingInterval: StateFlow<Long> = MutableStateFlow(5000L).asStateFlow()
            override val gatewayFingerprint: StateFlow<String?> = MutableStateFlow(null).asStateFlow()
            override val metadataDefaultFile: StateFlow<String> = MutableStateFlow("").asStateFlow()
            override val selectionsJson: StateFlow<String> = MutableStateFlow("").asStateFlow()
            override val storedOrderHash: StateFlow<Int> = MutableStateFlow(0).asStateFlow()
            override val lastRefreshTime: StateFlow<Long?> = MutableStateFlow(null).asStateFlow()
            override suspend fun setServerUrl(url: String) {}
            override suspend fun setWidgetWidth(width: String) {}
            override suspend fun setWidgetHeight(height: String) {}
            override suspend fun setBackgroundColor(color: String) {}
            override suspend fun setTextColor(color: String) {}
            override suspend fun setLoggingInterval(interval: Long) {}
            override suspend fun setGatewayFingerprint(fingerprint: String) {}
            override suspend fun setMetadataDefaultFile(filename: String) {}
            override suspend fun setSelectionsJson(json: String) {}
            override suspend fun setStoredOrderHash(hash: Int) {}
            override suspend fun setLastRefreshTime(time: Long) {}
        }

        viewModel = TemperatureViewModelNew(
            contextProvider = contextProvider,
            logger = logger,
            preferencesRepository = fakePreferencesRepository,
            metadataRepository = fakeMetadataRepository,
            valueRepository = fakeValueRepository,
            selectionRepository = fakeSelectionRepository,
            networkRepository = fakeNetworkRepository,
            loggingRepository = fakeLoggingRepository,
            stateRepository = fakeStateRepository,
            viewModelScope = testScope
        )
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    // Helper function to extract field values from merged metadata
    private fun <T> checkJsonThingValue2(
        metadata: MetadataJson,
        section: String,
        valueName: String,
        fieldSelector: (MetadataField) -> T
    ): T? {
        return metadata.sections[section]?.get(valueName)
            ?.let { fieldSelector(it as MetadataField) }
    }

    private fun checkJsonThingValue(metadata: MetadataJson, section: String, valueName: String): String? {
        return when (val entry = metadata.sections[section]?.get(valueName)) {
            is MetadataField -> entry.value
            is MetadataLabel -> entry.label
            null -> null
        }
    }

    @Test
    fun testFetchMetadata() = runTest {
        val serverJson = javaClass.getResource("/server.json")?.readText() ?: ""
        server.enqueue(MockResponse().setBody(serverJson).setResponseCode(200))

        viewModel.fetchMetadata(server.url("/").toString())
        testDispatcher.scheduler.advanceUntilIdle()

        val metadata = viewModel.metadata.value
        assertNotNull(metadata, "Metadata should not be null")
        assertEquals(2, metadata.sections.size, "Should have 2 sections: system, hk1")
        assertEquals("219", checkJsonThingValue2(metadata, "system", "L_ambient", { it.value }))
        assertEquals("228", checkJsonThingValue2(metadata, "hk1", "L_roomtemp_set", { it.value }))
    }

    @Test
    fun testLoadUserMetadataAndMerge() = runTest {
        fetchMetaDataAndMergeWithUserMetadata()

        val metadata = viewModel.metadata.value
        assertNotNull(metadata, "Merged metadata should not be null")

        assertEquals("Außentemperatur", checkJsonThingValue2(metadata, "system", "L_ambient", { it.name }))
        assertEquals(2, checkJsonThingValue2(metadata, "system", "L_ambient", { it.decimals }))
        assertEquals(
            null,
            metadata.sections["system"]?.get("L_usb_stick"),
            "L_usb_stick should be dropped"
        )
        assertEquals("°C", checkJsonThingValue2(metadata, "hk1", "L_roomtemp_set", { it.unit }))
    }

    private fun fetchMetaDataAndMergeWithUserMetadata() {
        // First fetch server metadata
        val serverJson = javaClass.getResource("/server.json")?.readText() ?: ""
        server.enqueue(MockResponse().setBody(serverJson).setResponseCode(200))
        viewModel.fetchMetadata(server.url("/").toString())
        testDispatcher.scheduler.advanceUntilIdle()

        // Load user metadata
        val userJson = javaClass.getResource("/user.json")?.readText() ?: ""
        viewModel.loadUserMetadata(userJson)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun testValues() = runTest {
        fetchMetaDataAndMergeWithUserMetadata()
        val valueTestData = javaClass.getResource("/testdata.json")?.readText() ?: ""
        server.enqueue(MockResponse().setBody(valueTestData).setResponseCode(200))

        viewModel.fetchValues(server.url("/").toString(), "test", false)
        testDispatcher.scheduler.advanceUntilIdle()

        val metadata = viewModel.metadata.value
        assertNotNull(metadata, "Metadata should not be null")
        assertEquals(2, metadata.sections.size, "Should have 2 sections: system, hk1")
        assertEquals("219", checkJsonThingValue2(metadata, "system", "L_ambient", { it.value }))
        assertEquals("228", checkJsonThingValue2(metadata, "hk1", "L_roomtemp_set", { it.value }))
    }

    @Test
    fun testUpdateValues() = runTest {
        val serverJson = javaClass.getResource("/server.json")?.readText() ?: ""
        server.enqueue(MockResponse().setBody(serverJson).setResponseCode(200))
        viewModel.fetchMetadata(server.url("/").toString())
        testDispatcher.scheduler.advanceUntilIdle()

        // Load user metadata
        val userJson = javaClass.getResource("/user.json")?.readText() ?: ""
        viewModel.loadUserMetadata(userJson)
        testDispatcher.scheduler.advanceUntilIdle()

        val rawData = mapOf(
            "system.L_ambient" to "219",
            "hk1.L_roomtemp_set" to "228"
        )
        viewModel.updateValues(rawData)
        testDispatcher.scheduler.advanceUntilIdle()

        val converted = viewModel.convertedValues.value
        // assume user.json provides decimals=2
        assertEquals(
            "21,90",
            converted["system.L_ambient"]?.logical,
            "L_ambient should be converted"
        )
        assertEquals("Außentemperatur", converted["system.L_ambient"]?.name)
        // user.json provides no extended metadata for L_roomtemp_set -> decimals therefore 1 and name should be hk1.L_roomtemp_set
        assertEquals(
            "22,8",
            converted["hk1.L_roomtemp_set"]?.logical,
            "L_roomtemp_set should be converted"
        )
        assertEquals("hk1.L_roomtemp_set", converted["hk1.L_roomtemp_set"]?.name)
    }
}