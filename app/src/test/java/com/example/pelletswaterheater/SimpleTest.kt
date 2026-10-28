package com.example.pelletswaterheater

import org.junit.Test
import junit.framework.TestCase

class SimpleTest : TestCase() {
    @Test
    fun testBasic() {
        assertEquals(2, 1 + 1)
    }

    @Test
    fun testString() {
        assertEquals("hello", "hello")
    }
}