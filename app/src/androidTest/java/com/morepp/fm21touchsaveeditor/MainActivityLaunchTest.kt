package com.morepp.fm21touchsaveeditor

import androidx.compose.ui.test.assertIsDisplayed
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
        composeRule.onNodeWithText("Editor Save FM21 Touch").assertIsDisplayed()
        composeRule.onNodeWithText("Buka save FMV").assertExists()
        composeRule.onNodeWithText("FOOTBALL MANAGEMENT SAVE DATA").assertIsDisplayed()
        composeRule.onNodeWithText("MODE HANYA-BACA").assertIsDisplayed()
        composeRule.onNodeWithText("Minimum sistem: Android 8.0 (API 26)").assertIsDisplayed()
        composeRule.onNodeWithText("Batas ukuran file saat ini: 128 MiB").assertIsDisplayed()
    }
}
