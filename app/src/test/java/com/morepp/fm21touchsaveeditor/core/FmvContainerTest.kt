package com.morepp.fm21touchsaveeditor.core

import com.github.luben.zstd.Zstd
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FmvContainerTest {
    private val footerPrefix = byteArrayOf(0x02, 0x01, 0x66, 0x6d, 0x66, 0x2e, 0x08, 0x00, 0x00)

    @Test
    fun parsesManifestAndExtractsSingleFrameMember() {
        val payload = "FM21 component parser test".toByteArray()
        val fixture = fixture(listOf(payload))
        val parsed = FmvContainer(fixture)
        assertEquals("Unit Test Save", parsed.manifest.saveName)
        assertEquals(1, parsed.manifest.topLevel.size)
        assertEquals(0, parsed.manifest.nested.size)
        assertArrayEquals(payload, parsed.extractByName("sample/.dat"))
    }

    @Test
    fun extractsMultipleConcatenatedFramesAndChecksLengthPrefix() {
        val first = ByteArray(128) { (it % 251).toByte() }
        val second = ByteArray(96) { ((it + 17) % 251).toByte() }
        val fixture = fixture(listOf(first, second))
        val parsed = FmvContainer(fixture)
        assertArrayEquals(first + second, parsed.extractByName("sample/.dat"))
    }

    @Test
    fun rejectsInvalidHeader() {
        val exception = assertThrows(FmvFormatException::class.java) {
            FmvContainer(ByteArray(64))
        }
        assertEquals(true, exception.message!!.contains("header"))
    }

    @Test
    fun comparatorFindsExactChangedByteAndOffset() {
        val before = FmvContainer(fixture(listOf("abc".toByteArray())))
        val after = FmvContainer(fixture(listOf("axc".toByteArray())))
        val directory = java.io.File.createTempFile("fmv-diff", "test")
        directory.delete()
        directory.mkdirs()
        try {
            val result = FmvSaveComparator.compare(before, after, directory)
            assertEquals(1, result.size)
            assertEquals(false, result.single().identical)
            assertEquals(1L, result.single().changedBytesInCommonLength)
            assertEquals(1L, result.single().firstDifferenceOffset)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun fixture(chunks: List<ByteArray>): ByteArray {
        val compressed = chunks.map { Zstd.compress(it, 3) }
        val memberData = ByteArrayOutputStream()
        compressed.forEachIndexed { index, frame ->
            memberData.write(frame)
            if (index < compressed.lastIndex) {
                memberData.write(le32(frameSize(compressed[index + 1])))
            }
        }
        val memberBytes = memberData.toByteArray()
        val plainLength = chunks.sumOf { it.size }.toLong()
        val storedLength = memberBytes.size.toLong() + 4L

        val manifest = ByteArrayOutputStream()
        writeString(manifest, "Unit Test Save")
        manifest.write(le32(1)) // top-level members
        writeString(manifest, "sample")
        writeString(manifest, ".dat")
        manifest.write(le64(0))
        manifest.write(le64(storedLength))
        manifest.write(le64(plainLength))
        manifest.write(ByteArray(16))
        manifest.write(le32(0)) // nested archives
        manifest.write(le32(0)) // terminator
        val manifestFrame = Zstd.compress(manifest.toByteArray(), 3)

        val footerOffset = 30 + memberBytes.size
        val result = ByteArrayOutputStream()
        val header = ByteArray(30)
        header[0] = 0x02
        header[1] = 0x01
        "fmf.".toByteArray().copyInto(header, 2)
        result.write(header)
        result.write(memberBytes)
        result.write(footerPrefix)
        result.write(le32(manifestFrame.size))
        result.write(manifestFrame)
        assertEquals(footerOffset, 30 + memberBytes.size)
        return result.toByteArray()
    }

    private fun frameSize(frame: ByteArray): Int = frame.size
    private fun writeString(out: ByteArrayOutputStream, value: String) {
        val data = value.toByteArray()
        out.write(le32(data.size))
        out.write(data)
    }
    private fun le32(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    private fun le64(value: Long): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
}
