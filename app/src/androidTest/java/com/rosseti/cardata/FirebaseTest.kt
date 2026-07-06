package com.rosseti.cardata

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Инструментальный тест для проверки инициализации Firebase.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseTest {

    @Test
    fun testFirebaseInitialization() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Попытка получить инстанс FirebaseApp. 
        // Если google-services.json отсутствует или настроен неверно, это может вызвать исключение.
        val firebaseApp = FirebaseApp.initializeApp(context)
        
        assertNotNull("FirebaseApp should be initialized", FirebaseApp.getInstance())
        assertTrue("Firebase should be initialized with the correct project ID", 
            FirebaseApp.getInstance().options.projectId?.isNotEmpty() ?: false)
    }
}
