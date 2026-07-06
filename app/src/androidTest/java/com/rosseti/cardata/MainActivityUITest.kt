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
        
        // Проверка наличия основных полей (используем новые названия)
        composeTestRule.onNodeWithText("Спидометр(км)").assertExists()
        composeTestRule.onNodeWithText("Остаток топлива").assertExists()
        composeTestRule.onNodeWithText("Норма расхода топлива").assertExists()
        composeTestRule.onNodeWithText("Макс. скорость (км/ч)").assertExists()
    }

    @Test
    fun testFieldInputAndValidation() {
        // Ввод данных в поле спидометра
        composeTestRule.onNodeWithText("Спидометр(км)").performTextInput("12345.67")
        
        // Проверка, что текст отобразился
        composeTestRule.onNodeWithText("12345.67").assertExists()
    }

    @Test
    fun testModeSelection() {
        // Проверка наличия переключателей режимов
        composeTestRule.onNodeWithText("Зима (+10%)").assertExists()
        composeTestRule.onNodeWithText("Весна (+10%)").assertExists()
    }

    @Test
    fun testTripStartedMarkerVisibility() {
        // Изначально маркера нет (в левом верхнем углу)
        composeTestRule.onNodeWithText("● РЕЙС").assertDoesNotExist()

        // Кнопка Старт должна существовать
        composeTestRule.onNodeWithText("Старт").assertExists()
    }

    @Test
    fun testLogoAndExitIconExistence() {
        // Проверка наличия логотипа (по описанию, если добавлено)
        composeTestRule.onNodeWithContentDescription("Logo").assertExists()
        // Проверка наличия иконки выхода
        composeTestRule.onNodeWithContentDescription("Выход").assertExists()
    }
}
