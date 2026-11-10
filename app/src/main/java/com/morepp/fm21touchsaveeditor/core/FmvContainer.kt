package com.morepp.fm21touchsaveeditor.core

import com.github.luben.zstd.Zstd
import java.io.ByteArrayOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
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
 * Read-only FMF/FMV container reader for the observed FM21 Touch 21.4.x format.
 * It never changes the source byte array. Manifest offsets are relative to byte 30.
 */
class FmvContainer(private val bytes: ByteArray) {
    companion object {
        private val FMF_PREFIX = byteArrayOf(0x02, 0x01, 0x66, 0x6d, 0x66, 0x2e, 0x08, 0x00, 0x00)
        private val ZSTD_MAGIC = byteArrayOf(0x28, 0xb5.toByte(), 0x2f, 0xfd.toByte())
        private const val HEADER_SIZE = 30
        private const val MAX_MANIFEST_BYTES = 16L * 1024 * 1024
        private const val MAX_MEMBER_BYTES = 512L * 1024 * 1024
        private const val MAX_MEMBERS = 20_000
    }

    val manifest: FmvManifest
    val gameVersion: String?

    init {
        if (bytes.size <= HEADER_SIZE ||
            bytes[0] != 0x02.toByte() || bytes[1] != 0x01.toByte() ||
            !bytes.copyOfRange(2, 6).contentEquals(byteArrayOf(0x66, 0x6d, 0x66, 0x2e))) {
            throw FmvFormatException("Unrecognized FMF/FMV header")
        }
        manifest = readManifest()
        validateManifestBounds()
        gameVersion = readGameVersion()
    }

