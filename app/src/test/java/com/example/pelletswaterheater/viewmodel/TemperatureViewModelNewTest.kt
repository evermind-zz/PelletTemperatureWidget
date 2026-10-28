package com.example.pelletswaterheater.viewmodel

import android.content.Context
import android.content.SharedPreferences
import com.example.pelletswaterheater.NetworkAccess
import com.example.pelletswaterheater.repository.LoggingRepository
import com.example.pelletswaterheater.repository.MetadataRepository
import com.example.pelletswaterheater.repository.NetworkRepository
import com.example.pelletswaterheater.repository.SelectionRepository
import com.example.pelletswaterheater.repository.StateRepository
import com.example.pelletswaterheater.repository.ValueRepository
import com.example.pelletswaterheater.utils.ContextProvider
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

    // Mock repositories
    private lateinit var mockMetadataRepository: MetadataRepository
    private lateinit var mockValueRepository: ValueRepository
    private lateinit var mockSelectionRepository: SelectionRepository
    private lateinit var mockNetworkRepository: NetworkRepository
    private lateinit var mockLoggingRepository: LoggingRepository
    private lateinit var mockStateRepository: StateRepository
    private lateinit var mockPreferencesRepository: com.example.pelletswaterheater.preferences.PreferencesRepository

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

        // Mock repositories
        val metadataFlow = MutableStateFlow<MetadataJson?>(null)
        mockMetadataRepository = mock<MetadataRepository> {
            on { metadata } doAnswer { metadataFlow }
            on { fetchMetadata(any()) } doAnswer { mock<kotlin.Result<MetadataJson>> { } }
            on { loadCachedMetadata() } doAnswer { null }
            on { loadUserMetadata(any()) } doAnswer { mock<kotlin.Result<MetadataJson>> { } }
            on { getCurrentMetadataFilename() } doAnswer { "metadata.json" }
            on { setCurrentMetadataFilename(any()) } doAnswer { }
            on { getMetadataFiles() } doAnswer { mock<StateFlow<List<File>>> { } }
            on { deleteMetadataFile(any()) } doAnswer { }
            on { shareMetadata(any()) } doAnswer { }
            on { importMetadata(any()) } doAnswer { }
            on { getMetadataUri(any()) } doAnswer { null }
            on { saveUserMetadata(any()) } doAnswer { }
            on { getMetadataContent(any()) } doAnswer { null }
        }

        val convertedValuesFlow = MutableStateFlow<Map<String, ConvertedValue>>(emptyMap())
        val lastTimestampFlow = MutableStateFlow<String>("")
        mockValueRepository = mock<ValueRepository> {
            on { convertedValues } doAnswer { convertedValuesFlow }
            on { lastTimestamp } doAnswer { lastTimestampFlow }
            on { updateValues(any(), any(), any()) } doAnswer { }
            on { loadTestDataFromStorage() } doAnswer { }
            on { getCurrentTimestamp() } doAnswer { java.time.Instant.now().toString() }
        }

        val selectionsFlow = MutableStateFlow<List<SelectionRepository.Selection>>(emptyList())
        mockSelectionRepository = mock<SelectionRepository> {
            on { selections } doAnswer { selectionsFlow }
            on { loadSelections() } doAnswer { emptyList() }
            on { updateSelection(any(), any(), any(), any(), any()) } doAnswer { }
            on { toggleAllLog(any()) } doAnswer { }
            on { toggleAllWidget(any()) } doAnswer { }
            on { toggleAllShowData(any()) } doAnswer { }
            on { toggleAllSectionName(any()) } doAnswer { }
            on { updateSelectionsIfNeeded(any()) } doAnswer { }
        }

        mockNetworkRepository = mock<NetworkRepository> {
            on { fetchDataWithRetry(any(), any(), any()) } doAnswer { mock<kotlin.Result<String>> { } }
        }

        mockLoggingRepository = mock<LoggingRepository> {
            on { logValues(any(), any(), any(), any()) } doAnswer { }
        }

        val systemStatsFlow = MutableStateFlow<StateRepository.SystemStats>(StateRepository.SystemStats("N/A", null, "N/A"))
        mockStateRepository = mock<StateRepository> {
            on { systemStats } doAnswer { systemStatsFlow }
            on { updateState(any()) } doAnswer { }
            on { updateError(any()) } doAnswer { }
            on { updateStats(any(), any()) } doAnswer { }
        }

        mockPreferencesRepository = mock<com.example.pelletswaterheater.preferences.PreferencesRepository> {
            on { serverUrl } doAnswer { mock<StateFlow<String>> { } }
            on { widgetWidth } doAnswer { mock<StateFlow<String>> { } }
            on { widgetHeight } doAnswer { mock<StateFlow<String>> { } }
            on { backgroundColor } doAnswer { mock<StateFlow<String>> { } }
            on { textColor } doAnswer { mock<StateFlow<String>> { } }
            on { loggingInterval } doAnswer { mock<StateFlow<Long>> { } }
            on { gatewayFingerprint } doAnswer { mock<StateFlow<String?>> { } }
            on { metadataDefaultFile } doAnswer { mock<StateFlow<String>> { } }
            on { selectionsJson } doAnswer { mock<StateFlow<String>> { } }
            on { storedOrderHash } doAnswer { mock<StateFlow<Int>> { } }
            on { lastRefreshTime } doAnswer { mock<StateFlow<Long?>> { } }
            on { setServerUrl(any()) } doAnswer { }
            on { setWidgetWidth(any()) } doAnswer { }
            on { setWidgetHeight(any()) } doAnswer { }
            on { setBackgroundColor(any()) } doAnswer { }
            on { setTextColor(any()) } doAnswer { }
            on { setLoggingInterval(any()) } doAnswer { }
            on { setGatewayFingerprint(any()) } doAnswer { }
            on { setMetadataDefaultFile(any()) } doAnswer { }
            on { setSelectionsJson(any()) } doAnswer { }
            on { setStoredOrderHash(any()) } doAnswer { }
            on { setLastRefreshTime(any()) } doAnswer { }
        }

        viewModel = TemperatureViewModelNew(
            contextProvider = contextProvider,
            logger = logger,
            preferencesRepository = mockPreferencesRepository,
            metadataRepository = mockMetadataRepository,
            valueRepository = mockValueRepository,
            selectionRepository = mockSelectionRepository,
            networkRepository = mockNetworkRepository,
            loggingRepository = mockLoggingRepository,
            stateRepository = mockStateRepository,
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