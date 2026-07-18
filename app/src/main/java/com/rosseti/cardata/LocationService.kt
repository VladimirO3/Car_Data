/**
 * © 2026 Osetrov V.V. Все права защищены.
 */
package com.rosseti.cardata

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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

class LocationService : Service(), SensorEventListener {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var repository: SettingsRepository
    private var lastLocation: Location? = null
    
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var currentHeading: Float = 0f

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "location_channel"
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.d(this, "Service onCreate - Инициализация службы")
        
        repository = SettingsRepository(applicationContext)
        
        // Инициализация датчиков для компаса в уведомлении
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        
        // Срочный запуск Foreground, чтобы избежать ForegroundServiceDidNotStartInTimeException
        startForegroundServiceSafe()
        
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
                        
                        // Рассчитываем скорость
                        val rawSpeedMps = if (location.hasSpeed()) {
                            location.speed
                        } else {
                            val timeDelta = (location.time - last.time) / 1000f
                            if (timeDelta > 0.5f) distance / timeDelta else 0f
                        }
                        
                        val speedKmh = if (rawSpeedMps < 0.5f) 0f else rawSpeedMps * 3.6f
                        repository.saveCurrentSpeed(speedKmh)
                        
                        if (distance >= 3.0f) {
                            val currentTotal = repository.getTotalDistance()
                            val newTotal = currentTotal + distance
                            repository.saveTotalDistance(newTotal)
                            lastLocation = location
                            AppLogger.d(applicationContext, "Одометр обновлен: +$distance м, Итого: $newTotal м")
                        }
                        
                        // Обновляем уведомление
                        updateNotification(speedKmh, repository.getTotalDistance())
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(speedKmh: Float, totalDistanceMeters: Float) {
        val traveledKm = totalDistanceMeters / 1000f
        val baseKm = repository.getFieldValue("km")
        val totalOdometer = baseKm + traveledKm
        
        val isRussian = repository.getIsRussian()
        
        val headingDegrees = ((currentHeading % 360 + 360) % 360).toInt()
        val direction = getDirectionString(headingDegrees.toFloat(), isRussian)

        val title = if (isRussian) "TrackLit: Поездка активна" else "TrackLit: Trip Active"
        val contentFormat = if (isRussian) {
            "Одометр: %.2f км | Путь: %.2f км\nСкорость: %.0f км/ч | Курс: %s"
        } else {
            "Odo: %.2f km | Trip: %.2f km\nSpeed: %.0f km/h | Heading: %s"
        }
        
        val formattedContent = String.format(java.util.Locale.US, contentFormat, totalOdometer, traveledKm, speedKmh, direction)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(formattedContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(formattedContent))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun getDirectionString(bearing: Float, isRussian: Boolean): String {
        val directions = if (isRussian) {
            listOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")
        } else {
            listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        }
        val index = ((bearing + 22.5) % 360 / 45).toInt()
        return directions[index % 8] + " (${bearing.toInt()}°)"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.d(this, "Service onStartCommand - Запуск обновления координат")
        startLocationUpdates()
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientationValues = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientationValues)
            val azimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
            if (!azimuth.isNaN()) {
                currentHeading = -azimuth
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startForegroundServiceSafe() {
        val isRussian = repository.getIsRussian()
        val channelName = if (isRussian) "Отслеживание GPS" else "GPS Tracking"
        val title = if (isRussian) "TrackLit: Поездка активна" else "TrackLit: Trip Active"
        val text = if (isRussian) "Идет отслеживание поездки..." else "Trip tracking in progress..."

        val channel = NotificationChannel(
            CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            AppLogger.d(this, "startForeground вызван успешно")
        } catch (e: Exception) {
            AppLogger.e(this, "Ошибка при вызове startForeground", e)
        }
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
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