    /** Convenience in-memory extraction. Prefer extractToFile for large members on Android. */
    fun extract(member: FmvMember): ByteArray {
        val output = ByteArrayOutputStream(member.plainLength.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        extract(member, output)
        return output.toByteArray()
    }

    fun extractToFile(member: FmvMember, output: File): Pair<Long, Int> {
        output.parentFile?.mkdirs()
        val temporary = File(output.parentFile, output.name + ".part")
        try {
            val result = FileOutputStream(temporary).use { raw ->
                BufferedOutputStream(raw).use { sink -> extract(member, sink) }
            }
            if (output.exists() && !output.delete()) {
                throw FmvFormatException("Cannot replace previous extracted component: ${output.name}")
            }
            if (!temporary.renameTo(output)) {
                throw FmvFormatException("Cannot finalize extracted component: ${output.name}")
            }
            return result
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    /** Stream decompressed bytes without allocating the entire component in memory. */
    fun extract(member: FmvMember, output: OutputStream): Pair<Long, Int> {
        val canonical = manifest.members.firstOrNull {
            it.name == member.name && it.offset == member.offset &&
                it.storedLength == member.storedLength && it.plainLength == member.plainLength
        } ?: throw FmvFormatException("Member does not belong to this save: ${member.name}")

        if (canonical.plainLength < 0 || canonical.plainLength > MAX_MEMBER_BYTES) {
            throw FmvFormatException("Member exceeds safe extraction limit: ${member.name}")
        }

        val startLong = HEADER_SIZE.toLong() + canonical.offset
        val endLong = startLong + canonical.storedLength
        if (startLong < HEADER_SIZE || endLong < startLong ||
            endLong > manifest.footerOffset.toLong() + 4L ||
            canonical.storedLength < 4 || endLong > bytes.size) {
            throw FmvFormatException("Member is outside data-region bounds: ${member.name}")
        }

        var cursor = startLong.toInt()
        val end = endLong.toInt()
        var written = 0L
        var frameCount = 0

        while (written < canonical.plainLength) {
            if (cursor >= end - 4) throw FmvFormatException("Missing Zstandard frame: ${member.name}")
            val remaining = bytes.copyOfRange(cursor, end)
            val frameSize = Zstd.findFrameCompressedSize(remaining)
            if (Zstd.isError(frameSize) || frameSize <= 0 || frameSize > (end - cursor - 4).toLong()) {
                throw FmvFormatException("Invalid Zstandard frame boundary: ${member.name}")
            }

            val frameEnd = cursor + frameSize.toInt()
            val frame = bytes.copyOfRange(cursor, frameEnd)
            val expected = Zstd.decompressedSize(frame)
            if (expected <= 0 || expected > MAX_MEMBER_BYTES ||
                written + expected > canonical.plainLength) {
                throw FmvFormatException("Invalid decompressed frame size in ${member.name}: $expected")
            }

            val decoded = try {
                Zstd.decompress(frame, expected.toInt())
            } catch (e: Exception) {
                throw FmvFormatException("Zstandard decompression failed for ${member.name}: ${e.message}")
            }
            if (decoded.size.toLong() != expected) {
                throw FmvFormatException("Zstandard output length mismatch in ${member.name}")
            }
            output.write(decoded)
            written += decoded.size
            cursor = frameEnd
            frameCount++

            if (written < canonical.plainLength) {
                if (cursor + 4 > end) throw FmvFormatException("Missing next-frame length prefix")
                val declaredNextSize = littleEndianU32(bytes, cursor)
                val nextStart = cursor + 4
                if (declaredNextSize <= 0 || declaredNextSize > (end - nextStart).toLong()) {
                    throw FmvFormatException("Invalid next-frame length in ${member.name}")
                }
                val actualNextSize = Zstd.findFrameCompressedSize(bytes.copyOfRange(nextStart, end))
                if (Zstd.isError(actualNextSize) || actualNextSize != declaredNextSize) {
                    throw FmvFormatException("Next-frame length prefix mismatch in ${member.name}")
                }
                cursor = nextStart
            }
        }

        if (written != canonical.plainLength || cursor != end - 4) {
            throw FmvFormatException("Member length/boundary mismatch: ${member.name}")
        }
        return written to frameCount
    }

    private fun readGameVersion(): String? {
        val member = manifest.members.firstOrNull { it.name == "game_info/.dat" } ?: return null
        val header = extract(member).take(128).toByteArray().toString(Charsets.ISO_8859_1)
        return Regex("\\\\b\\\\d{2}\\\\.\\\\d+\\\\.\\\\d+-\\\\d+\\\\b").find(header)?.value
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
        if (compressedLength <= 0 || compressedLength > Int.MAX_VALUE ||
            dataAt.toLong() + compressedLength != bytes.size.toLong()) {
            throw FmvFormatException("Footer compressed length does not match file end")
        }
        val frame = bytes.copyOfRange(dataAt, bytes.size)
        if (!frame.copyOfRange(0, 4).contentEquals(ZSTD_MAGIC)) {
            throw FmvFormatException("Footer manifest is not a Zstandard frame")
        }
        val size = Zstd.decompressedSize(frame)
        if (size <= 0 || size > MAX_MANIFEST_BYTES) {
            throw FmvFormatException("Invalid manifest size: $size")
        }
        val plain = try {
            Zstd.decompress(frame, size.toInt())
        } catch (e: Exception) {
            throw FmvFormatException("Manifest decompression failed: ${e.message}")
        }
        return parseManifest(plain, footer)
    }

    private fun parseManifest(data: ByteArray, footer: Int): FmvManifest {
        val reader = BinaryReader(data)
        val saveName = reader.string()
        val topCount = reader.u32().checkedCount()
        val top = List(topCount) { reader.member(null) }
        val archiveCount = reader.u32().also {
            if (it > 1_000) throw FmvFormatException("Manifest archive count is invalid")
        }.toInt()
        val nested = ArrayList<FmvMember>()
        repeat(archiveCount) {
            val archiveName = reader.string()
            val childCount = reader.u32().checkedCount()
            if (nested.size.toLong() + childCount > MAX_MEMBERS) {
                throw FmvFormatException("Total manifest member count is invalid")
            }
            repeat(childCount) { nested += reader.member(archiveName) }
        }
        if (reader.u32() != 0L || reader.remaining() != 0) {
            throw FmvFormatException("Manifest terminator or trailing data is invalid")
        }
        return FmvManifest(saveName, top, nested, footer)
    }

    private fun validateManifestBounds() {
        if (manifest.saveName.isBlank() || manifest.members.isEmpty() ||
            manifest.members.size > MAX_MEMBERS) {
            throw FmvFormatException("Manifest metadata is invalid")
        }
        val sorted = manifest.members.sortedBy { it.offset }
        var previousEnd = 0L
        for (member in sorted) {
            if (member.offset < previousEnd || member.storedLength < 4 ||
                member.plainLength < 0 || member.plainLength > MAX_MEMBER_BYTES) {
                throw FmvFormatException("Invalid or overlapping component: ${member.name}")
            }
            val end = member.offset + member.storedLength
            if (end < member.offset || HEADER_SIZE.toLong() + end > manifest.footerOffset.toLong() + 4L) {
                throw FmvFormatException("Component points outside the data region: ${member.name}")
            }
            previousEnd = end
        }
    }

    private fun Long.checkedCount(): Int {
        if (this < 0 || this > MAX_MEMBERS) throw FmvFormatException("Manifest member count is invalid")
        return toInt()
    }

    private class BinaryReader(private val data: ByteArray) {
        private var p = 0
        fun remaining(): Int = data.size - p
        fun bytes(n: Int): ByteArray {
            if (n < 0 || p.toLong() + n > data.size) throw FmvFormatException("Manifest is truncated")
            return data.copyOfRange(p, p + n).also { p += n }
        }
        fun u32(): Long {
            if (p + 4 > data.size) throw FmvFormatException("Manifest is truncated")
            return ByteBuffer.wrap(bytes(4)).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
        }
        private fun u64(): Long {
            if (p + 8 > data.size) throw FmvFormatException("Manifest is truncated")
            val value = ByteBuffer.wrap(bytes(8)).order(ByteOrder.LITTLE_ENDIAN).long
            if (value < 0) throw FmvFormatException("Unsigned manifest value exceeds supported range")
            return value
        }
        fun string(): String {
            val n = u32()
            if (n > 1_048_576) throw FmvFormatException("Manifest string too large")
            return bytes(n.toInt()).toString(Charsets.UTF_8)
        }
        fun member(archive: String?): FmvMember {
            val parts = ArrayList<String>()
            repeat(32) {
                val part = string()
                parts += part
                if (part.startsWith(".")) {
                    val offset = u64()
                    val stored = u64()
                    val plain = u64()
                    val stamps = bytes(16)
                    return FmvMember(parts.joinToString("/"), offset, stored, plain,
                        stamps.copyOfRange(0, 8), stamps.copyOfRange(8, 16), archive)
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

data class TadHeaderInfo(val signature: String, val versionBytes: String, val preambleHex: String)

object TadInspector {
    fun inspect(bytes: ByteArray): TadHeaderInfo? {
        if (bytes.size < 12 || bytes[0] != 0x03.toByte() || bytes[1] != 0x01.toByte() ||
            String(bytes, 2, 4, Charsets.US_ASCII) != "tad.") return null
        return TadHeaderInfo(
            signature = "tad.",
            versionBytes = "03 01",
            preambleHex = bytes.take(48).joinToString(" ") { "%02x".format(it.toInt() and 255) }
        )
    }
}
