package com.morepp.fm21touchsaveeditor

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class SaveReadLimitTest {
    private class CloseTrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var wasClosed = false
            private set

        override fun close() {
            wasClosed = true
            super.close()
        }
    }


    private class FailingInputStream : InputStream() {
        var wasClosed = false
            private set
        private var reads = 0

        override fun read(): Int {
            if (reads++ < 3) return reads
            throw IOException("synthetic read failure")
        }

        override fun close() {
            wasClosed = true
        }
    }

    @Test
    fun acceptsInputExactlyAtConfiguredLimitAndRemovesTemporaryFile() {
        val input = CloseTrackingInputStream(byteArrayOf(1, 2, 3, 4, 5))
        val directory = java.nio.file.Files.createTempDirectory("fm21-read-limit").toFile()

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
        val directory = java.nio.file.Files.createTempDirectory("fm21-read-limit").toFile()

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
        val directory = java.nio.file.Files.createTempDirectory("fm21-read-limit").toFile()

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
    @Test
    fun removesTemporaryFileWhenSourceReadFails() {
        val input = FailingInputStream()
        val directory = java.nio.file.Files.createTempDirectory("fm21-read-limit").toFile()

        try {
            assertThrows(IOException::class.java) {
                readSaveBytes(input, maxBytes = 10, temporaryDirectory = directory)
            }
            assertEquals(true, input.wasClosed)
            assertEquals(0, directory.listFiles()?.size)
        } finally {
            directory.deleteRecursively()
        }
    }

}
