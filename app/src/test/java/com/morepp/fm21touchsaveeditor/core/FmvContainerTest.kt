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
    fun noOpRebuildPreservesEveryOriginalByte() {
        val fixture = fixture(listOf("unchanged FM21 component".toByteArray()))
        val parsed = FmvContainer(fixture)
        assertArrayEquals(fixture, FmvRebuilder.rebuild(parsed, emptyMap()))
    }

    @Test
    fun rebuildsEditedComponentAndReopensTheContainer() {
        val fixture = fixture(listOf("21.4.0-0|original".toByteArray()), memberName = "game_info/.dat")
        val parsed = FmvContainer(fixture)
        val versionedReplacement = "21.4.0-0|edited".toByteArray()

        val rebuiltBytes = FmvRebuilder.rebuild(parsed, mapOf("game_info/.dat" to versionedReplacement))
        val reopened = FmvContainer(rebuiltBytes)

        assertArrayEquals(versionedReplacement, reopened.extractByName("game_info/.dat"))
        assertEquals(parsed.manifest.saveName, reopened.manifest.saveName)
        assertEquals(parsed.manifest.members.size, reopened.manifest.members.size)
    }

    @Test
    fun rejectsEditedRebuildWhenDatabaseVersionIsUnknown() {
        val unknown = FmvContainer(fixture(listOf("opaque component".toByteArray())))
        val error = assertThrows(FmvFormatException::class.java) {
            FmvRebuilder.rebuild(unknown, mapOf("sample/.dat" to "edited".toByteArray()))
        }
        assertEquals(true, error.message!!.contains("detected: unknown"))
    }

    @Test
    fun rejectsEditedRebuildForUnsupportedDatabaseVersion() {
        val unsupported = FmvContainer(
            fixture(listOf("22.0.0-0|original".toByteArray()), memberName = "game_info/.dat")
        )
        val error = assertThrows(FmvFormatException::class.java) {
            FmvRebuilder.rebuild(
                unsupported,
                mapOf("game_info/.dat" to "22.0.0-0|edited".toByteArray())
            )
        }
        assertEquals(true, error.message!!.contains("restricted to database version 21.4.0-0"))
    }

    @Test
    fun extractToFileReplacesOnlyAfterSuccessfulValidation() {
        val payload = "new extracted component".toByteArray()
        val parsed = FmvContainer(fixture(listOf(payload)))
        val directory = java.io.File.createTempFile("fmv-output", "test")
        directory.delete()
        directory.mkdirs()
        val output = java.io.File(directory, "component.dat")
        try {
            output.writeText("previous content")
            val member = parsed.manifest.members.single()
            parsed.extractToFile(member, output)
            assertArrayEquals(payload, output.readBytes())

            output.writeText("keep this on failure")
            val foreignMember = member.copy(name = "foreign/.dat")
            assertThrows(FmvFormatException::class.java) {
                parsed.extractToFile(foreignMember, output)
            }
            assertEquals("keep this on failure", output.readText())

            // Fail after the temporary output has been created, during frame validation.
            val corruptBytes = fixture(listOf(payload)).also { it[30] = 0 }
            val corruptContainer = FmvContainer(corruptBytes)
            assertThrows(FmvFormatException::class.java) {
                corruptContainer.extractToFile(corruptContainer.manifest.members.single(), output)
            }
            assertEquals("keep this on failure", output.readText())
            assertEquals(1, directory.listFiles()?.size)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsDuplicateComponentNamesAsAmbiguous() {
        val error = assertThrows(FmvFormatException::class.java) {
            FmvContainer(duplicateNameFixture())
        }
        assertEquals(true, error.message!!.contains("duplicate component name"))
    }

    @Test
    fun rejectsHeaderFooterPointerThatDoesNotMatchManifest() {
        val corrupt = fixture(listOf("pointer check".toByteArray()))
        corrupt[9] = 0
        corrupt[10] = 0
        corrupt[11] = 0
        corrupt[12] = 0

        val error = assertThrows(FmvFormatException::class.java) {
            FmvContainer(corrupt)
        }
        assertEquals(true, error.message!!.contains("footer offset"))
    }

    @Test
    fun rejectsHeaderFooterPointerThatLandsOnNonFooterBytes() {
        val corrupt = fixture(listOf("pointer target check".toByteArray()))
        corrupt[9] = 21
        corrupt[10] = 0
        corrupt[11] = 0
        corrupt[12] = 0

        val error = assertThrows(FmvFormatException::class.java) {
            FmvContainer(corrupt)
        }
        assertEquals(true, error.message!!.contains("does not point to the manifest footer"))
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

    private fun duplicateNameFixture(): ByteArray {
        val first = Zstd.compress("first".toByteArray(), 3)
        val second = Zstd.compress("second".toByteArray(), 3)
        val firstStored = first.size + 4
        val secondStored = second.size + 4
        val manifest = ByteArrayOutputStream()
        writeString(manifest, "Duplicate Name Save")
        manifest.write(le32(2))
        repeat(2) { index ->
            writeString(manifest, "sample")
            writeString(manifest, ".dat")
            manifest.write(le64(if (index == 0) 0 else firstStored.toLong()))
            manifest.write(le64(if (index == 0) firstStored.toLong() else secondStored.toLong()))
            manifest.write(le64(if (index == 0) 5 else 6))
            manifest.write(ByteArray(16))
        }
        manifest.write(le32(0))
        manifest.write(le32(0))
        val manifestFrame = Zstd.compress(manifest.toByteArray(), 3)
        val footerOffset = 30 + firstStored + secondStored - 4
        val header = ByteArray(30)
        header[0] = 0x02
        header[1] = 0x01
        "fmf.".toByteArray().copyInto(header, 2)
        le32(footerOffset - 9).copyInto(header, 9)
        return ByteArrayOutputStream().apply {
            write(header)
            write(first)
            write(le32(second.size))
            write(second)
            write(footerPrefix.copyOfRange(0, 4))
            write(footerPrefix.copyOfRange(4, footerPrefix.size))
            write(le32(manifestFrame.size))
            write(manifestFrame)
        }.toByteArray()
    }

    private fun fixture(chunks: List<ByteArray>, memberName: String = "sample/.dat"): ByteArray {
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
        memberName.split('/').forEach { writeString(manifest, it) }
        manifest.write(le64(0))
        manifest.write(le64(storedLength))
        manifest.write(le64(plainLength))
        manifest.write(ByteArray(16))
        manifest.write(le32(0)) // nested archives
        manifest.write(le32(0)) // terminator
        val manifestFrame = Zstd.compress(manifest.toByteArray(), 3)

        val result = ByteArrayOutputStream()
        val footerOffset = 30 + memberBytes.size
        val header = ByteArray(30)
        header[0] = 0x02
        header[1] = 0x01
        "fmf.".toByteArray().copyInto(header, 2)
        le32(footerOffset - 9).copyInto(header, 9)
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
