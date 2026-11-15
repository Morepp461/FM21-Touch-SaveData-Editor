package com.morepp.fm21touchsaveeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TemporaryCacheCleanupTest {
    @Test
    fun deletesOnlyStaleAppOwnedFiles() {
        val root = Files.createTempDirectory("fm21-cache-cleanup").toFile()
        val now = 1_000_000L
        val staleInput = makeFile(root, "fm21-input-stale.part", now - 101)
        val freshInput = makeFile(root, "fm21-input-fresh.part", now - 99)
        val unrelated = makeFile(root, "user-document.bin", now - 10_000)
        val components = File(root, "fm21_components").apply { mkdirs() }
        val staleComponent = makeFile(components, "game_db_person_map.dat", now - 101)
        val freshComponent = makeFile(components, "player_export.dat", now - 99)
        val diff = File(root, "fm21_person_diff").apply { mkdirs() }
        val staleDiff = makeFile(diff, "before_game_db.dat", now - 101)

        try {
            cleanupStaleTemporaryFiles(root, nowMillis = now, maxAgeMillis = 100)

            assertEquals(false, staleInput.exists())
            assertEquals(true, freshInput.exists())
            assertEquals(true, unrelated.exists())
            assertEquals(false, staleComponent.exists())
            assertEquals(true, freshComponent.exists())
            assertEquals(false, staleDiff.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsNegativeCacheAge() {
        val root = Files.createTempDirectory("fm21-cache-cleanup").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                cleanupStaleTemporaryFiles(root, nowMillis = 1_000_000L, maxAgeMillis = -1)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun makeFile(parent: File, name: String, modifiedAt: Long): File =
        File(parent, name).apply {
            writeText("temporary test payload")
            check(setLastModified(modifiedAt))
        }
}
