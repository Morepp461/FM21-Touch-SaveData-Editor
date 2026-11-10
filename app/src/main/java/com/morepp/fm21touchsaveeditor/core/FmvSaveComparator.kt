package com.morepp.fm21touchsaveeditor.core

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class MemberDiff(
    val name: String,
    val beforeBytes: Long?,
    val afterBytes: Long?,
    val identical: Boolean,
    val changedBytesInCommonLength: Long,
    val firstDifferenceOffset: Long?,
    val beforeSha256: String?,
    val afterSha256: String?
)

object FmvSaveComparator {
    /**
     * Read-only comparison. Each component is streamed to temporary files so
     * large database members are never loaded into memory together.
     */
    fun compare(
        before: FmvContainer,
        after: FmvContainer,
        workDir: File,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<MemberDiff> {
        workDir.mkdirs()
        val beforeMembers = before.manifest.members.associateBy { it.name }
        val afterMembers = after.manifest.members.associateBy { it.name }
        val names = (beforeMembers.keys + afterMembers.keys).toSortedSet()
        val results = ArrayList<MemberDiff>(names.size)

        names.forEachIndexed { index, name ->
            val a = beforeMembers[name]
            val b = afterMembers[name]
            if (a == null || b == null) {
                results += MemberDiff(
                    name, a?.plainLength, b?.plainLength, false, 0L, null, null, null
                )
            } else {
                val key = sha256(name.toByteArray()).take(20)
                val aFile = File(workDir, "before_$key.bin")
                val bFile = File(workDir, "after_$key.bin")
                try {
                    before.extractToFile(a, aFile)
                    after.extractToFile(b, bFile)
                    val aHash = sha256(aFile)
                    val bHash = sha256(bFile)
                    if (aHash == bHash && aFile.length() == bFile.length()) {
                        results += MemberDiff(
                            name, aFile.length(), bFile.length(), true, 0L, null, aHash, bHash
                        )
                    } else {
                        val (changed, first) = compareCommonPrefix(aFile, bFile)
                        results += MemberDiff(
                            name, aFile.length(), bFile.length(), false,
                            changed, first, aHash, bHash
                        )
                    }
                } finally {
                    aFile.delete()
                    bFile.delete()
                }
            }
            onProgress(index + 1, names.size)
        }
        return results
    }

    private fun compareCommonPrefix(a: File, b: File): Pair<Long, Long?> {
        val common = minOf(a.length(), b.length())
        val left = ByteArray(64 * 1024)
        val right = ByteArray(64 * 1024)
        var compared = 0L
        var changed = 0L
        var first: Long? = null
        BufferedInputStream(FileInputStream(a)).use { inputA ->
            BufferedInputStream(FileInputStream(b)).use { inputB ->
                while (compared < common) {
                    val wanted = minOf(left.size.toLong(), common - compared).toInt()
                    val readA = readFully(inputA, left, wanted)
                    val readB = readFully(inputB, right, wanted)
                    if (readA != wanted || readB != wanted) {
                        throw IllegalStateException("Unexpected EOF while comparing extracted components")
                    }
                    for (i in 0 until wanted) {
                        if (left[i] != right[i]) {
                            changed++
                            if (first == null) first = compared + i
                        }
                    }
                    compared += wanted
                }
            }
        }
        return changed to first
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray, count: Int): Int {
        var total = 0
        while (total < count) {
            val read = input.read(buffer, total, count - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file)).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
