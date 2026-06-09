package com.example.stridemap.location

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import com.example.stridemap.core.LocationPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import java.time.Instant

class GooglePlayServicesLocationProvider(
    private val client: FusedLocationProviderClient,
    private val looper: Looper = Looper.getMainLooper(),
) : LocationProvider {
    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun start(request: LocationRequestSpec, listener: (LocationPoint) -> Unit, onFailure: (Throwable) -> Unit) {
        stop()
        val locationRequest = LocationRequest.Builder(
            if (request.highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            request.intervalMillis,
        ).setMinUpdateIntervalMillis(request.intervalMillis).build()
        val nextCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.mapNotNull { it.toPoint() }.forEach(listener)
            }
        }
        callback = nextCallback
        client.requestLocationUpdates(locationRequest, nextCallback, looper)
            .addOnFailureListener { error ->
                if (callback === nextCallback) callback = null
                onFailure(error)
            }
    }

    override fun stop() {
        callback?.let(client::removeLocationUpdates)
        callback = null
    }

    private fun Location.toPoint(): LocationPoint? {
        val elapsedWallTime = time.takeIf { it > 0 } ?: return null
        return LocationPoint(
            latitude = latitude,
            longitude = longitude,
            timestamp = Instant.ofEpochMilli(elapsedWallTime),
            accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else null,
            speedMetersPerSecond = if (hasSpeed()) speed.toDouble() else null,
            elevationMeters = if (hasAltitude()) altitude.takeIf { it.isFinite() } else null,
        )
    }
}
