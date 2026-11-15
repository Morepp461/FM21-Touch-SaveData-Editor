package com.morepp.fm21touchsaveeditor.core

import com.github.luben.zstd.Zstd
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedHashMap

/**
 * Rebuilds an FM21 Touch 21.4.x container while preserving untouched compressed
 * component streams byte-for-byte. Changed components are re-encoded as bounded
 * independent Zstandard frames. The output is reparsed and every component is
 * decompressed before it is returned.
 */
object FmvRebuilder {
    private val footerPrefix = byteArrayOf(
        0x02, 0x01, 0x66, 0x6d, 0x66, 0x2e, 0x08, 0x00, 0x00
    )
    private const val HEADER_SIZE = 30
    private const val MAX_OUTPUT_BYTES = 1_500_000_000L
    private const val MAX_COMPONENT_BYTES = 512L * 1024 * 1024
    private const val FRAME_PLAIN_BYTES = 8 * 1024 * 1024
    private const val COMPRESSION_LEVEL = 3

    private data class EncodedMember(
        val member: FmvMember,
        val frameStream: ByteArray,
        val firstFrameSize: Int,
        val plainLength: Long
    )

    private data class NewMetadata(
        val offset: Long,
        val storedLength: Long,
        val plainLength: Long
    )

    /**
     * Validate every component of an untouched save and return an exact byte-for-byte
     * copy. This is the safe first export path while no semantic database fields
     * have been proven writable.
     */
    fun validatedUnchangedCopy(source: FmvContainer): ByteArray {
        val discard = object : java.io.OutputStream() {
            override fun write(value: Int) = Unit
            override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
        }
        source.manifest.members.forEach { member ->
            val (written, _) = source.extract(member, discard)
            if (written != member.plainLength) {
                throw FmvFormatException("Source component failed integrity validation: ${member.name}")
            }
        }
        return source.originalBytes()
    }

