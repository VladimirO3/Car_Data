package com.rosseti.cardata

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rosseti.cardata.data.SettingsRepository

class LocationService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var repository: SettingsRepository
    private var lastLocation: Location? = null

    override fun onCreate() {
        super.onCreate()
        AppLogger.d(this, "Service onCreate - Инициализация службы")
        repository = SettingsRepository(applicationContext)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    if (location.accuracy > 50) {
                        AppLogger.d(applicationContext, "Пропуск неточной локации: accuracy=${location.accuracy}")
                        continue
                    }

                    val last = lastLocation
                    if (last == null) {
                        lastLocation = location
                        AppLogger.d(applicationContext, "Первая точка GPS получена: lat=${location.latitude}, lon=${location.longitude}")
                    } else {
                        val distance = last.distanceTo(location)
                        
                        // Рассчитываем скорость: приоритет датчику, иначе считаем по времени и расстоянию
                        val speedMps = if (location.hasSpeed()) {
                            location.speed
                        } else {
                            val timeDelta = (location.time - last.time) / 1000f
                            if (timeDelta > 0.5f) distance / timeDelta else 0f
                        }
                        
                        val speedKmh = speedMps * 3.6f
                        
                        // Сохраняем мгновенную скорость
                        repository.saveCurrentSpeed(speedKmh)
                        
                        // Обновляем максимальную скорость, если текущая выше (и правдоподобна)
                        val savedMax = repository.getMaxSpeed().toFloatOrNull() ?: 0f
                        if (speedKmh > savedMax && speedKmh < 250f) {
                            repository.saveMaxSpeed(String.format(java.util.Locale.US, "%.2f", speedKmh))
                            AppLogger.d(applicationContext, "Новый рекорд скорости: $speedKmh км/ч")
                        }
                        
                        // Игнорируем перемещения менее 1 метра для фильтрации дрейфа одометра
                        if (distance >= 1.0f) {
                            val currentTotal = repository.getTotalDistance()
                            val newTotal = currentTotal + distance
                            repository.saveTotalDistance(newTotal)
                            lastLocation = location
                            AppLogger.d(applicationContext, "Одометр обновлен: +$distance м, Итого: $newTotal м")
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.d(this, "Service onStartCommand - Запуск Foreground")
        val channelId = "location_channel"
        val channel = NotificationChannel(
            channelId, 
            "GPS Tracking", 
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("TrackLit GPS-трекер")
            .setContentText("Идет отслеживание рейса...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        Log.d("LocationService", "Starting foreground with type location")
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        
        startLocationUpdates()
        
        return START_STICKY
    }

    private fun startLocationUpdates() {
        Log.d("LocationService", "Requesting location updates")
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(1000)
            .setMinUpdateDistanceMeters(0f)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d("LocationService", "Location updates requested successfully")
        } catch (e: SecurityException) {
            Log.e("LocationService", "SecurityException: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
