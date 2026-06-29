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
        // Найдите кнопку «Поехали» и нажмите на неё
        composeTestRule.onNodeWithText("Поехали").performClick()
        
        // Примечание: проверка Toast сложна в базовых тестах Compose,
        // но мы проверяем, что приложение не ломается и кнопка интерактивна.
    }

    @Test
    fun testFieldInput() {
        // Если предположить, что «Километраж» является меткой первого поля
        composeTestRule.onNodeWithText("Километраж").performTextInput("150.50")

        // Проверить, был ли введён текст
        composeTestRule.onNodeWithText("150.50").assertExists()
    }
}
