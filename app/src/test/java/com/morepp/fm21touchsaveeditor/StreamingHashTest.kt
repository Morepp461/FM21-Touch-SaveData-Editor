package com.morepp.fm21touchsaveeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class StreamingHashTest {
    @Test
    fun matchesKnownSha256ForSmallInput() {
        val hash = sha256(ByteArrayInputStream("abc".toByteArray()))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            hash.joinToString("") { "%02x".format(it) }
        )
    }

    @Test
    fun handlesShortReadsAndClosesTheInputStream() {
        val bytes = "streamed FMV verification".repeat(10_000).toByteArray()
        var closed = false
        val source = object : InputStream() {
            private val delegate = ByteArrayInputStream(bytes)
            override fun read(): Int = delegate.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                delegate.read(buffer, offset, minOf(length, 7))
            override fun close() {
                closed = true
                delegate.close()
            }
        }

        val actual = sha256(source)
        val expected = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)

        assertTrue(expected.contentEquals(actual))
        assertTrue(closed)
    }
}