    fun rebuild(
        source: FmvContainer,
        editedComponents: Map<String, ByteArray>
    ): ByteArray {
        if (editedComponents.isEmpty()) return validatedUnchangedCopy(source)
        if (source.gameVersion != "21.4.0-0") {
            throw FmvFormatException(
                "Edited FMV rebuild is restricted to database version 21.4.0-0; detected: ${source.gameVersion ?: "unknown"}"
            )
        }

        val allMembers = source.manifest.members
        val byName = allMembers.associateBy { it.name }
        if (byName.size != allMembers.size) {
            throw FmvFormatException("Duplicate component names are ambiguous; refusing to rebuild")
        }
        val unknown = editedComponents.keys - byName.keys
        if (unknown.isNotEmpty()) {
            throw FmvFormatException("Edited component does not exist in this save: ${unknown.first()}")
        }
        editedComponents.forEach { (name, data) ->
            if (data.size.toLong() > MAX_COMPONENT_BYTES) {
                throw FmvFormatException("Edited component exceeds the safe limit: $name")
            }
        }

        val physical = allMembers.sortedBy { it.offset }
        val encoded = physical.map { member ->
            val replacement = editedComponents[member.name]
            if (replacement == null) {
                val stream = source.compressedFrameStream(member)
                EncodedMember(
                    member = member,
                    frameStream = stream,
                    firstFrameSize = source.firstCompressedFrameSize(member),
                    plainLength = member.plainLength
                )
            } else {
                val frames = ArrayList<ByteArray>()
                var chunkStart = 0
                while (chunkStart < replacement.size) {
                    val chunkEnd = minOf(chunkStart + FRAME_PLAIN_BYTES, replacement.size)
                    frames += Zstd.compress(replacement.copyOfRange(chunkStart, chunkEnd), COMPRESSION_LEVEL)
                    chunkStart = chunkEnd
                }
                if (frames.isEmpty()) {
                    throw FmvFormatException("Empty component replacement is not supported: ${member.name}")
                }
                val stream = ByteArrayOutputStream()
                frames.forEachIndexed { index, frame ->
                    stream.write(frame)
                    if (index < frames.lastIndex) writeU32(stream, frames[index + 1].size.toLong())
                }
                EncodedMember(
                    member = member,
                    frameStream = stream.toByteArray(),
                    firstFrameSize = frames.first().size,
                    plainLength = replacement.size.toLong()
                )
            }
        }

        val output = ByteArrayOutputStream()
        val originalHeader = source.originalHeader()
        val metadata = HashMap<String, NewMetadata>()
        var dataOffset = 0L

        encoded.forEachIndexed { index, item ->
            val memberOffset = dataOffset
            val storedLength = item.frameStream.size.toLong() + 4L
            metadata[item.member.name] = NewMetadata(
                offset = memberOffset,
                storedLength = storedLength,
                plainLength = item.plainLength
            )
            dataOffset += storedLength
            if (dataOffset > MAX_OUTPUT_BYTES) {
                throw FmvFormatException("Rebuilt save exceeds the safe output limit")
            }
        }

        // The final component's four-byte trailer is also the first four bytes
        // of the footer marker, so the footer itself starts four bytes earlier.
        val footerOffset = HEADER_SIZE.toLong() + dataOffset - 4L
        if (footerOffset < HEADER_SIZE || footerOffset - 9L > 0xffff_ffffL) {
            throw FmvFormatException("Rebuilt footer offset is outside the supported header range")
        }

        val manifestBytes = serializeManifest(source.manifest, metadata)
        val compressedManifest = Zstd.compress(manifestBytes, COMPRESSION_LEVEL)
        if (compressedManifest.size.toLong() > 0xffff_ffffL) {
            throw FmvFormatException("Compressed manifest is too large")
        }

        val header = originalHeader
        putU32(header, 9, footerOffset - 9L)
        output.write(header)
        encoded.forEachIndexed { index, item ->
            output.write(item.frameStream)
            if (index < encoded.lastIndex) {
                writeU32(output, encoded[index + 1].firstFrameSize.toLong())
            } else {
                output.write(footerPrefix, 0, 4)
            }
        }
        output.write(footerPrefix, 4, footerPrefix.size - 4)
        writeU32(output, compressedManifest.size.toLong())
        output.write(compressedManifest)

        val rebuilt = output.toByteArray()
        val verified = FmvContainer(rebuilt)
        if (verified.manifest.members.size != allMembers.size ||
            verified.manifest.saveName != source.manifest.saveName) {
            throw FmvFormatException("Rebuilt manifest verification failed")
        }
        val discard = object : java.io.OutputStream() {
            override fun write(value: Int) = Unit
            override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
        }
        val rebuiltByName = verified.manifest.members.associateBy { it.name }
        if (rebuiltByName.size != allMembers.size || rebuiltByName.keys != byName.keys) {
            throw FmvFormatException("Rebuilt component list does not match the source save")
        }
        verified.manifest.members.forEach { member ->
            val (written, _) = verified.extract(member, discard)
            if (written != member.plainLength) {
                throw FmvFormatException("Rebuilt component length verification failed: ${member.name}")
            }
        }

        // Re-encoding one edited component must never silently rewrite any
        // untouched component stream or its opaque metadata stamps.
        allMembers.filterNot { it.name in editedComponents }.forEach { original ->
            val rebuiltMember = rebuiltByName.getValue(original.name)
            if (original.archive != rebuiltMember.archive ||
                !original.stamp1.contentEquals(rebuiltMember.stamp1) ||
                !original.stamp2.contentEquals(rebuiltMember.stamp2) ||
                !source.compressedFrameStream(original).contentEquals(
                    verified.compressedFrameStream(rebuiltMember)
                )) {
                throw FmvFormatException("Untouched component changed during rebuild: ${original.name}")
            }
        }
        editedComponents.forEach { (name, expected) ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val sink = object : java.io.OutputStream() {
                override fun write(value: Int) { digest.update(value.toByte()) }
                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    digest.update(buffer, offset, length)
                }
            }
            val member = verified.manifest.members.first { it.name == name }
            verified.extract(member, sink)
            val expectedDigest = java.security.MessageDigest.getInstance("SHA-256").digest(expected)
            if (!digest.digest().contentEquals(expectedDigest)) {
                throw FmvFormatException("Rebuilt edited component failed byte verification: $name")
            }
        }
        return rebuilt
    }

    private fun serializeManifest(
        manifest: FmvManifest,
        metadata: Map<String, NewMetadata>
    ): ByteArray {
        val out = ByteArrayOutputStream()
        writeString(out, manifest.saveName)
        writeU32(out, manifest.topLevel.size.toLong())
        manifest.topLevel.forEach { writeMember(out, it, metadata) }

        val groups = LinkedHashMap<String, MutableList<FmvMember>>()
        manifest.nested.forEach { member ->
            val archive = member.archive
                ?: throw FmvFormatException("Nested component has no archive name: ${member.name}")
            groups.getOrPut(archive) { mutableListOf() }.add(member)
        }
        writeU32(out, groups.size.toLong())
        groups.forEach { (archive, members) ->
            writeString(out, archive)
            writeU32(out, members.size.toLong())
            members.forEach { writeMember(out, it, metadata) }
        }
        writeU32(out, 0)
        return out.toByteArray()
    }

    private fun writeMember(
        out: ByteArrayOutputStream,
        member: FmvMember,
        metadata: Map<String, NewMetadata>
    ) {
        val parts = member.name.split('/')
        if (parts.isEmpty() || parts.size > 32 || !parts.last().startsWith(".")) {
            throw FmvFormatException("Invalid component path: ${member.name}")
        }
        parts.forEach { writeString(out, it) }
        val values = metadata[member.name]
            ?: throw FmvFormatException("Missing rebuilt metadata: ${member.name}")
        writeU64(out, values.offset)
        writeU64(out, values.storedLength)
        writeU64(out, values.plainLength)
        if (member.stamp1.size != 8 || member.stamp2.size != 8) {
            throw FmvFormatException("Invalid component metadata stamps: ${member.name}")
        }
        out.write(member.stamp1)
        out.write(member.stamp2)
    }

    private fun writeString(out: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeU32(out, bytes.size.toLong())
        out.write(bytes)
    }

    private fun writeU32(out: ByteArrayOutputStream, value: Long) {
        if (value < 0 || value > 0xffff_ffffL) {
            throw FmvFormatException("Unsigned 32-bit value is out of range")
        }
        out.write(littleEndianU32(value))
    }

    private fun writeU64(out: ByteArrayOutputStream, value: Long) {
        if (value < 0) throw FmvFormatException("Unsigned 64-bit value is out of range")
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array())
    }

    private fun littleEndianU32(value: Long): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt()).array()

    private fun putU32(target: ByteArray, offset: Int, value: Long) {
        val bytes = littleEndianU32(value)
        bytes.copyInto(target, offset)
    }
}
