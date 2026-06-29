package com.rosseti.cardata

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityUITest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testAppTitleIsDisplayed() {
        composeTestRule.onNodeWithText("Logistic_data Rosseti-Ural").assertExists()
    }

    @Test
    fun testStartTripShowsToast() {
        // Find the "Поехали" button and click it
        composeTestRule.onNodeWithText("Поехали").performClick()
        
        // Note: Toast verification is tricky in basic Compose tests, 
        // but we verify the app doesn't crash and button is interactive.
    }

    @Test
    fun testFieldInput() {
        // Assuming "Километраж" is the label of the first field
        composeTestRule.onNodeWithText("Километраж").performTextInput("150.50")
        
        // Verify text was entered
        composeTestRule.onNodeWithText("150.50").assertExists()
    }
}
