package com.morepp.fm21touchsaveeditor.core

import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Cross-snapshot comparison of mapped person entity rows.
 *
 * Rows are joined by the observed stable UID/entity ID mapping, never by raw
 * byte offset. Changed offsets are reported as opaque evidence only; this
 * class deliberately does not label any byte as CA, PA, an attribute, or
 * another semantic field.
 */
data class PersonRowChange(
    val name: String,
    val uniqueId: Long,
    val entityRecordId: Long,
    val beforeOffset: Int,
    val afterOffset: Int,
    val recordLength: Int,
    val changedByteOffsets: List<Int>,
    val beforeTypeTag: String?,
    val afterTypeTag: String?
)

data class PersonSnapshotDiff(
    val beforeMapped: Int,
    val afterMapped: Int,
    val compared: Int,
    val changed: List<PersonRowChange>,
    val unchanged: Int,
    val missingBefore: Int,
    val missingAfter: Int,
    val skippedUnknownLength: Int
)

object PersonRecordDiffAnalyzer {
    fun compare(beforeGameDb: ByteArray, afterGameDb: ByteArray): PersonSnapshotDiff =
        compare(ByteBuffer.wrap(beforeGameDb), ByteBuffer.wrap(afterGameDb))

    /**
     * File-backed variant for Android: memory maps the extracted database
     * components instead of allocating two additional 69 MB byte arrays.
     */
    fun compareFiles(beforeFile: File, afterFile: File): PersonSnapshotDiff {
        FileChannel.open(beforeFile.toPath(), StandardOpenOption.READ).use { beforeChannel ->
            FileChannel.open(afterFile.toPath(), StandardOpenOption.READ).use { afterChannel ->
                require(beforeChannel.size() <= Int.MAX_VALUE && afterChannel.size() <= Int.MAX_VALUE) {
                    "Database component exceeds the supported mapped-buffer size"
                }
                val before = beforeChannel.map(FileChannel.MapMode.READ_ONLY, 0, beforeChannel.size())
                val after = afterChannel.map(FileChannel.MapMode.READ_ONLY, 0, afterChannel.size())
                return compare(before, after)
            }
        }
    }

    fun compare(beforeGameDb: ByteBuffer, afterGameDb: ByteBuffer): PersonSnapshotDiff {
        val beforeData = beforeGameDb.asReadOnlyBuffer()
        val afterData = afterGameDb.asReadOnlyBuffer()
        val before = PersonRecordMapper.map(beforeData)
        val after = PersonRecordMapper.map(afterData)
        val beforeByUid = before.entries.filter { it.isMapped }.associateBy { it.uniqueId }
        val afterByUid = after.entries.filter { it.isMapped }.associateBy { it.uniqueId }

        var compared = 0
        var unchanged = 0
        var skipped = 0
        val changes = ArrayList<PersonRowChange>()

        beforeByUid.forEach { (uid, left) ->
            val right = afterByUid[uid] ?: return@forEach
            if (left.entityRecordId != right.entityRecordId) return@forEach
            val leftOffset = left.entityRecordOffset ?: return@forEach
            val rightOffset = right.entityRecordOffset ?: return@forEach
            val leftLength = left.entityRecordLength
            val rightLength = right.entityRecordLength
            if (leftLength == null || rightLength == null || leftLength != rightLength ||
                !isRangeValid(beforeData, leftOffset, leftLength) ||
                !isRangeValid(afterData, rightOffset, rightLength)) {
                skipped++
                return@forEach
            }

            compared++
            val changedOffsets = ArrayList<Int>()
            for (offset in 0 until leftLength) {
                if (beforeData.get(leftOffset + offset) != afterData.get(rightOffset + offset)) {
                    changedOffsets += offset
                }
            }
            if (changedOffsets.isEmpty()) {
                unchanged++
            } else {
                changes += PersonRowChange(
                    name = right.name,
                    uniqueId = uid,
                    entityRecordId = right.entityRecordId!!,
                    beforeOffset = leftOffset,
                    afterOffset = rightOffset,
                    recordLength = leftLength,
                    changedByteOffsets = changedOffsets,
                    beforeTypeTag = left.entityRecordTypeTag,
                    afterTypeTag = right.entityRecordTypeTag
                )
            }
        }

        return PersonSnapshotDiff(
            beforeMapped = before.mappedEntries,
            afterMapped = after.mappedEntries,
            compared = compared,
            changed = changes.sortedBy { it.name.lowercase() },
            unchanged = unchanged,
            missingBefore = (afterByUid.keys - beforeByUid.keys).size,
            missingAfter = (beforeByUid.keys - afterByUid.keys).size,
            skippedUnknownLength = skipped
        )
    }

    private fun isRangeValid(data: ByteBuffer, offset: Int, length: Int): Boolean =
        offset >= 0 && length > 0 && offset.toLong() + length <= data.limit().toLong()
}
