package com.morepp.fm21touchsaveeditor.core

import com.github.luben.zstd.Zstd
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class FmvMember(
    val name: String,
    val offset: Long,
    val storedLength: Long,
    val plainLength: Long,
    val stamp1: ByteArray,
    val stamp2: ByteArray,
    val archive: String? = null
)

data class FmvManifest(
    val saveName: String,
    val topLevel: List<FmvMember>,
    val nested: List<FmvMember>,
    val footerOffset: Int
) {
    val members: List<FmvMember> get() = topLevel + nested
}

class FmvFormatException(message: String) : Exception(message)

/**
 * Read-only FMF/FMV container implementation for FM21 Touch 21.4.x.
 * Manifest offsets are relative to byte 30. Unknown structures are rejected.
 */
class FmvContainer(private val bytes: ByteArray) {
    companion object {
        private val FMF_PREFIX = byteArrayOf(0x02, 0x01, 0x66, 0x6d, 0x66, 0x2e, 0x08, 0x00, 0x00)
        private val ZSTD_MAGIC = byteArrayOf(0x28, 0xb5.toByte(), 0x2f, 0xfd.toByte())
        private const val HEADER_SIZE = 30
        private const val MAX_MEMBERS = 20_000
        private const val MAX_MEMBER_BYTES = 512L * 1024L * 1024L
    }

    val manifest: FmvManifest

    init {
        if (bytes.size < HEADER_SIZE || bytes[0] != 0x02.toByte() ||
            bytes[1] != 0x01.toByte() || !bytes.copyOfRange(2, 6).contentEquals(byteArrayOf(0x66,0x6d,0x66,0x2e))) {
            throw FmvFormatException("Unrecognized FMF/FMV header")
        }
        manifest = readManifest()
    }

