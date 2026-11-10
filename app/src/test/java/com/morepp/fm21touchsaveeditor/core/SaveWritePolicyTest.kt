package com.morepp.fm21touchsaveeditor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SaveWritePolicyTest {
    @Test
    fun unverifiedContainerIsRejectedForWriting() {
        val inspection = ContainerInspection(
            formatName = "FM21 Touch",
            version = "21.4.0-0",
            components = emptyList(),
            isWritable = false
        )
        val error = assertThrows(UnsafeSaveWrite::class.java) {
            SaveWritePolicy.requireVerifiedWritable(inspection)
        }
        assertEquals("This save format has not passed write-validation.", error.message)
    }

    @Test
    fun verifiedContainerPassesWriteGate() {
        val inspection = ContainerInspection(
            formatName = "FM21 Touch",
            version = "21.4.0-0",
            components = emptyList(),
            isWritable = true
        )
        SaveWritePolicy.requireVerifiedWritable(inspection)
    }
}
