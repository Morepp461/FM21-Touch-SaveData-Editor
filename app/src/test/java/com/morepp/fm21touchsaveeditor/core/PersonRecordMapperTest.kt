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
        assertEquals("0a8000f4", bob.entityRecordTypeTag)
        assertNull(bob.entityRecordLength)

        val missing = index.entries.single { it.name == "Unresolved Player" }
        assertNull(missing.entityRecordId)
        assertNull(missing.entityRecordOffset)
    }

    @Test
    fun ambiguousEntityRowsForSameUidRemainUnresolved() {
        val data = ByteArrayOutputStream()
        writeEntityRow(data, 51_788L, 2_000_102_078L)
        writeEntityRow(data, 51_799L, 2_000_102_078L)
        writeNameEntry(data, "Ambiguous Person", 26_630L, 2_000_102_078L)

        val index = PersonRecordMapper.map(data.toByteArray())
        val person = index.entries.single()
        assertEquals(1, index.scannedNameEntries)
        assertEquals(0, index.mappedEntries)
        assertEquals(1, index.unresolvedEntries)
        assertNull(person.entityRecordId)
        assertNull(person.entityRecordOffset)
    }

    @Test
    fun rejectsPayloadWithoutSupportedNameIndex() {
        val error = org.junit.Assert.assertThrows(FmvFormatException::class.java) {
            PersonRecordMapper.map(ByteArray(128))
        }
        assertTrue(error.message!!.isNotBlank())
    }

    @Test
    fun mapsReadOnlyBufferWithoutChangingCallerPosition() {
        val data = ByteArrayOutputStream()
        writeEntityRow(data, 51_788L, 2_000_102_078L)
        writeNameEntry(data, "Buffer Person", 26_630L, 2_000_102_078L)
        val buffer = ByteBuffer.wrap(data.toByteArray()).asReadOnlyBuffer()
        buffer.position(3)

        val index = PersonRecordMapper.map(buffer)

        assertEquals(3, buffer.position())
        assertEquals(1, index.mappedEntries)
        assertEquals("Buffer Person", index.entries.single().name)
    }

    @Test
    fun mapsMemoryMappedFileWithoutCopyingDatabaseBytes() {
        val data = ByteArrayOutputStream()
        writeEntityRow(data, 51_788L, 2_000_102_078L)
        writeNameEntry(data, "Mapped File Person", 26_630L, 2_000_102_078L)
        val file = java.io.File.createTempFile("fm21-person-map", ".dat")
        try {
            file.writeBytes(data.toByteArray())
            val mapped = PersonRecordMapper.mapFile(file)
            assertEquals(1, mapped.mappedEntries)
            assertEquals("Mapped File Person", mapped.entries.single().name)
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsEmptyPersonDatabaseFile() {
        val file = java.io.File.createTempFile("fm21-empty-map", ".dat")
        try {
            val error = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                PersonRecordMapper.mapFile(file)
            }
            assertTrue(error.message!!.contains("unsupported size"))
        } finally {
            file.delete()
        }
    }

    private fun writeEntityRow(out: ByteArrayOutputStream, entityId: Long, uid: Long) {
        out.write(le32(entityId))
        out.write(le32(uid))
        out.write(le32(uid))
        out.write(byteArrayOf(0x0a, 0x80.toByte(), 0x00, 0xf4.toByte(), 0x01))
    }


    @Test
    fun rejectsAmbiguousUidWhenItAppearsInDifferentEntityRows() {
        val data = ByteArrayOutputStream()
        val uid = 2_000_102_078L

        // Two different entity IDs claim the same duplicated UID.
        data.write(le32(51_788L))
        data.write(le32(uid))
        data.write(le32(uid))
        data.write(byteArrayOf(0x0a, 0x80.toByte(), 0x00, 0xf4.toByte(), 0x01))
        data.write(le32(51_789L))
        data.write(le32(uid))
        data.write(le32(uid))
        data.write(byteArrayOf(0x0a, 0x80.toByte(), 0x00, 0xf4.toByte(), 0x01))
        writeNameEntry(data, "Ambiguous Player", 26_630L, uid)

        val index = PersonRecordMapper.map(data.toByteArray())
        val player = index.entries.single()
        assertEquals(0, index.mappedEntries)
        assertEquals(1, index.unresolvedEntries)
        assertNull(player.entityRecordId)
        assertNull(player.entityRecordOffset)
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
