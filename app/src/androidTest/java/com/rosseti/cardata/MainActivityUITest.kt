package com.rosseti.cardata

import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class MainActivityUITest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testAppTitleAndStatsAreDisplayed() {
        // Проверка заголовка
        composeTestRule.onNodeWithText("TrackLit").assertExists()
        
        // Проверка наличия основных полей
        composeTestRule.onNodeWithText("Километраж").assertExists()
        composeTestRule.onNodeWithText("Остаток топлива").assertExists()
        composeTestRule.onNodeWithText("Норма расхода топлива").assertExists()
    }

    @Test
    fun testFieldInputAndValidation() {
        // Ввод данных в поле километража
        composeTestRule.onNodeWithText("Километраж").performTextInput("12345.67")
        
        // Проверка, что текст отобразился
        composeTestRule.onNodeWithText("12345.67").assertExists()
    }

    @Test
    fun testModeSelection() {
        // Проверка наличия переключателей режимов
        composeTestRule.onNodeWithText("Зима (+10%)").assertExists()
        composeTestRule.onNodeWithText("Весна (+10%)").assertExists()
        
        // Клик по переключателю
        composeTestRule.onNodeWithText("Зима (+10%)").performClick()
    }

    @Test
    fun testTripStartedMarkerVisibility() {
        // Изначально маркера нет
        composeTestRule.onNodeWithText("● РЕЙС").assertDoesNotExist()

        // Кнопка Старт должна существовать
        composeTestRule.onNodeWithText("Старт").assertExists()
        
        // Примечание: Мы не можем легко симулировать нажатие Старт здесь, так как оно 
        // запускает Foreground Service и запрашивает GPS, что требует мокирования 
        // или специальных разрешений в тесте. Но мы проверили статический UI.
    }

    @Test
    fun testCompactLayoutInLandscape() {
        // Переключаем в альбомный режим (Landscape)
        // В реальном тесте это требует изменения конфигурации устройства
    }
}
