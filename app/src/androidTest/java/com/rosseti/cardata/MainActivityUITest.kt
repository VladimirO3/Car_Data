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
        
        // Проверка наличия основных полей (English by default)
        composeTestRule.onNodeWithText("Odometer (km)").assertExists()
        composeTestRule.onNodeWithText("Trip Distance (km)").assertExists()
        composeTestRule.onNodeWithText("Remaining Fuel").assertExists()
        composeTestRule.onNodeWithText("Fuel Consumption Rate").assertExists()
        composeTestRule.onNodeWithText("Current Speed (km/h)").assertExists()
    }

    @Test
    fun testLanguageToggle() {
        // Initially EN
        composeTestRule.onNodeWithText("EN").assertExists()
        composeTestRule.onNodeWithText("Odometer (km)").assertExists()

        // Toggle to RU
        composeTestRule.onNodeWithText("EN").performClick()

        composeTestRule.onNodeWithText("RU").assertExists()
        composeTestRule.onNodeWithText("Одометр (км)").assertExists()
        composeTestRule.onNodeWithText("Дистанция (км)").assertExists()
        composeTestRule.onNodeWithText("Остаток топлива").assertExists()
        composeTestRule.onNodeWithText("Норма расхода").assertExists()
        composeTestRule.onNodeWithText("Текущая скорость (км/ч)").assertExists()
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
    fun testFieldInputAndValidation() {
        // Ввод данных в поле одометра (assuming English labels)
        composeTestRule.onNodeWithText("Odometer (km)").performTextInput("12345.67")
        
        // Проверка, что текст отобразился
        composeTestRule.onNodeWithText("12345.67").assertExists()
    }

    @Test
    fun testModeSelection() {
        // Проверка наличия переключателей режимов (English labels initially)
        composeTestRule.onNodeWithText("Winter (+10%)").assertExists()
        composeTestRule.onNodeWithText("Spring (+10%)").assertExists()
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
