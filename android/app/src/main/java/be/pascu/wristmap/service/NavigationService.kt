package be.pascu.wristmap.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.content.ContextCompat
import be.pascu.wristmap.MainActivity
import be.pascu.wristmap.Navigator
import be.pascu.wristmap.R

class NavigationService : Service() {
    private lateinit var locationManager: LocationManager
    private val listener =
        object : LocationListener {
            override fun onLocationChanged(location: Location) = Navigator.onLocation(location)

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {}

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(
                provider: String?,
                status: Int,
                extras: Bundle?,
            ) {}
        }
    private var listening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        try {
            startAsForeground()
        } catch (e: Exception) {
            Navigator.onServiceFailed("foreground service refused: ${e.javaClass.simpleName}")
            stopSelf()
            return START_NOT_STICKY
        }
        startLocationUpdates()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopLocationUpdates()
        Navigator.onServiceStopped()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            Notification
                .Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.service_title))
                .setContentText(getString(R.string.service_text))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startLocationUpdates() {
        if (listening) {
            Navigator.onServiceStarted()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Navigator.onServiceFailed("location permission not granted")
            stopSelf()
            return
        }
        try {
            for (provider in providers()) {
                locationManager.requestLocationUpdates(provider, UPDATE_INTERVAL_MS, 0f, listener, mainLooper)
                locationManager.getLastKnownLocation(provider)?.let { Navigator.onLocation(it) }
            }
            listening = true
            Navigator.onServiceStarted()
        } catch (e: SecurityException) {
            Navigator.onServiceFailed("location denied: $e")
            stopSelf()
        }
    }

    private fun providers(): List<String> {
        val providers = ArrayList<String>()
        if (locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)) providers.add(LocationManager.GPS_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && locationManager.allProviders.contains(LocationManager.FUSED_PROVIDER)) {
            providers.add(LocationManager.FUSED_PROVIDER)
        }
        if (providers.isEmpty() && locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
            providers.add(LocationManager.NETWORK_PROVIDER)
        }
        return providers
    }

    private fun stopLocationUpdates() {
        if (!listening) return
        listening = false
        runCatching { locationManager.removeUpdates(listener) }
    }

    companion object {
        private const val CHANNEL_ID = "navigation"
        private const val NOTIFICATION_ID = 7
        private const val UPDATE_INTERVAL_MS = 1000L

        fun start(context: Context): Boolean = context.startForegroundService(Intent(context, NavigationService::class.java)) != null

        fun stop(context: Context) {
            context.stopService(Intent(context, NavigationService::class.java))
        }
    }
}
