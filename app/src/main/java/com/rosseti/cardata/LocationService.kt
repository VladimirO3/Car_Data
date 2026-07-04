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
        Log.d("LocationService", "Service onCreate")
        repository = SettingsRepository(applicationContext)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    Log.d("LocationService", "New location: lat=${location.latitude}, lon=${location.longitude}, acc=${location.accuracy}")
                    
                    if (location.accuracy > 50) {
                        Log.d("LocationService", "Skipping inaccurate location")
                        continue
                    }

                    val last = lastLocation
                    if (last == null) {
                        lastLocation = location
                        Log.d("LocationService", "First location fixed")
                    } else {
                        val distance = last.distanceTo(location)
                        val speed = if (location.hasSpeed()) location.speed else 0f
                        
                        // Сохраняем мгновенную скорость (переводим м/с в км/ч)
                        repository.saveCurrentSpeed(speed * 3.6f)
                        
                        // Игнорируем перемещения менее 3 метров или если скорость слишком мала (фильтрация дрейфа)
                        if (distance >= 3.0f && (speed > 0.5f || distance > 10f)) {
                            val currentTotal = repository.getTotalDistance()
                            val newTotal = currentTotal + distance
                            repository.saveTotalDistance(newTotal)
                            lastLocation = location
                            Log.d("LocationService", "Total distance updated: $newTotal meters")
                        } else {
                            Log.d("LocationService", "Ignoring GPS jitter: dist=$distance, speed=$speed")
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "Service onStartCommand")
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
