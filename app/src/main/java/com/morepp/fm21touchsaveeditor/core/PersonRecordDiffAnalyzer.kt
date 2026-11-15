package com.morepp.fm21touchsaveeditor.core

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
    fun compare(beforeGameDb: ByteArray, afterGameDb: ByteArray): PersonSnapshotDiff {
        val before = PersonRecordMapper.map(beforeGameDb)
        val after = PersonRecordMapper.map(afterGameDb)
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
                !isRangeValid(beforeGameDb, leftOffset, leftLength) ||
                !isRangeValid(afterGameDb, rightOffset, rightLength)) {
                skipped++
                return@forEach
            }

            compared++
            val changedOffsets = ArrayList<Int>()
            for (offset in 0 until leftLength) {
                if (beforeGameDb[leftOffset + offset] != afterGameDb[rightOffset + offset]) {
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

    private fun isRangeValid(data: ByteArray, offset: Int, length: Int): Boolean =
        offset >= 0 && length > 0 && offset.toLong() + length <= data.size.toLong()
}
