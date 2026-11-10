package com.morepp.fm21touchsaveeditor.core

import org.junit.Assert.*
import org.junit.Test

class BinaryEditSessionTest {
    @Test
    fun appliesMultiplePatchesAsOneTransaction() {
        val session = BinaryEditSession(byteArrayOf(1, 2, 3, 4))
        session.apply(listOf(
            BinaryPatch(0, byteArrayOf(1), byteArrayOf(9), "first"),
            BinaryPatch(2, byteArrayOf(3, 4), byteArrayOf(7, 8), "second")
        ))
        assertArrayEquals(byteArrayOf(9, 2, 7, 8), session.snapshot().bytes)
        assertTrue(session.hasChanges)
        assertEquals(1L, session.revision)
    }

    @Test
    fun failedGuardDoesNotPartiallyCommit() {
        val session = BinaryEditSession(byteArrayOf(1, 2, 3))
        assertThrows(IllegalArgumentException::class.java) {
            session.apply(listOf(
                BinaryPatch(0, byteArrayOf(1), byteArrayOf(9), "valid"),
                BinaryPatch(2, byteArrayOf(0), byteArrayOf(8), "stale")
            ))
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), session.snapshot().bytes)
        assertFalse(session.snapshot().canUndo)
        assertEquals(0L, session.revision)
    }

    @Test
    fun rejectsOverlappingAndLengthChangingPatches() {
        val session = BinaryEditSession(byteArrayOf(1, 2, 3, 4))
        assertThrows(IllegalArgumentException::class.java) {
            session.apply(listOf(
                BinaryPatch(0, byteArrayOf(1, 2), byteArrayOf(8, 8), "a"),
                BinaryPatch(1, byteArrayOf(2), byteArrayOf(9), "b")
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            session.apply(listOf(BinaryPatch(0, byteArrayOf(1), byteArrayOf(8, 9), "grow")))
        }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), session.snapshot().bytes)
    }

    @Test
    fun undoRedoAndResetRestoreExactBytes() {
        val original = byteArrayOf(10, 20, 30)
        val session = BinaryEditSession(original)
        original[0] = 99 // constructor must own its copy
        session.apply(listOf(BinaryPatch(1, byteArrayOf(20), byteArrayOf(77), "edit")))
        assertTrue(session.undo())
        assertArrayEquals(byteArrayOf(10, 20, 30), session.snapshot().bytes)
        assertTrue(session.redo())
        assertArrayEquals(byteArrayOf(10, 77, 30), session.snapshot().bytes)
        session.reset()
        assertArrayEquals(byteArrayOf(10, 20, 30), session.snapshot().bytes)
        assertFalse(session.hasChanges)
    }

    @Test
    fun snapshotsCannotMutateSessionState() {
        val session = BinaryEditSession(byteArrayOf(1, 2))
        val snapshot = session.snapshot()
        snapshot.bytes[0] = 99
        assertArrayEquals(byteArrayOf(1, 2), session.snapshot().bytes)
    }
}
