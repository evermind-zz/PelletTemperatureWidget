package com.example.pelletswaterheater.viewmodel

import com.example.pelletswaterheater.utils.ContextProvider
import com.example.pelletswaterheater.utils.ILogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TemperatureViewModelNewTest {
    private lateinit var server: MockWebServer
    private lateinit var viewModel: TemperatureViewModelNew
    private lateinit var contextProvider: ContextProvider
    private lateinit var logger: ILogger
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        server = MockWebServer()
        server.start()

        logger = mock<ILogger> {
            on { w(any(), any()) } doAnswer { /* suppress or log */ }
            on { i(any(), any()) } doAnswer { /* suppress or log */ }
            on { e(any(), any(), anyOrNull()) } doAnswer { /* suppress or log */ }
        }

        contextProvider = mock {
            on { loadCachedMetadata() } doReturn null
            on { saveMetadata(any()) } doAnswer {}
        }

        viewModel = TemperatureViewModelNew(
            contextProvider = contextProvider,
            httpClient = OkHttpClient(),
            logger = logger
        )
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun testFetchMetadata() = runTest {
        val serverJson = File("src/test/resources/server.json").readText()
        server.enqueue(MockResponse().setBody(serverJson).setResponseCode(200))

        viewModel.fetchMetadata(server.url("/").toString())
        testDispatcher.scheduler.advanceUntilIdle()

        val metadata = viewModel.metadata.value
        assertNotNull(metadata, "Metadata should not be null")
        assertEquals(2, metadata.sections.size, "Should have 2 sections: system, hk1")
        assertEquals("219", metadata.sections["system"]?.get("L_ambient")?.value)
        assertEquals("228", metadata.sections["hk1"]?.get("L_room_temp")?.value)
    }

    @Test
    fun testLoadUserMetadataAndMerge() = runTest {
        // First fetch server metadata
        val serverJson = File("src/test/resources/server.json").readText()
        server.enqueue(MockResponse().setBody(serverJson).setResponseCode(200))
        viewModel.fetchMetadata(server.url("/").toString())
        testDispatcher.scheduler.advanceUntilIdle()

        // Load user metadata
        val userJson = File("src/test/resources/user.json").readText()
        viewModel.loadUserMetadata(userJson)
        testDispatcher.scheduler.advanceUntilIdle()

        val metadata = viewModel.metadata.value
        assertNotNull(metadata, "Merged metadata should not be null")
        assertEquals("Außentemperatur", metadata.sections["system"]?.get("L_ambient")?.name)
        assertEquals(2, metadata.sections["system"]?.get("L_ambient")?.decimals)
        assertEquals(
            null,
            metadata.sections["system"]?.get("L_usb_stick"),
            "L_usb_stick should be dropped"
        )
        assertEquals("°C", metadata.sections["hk1"]?.get("L_room_temp")?.unit)
    }

    @Test
    fun testUpdateValues() = runTest {
        val serverJson = File("src/test/resources/server.json").readText()
        server.enqueue(MockResponse().setBody(serverJson).setResponseCode(200))
        viewModel.fetchMetadata(server.url("/").toString())
        testDispatcher.scheduler.advanceUntilIdle()

        // Load user metadata
        val userJson = File("src/test/resources/user.json").readText()
        viewModel.loadUserMetadata(userJson)
        testDispatcher.scheduler.advanceUntilIdle()

        val rawData = mapOf(
            "system.L_ambient" to "219",
            "hk1.L_room_temp" to "228"
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
        // user.json provides no extended metadata for L_room_temp -> decimals therefore 1 and name should be hk1.L_room_temp
        assertEquals(
            "22,8",
            converted["hk1.L_room_temp"]?.logical,
            "L_room_temp should be converted"
        )
        assertEquals("hk1.L_room_temp", converted["hk1.L_room_temp"]?.name)
    }
}
