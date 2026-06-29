package com.example.stridemap.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object MapPointPresentation {
    fun distanceSinceStartMeters(points: List<LocationPoint>, pointIndex: Int): Double {
        if (pointIndex <= 0 || points.size < 2) return 0.0
        val safeIndex = pointIndex.coerceAtMost(points.lastIndex)
        return DistanceCalculator.totalMeters(points.take(safeIndex + 1))
    }

    fun directionBearingDegrees(points: List<LocationPoint>, pointIndex: Int): Double? {
        if (points.size < 2 || pointIndex !in points.indices) return null
        val startIndex = (pointIndex - 1).coerceAtLeast(0)
        val endIndex = if (pointIndex == 0) 1 else pointIndex
        val start = points[startIndex]
        val end = points[endIndex]
        if (start.latitude == end.latitude && start.longitude == end.longitude) return null

        val startLat = Math.toRadians(start.latitude)
        val endLat = Math.toRadians(end.latitude)
        val deltaLon = Math.toRadians(end.longitude - start.longitude)
        val y = sin(deltaLon) * cos(endLat)
        val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(deltaLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun elevationChangeFromStartMeters(points: List<LocationPoint>, pointIndex: Int): Double? {
        if (pointIndex !in points.indices) return null
        val startElevation = points.firstOrNull()?.elevationMeters ?: return null
        val selectedElevation = points[pointIndex].elevationMeters ?: return null
        return selectedElevation - startElevation
    }

    fun speedKilometersPerHour(point: LocationPoint): Double? = point.speedMetersPerSecond?.times(3.6)
}
