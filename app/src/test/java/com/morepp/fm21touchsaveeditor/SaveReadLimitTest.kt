package com.morepp.fm21touchsaveeditor

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class SaveReadLimitTest {
    private class CloseTrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var wasClosed = false
            private set

        override fun close() {
            wasClosed = true
            super.close()
        }
    }

    @Test
    fun acceptsInputExactlyAtConfiguredLimitAndClosesStream() {
        val input = CloseTrackingInputStream(byteArrayOf(1, 2, 3, 4, 5))

        val result = readSaveBytes(input, maxBytes = 5)

        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), result)
        assertEquals(true, input.wasClosed)
    }

    @Test
    fun rejectsInputBeyondConfiguredLimitAndClosesStream() {
        val input = CloseTrackingInputStream(byteArrayOf(1, 2, 3, 4, 5, 6))

        val error = assertThrows(IllegalArgumentException::class.java) {
            readSaveBytes(input, maxBytes = 5)
        }

        assertEquals(true, error.message!!.contains("5 byte"))
        assertEquals(true, input.wasClosed)
    }

    @Test
    fun rejectsNegativeLimitBeforeReading() {
        val input = CloseTrackingInputStream(byteArrayOf(1))

        assertThrows(IllegalArgumentException::class.java) {
            readSaveBytes(input, maxBytes = -1)
        }
        assertEquals(false, input.wasClosed)
    }
}
