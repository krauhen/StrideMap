package com.example.stridemap.location

import com.example.stridemap.core.LocationPoint
import com.example.stridemap.core.MovementType
import kotlin.math.max
import kotlin.math.min

data class LocationRequestSpec(
    val intervalMillis: Long = forMovement(MovementType.Walk).intervalMillis,
    val highAccuracy: Boolean = true,
) {
    companion object {
        const val MinimumProviderIntervalMillis = 1_000L
        const val MaximumProviderIntervalMillis = 60_000L

        fun forMovement(movementType: MovementType, intervalMillisOverride: Long? = null): LocationRequestSpec {
            val intervalMillis = intervalMillisOverride ?: defaultIntervalMillisForMovement(movementType)
            return LocationRequestSpec(intervalMillis = clampIntervalMillis(intervalMillis), highAccuracy = true)
        }

        fun defaultIntervalMillisForMovement(movementType: MovementType): Long = MinimumProviderIntervalMillis

        fun clampIntervalMillis(intervalMillis: Long): Long = min(MaximumProviderIntervalMillis, max(MinimumProviderIntervalMillis, intervalMillis))
    }
}

interface LocationProvider {
    fun start(request: LocationRequestSpec, listener: (LocationPoint) -> Unit, onFailure: (Throwable) -> Unit = {})
    fun stop()
}
