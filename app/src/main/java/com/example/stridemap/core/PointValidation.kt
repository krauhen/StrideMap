package com.example.stridemap.core

import java.time.Duration

object CaptureCadence {
    const val MinSavedPointIntervalMillis = 1_000L
    const val TargetSavedPointDistanceMeters = 10.0

    fun shouldAccept(lastAccepted: LocationPoint?, candidate: LocationPoint): Boolean {
        if (lastAccepted == null) return true
        if (candidate.timestamp.isBefore(lastAccepted.timestamp)) return false
        val elapsedMillis = Duration.between(lastAccepted.timestamp, candidate.timestamp).toMillis()
        val distanceMeters = DistanceCalculator.metersBetween(lastAccepted, candidate)
        return elapsedMillis >= MinSavedPointIntervalMillis && distanceMeters >= TargetSavedPointDistanceMeters
    }
}

object PointValidator {
    fun validate(candidate: LocationPoint, previousAccepted: LocationPoint?): PointValidationResult {
        if (candidate.latitude !in -90.0..90.0) return PointValidationResult.Rejected(PointRejectionReason.InvalidLatitude)
        if (candidate.longitude !in -180.0..180.0) return PointValidationResult.Rejected(PointRejectionReason.InvalidLongitude)
        if (candidate.accuracyMeters != null && candidate.accuracyMeters <= 0.0) {
            return PointValidationResult.Rejected(PointRejectionReason.InvalidAccuracy)
        }
        if (candidate.speedMetersPerSecond != null && candidate.speedMetersPerSecond < 0.0) {
            return PointValidationResult.Rejected(PointRejectionReason.InvalidSpeed)
        }
        if (candidate.elevationMeters != null && !candidate.elevationMeters.isFinite()) {
            return PointValidationResult.Rejected(PointRejectionReason.InvalidElevation)
        }
        if (previousAccepted != null) {
            if (candidate.timestamp.isBefore(previousAccepted.timestamp)) {
                return PointValidationResult.Rejected(PointRejectionReason.TimestampWentBackwards)
            }
            if (
                candidate.timestamp == previousAccepted.timestamp &&
                candidate.latitude == previousAccepted.latitude &&
                candidate.longitude == previousAccepted.longitude
            ) {
                return PointValidationResult.Rejected(PointRejectionReason.DuplicateTimestampAndCoordinate)
            }
        }
        return PointValidationResult.Accepted(warnPoorAccuracy = (candidate.accuracyMeters ?: 0.0) > 25.0)
    }
}

sealed interface PointValidationResult {
    data class Accepted(val warnPoorAccuracy: Boolean) : PointValidationResult
    data class Rejected(val reason: PointRejectionReason) : PointValidationResult
}

enum class PointRejectionReason {
    InvalidLatitude,
    InvalidLongitude,
    InvalidAccuracy,
    InvalidSpeed,
    InvalidElevation,
    TimestampWentBackwards,
    DuplicateTimestampAndCoordinate,
}
