package com.midas26.mobileapp.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.midas26.mobileapp.R
import com.midas26.mobileapp.network.LocationRepository
import com.midas26.mobileapp.util.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocationForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        startLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        locationCallback?.let { callback ->
            fusedClient.removeLocationUpdates(callback)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(INTERVAL_MS)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                val prefs = PrefsManager.from(applicationContext)
                val userId = prefs.getUserId()

                if (userId == -1) return

                serviceScope.launch {
                    LocationRepository.saveLocation(
                        userId = userId,
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                }
            }
        }

        locationCallback = callback

        try {
            fusedClient.requestLocationUpdates(
                request,
                callback,
                mainLooper
            )
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "위치 공유",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "보호자에게 위치를 공유하는 동안 표시됩니다"
            }

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("위치 공유 중")
            .setContentText("보호자에게 현재 위치를 공유하고 있어요")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "location_sharing"
        private const val NOTIF_ID = 2001
        private const val INTERVAL_MS = 300_000L
    }
}