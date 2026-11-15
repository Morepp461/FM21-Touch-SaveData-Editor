package com.morepp.fm21touchsaveeditor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PersonRecordDiffAnalyzerTest {
    @Test
    fun joinsRowsByUidWhenOffsetsMoveAndReportsOpaqueChangedOffsets() {
        val before = fixture(firstPrefix = 0, firstChangedByte = 0x11)
        val after = fixture(firstPrefix = 7, firstChangedByte = 0x22)

        val result = PersonRecordDiffAnalyzer.compare(before, after)

        assertEquals(1, result.compared)
        assertEquals(1, result.changed.size)
        assertEquals(1, result.changed.single().changedByteOffsets.size)
        assertEquals(16, result.changed.single().changedByteOffsets.single())
        assertTrue(result.changed.single().afterOffset > result.changed.single().beforeOffset)
    }

    @Test
    fun comparesMemoryMappedFilesWithoutChangingBufferPositions() {
        val beforeBytes = fixture(firstPrefix = 0, firstChangedByte = 0x11)
        val afterBytes = fixture(firstPrefix = 7, firstChangedByte = 0x22)
        val beforeBuffer = ByteBuffer.wrap(beforeBytes).asReadOnlyBuffer()
        val afterBuffer = ByteBuffer.wrap(afterBytes).asReadOnlyBuffer()
        beforeBuffer.position(2)
        afterBuffer.position(3)

        val bufferResult = PersonRecordDiffAnalyzer.compare(beforeBuffer, afterBuffer)
        assertEquals(2, beforeBuffer.position())
        assertEquals(3, afterBuffer.position())
        assertEquals(16, bufferResult.changed.single().changedByteOffsets.single())

        val beforeFile = java.io.File.createTempFile("fm21-before", ".dat")
        val afterFile = java.io.File.createTempFile("fm21-after", ".dat")
        try {
            beforeFile.writeBytes(beforeBytes)
            afterFile.writeBytes(afterBytes)
            val fileResult = PersonRecordDiffAnalyzer.compareFiles(beforeFile, afterFile)
            assertEquals(bufferResult.changed, fileResult.changed)
            assertEquals(bufferResult.compared, fileResult.compared)
        } finally {
            beforeFile.delete()
            afterFile.delete()
        }
    }

    @Test
    fun doesNotSilentlyCompareDuplicateUidEntries() {
        val beforeOut = ByteArrayOutputStream()
        writeEntity(beforeOut, 51_788, 2_000_102_078, 0x11)
        writeEntity(beforeOut, 51_789, 2_000_102_079, 0x33)
        writeName(beforeOut, "Bob Fullam", 26_630, 2_000_102_078)
        writeName(beforeOut, "Other Person", 26_631, 2_000_102_079)
        writeName(beforeOut, "Bob Alias", 26_632, 2_000_102_078)

        val after = fixture(firstPrefix = 0, firstChangedByte = 0x11)
        val result = PersonRecordDiffAnalyzer.compare(beforeOut.toByteArray(), after)

        assertEquals(1, result.ambiguousBefore)
        assertEquals(0, result.ambiguousAfter)
        assertEquals(0, result.missingBefore)
        assertEquals(0, result.missingAfter)
        assertEquals(0, result.compared)
    }

    @Test
    fun rejectsEmptyMappedDatabaseFiles() {
        val empty = java.io.File.createTempFile("fm21-empty", ".dat")
        val valid = java.io.File.createTempFile("fm21-data", ".dat")
        try {
            valid.writeBytes(fixture(firstPrefix = 0, firstChangedByte = 0x11))
            val error = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                PersonRecordDiffAnalyzer.compareFiles(empty, valid)
            }
            assertTrue(error.message!!.contains("must not be empty"))
        } finally {
            empty.delete()
            valid.delete()
        }
    }

    private fun fixture(firstPrefix: Int, firstChangedByte: Int): ByteArray {
        val out = ByteArrayOutputStream()
        repeat(firstPrefix) { out.write(0x7f) }
        writeEntity(out, 51_788, 2_000_102_078, firstChangedByte)
        writeEntity(out, 51_789, 2_000_102_079, 0x33)
        writeName(out, "Bob Fullam", 26_630, 2_000_102_078)
        writeName(out, "Other Person", 26_631, 2_000_102_079)
        return out.toByteArray()
    }

    private fun writeEntity(out: ByteArrayOutputStream, id: Long, uid: Long, marker: Int) {
        out.write(le32(id))
        out.write(le32(uid))
        out.write(le32(uid))
        out.write(byteArrayOf(0x0a, 0x80.toByte(), 0x00, 0xf4.toByte()))
        out.write(marker)
    }

    private fun writeName(out: ByteArrayOutputStream, name: String, localId: Long, uid: Long) {
        val bytes = name.toByteArray(Charsets.UTF_8)
        out.write(le32(bytes.size.toLong()))
        out.write(bytes)
        out.write(le32(0))
        out.write(le32(localId))
        out.write(le32(uid))
        out.write(le32(uid))
        out.write(le32(5))
        out.write(0)
    }

    private fun le32(value: Long): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt()).array()
}
