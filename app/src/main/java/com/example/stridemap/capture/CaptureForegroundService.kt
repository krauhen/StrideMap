package com.example.stridemap.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.stridemap.MainActivity
import com.example.stridemap.R
import com.example.stridemap.StrideMapRepository
import com.example.stridemap.location.GooglePlayServicesLocationProvider
import com.example.stridemap.location.LocationProvider
import com.google.android.gms.location.LocationServices

class CaptureForegroundService : Service() {
    private var provider: LocationProvider? = null
    private var stoppingNormally = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionStop -> stopCapture()
            else -> startCapture()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        provider?.stop()
        provider = null
        activeOwnerTrackId = null
        if (!stoppingNormally) {
            StrideMapRepository.interruptLive("Capture service stopped unexpectedly")
        }
        super.onDestroy()
    }

    private fun startCapture() {
        if (provider != null) return
        val liveTrack = StrideMapRepository.state.liveTrack ?: run {
            stopSelf()
            return
        }
        activeOwnerTrackId = liveTrack.id
        try {
            createChannel()
            startForeground(NotificationId, notification(liveTrack.fileName))
        } catch (error: Exception) {
            activeOwnerTrackId = null
            StrideMapRepository.interruptLive("Could not start foreground recording service")
            stopSelf()
            return
        }
        provider = GooglePlayServicesLocationProvider(LocationServices.getFusedLocationProviderClient(this)).also { locationProvider ->
            runCatching {
                locationProvider.start(
                    StrideMapRepository.locationRequestSpecFor(liveTrack.movementType),
                    listener = { point -> StrideMapRepository.appendLocationFromService(point) },
                    onFailure = {
                        StrideMapRepository.interruptLive("Could not receive location updates")
                        stopCaptureAfterFailure()
                    },
                )
            }.onFailure {
                StrideMapRepository.interruptLive("Could not receive location updates")
                stopCaptureAfterFailure()
            }
        }
    }

    private fun stopCapture() {
        provider?.stop()
        provider = null
        stoppingNormally = StrideMapRepository.stopCaptureConfirmed()
        activeOwnerTrackId = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopCaptureAfterFailure() {
        provider?.stop()
        provider = null
        activeOwnerTrackId = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(fileName: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("StrideMap recording")
            .setContentText("Saving $fileName")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(ChannelId, "StrideMap capture", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Persistent notification while recording a GPX track"
                },
            )
        }
    }

    companion object {
        const val ActionStart = "com.example.stridemap.capture.START"
        const val ActionStop = "com.example.stridemap.capture.STOP"
        private const val ChannelId = "stridemap_capture"
        private const val NotificationId = 1001
        private var activeOwnerTrackId: String? = null

        fun hasActiveOwner(): Boolean = activeOwnerTrackId != null
        fun activeOwnerTrackId(): String? = activeOwnerTrackId
    }
}
