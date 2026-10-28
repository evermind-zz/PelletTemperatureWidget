package com.example.pelletswaterheater

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SimpleTest {
    @Test
    fun testBasic() {
        assertEquals(2, 1 + 1)
    }

    @Test
    fun testString() {
        assertEquals("hello", "hello")
    }

    @Test
    fun testTrue() {
        assertTrue(true)
    }
}