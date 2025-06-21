package com.example.pelletswaterheater.utils

import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemperatureLoggerTest {
    private lateinit var logger: TemperatureLogger
    private lateinit var contextProvider: ContextProvider
    private lateinit var mockLogger: ILogger
    private lateinit var tempDir: File
    private lateinit var mockContext: Context

    @BeforeEach
    fun setup(@TempDir tempDir: File) {
        this.tempDir = tempDir
        mockLogger = mock<ILogger> {
            on { e(any(), any(), anyOrNull()) } doAnswer { /* Suppress */ }
            on { w(any(), any()) } doAnswer { /* Suppress */ }
        }

        mockContext = mock<Context> {
            on { filesDir } doAnswer { tempDir }
        }

        contextProvider = mock<ContextProvider> {
            on { loadCachedMetadata() } doAnswer { null }
            on { saveMetadata(any()) } doAnswer { }
            on { getContext() } doAnswer { mockContext }
        }

        logger = TemperatureLoggerImpl(contextProvider, mockLogger)
    }

    @Test
    fun testLogValuesAppendsToSingleCsvFile() = runTest {
        val values1 = mapOf(
            "system.L_ambient" to ConvertedValue("21,90", "Außentemperatur", "°C")
        )
        val values2 = mapOf(
            "hk1.L_room_temp" to ConvertedValue("22,8", "Room Temp", "°C")
        )

        logger.logValues(values1)
        logger.logValues(values2)

        val logDir = File(tempDir, "logs")
        assertTrue(logDir.exists(), "Log directory should exist")
        val logFiles = logDir.listFiles()?.filter { it.name.endsWith(".csv") }
        assertEquals(1, logFiles?.size, "Only one CSV file should be created")
        assertEquals("temp_log.csv", logFiles?.first()?.name, "File should be temp_log.csv")

        val content = logFiles?.first()?.readText()
        assertTrue(content?.contains("timestamp;section;key;logical;name;unit") ?: false, "CSV header should exist")
        assertTrue(content?.contains("system;L_ambient;21,90;Außentemperatur;°C") ?: false, "System ambient should be logged")
        assertTrue(content?.contains("hk1;L_room_temp;22,8;Room Temp;°C") ?: false, "Room temp should be logged")
    }

    @Test
    fun testLogValuesRotatesFileWhenOver5MB() = runTest {
        val largeValue = ConvertedValue("1".repeat(1000), "Large", "unit")
        val values = mapOf("system.L_large" to largeValue)

        // Log enough data to exceed 5MB
        repeat(5000) { logger.logValues(values) }

        val logDir = File(tempDir, "logs")
        val logFiles = logDir.listFiles()?.filter { it.name.endsWith(".csv") }
        assertTrue((logFiles?.size ?: 0) >= 2, "At least two CSV files should be created due to rotation")
        assertTrue(logFiles?.any { it.name == "temp_log.csv" } ?: false, "Current file should exist")
        assertTrue(logFiles?.any { it.name.startsWith("temp_log_") } ?: false, "Rotated file should exist")

        val latestFile = logFiles?.find { it.name == "temp_log.csv" }
        assertTrue(latestFile?.length() ?: 0 < 5 * 1024 * 1024, "Current file should be under 5MB")
    }
}