    fun extract(member: FmvMember): ByteArray {
        if (member.plainLength < 0 || member.plainLength > MAX_MEMBER_BYTES) {
            throw FmvFormatException("Member exceeds safe extraction limit: ${member.name}")
        }
        val startLong = HEADER_SIZE.toLong() + member.offset
        val endLong = startLong + member.storedLength
        if (startLong < HEADER_SIZE || endLong > bytes.size || endLong < startLong || member.storedLength < 4) {
            throw FmvFormatException("Member is outside file bounds: ${member.name}")
        }
        var cursor = startLong.toInt()
        val end = endLong.toInt()
        val out = java.io.ByteArrayOutputStream(member.plainLength.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        while (out.size().toLong() < member.plainLength) {
            if (cursor >= end - 4) throw FmvFormatException("Missing Zstandard frame: ${member.name}")
            val remaining = bytes.copyOfRange(cursor, end)
            val frameSize = Zstd.findFrameCompressedSize(remaining)
            if (Zstd.isError(frameSize) || frameSize <= 0 || frameSize > (end - cursor).toLong()) {
                throw FmvFormatException("Invalid Zstandard frame in ${member.name}")
            }
            val frame = bytes.copyOfRange(cursor, cursor + frameSize.toInt())
            val expected = Zstd.getFrameContentSize(frame)
            if (expected < 0 || expected > MAX_MEMBER_BYTES) {
                throw FmvFormatException("Unknown or excessive Zstandard frame size in ${member.name}")
            }
            val decoded = try { Zstd.decompress(frame, expected.toInt()) } catch (e: Exception) {
                throw FmvFormatException("Zstandard decompression failed for ${member.name}: ${e.message}")
            }
            out.write(decoded)
            cursor += frameSize.toInt()
            if (out.size().toLong() < member.plainLength) {
                if (cursor + 4 > end) throw FmvFormatException("Missing next-frame length prefix")
                val nextSize = littleEndianU32(bytes, cursor)
                if (nextSize == 0L || nextSize > (end - cursor - 4).toLong()) {
                    throw FmvFormatException("Invalid next-frame length in ${member.name}")
                }
                cursor += 4
            }
        }
        if (out.size().toLong() != member.plainLength || cursor != end - 4) {
            throw FmvFormatException("Member length/boundary mismatch: ${member.name}")
        }
        return out.toByteArray()
    }

    fun extractByName(name: String): ByteArray {
        val member = manifest.members.firstOrNull { it.name == name }
            ?: throw FmvFormatException("Member not found: $name")
        return extract(member)
    }

    private fun readManifest(): FmvManifest {
        val footer = lastIndexOf(bytes, FMF_PREFIX)
        if (footer < HEADER_SIZE) throw FmvFormatException("Footer manifest header not found")
        val lengthAt = footer + FMF_PREFIX.size
        if (lengthAt + 4 > bytes.size) throw FmvFormatException("Truncated footer length")
        val compressedLength = littleEndianU32(bytes, lengthAt)
        val dataAt = lengthAt + 4
        if (compressedLength <= 0 || dataAt.toLong() + compressedLength != bytes.size.toLong()) {
            throw FmvFormatException("Footer compressed length does not match file end")
        }
        val frame = bytes.copyOfRange(dataAt, bytes.size)
        if (!frame.copyOfRange(0, 4).contentEquals(ZSTD_MAGIC)) {
            throw FmvFormatException("Footer manifest is not a Zstandard frame")
        }
        val size = Zstd.getFrameContentSize(frame)
        if (size < 0 || size > MAX_MEMBER_BYTES) throw FmvFormatException("Invalid manifest size")
        val plain = Zstd.decompress(frame, size.toInt())
        return parseManifest(plain, footer)
    }

    private fun parseManifest(data: ByteArray, footer: Int): FmvManifest {
        val reader = BinaryReader(data)
        val saveName = reader.string()
        val topCount = reader.u32().checkedCount()
        val top = List(topCount) { reader.member() }
        val archiveCount = reader.u32().checkedCount()
        val nested = ArrayList<FmvMember>()
        repeat(archiveCount) {
            val archiveName = reader.string()
            val childCount = reader.u32().checkedCount()
            repeat(childCount) {
                val m = reader.member()
                nested += m.copy(archive = archiveName)
            }
        }
        if (!reader.bytes(4).contentEquals(byteArrayOf(0, 0, 0, 0))) {
            throw FmvFormatException("Manifest terminator missing")
        }
        return FmvManifest(saveName, top, nested, footer)
    }

    private fun Long.checkedCount(): Int {
        if (this < 0 || this > MAX_MEMBERS) throw FmvFormatException("Manifest member count is invalid")
        return toInt()
    }

    private class BinaryReader(private val data: ByteArray) {
        private var p = 0
        fun bytes(n: Int): ByteArray {
            if (n < 0 || p.toLong() + n > data.size) throw FmvFormatException("Manifest is truncated")
            return data.copyOfRange(p, p + n).also { p += n }
        }
        fun u32(): Long = ByteBuffer.wrap(bytes(4)).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
        fun u64(): Long {
            val v = ByteBuffer.wrap(bytes(8)).order(ByteOrder.LITTLE_ENDIAN).long
            if (v < 0) throw FmvFormatException("Unsigned manifest value exceeds supported range")
            return v
        }
        fun string(): String {
            val n = u32()
            if (n > 1_048_576) throw FmvFormatException("Manifest string too large")
            return bytes(n.toInt()).toString(Charsets.UTF_8)
        }
        fun member(): FmvMember {
            val parts = ArrayList<String>()
            repeat(64) {
                val part = string()
                parts += part
                if (part.startsWith(".")) {
                    val offset = u64()
                    val stored = u64()
                    val plain = u64()
                    val stamp1 = bytes(8)
                    val stamp2 = bytes(8)
                    return FmvMember(parts.joinToString("/"), offset, stored, plain, stamp1, stamp2)
                }
            }
            throw FmvFormatException("Manifest member path is malformed")
        }
    }

    private fun littleEndianU32(data: ByteArray, at: Int): Long =
        ByteBuffer.wrap(data, at, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL

    private fun lastIndexOf(haystack: ByteArray, needle: ByteArray): Int {
        for (i in haystack.size - needle.size downTo 0) {
            var match = true
            for (j in needle.indices) if (haystack[i + j] != needle[j]) { match = false; break }
            if (match) return i
        }
        return -1
    }
}
