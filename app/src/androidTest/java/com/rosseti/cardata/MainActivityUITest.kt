package com.rosseti.cardata

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Тесты UI для [MainActivity] с использованием тестовых API Jetpack Compose.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityUITest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testAppTitleAndStatsAreDisplayed() {
        // Проверка заголовка
        composeTestRule.onNodeWithText("TrackLit").assertExists()
        
        // Проверка наличия основных полей (English by default, labels are Uppercase in Gauge fields)
        composeTestRule.onNodeWithText("ODOMETER (KM)").assertExists()
        composeTestRule.onNodeWithText("TRIP DISTANCE (KM)").assertExists()
        composeTestRule.onNodeWithText("REMAINING FUEL").assertExists()
        composeTestRule.onNodeWithText("FUEL CONSUMPTION RATE").assertExists()
        composeTestRule.onNodeWithText("CURRENT SPEED (KM/H)").assertExists()
    }

    @Test
    fun testLanguageToggle() {
        // Initially EN
        composeTestRule.onNodeWithText("EN").assertExists()
        composeTestRule.onNodeWithText("ODOMETER (KM)").assertExists()

        // Toggle to RU
        composeTestRule.onNodeWithText("EN").performClick()

        composeTestRule.onNodeWithText("RU").assertExists()
        composeTestRule.onNodeWithText("ОДОМЕТР (КМ)").assertExists()
        composeTestRule.onNodeWithText("ДИСТАНЦИЯ (КМ)").assertExists()
        composeTestRule.onNodeWithText("ОСТАТОК ТОПЛИВА").assertExists()
        composeTestRule.onNodeWithText("НОРМА РАСХОДА").assertExists()
        composeTestRule.onNodeWithText("ТЕКУЩАЯ СКОРОСТЬ (КМ/Ч)").assertExists()
    }

    @Test
    fun testHistoryScreenNavigationAndClear() {
        // Click on History icon
        composeTestRule.onNodeWithContentDescription("Trip History").performClick()

        // Check if History screen is displayed
        composeTestRule.onNodeWithText("Trip History").assertExists()
        
        // Go back
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        
        // Check if Main screen is displayed again
        composeTestRule.onNodeWithText("TrackLit").assertExists()
    }

    @Test
    fun testInstructionScreenNavigation() {
        // Click on Info icon
        composeTestRule.onNodeWithContentDescription("Instructions").performClick()

        // Check if Instructions screen is displayed
        composeTestRule.onNodeWithText("Instructions").assertExists()
        
        // Check for some instruction item
        composeTestRule.onNodeWithText("Preparation").assertExists()
        
        // Go back
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        
        // Check if Main screen is displayed again
        composeTestRule.onNodeWithText("TrackLit").assertExists()
    }

    @Test
    fun testTripStartedMarkerVisibility() {
        // Изначально маркера нет (в верхнем баре)
        composeTestRule.onNodeWithText("● TRIP").assertDoesNotExist()

        // Кнопка Старт должна существовать
        composeTestRule.onNodeWithText("Start").assertExists()
    }

    @Test
    fun testIconsExistence() {
        // Проверка наличия иконок в верхней панели
        composeTestRule.onNodeWithContentDescription("Exit").assertExists()
        composeTestRule.onNodeWithContentDescription("Send Coordinates").assertExists()
        composeTestRule.onNodeWithContentDescription("Trip History").assertExists()
        composeTestRule.onNodeWithContentDescription("Instructions").assertExists()
    }

    @Test
    fun testCompassDegreeDisplay() {
        // Проверка, что на компасе отображаются градусы (символ °)
        composeTestRule.onNodeWithText("0°", substring = true).assertExists()
    }
}
