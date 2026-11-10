package com.morepp.fm21touchsaveeditor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PersonRecordMapperTest {
    @Test
    fun mapsNameUidToPersonEntityRecordAndLeavesMissingUidUnresolved() {
        val data = ByteArrayOutputStream()

        // Person entity row: entity ID, duplicated unique ID, then opaque row fields.
        data.write(le32(51_788L))
        data.write(le32(2_000_102_078L))
        data.write(le32(2_000_102_078L))
        data.write(byteArrayOf(0x0a, 0x80.toByte(), 0x00, 0xf4.toByte(), 0x01))

        // Name index entry which resolves to the entity row above.
        writeNameEntry(data, "Bob Fullam", 26_630L, 2_000_102_078L)

        // A second name index entry without an entity row must not be guessed.
        writeNameEntry(data, "Unresolved Player", 26_631L, 2_000_102_079L)

        val index = PersonRecordMapper.map(data.toByteArray())
        assertEquals(2, index.scannedNameEntries)
        assertEquals(1, index.mappedEntries)
        assertEquals(1, index.unresolvedEntries)

        val bob = index.entries.single { it.name == "Bob Fullam" }
        assertEquals(26_630L, bob.nameIndexId)
        assertEquals(2_000_102_078L, bob.uniqueId)
        assertEquals(51_788L, bob.entityRecordId)
        assertTrue(bob.entityRecordOffset != null)

        val missing = index.entries.single { it.name == "Unresolved Player" }
        assertNull(missing.entityRecordId)
        assertNull(missing.entityRecordOffset)
    }

    private fun writeNameEntry(out: ByteArrayOutputStream, name: String, localId: Long, uid: Long) {
        val bytes = name.toByteArray(Charsets.UTF_8)
        out.write(le32(bytes.size.toLong()))
        out.write(bytes)
        out.write(le32(0)) // observed flags field
        out.write(le32(localId))
        out.write(le32(uid))
        out.write(le32(uid))
        out.write(le32(5)) // observed name-index entry kind
        out.write(0)
    }

    private fun le32(value: Long): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt()).array()
}
