package com.example.stridemap.core

import java.time.Duration
import java.time.Instant

enum class MovementType(val serialized: String, val label: String) {
    Walk("walk", "Walk"),
    Run("run", "Run"),
    Bike("bike", "Bike"),
    Car("car", "Car"),
    Train("train", "Train");

    companion object {
        fun fromSerialized(value: String): MovementType? = entries.firstOrNull { it.serialized == value }
    }
}

enum class TrackState(val serialized: String) {
    Live("live"),
    Stopped("stopped"),
    Interrupted("interrupted");

    companion object {
        fun fromSerialized(value: String): TrackState? = entries.firstOrNull { it.serialized == value }
    }
}

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Instant,
    val accuracyMeters: Double? = null,
    val speedMetersPerSecond: Double? = null,
    val elevationMeters: Double? = null,
)

data class Track(
    val id: String,
    val fileName: String,
    val message: String,
    val movementType: MovementType,
    val state: TrackState,
    val points: List<LocationPoint>,
    val createdAt: Instant,
    val updatedAt: Instant = points.lastOrNull()?.timestamp ?: createdAt,
    val distanceMeters: Double = DistanceCalculator.totalMeters(points),
    val completedDurationSeconds: Long? = null,
) {
    val startTimestamp: Instant? = points.firstOrNull()?.timestamp
    val latestTimestamp: Instant? = points.lastOrNull()?.timestamp
    val durationSeconds: Long = when {
        state != TrackState.Live && completedDurationSeconds != null -> completedDurationSeconds.coerceAtLeast(0)
        startTimestamp != null && latestTimestamp != null -> Duration.between(startTimestamp, latestTimestamp).seconds.coerceAtLeast(0)
        else -> 0
    }

    fun elapsedSecondsAt(now: Instant): Long = Duration.between(createdAt, now).seconds.coerceAtLeast(0)
}

data class MalformedTrack(
    val fileName: String,
    val category: ParseErrorCategory,
    val safeSummary: String,
)

enum class ParseErrorCategory {
    InvalidXml,
    MissingMetadata,
    InvalidMetadata,
    InvalidPoint,
}

sealed interface ParsedTrackEntry {
    data class Valid(val track: Track) : ParsedTrackEntry
    data class Malformed(val error: MalformedTrack) : ParsedTrackEntry
}
