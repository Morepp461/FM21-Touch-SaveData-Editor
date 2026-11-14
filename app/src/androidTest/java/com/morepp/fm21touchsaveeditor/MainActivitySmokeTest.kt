package com.morepp.fm21touchsaveeditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenLaunchesAndShowsSavePicker() {
        composeRule.onNodeWithText("Editor Save FM21 Touch").assertIsDisplayed()
        composeRule.onNodeWithText("Buka save FMV").assertIsDisplayed()
        composeRule.onNodeWithText("Minimum sistem: Android 8.0 (API 26)").assertIsDisplayed()
    }
}
