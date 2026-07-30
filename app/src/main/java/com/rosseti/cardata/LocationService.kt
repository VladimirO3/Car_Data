/**
 * © 2026 Osetrov V.V. Все права защищены.
 */
package com.rosseti.cardata

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.os.SystemClock
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
        val startTime = SystemClock.elapsedRealtime()
        super.onCreate()
        Log.d("LocationService", "Service onCreate started at $startTime ms")
        // Срочный запуск Foreground, чтобы избежать ForegroundServiceDidNotStartInTimeException
        startForegroundServiceSafe()
        
        if (!::repository.isInitialized) {
            repository = SettingsRepository(applicationContext)
        }
        
        AppLogger.d(this, "Service onCreate - Инициализация службы")
        
        // Инициализация датчиков для компаса в уведомлении
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        
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
                        
                        val speedKmh = if (rawSpeedMps < 1.0f) 0f else rawSpeedMps * 3.6f
                        repository.saveCurrentSpeed(speedKmh)
                        
                        // Фильтрация дрейфа GPS: добавляем расстояние только если есть реальное движение (скорость > 3.6 км/ч)
                        // и пройденное расстояние между точками значимо (>= 5 метров)
                        if (speedKmh > 2.0f && distance >= 5.0f) {
                            val currentTotal = repository.getTotalDistance()
                            val newTotal = currentTotal + distance
                            repository.saveTotalDistance(newTotal)
                            lastLocation = location
                            AppLogger.d(applicationContext, "Одометр обновлен: +$distance м, Итого: $newTotal м")
                        } else if (speedKmh == 0f) {
                            // Если стоим на месте, обновляем базовую точку, чтобы не накапливать микродвижения
                            lastLocation = location
                        }
                        
                        // Обновляем уведомление
                        updateNotification(speedKmh, repository.getTotalDistance())
                    }
                }
            }
        }
        
        // После инициализации всех компонентов обновляем уведомление на корректное
        updateNotification(0f, repository.getTotalDistance())
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(speedKmh: Float, totalDistanceMeters: Float) {
        if (!::repository.isInitialized) return
        
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

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(formattedContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(formattedContent))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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
        val startTime = SystemClock.elapsedRealtime()
        Log.d("LocationService", "Service onStartCommand started at $startTime ms")
        // ПЕРВЫМ ДЕЛОМ вызываем startForegroundServiceSafe, чтобы избежать ForegroundServiceDidNotStartInTimeException
        startForegroundServiceSafe()
        
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
            val azimuth = Math.toDegrees(orientationValues[0].toDouble()).toInt().toFloat()
            if (!azimuth.isNaN()) {
                currentHeading = -azimuth
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startForegroundServiceSafe() {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GPS Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Используем фиксированные строки для максимально быстрого запуска без обращения к SharedPreferences
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TrackLit")
                .setContentText("GPS Tracking active")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("LocationService", "Critical error in startForegroundServiceSafe", e)
            // Не замалчиваем ошибку полностью, но пытаемся выжить
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S 
                && e is android.app.ForegroundServiceStartNotAllowedException) {
                AppLogger.e(this, "Foreground service start not allowed from background", e)
            }
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
