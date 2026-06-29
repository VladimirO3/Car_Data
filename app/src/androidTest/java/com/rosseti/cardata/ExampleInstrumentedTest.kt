package com.rosseti.cardata

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Инструментальный тест, который будет выполняться на устройстве Android.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Контекст тестируемого приложения.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.rosseti.cardata", appContext.packageName)
    }
}