package com.morepp.fm21touchsaveeditor

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.luben.zstd.Zstd
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun zstandardNativeRuntimeCompressesAndDecompressesOnDevice() {
        val payload = ByteArray(64 * 1024) { (it % 239).toByte() }
        val compressed = Zstd.compress(payload, 3)
        assertArrayEquals(payload, Zstd.decompress(compressed, payload.size))
    }

    @Test
    fun launchesAndShowsSaveEditorHomeOnApi26Plus() {
        composeRule.onNodeWithText("FM21 Touch Save Editor").assertExists()
        composeRule.onNodeWithText("Buka save FMV").assertExists()
        composeRule.onNodeWithText("FULL DATABASE EDITOR").assertExists()
    }
}
