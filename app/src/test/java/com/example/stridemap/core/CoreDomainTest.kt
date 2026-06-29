package com.example.stridemap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class CoreDomainTest {
    private val t0 = Instant.parse("2026-06-08T15:30:04Z")

    @Test
    fun validatorRejectsInvalidCoordinatesAndDuplicatePoint() {
        assertEquals(
            PointValidationResult.Rejected(PointRejectionReason.InvalidLatitude),
            PointValidator.validate(LocationPoint(91.0, 0.0, t0), null),
        )
        assertEquals(
            PointValidationResult.Rejected(PointRejectionReason.InvalidLongitude),
            PointValidator.validate(LocationPoint(0.0, -181.0, t0), null),
        )

        val point = LocationPoint(52.0, 13.0, t0, accuracyMeters = 5.0)
        assertEquals(
            PointValidationResult.Rejected(PointRejectionReason.DuplicateTimestampAndCoordinate),
            PointValidator.validate(point, point),
        )
    }

    @Test
    fun validatorWarnsForPoorAccuracyButAcceptsPoint() {
        val result = PointValidator.validate(LocationPoint(52.0, 13.0, t0, accuracyMeters = 38.0), null)

        assertEquals(PointValidationResult.Accepted(warnPoorAccuracy = true), result)
    }

    @Test
    fun cadenceAcceptsFirstPointThenRequiresOneSecondAndTenMeters() {
        val first = LocationPoint(0.0, 0.0, t0)
        assertTrue(CaptureCadence.shouldAccept(null, first))
        assertFalse(CaptureCadence.shouldAccept(first, LocationPoint(0.0, 0.0002, t0.plusMillis(999))))
        assertFalse(CaptureCadence.shouldAccept(first, LocationPoint(0.0, 0.00005, t0.plusSeconds(5))))
        assertTrue(CaptureCadence.shouldAccept(first, LocationPoint(0.0, 0.00009, t0.plusSeconds(1))))
    }

    @Test
    fun distanceUsesDeterministicHaversineMeters() {
        val start = LocationPoint(0.0, 0.0, t0)
        val oneDegreeEastAtEquator = LocationPoint(0.0, 1.0, t0.plusSeconds(7))

        assertEquals(111_195.0, DistanceCalculator.metersBetween(start, oneDegreeEastAtEquator), 1.0)
    }

    @Test
    fun liveElapsedTimeUsesCaptureStartEvenWhenOnlyOnePointIsSaved() {
        val track = Track(
            id = "car-live.gpx",
            fileName = "car-live.gpx",
            message = "",
            movementType = MovementType.Car,
            state = TrackState.Live,
            points = listOf(LocationPoint(52.0, 13.0, t0.plusSeconds(2))),
            createdAt = t0,
        )

        assertEquals(0, track.durationSeconds)
        assertEquals(60, track.elapsedSecondsAt(t0.plusSeconds(60)))
    }

    @Test
    fun mapOverlaySummaryUsesOneLineEssentialsWithoutMessage() {
        val track = Track(
            id = "walk.gpx",
            fileName = "walk.gpx",
            message = "A long custom note that should stay out of the map overlay",
            movementType = MovementType.Walk,
            state = TrackState.Stopped,
            points = emptyList(),
            createdAt = t0,
            distanceMeters = 1_234.0,
            completedDurationSeconds = 754,
        )

        assertEquals(
            "Walk • 2026-06-08 15:30 • 1.23 km • 00:12:34",
            MapOverlayText.summary(track, displayDurationSeconds = 754, zoneId = ZoneOffset.UTC),
        )
    }

    @Test
    fun filenameUsesUtcMovementTypeSanitizedMessageAndCollisionSuffix() {
        val base = TrackFilename.buildBaseName(t0, MovementType.Walk, "Walk to / Park!!! now")

        assertEquals("2026-06-08_15-30-04Z_walk_walk-to-park-now.gpx", base)
        assertEquals(
            "2026-06-08_15-30-04Z_walk_walk-to-park-now-3.gpx",
            TrackFilename.uniqueName(base) { it == base || it.endsWith("-2.gpx") },
        )
    }

    @Test
    fun editedFilenameUsesOriginalTimestampMovementSanitizedMessageAndCollisionSuffix() {
        val base = TrackFilename.buildBaseName(t0, MovementType.Walk, "Evening / café loop!!!")

        assertEquals("2026-06-08_15-30-04Z_walk_evening-cafe-loo.gpx", base)
        assertEquals(
            "2026-06-08_15-30-04Z_walk_evening-cafe-loo-3.gpx",
            TrackFilename.uniqueName(base) { it == base || it.endsWith("-2.gpx") },
        )
        assertEquals("2026-06-08_15-30-04Z_walk.gpx", TrackFilename.buildBaseName(t0, MovementType.Walk, " "))
    }

    @Test
    fun trackOrderingFiltersMovementAndKeepsLiveRowsFirst() {
        val oldWalk = track("old.gpx", MovementType.Walk, TrackState.Stopped, t0)
        val newWalk = track("new.gpx", MovementType.Walk, TrackState.Stopped, t0.plusSeconds(60))
        val liveBike = track("live.gpx", MovementType.Bike, TrackState.Live, t0.plusSeconds(30))

        assertEquals(
            listOf("new.gpx", "old.gpx"),
            TrackOrdering.sort(listOf(oldWalk, liveBike, newWalk), MovementType.Walk, TrackSortField.Date, ascending = false).map { it.id },
        )
        assertEquals(
            "live.gpx",
            TrackOrdering.sort(listOf(oldWalk, liveBike, newWalk), null, TrackSortField.Date, ascending = false).first().id,
        )
    }

    private fun track(id: String, movementType: MovementType, state: TrackState, createdAt: Instant): Track = Track(
        id = id,
        fileName = id,
        message = "",
        movementType = movementType,
        state = state,
        points = emptyList(),
        createdAt = createdAt,
    )
}
