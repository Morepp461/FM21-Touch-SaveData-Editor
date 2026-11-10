package com.morepp.fm21touchsaveeditor.core

/**
 * A guarded, same-length binary edit transaction.
 *
 * This is deliberately format-agnostic: callers must supply offsets and expected
 * original bytes obtained from a separately verified FM21 record decoder.
 * No FMV component is writable merely because this class exists.
 */
data class BinaryPatch(
    val offset: Int,
    val expected: ByteArray,
    val replacement: ByteArray,
    val description: String
)

data class EditSnapshot(
    val bytes: ByteArray,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val revision: Long
)

class BinaryEditSession(source: ByteArray) {
    private val original = source.copyOf()
    private var working = source.copyOf()
    private val undoStack = ArrayDeque<ByteArray>()
    private val redoStack = ArrayDeque<ByteArray>()
    private var revisionNumber = 0L

    val size: Int get() = working.size
    val revision: Long get() = revisionNumber
    val hasChanges: Boolean get() = !working.contentEquals(original)

    fun snapshot(): EditSnapshot = EditSnapshot(
        bytes = working.copyOf(),
        canUndo = undoStack.isNotEmpty(),
        canRedo = redoStack.isNotEmpty(),
        revision = revisionNumber
    )

    /**
     * Validates every patch against the same pre-transaction state, then commits
     * all patches together. Any invalid patch rejects the entire transaction.
     */
    @Synchronized
    fun apply(patches: List<BinaryPatch>) {
        require(patches.isNotEmpty()) { "At least one patch is required" }
        validatePatches(patches, working.size)

        patches.forEach { patch ->
            val start = patch.offset
            val end = start + patch.expected.size
            val actual = working.copyOfRange(start, end)
            require(actual.contentEquals(patch.expected)) {
                "Original-byte guard failed for: ${patch.description}"
            }
        }

        val next = working.copyOf()
        patches.forEach { patch ->
            patch.replacement.copyInto(next, destinationOffset = patch.offset)
        }

        undoStack.addLast(working.copyOf())
        working = next
        redoStack.clear()
        revisionNumber++
    }

    @Synchronized
    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        redoStack.addLast(working.copyOf())
        working = undoStack.removeLast()
        revisionNumber++
        return true
    }

    @Synchronized
    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        undoStack.addLast(working.copyOf())
        working = redoStack.removeLast()
        revisionNumber++
        return true
    }

    @Synchronized
    fun reset() {
        if (working.contentEquals(original)) return
        undoStack.addLast(working.copyOf())
        working = original.copyOf()
        redoStack.clear()
        revisionNumber++
    }

    private fun validatePatches(patches: List<BinaryPatch>, byteCount: Int) {
        val ordered = patches.sortedBy { it.offset }
        var previousEnd = -1
        ordered.forEach { patch ->
            require(patch.offset >= 0) { "Patch offset cannot be negative" }
            require(patch.expected.isNotEmpty()) { "Patch cannot be empty: ${patch.description}" }
            require(patch.expected.size == patch.replacement.size) {
                "Patch must preserve component length: ${patch.description}"
            }
            val end = patch.offset.toLong() + patch.expected.size.toLong()
            require(end <= byteCount.toLong()) { "Patch exceeds component bounds: ${patch.description}" }
            require(patch.offset >= previousEnd) { "Overlapping patches are not allowed" }
            previousEnd = end.toInt()
        }
    }
}
