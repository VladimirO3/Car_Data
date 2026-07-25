/**
 * © 2026 Osetrov V.V. Все права защищены.
 */
package com.rosseti.cardata

import android.app.Application
import com.my.tracker.MyTracker
import com.my.tracker.MyTrackerConfig.LocationTrackingMode
import ru.ok.tracer.Tracer

class TrackLitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Включить отслеживание местоположения
        MyTracker.getTrackerConfig().setLocationTrackingMode(LocationTrackingMode.ACTIVE)
        
        // Инициализация MyTracker
        // Замените "YOUR_SDK_KEY" на реальный ключ из панели управления MyTracker
        MyTracker.initTracker("83626786657934759685", this)

        // Tracer инициализируется автоматически через плагин, 
        // но здесь можно добавить кастомную конфигурацию или логи
        // Tracer.common.log("Application started")
    }
}
