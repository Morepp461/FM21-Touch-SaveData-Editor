package com.morepp.fm21touchsaveeditor.core

import com.github.luben.zstd.Zstd
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FmvRebuilderTest {
    private data class FixtureMember(
        val name: String,
        val archive: String? = null,
        val chunks: List<ByteArray>
    )

    @Test
    fun productionEntryPointRejectsEveryEditedComponentUntilSemanticWritesAreVerified() {
        val source = FmvContainer(
            fixture(listOf(FixtureMember("sample/.dat", chunks = listOf("original".toByteArray()))))
        )

        val error = assertThrows(FmvFormatException::class.java) {
            FmvRebuilder.rebuild(source, mapOf("sample/.dat" to "replacement".toByteArray()))
        }

        assertEquals(
            "Edited save writing is disabled: no FM21 field mapping has passed controlled-fixture and in-game validation.",
            error.message
        )
    }

    @Test
    fun productionEntryPointAllowsOnlyValidatedByteIdenticalCopy() {
        val bytes = fixture(listOf(FixtureMember("sample/.dat", chunks = listOf("original".toByteArray()))))
        val source = FmvContainer(bytes)

        assertArrayEquals(bytes, FmvRebuilder.rebuild(source, emptyMap()))
    }

    @Test
    fun rebuildsMultipleTopLevelAndNestedMembersAndPreservesUntouchedPayloads() {
        val originalA = "first top-level payload".toByteArray()
        val originalB = "nested payload that must remain untouched".toByteArray()
        val originalC = "second nested payload".toByteArray()
        val replacement = "edited first payload with a different length".toByteArray()
        val source = FmvContainer(
            fixture(
                listOf(
                    FixtureMember("sample_a/.dat", chunks = listOf(originalA)),
                    FixtureMember("archive/sample_b/.dat", "archive", listOf(originalB)),
                    FixtureMember("archive/sample_c/.dat", "archive", listOf(originalC))
                )
            )
        )

        val rebuilt = FmvContainer(
            FmvRebuilder.rebuildExperimental(source, mapOf("sample_a/.dat" to replacement))
        )

        assertEquals(2, rebuilt.manifest.topLevel.size)
        assertEquals(2, rebuilt.manifest.nested.size)
        assertArrayEquals(replacement, rebuilt.extractByName("sample_a/.dat"))
        assertArrayEquals(originalB, rebuilt.extractByName("archive/sample_b/.dat"))
        assertArrayEquals(originalC, rebuilt.extractByName("archive/sample_c/.dat"))

        // Untouched compressed streams and per-component metadata must survive
        // the rebuild byte-for-byte, not merely decompress to equivalent data.
        val sourceB = source.manifest.members.single { it.name == "archive/sample_b/.dat" }
        val rebuiltB = rebuilt.manifest.members.single { it.name == sourceB.name }
        assertArrayEquals(source.compressedFrameStream(sourceB), rebuilt.compressedFrameStream(rebuiltB))
        assertArrayEquals(sourceB.stamp1, rebuiltB.stamp1)
        assertArrayEquals(sourceB.stamp2, rebuiltB.stamp2)
        val sourceC = source.manifest.members.single { it.name == "archive/sample_c/.dat" }
        val rebuiltC = rebuilt.manifest.members.single { it.name == sourceC.name }
        assertArrayEquals(source.compressedFrameStream(sourceC), rebuilt.compressedFrameStream(rebuiltC))
        assertArrayEquals(sourceC.stamp1, rebuiltC.stamp1)
        assertArrayEquals(sourceC.stamp2, rebuiltC.stamp2)
    }

    @Test
    fun validatedUnchangedCopyPreservesEveryOriginalByte() {
        val fixture = fixture(
            listOf(
                FixtureMember("top/.dat", chunks = listOf("top-level".toByteArray())),
                FixtureMember("archive/child/.dat", "archive", listOf("nested".toByteArray()))
            )
        )
        val source = FmvContainer(fixture)

        val copy = FmvRebuilder.validatedUnchangedCopy(source)

        assertArrayEquals(fixture, copy)
        val reopened = FmvContainer(copy)
        assertArrayEquals("top-level".toByteArray(), reopened.extractByName("top/.dat"))
        assertArrayEquals("nested".toByteArray(), reopened.extractByName("archive/child/.dat"))
    }

    @Test
    fun rebuildsEditedMemberUsingMultipleZstandardFrames() {
        val original = "small original".toByteArray()
        // FmvRebuilder currently splits edited payloads at 8 MiB. Keep this
        // fixture larger than that boundary so the test really exercises the
        // writer's inter-frame length prefix instead of only reading a
        // multi-frame source fixture.
        val replacement = ByteArray(8 * 1024 * 1024 + 4096) { (it % 251).toByte() }
        val source = FmvContainer(fixture(listOf(FixtureMember("sample/.dat", chunks = listOf(original)))))

        val rebuilt = FmvContainer(
            FmvRebuilder.rebuildExperimental(source, mapOf("sample/.dat" to replacement))
        )

        assertArrayEquals(replacement, rebuilt.extractByName("sample/.dat"))
        val rebuiltMember = rebuilt.manifest.members.single { it.name == "sample/.dat" }
        val (_, frameCount) = rebuilt.extract(rebuiltMember, ByteArrayOutputStream())
        assertEquals(2, frameCount)
    }

    @Test
    fun rejectsUnknownAndEmptyComponentReplacements() {
        val source = FmvContainer(fixture(listOf(FixtureMember("sample/.dat", chunks = listOf(byteArrayOf(1, 2, 3))))))

        assertThrows(FmvFormatException::class.java) {
            FmvRebuilder.rebuildExperimental(source, mapOf("missing/.dat" to byteArrayOf(1)))
        }
        assertThrows(FmvFormatException::class.java) {
            FmvRebuilder.rebuildExperimental(source, mapOf("sample/.dat" to byteArrayOf()))
        }
    }

    private fun fixture(members: List<FixtureMember>): ByteArray {
        val membersWithGameVersion = members + FixtureMember(
            "game_info/.dat",
            chunks = listOf("21.4.0-0".toByteArray())
        )
        val encoded = membersWithGameVersion.map { member ->
            val frames = member.chunks.map { Zstd.compress(it, 3) }
            val stream = ByteArrayOutputStream()
            frames.forEachIndexed { index, frame ->
                stream.write(frame)
                if (index < frames.lastIndex) stream.write(le32(frames[index + 1].size))
            }
            Triple(member, stream.toByteArray(), member.chunks.sumOf { it.size }.toLong())
        }

        val metadata = LinkedHashMap<String, Triple<Long, Long, Long>>()
        var offset = 0L
        encoded.forEach { (member, stream, plainLength) ->
            metadata[member.name] = Triple(offset, stream.size.toLong() + 4L, plainLength)
            offset += stream.size.toLong() + 4L
        }

        val manifest = ByteArrayOutputStream()
        writeString(manifest, "Rebuilder Test Save")
        val top = encoded.filter { it.first.archive == null }
        val nestedGroups = encoded.filter { it.first.archive != null }.groupBy { it.first.archive!! }
        manifest.write(le32(top.size))
        top.forEach { (member, _, _) -> writeMember(manifest, member, metadata) }
        manifest.write(le32(nestedGroups.size))
        nestedGroups.forEach { (archive, group) ->
            writeString(manifest, archive)
            manifest.write(le32(group.size))
            group.forEach { (member, _, _) -> writeMember(manifest, member, metadata) }
        }
        manifest.write(le32(0))
        val compressedManifest = Zstd.compress(manifest.toByteArray(), 3)

        val footerOffset = 30L + offset - 4L
        val header = ByteArray(30)
        header[0] = 0x02
        header[1] = 0x01
        "fmf.".toByteArray().copyInto(header, 2)
        le32((footerOffset - 9L).toInt()).copyInto(header, 9)

        val result = ByteArrayOutputStream()
        result.write(header)
        encoded.forEachIndexed { index, (_, stream, _) ->
            result.write(stream)
            if (index < encoded.lastIndex) {
                val nextFirstFrameSize = Zstd.compress(encoded[index + 1].first.chunks.first(), 3).size
                result.write(le32(nextFirstFrameSize))
            } else {
                result.write(footerPrefix.copyOfRange(0, 4))
            }
        }
        result.write(footerPrefix.copyOfRange(4, footerPrefix.size))
        result.write(le32(compressedManifest.size))
        result.write(compressedManifest)
        return result.toByteArray()
    }

    private fun writeMember(
        out: ByteArrayOutputStream,
        member: FixtureMember,
        metadata: Map<String, Triple<Long, Long, Long>>
    ) {
        member.name.split('/').forEach { writeString(out, it) }
        val values = metadata.getValue(member.name)
        out.write(le64(values.first))
        out.write(le64(values.second))
        out.write(le64(values.third))
        out.write(ByteArray(16))
    }

    private fun writeString(out: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray()
        out.write(le32(bytes.size))
        out.write(bytes)
    }

    private fun le32(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun le64(value: Long): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()

    private companion object {
        val footerPrefix = byteArrayOf(0x02, 0x01, 0x66, 0x6d, 0x66, 0x2e, 0x08, 0x00, 0x00)
    }
}
