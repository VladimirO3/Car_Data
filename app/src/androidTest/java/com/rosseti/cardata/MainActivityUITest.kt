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
        composeTestRule.onNodeWithText("Speedometer (km)").assertExists()
        composeTestRule.onNodeWithText("Trip Distance (km)").assertExists()
        composeTestRule.onNodeWithText("Remaining Fuel").assertExists()
        composeTestRule.onNodeWithText("Fuel Consumption Rate").assertExists()
        composeTestRule.onNodeWithText("Max Speed (km/h)").assertExists()
    }

    @Test
    fun testLanguageToggle() {
        // Initially EN
        composeTestRule.onNodeWithText("EN").assertExists()
        composeTestRule.onNodeWithText("Speedometer (km)").assertExists()

        // Toggle to RU
        composeTestRule.onNodeWithText("EN").performClick()

        composeTestRule.onNodeWithText("RU").assertExists()
        composeTestRule.onNodeWithText("Спидометр (км)").assertExists()
        composeTestRule.onNodeWithText("Дистанция (км)").assertExists()
        composeTestRule.onNodeWithText("Остаток топлива").assertExists()
        composeTestRule.onNodeWithText("Норма расхода").assertExists()
        composeTestRule.onNodeWithText("Макс. скорость (км/ч)").assertExists()
    }

    @Test
    fun testFieldInputAndValidation() {
        // Ввод данных в поле спидометра (assuming English labels)
        composeTestRule.onNodeWithText("Speedometer (km)").performTextInput("12345.67")
        
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
        // Изначально маркера нет (в левом верхнем углу)
        composeTestRule.onNodeWithText("● TRIP").assertDoesNotExist()

        // Кнопка Старт должна существовать
        composeTestRule.onNodeWithText("Start").assertExists()
    }

    @Test
    fun testLogoAndExitIconExistence() {
        // Проверка наличия логотипа (по описанию)
        composeTestRule.onNodeWithContentDescription("Logo").assertExists()
        // Проверка наличия иконки выхода
        composeTestRule.onNodeWithContentDescription("Exit").assertExists()
    }

    @Test
    fun testShareLocationIconExistence() {
        // Проверка наличия иконки отправки координат
        composeTestRule.onNodeWithContentDescription("Send Coordinates").assertExists()
    }
}
