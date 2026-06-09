package com.example.stridemap.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object DistanceCalculator {
    private const val EarthRadiusMeters = 6_371_000.0

    fun metersBetween(start: LocationPoint, end: LocationPoint): Double {
        val startLat = Math.toRadians(start.latitude)
        val endLat = Math.toRadians(end.latitude)
        val deltaLat = Math.toRadians(end.latitude - start.latitude)
        val deltaLon = Math.toRadians(end.longitude - start.longitude)

        val a = sin(deltaLat / 2).pow(2) + cos(startLat) * cos(endLat) * sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EarthRadiusMeters * c
    }

    fun totalMeters(points: List<LocationPoint>): Double = points.zipWithNext().sumOf { (start, end) ->
        metersBetween(start, end)
    }
}
