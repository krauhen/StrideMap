package com.example.stridemap.core

data class ElevationSummary(
    val totalAscentMeters: Double,
    val totalDescentMeters: Double,
    val minElevationMeters: Double,
    val maxElevationMeters: Double,
    val elevatedPointCount: Int,
)

object ElevationSummaryCalculator {
    fun summary(points: List<LocationPoint>): ElevationSummary? {
        val elevations = points.mapNotNull { it.elevationMeters }
        if (elevations.size < 2) return null

        var ascent = 0.0
        var descent = 0.0
        elevations.zipWithNext().forEach { (previous, next) ->
            val delta = next - previous
            when {
                delta > 0.0 -> ascent += delta
                delta < 0.0 -> descent += -delta
            }
        }

        return ElevationSummary(
            totalAscentMeters = ascent,
            totalDescentMeters = descent,
            minElevationMeters = elevations.min(),
            maxElevationMeters = elevations.max(),
            elevatedPointCount = elevations.size,
        )
    }
}
