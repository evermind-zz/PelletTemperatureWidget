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

    @BeforeEach
    fun setup(@TempDir tempDir: File) {
        this.tempDir = tempDir
        mockLogger = mock<ILogger> {
            on { e(any(), any(), anyOrNull()) } doAnswer { /* Suppress */ }
            on { w(any(), any()) } doAnswer { /* Suppress */ }
        }

        contextProvider = mock<ContextProvider> {
            on { loadCachedMetadata() } doAnswer { null }
            on { saveMetadata(any()) } doAnswer { }
        }
        // Mock AndroidContextProvider's context field
        (contextProvider as? AndroidContextProvider)?.apply {
            javaClass.getDeclaredField("context").apply {
                isAccessible = true
                set(contextProvider, mock<Context> {
                    on { filesDir } doAnswer { tempDir }
                })
            }
        }

        logger = TemperatureLoggerImpl(contextProvider, mockLogger)
    }

    @Test
    fun testLogValuesCreatesCsvFile() = runTest {
        val values = mapOf(
            "system.L_ambient" to ConvertedValue("21,90", "Außentemperatur", "°C"),
            "hk1.L_room_temp" to ConvertedValue("22,8", "Room Temp", "°C")
        )

        logger.logValues(values)

        val logDir = File(tempDir, "logs")
        assertTrue(logDir.exists(), "Log directory should exist")
        val logFiles = logDir.listFiles()?.filter { it.name.endsWith(".csv") }
        assertEquals(1, logFiles?.size, "One CSV file should be created")

        val content = logFiles?.first()?.readText()
        assertTrue(content?.contains("timestamp;section;key;logical;name;unit") ?: false, "CSV header should exist")
        assertTrue(content?.contains("system;L_ambient;21,90;Außentemperatur;°C") ?: false, "System ambient should be logged")
        assertTrue(content?.contains("hk1;L_room_temp;22,8;Room Temp;°C") ?: false, "Room temp should be logged")
    }

    @Test
    fun testLogValuesRotatesFileWhenOver5MB() = runTest {
        val largeValue = ConvertedValue("1".repeat(1024), "Large", "unit")
        val values = mapOf("system.L_large" to largeValue)

        // Simulate large file by logging repeatedly
        repeat(5000) { logger.logValues(values) }

        val logDir = File(tempDir, "logs")
        val logFiles = logDir.listFiles()?.filter { it.name.endsWith(".csv") }
        assertTrue((logFiles?.size ?: 0) > 1, "Multiple CSV files should be created due to rotation")

        val latestFile = logFiles?.maxByOrNull { it.lastModified() }
        assertTrue(latestFile?.length() ?: 0 < 5 * 1024 * 1024, "Latest file should be under 5MB")
    }
}