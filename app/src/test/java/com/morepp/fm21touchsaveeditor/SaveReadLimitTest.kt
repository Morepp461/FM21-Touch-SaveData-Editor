package com.morepp.fm21touchsaveeditor

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

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
    fun acceptsInputExactlyAtConfiguredLimitAndRemovesTemporaryFile() {
        val input = CloseTrackingInputStream(byteArrayOf(1, 2, 3, 4, 5))
        val directory = createTempDirectory("fm21-read-limit").toFile()

        try {
            val result = readSaveBytes(input, maxBytes = 5, temporaryDirectory = directory)

            assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), result)
            assertEquals(true, input.wasClosed)
            assertEquals(0, directory.listFiles()?.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsInputBeyondConfiguredLimitAndRemovesTemporaryFile() {
        val input = CloseTrackingInputStream(byteArrayOf(1, 2, 3, 4, 5, 6))
        val directory = createTempDirectory("fm21-read-limit").toFile()

        try {
            val error = assertThrows(IllegalArgumentException::class.java) {
                readSaveBytes(input, maxBytes = 5, temporaryDirectory = directory)
            }

            assertEquals(true, error.message!!.contains("5 byte"))
            assertEquals(true, input.wasClosed)
            assertEquals(0, directory.listFiles()?.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsNegativeLimitAndStillClosesInput() {
        val input = CloseTrackingInputStream(byteArrayOf(1))
        val directory = createTempDirectory("fm21-read-limit").toFile()

        try {
            assertThrows(IllegalArgumentException::class.java) {
                readSaveBytes(input, maxBytes = -1, temporaryDirectory = directory)
            }
            assertEquals(true, input.wasClosed)
            assertEquals(0, directory.listFiles()?.size)
        } finally {
            directory.deleteRecursively()
        }
    }
}
