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

    private var cachedPendingIntent: PendingIntent? = null
    private var isChannelCreated = false

    override fun onCreate() {
        // 1. МГНОВЕННЫЙ запуск Foreground на самой первой строке
        startForegroundServiceSafe()
        
        val startTime = SystemClock.elapsedRealtime()
        super.onCreate()
        Log.d("LocationService", "Service onCreate continue at $startTime ms")
        
        if (!::repository.isInitialized) {
            repository = SettingsRepository(applicationContext)
        }
        
        AppLogger.d(this, "Service onCreate - Инициализация компонентов")
        
        // Дальнейшая инициализация датчиков и GPS...
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        setupLocationCallback()
        
        // Обновляем уведомление на корректное после загрузки репозитория
        updateNotification(0f, repository.getTotalDistance())
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    if (location.accuracy > 80) continue

                    val last = lastLocation
                    if (last == null) {
                        lastLocation = location
                    } else {
                        val distance = last.distanceTo(location)
                        val rawSpeedMps = if (location.hasSpeed()) location.speed 
                                         else (location.time - last.time).let { if (it > 500) distance / (it / 1000f) else 0f }
                        
                        val speedKmh = if (rawSpeedMps < 0.15f) 0f else rawSpeedMps * 3.6f
                        repository.saveCurrentSpeed(speedKmh)
                        
                        if (speedKmh > 0.5f && distance >= 2.0f) {
                            val newTotal = repository.getTotalDistance() + distance
                            repository.saveTotalDistance(newTotal)
                            lastLocation = location
                        } else if (speedKmh == 0f) {
                            lastLocation = location
                        }
                        
                        updateNotification(speedKmh, repository.getTotalDistance())
                    }
                }
            }
        }
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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(formattedContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(formattedContent))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(cachedPendingIntent)
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

    private fun startForegroundServiceSafe() {
        try {
            if (!isChannelCreated) {
                val manager = getSystemService(NotificationManager::class.java)
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "GPS Tracking",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
                manager.createNotificationChannel(channel)
                isChannelCreated = true
            }

            if (cachedPendingIntent == null) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                cachedPendingIntent = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TrackLit")
                .setContentText("GPS Tracking active")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(cachedPendingIntent)
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
            Log.e("LocationService", "Failed to startForeground", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startTime = SystemClock.elapsedRealtime()
        Log.d("LocationService", "Service onStartCommand at $startTime ms")
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

    private fun startLocationUpdates() {
        Log.d("LocationService", "Requesting location updates")
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
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
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        if (::sensorManager.isInitialized) {
            sensorManager.unregisterListener(this)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
