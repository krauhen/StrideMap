package com.example.stridemap.location

import com.example.stridemap.core.MovementType
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationRequestSpecTest {
    @Test
    fun movementTypeDefaultsToOneSecondProviderPolling() {
        assertEquals(1_000L, LocationRequestSpec.forMovement(MovementType.Walk).intervalMillis)
        assertEquals(1_000L, LocationRequestSpec.forMovement(MovementType.Run).intervalMillis)
        assertEquals(1_000L, LocationRequestSpec.forMovement(MovementType.Bike).intervalMillis)
        assertEquals(1_000L, LocationRequestSpec.forMovement(MovementType.Car).intervalMillis)
        assertEquals(1_000L, LocationRequestSpec.forMovement(MovementType.Train).intervalMillis)
    }

    @Test
    fun exposesCurrentDefaultsForSettingsUi() {
        assertEquals(1_000L, LocationRequestSpec.defaultIntervalMillisForMovement(MovementType.Walk))
        assertEquals(1_000L, LocationRequestSpec.defaultIntervalMillisForMovement(MovementType.Run))
        assertEquals(1_000L, LocationRequestSpec.defaultIntervalMillisForMovement(MovementType.Bike))
        assertEquals(1_000L, LocationRequestSpec.defaultIntervalMillisForMovement(MovementType.Car))
        assertEquals(1_000L, LocationRequestSpec.defaultIntervalMillisForMovement(MovementType.Train))
    }

    @Test
    fun overrideIntervalIsClampedToSafeProviderBounds() {
        assertEquals(1_000L, LocationRequestSpec.forMovement(MovementType.Walk, intervalMillisOverride = 0L).intervalMillis)
        assertEquals(1_000L, LocationRequestSpec.forMovement(MovementType.Walk, intervalMillisOverride = 999L).intervalMillis)
        assertEquals(15_000L, LocationRequestSpec.forMovement(MovementType.Walk, intervalMillisOverride = 15_000L).intervalMillis)
        assertEquals(60_000L, LocationRequestSpec.forMovement(MovementType.Walk, intervalMillisOverride = 600_000L).intervalMillis)
    }
}
