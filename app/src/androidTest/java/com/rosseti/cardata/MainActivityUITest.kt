package com.rosseti.cardata

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Тесты UI для [MainActivity] v1.5 с использованием тестовых API Jetpack Compose.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityUITest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testAppTitleAndSettingsButton() {
        // Проверка заголовка
        composeTestRule.onNodeWithText("TrackLit").assertExists()
        // Проверка наличия кнопки настроек
        composeTestRule.onNodeWithContentDescription("Settings").assertExists()
    }

    @Test
    fun testSettingsNavigation() {
        // Переход в настройки
        composeTestRule.onNodeWithContentDescription("Settings").performClick()

        // Проверка заголовка настроек (в зависимости от текущего языка, но иконка назад должна быть)
        composeTestRule.onNodeWithContentDescription("Back").assertExists()
        
        // Проверка наличия пунктов меню (хотя бы одного для подтверждения экрана)
        // Мы ищем по тексту, который есть в обоих языках или по иконкам/свойствам
        composeTestRule.onNodeWithText("About Author", substring = true).assertExists()
        composeTestRule.onNodeWithText("Legal Information", substring = true).assertExists()
    }

    @Test
    fun testThemeToggle() {
        // Переход в настройки
        composeTestRule.onNodeWithContentDescription("Settings").performClick()

        // Проверка наличия переключателя темы
        composeTestRule.onNodeWithText("App Theme", substring = true).assertExists()
        
        // Клик по переключателю темы
        composeTestRule.onNodeWithText("App Theme", substring = true).performClick()
        
        // Убеждаемся, что экран все еще активен (тема сменилась)
        composeTestRule.onNodeWithText("Selected:", substring = true).assertExists()
    }

    @Test
    fun testAuthorScreenNavigation() {
        // Переход в настройки
        composeTestRule.onNodeWithContentDescription("Settings").performClick()

        // Переход к автору
        composeTestRule.onNodeWithText("About Author", substring = true).performClick()

        // Проверка контента экрана автора
        composeTestRule.onNodeWithText("Osetrov V.V.").assertExists()
        composeTestRule.onNodeWithContentDescription("GitHub").assertExists()
        
        // Возврат
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithText("Settings", substring = true).assertExists()
    }

    @Test
    fun testExitFlow() {
        // Проверка кнопки выхода на главном экране
        composeTestRule.onNodeWithContentDescription("Exit").assertExists()
        
        // Клик по кнопке выхода (должен появиться экран прощания)
        composeTestRule.onNodeWithContentDescription("Exit").performClick()
        
        // Проверка текста на экране прощания
        composeTestRule.onNodeWithText("До встречи!").assertExists()
    }

    @Test
    fun testCompassDisplay() {
        // Проверка, что на компасе отображаются градусы
        composeTestRule.onNodeWithText("°", substring = true).assertExists()
    }
}
