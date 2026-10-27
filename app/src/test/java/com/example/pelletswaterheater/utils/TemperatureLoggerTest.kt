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
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
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
            on { packageName } doAnswer { "com.example.pelletswaterheater" }
        }

        contextProvider = mock<ContextProvider> {
            on { loadCachedMetadata() } doAnswer { null }
            on { saveMetadata(any()) } doAnswer { }
            on { getContext() } doAnswer { mockContext }
        }

        logger = TemperatureLoggerCsvImpl(contextProvider, mockLogger)
    }

    data class TestData(
        val timestamp: String,
        val values: Map<String, ConvertedValue>,
        val keyOrder: List<String>,
        val expectedSectionHeader: String,
        val expectedFieldHeader: String,
        val expectedNameHeader: String,
        val expectedUnitHeader: String,
        val expectedDataRow: String
    )

    @Test
    fun testLogValuesAppendsToSingleCsvRowThreeSystemValues() = runTest {
        val timestamp = getTimestamp()

        val values = mapOf(
            "system.L_ambient" to ConvertedValue("22.5", "Außentemperatur", "°C"),
            "system.L_errors" to ConvertedValue("0", "", ""),
            "system.L_usb_stick" to ConvertedValue("Aus", "", ""),
            "hk1.L_roomtemp_act" to ConvertedValue("24.0", "Raumtemperatur aktuell", "°C")
        )
        val keyOrder = listOf(
            "system.L_ambient",
            "system.L_errors",
            "system.L_usb_stick",
            "hk1.L_roomtemp_act"
        )

        val testData = TestData(
            timestamp,
            values,
            keyOrder,
            expectedSectionHeader = "Machine parts:,system,,,hk1",
            expectedFieldHeader = ",L_ambient,L_errors,L_usb_stick,L_roomtemp_act",
            expectedNameHeader = ",Außentemperatur,,,Raumtemperatur aktuell",
            expectedUnitHeader = "timestamp,°C,,,°C",
            expectedDataRow = "$timestamp,22.5,0,Aus,24.0"
        )

        testLogValuesAppendsToSingleCsvRowHelper(testData)
    }

    // this test is a slight variation of the previous test but  with only two system section values
    // goal: to test the numbers of ',' between the sections in the section header
    @Test
    fun testLogValuesAppendsToSingleCsvRowTwoSystemValues() = runTest {
        val timestamp = getTimestamp()

        val values = mapOf(
            "system.L_ambient" to ConvertedValue("22.5", "Außentemperatur", "°C"),
            "system.L_errors" to ConvertedValue("0", "", ""),
            "hk1.L_roomtemp_act" to ConvertedValue("24.0", "Raumtemperatur aktuell", "°C")
        )
        val keyOrder = listOf(
            "system.L_ambient",
            "system.L_errors",
            "hk1.L_roomtemp_act"
        )

        val testData = TestData(
            timestamp,
            values,
            keyOrder,
            expectedSectionHeader = "Machine parts:,system,,hk1",
            expectedFieldHeader = ",L_ambient,L_errors,L_roomtemp_act",
            expectedNameHeader = ",Außentemperatur,,Raumtemperatur aktuell",
            expectedUnitHeader = "timestamp,°C,,°C",
            expectedDataRow = "$timestamp,22.5,0,24.0"
        )

        testLogValuesAppendsToSingleCsvRowHelper(testData)
    }

    fun testLogValuesAppendsToSingleCsvRowHelper(testData: TestData) {
        logger.logValues(testData.values, testData.keyOrder, isoTimestampToMillis(testData.timestamp))

        val logDir = File(tempDir, "logs")
        assertTrue(logDir.exists(), "Log directory should exist")
        val logFiles = logDir.listFiles()?.filter { it.name.endsWith(".csv") }
        assertEquals(1, logFiles?.size, "Only one CSV file should be created")
        assertEquals("temp_log.csv", logFiles?.first()?.name, "File should be temp_log.csv")

        val content = logFiles?.first()?.readText()
        assertTrue(
            content?.contains("# File generated by PelletsWaterHeater app see https://github.com/whatever/pelletswaterheater") ?: false,
            "Comment header should exist"
        )
        assertTrue(
            content?.contains(testData.expectedSectionHeader) ?: false,
            "Section header should exist"
        )
        assertTrue(
            content?.contains(testData.expectedFieldHeader) ?: false,
            "Field header should exist"
        )
        assertTrue(
            content?.contains(testData.expectedNameHeader) ?: false,
            "Name header should exist"
        )
        assertTrue(
            content?.contains(testData.expectedUnitHeader) ?: false,
            "Unit header should exist"
        )
        assertTrue(
            content?.contains(testData.expectedDataRow) ?: false,
            "Data row should be logged"
        )
    }
/**
 * Parses an ISO‑8601 UTC timestamp of the form
 * "yyyy-MM-dd'T'HH:mm:ss'Z'" and returns epoch‑milliseconds.
 *
 * @throws ParseException if the string is not in the expected format.
 */
@Throws(java.text.ParseException::class)
fun isoTimestampToMillis(iso: String): Long {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.parse(iso)?.time
        ?: throw IllegalArgumentException("Unable to parse timestamp: $iso")
}

    @Test
    fun testLogValuesHandlesMissingFields() = runTest {
        val timestamp = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        val values = mapOf(
            "system.L_ambient" to ConvertedValue("22.5", "Außentemperatur", "°C"),
            "hk1.L_roomtemp_act" to ConvertedValue("24.0", "Raumtemperatur aktuell", "°C")
        )
        val keyOrder = listOf(
            "system.L_ambient",
            "system.L_errors",
            "system.L_usb_stick",
            "hk1.L_roomtemp_act"
        )

        logger.logValues(values, keyOrder, isoTimestampToMillis(timestamp))

        val logDir = File(tempDir, "logs")
        val logFiles = logDir.listFiles()?.filter { it.name.endsWith(".csv") }
        assertEquals(1, logFiles?.size, "Only one CSV file should be created")

        val content = logFiles?.first()?.readText()
        assertTrue(
            content?.contains("$timestamp,22.5,,,24.0") ?: false,
            "Missing fields should be empty"
        )
    }

    @Test
    fun testLogValuesRotatesFileWhenOver5MB() = runTest {
        val timestamp = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        val largeValue = ConvertedValue("1".repeat(1000), "Large", "unit") // each entry will result in 1022Bytes
        val values = mapOf("system.L_large" to largeValue)
        val keyOrder = listOf("system.L_large")

        // Log enough data to exceed 5MB
        repeat(6000) { logger.logValues(values, keyOrder, isoTimestampToMillis(timestamp)) }

        val logDir = File(tempDir, "logs")
        val logFiles = logDir.listFiles()?.filter { it.name.endsWith(".csv") }
        assertTrue((logFiles?.size ?: 0) >= 2, "At least two CSV files should be created due to rotation")
        assertTrue(logFiles?.any { it.name == "temp_log.csv" } ?: false, "Current file should exist")
        assertTrue(logFiles?.any { it.name.startsWith("temp_log_") } ?: false, "Rotated file should exist")

        val latestFile = logFiles?.find { it.name == "temp_log.csv" }
        assertTrue(latestFile?.length() ?: 0 < 5 * 1024 * 1024, "Current file should be under 5MB")
    }

    private fun getTimestamp(): String {
        val timestamp = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        return timestamp
    }
}