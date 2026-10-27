package com.example.pelletswaterheater.utils

import com.example.pelletswaterheater.checkJsonThingValue2
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ValueConverterTest {
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun testNumericConversion() {
        val metadata = MetadataField(
            value = "219",
            unit = "°C",
            factor = 0.1,
            offset = 10.0,
            name = "Außentemperatur",
            decimals = 1
        )
        val result = ValueConverter.convert("system.L_ambient", "219", metadata)
        assertEquals("31,9", result.logical) // German locale
        assertEquals("Außentemperatur", result.name)
        assertEquals("°C", result.unit)
    }

    @Test
    fun testFormattedConversion() {
        val metadata = MetadataField(
            value = "0",
            format = "0:Aus|1:Ein",
            name = "Usb Stick erkannt"
        )
        val result = ValueConverter.convert("system.L_usb_stick", "0", metadata)
        assertEquals("Aus", result.logical)
        assertEquals("Usb Stick erkannt", result.name)
        assertEquals(null, result.unit)
    }

    @Test
    fun testStringConversion() {
        val metadata = MetadataField(
            value = "Betriebsart Aus",
            name = "Status Text"
        )
        val result = ValueConverter.convert("system.L_statetext", "Betriebsart Aus", metadata)
        assertEquals("Betriebsart Aus", result.logical)
        assertEquals("Status Text", result.name)
        assertEquals(null, result.unit)
    }

    @Test
    fun testNoMetadata() {
        val result = ValueConverter.convert("system.L_ambient", "219", null)
        assertEquals("219", result.logical)
        assertEquals("system.L_ambient", result.name)
        assertEquals(null, result.unit)
    }

    @Test
    fun testMetadataMerging() {
        val serverJson = File("src/test/resources/server.json").readText()
        val userJson = File("src/test/resources/user.json").readText()
        val serverMetadata = MetadataUtils.parseServerJson(serverJson)
        val userMetadata = MetadataUtils.parseUserJson(userJson)
        assertNotNull(serverMetadata, "Server metadata should not be null")
        assertNotNull(userMetadata, "User metadata should not be null")
        val merged = MetadataUtils.mergeMetadata(serverMetadata, userMetadata)
        assertEquals("Außentemperatur", checkJsonThingValue2(merged, "system", "L_ambient", {it.name}))
        assertEquals(2, checkJsonThingValue2(merged, "system", "L_ambient", {it.decimals }))
        assertEquals(null, merged.sections["system"]?.get("L_usb_stick")) // Dropped
        assertEquals("°C", checkJsonThingValue2(merged, "hk1", "L_roomtemp_set", {it.unit})) // From server
    }
}
