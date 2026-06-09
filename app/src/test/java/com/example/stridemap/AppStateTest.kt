package com.example.stridemap

import com.example.stridemap.core.MovementType
import com.example.stridemap.core.ParsedTrackEntry
import com.example.stridemap.core.Track
import com.example.stridemap.core.TrackSortField
import com.example.stridemap.core.TrackState
import com.example.stridemap.session.SetupBlocker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AppStateTest {
    @Test
    fun freshStateDefaultsToWalkAndDoesNotBlockOnMovementType() {
        val state = AppState()

        assertEquals(MovementType.Walk, state.movementType)
        assertFalse(SetupBlocker.MovementTypeMissing in state.readiness.blockers)
    }

    @Test
    fun freshStateIncludesDefaultGpsPollingSettings() {
        val state = AppState()

        assertEquals(1_000L, state.settings.gpsPollingIntervalMillis(MovementType.Walk))
        assertEquals(1_000L, state.settings.gpsPollingIntervalMillis(MovementType.Run))
        assertEquals(1_000L, state.settings.gpsPollingIntervalMillis(MovementType.Bike))
        assertEquals(1_000L, state.settings.gpsPollingIntervalMillis(MovementType.Car))
        assertEquals(1_000L, state.settings.gpsPollingIntervalMillis(MovementType.Train))
    }

    @Test
    fun freshStateIncludesDefaultUserSettings() {
        val settings = UserSettings.default()

        assertEquals(MovementType.Walk, settings.defaultMovementType)
        assertEquals(AfterStartDestination.Map, settings.afterStartDestination)
        assertEquals("", settings.defaultCaptureNote)
        assertEquals(null, settings.defaultTrackMovementFilter)
        assertEquals(TrackSortField.Date, settings.defaultTrackSortField)
        assertFalse(settings.defaultTrackSortAscending)
        assertTrue(settings.followLiveByDefault)
        assertTrue(settings.showSavedPointDots)
        assertEquals(8f, settings.routeLineWidth, 0.001f)
    }

    @Test
    fun userSettingsSettersPreserveTypedValuesAndClampUnsafeInput() {
        val settings = UserSettings.default()
            .withDefaultMovementType(MovementType.Bike)
            .withAfterStartDestination(AfterStartDestination.Capture)
            .withDefaultCaptureNote("  Morning loop  ")
            .withDefaultTrackMovementFilter(MovementType.Run)
            .withDefaultTrackSortField(TrackSortField.Distance)
            .withDefaultTrackSortAscending(true)
            .withFollowLiveByDefault(false)
            .withShowSavedPointDots(false)
            .withRouteLineWidth(99f)

        assertEquals(MovementType.Bike, settings.defaultMovementType)
        assertEquals(AfterStartDestination.Capture, settings.afterStartDestination)
        assertEquals("Morning loop", settings.defaultCaptureNote)
        assertEquals(MovementType.Run, settings.defaultTrackMovementFilter)
        assertEquals(TrackSortField.Distance, settings.defaultTrackSortField)
        assertTrue(settings.defaultTrackSortAscending)
        assertFalse(settings.followLiveByDefault)
        assertFalse(settings.showSavedPointDots)
        assertEquals(UserSettings.MaxRouteLineWidth, settings.routeLineWidth, 0.001f)
        assertEquals(UserSettings.MinRouteLineWidth, settings.withRouteLineWidth(0f).routeLineWidth, 0.001f)
        assertEquals(UserSettings.default().routeLineWidth, settings.withRouteLineWidth(Float.NaN).routeLineWidth, 0.001f)
    }

    @Test
    fun gpsPollingSettingsClampUnsafeValues() {
        val settings = UserSettings.default().withGpsPollingInterval(MovementType.Walk, 0L)

        assertEquals(1_000L, settings.gpsPollingIntervalMillis(MovementType.Walk))
        assertEquals(60_000L, settings.withGpsPollingInterval(MovementType.Run, 90_000L).gpsPollingIntervalMillis(MovementType.Run))
    }

    @Test
    fun displayEntriesIncludesLiveTrackImmediately() {
        val live = track("live.gpx", TrackState.Live)

        val state = AppState(liveTrack = live)

        assertEquals(listOf(ParsedTrackEntry.Valid(live)), state.displayEntries)
    }

    @Test
    fun displayEntriesUsesCurrentLiveTrackWithoutDuplicateScannedRow() {
        val staleLive = track("live.gpx", TrackState.Live, message = "stale")
        val currentLive = track("live.gpx", TrackState.Live, message = "current")
        val stopped = track("stopped.gpx", TrackState.Stopped)

        val state = AppState(
            entries = listOf(ParsedTrackEntry.Valid(staleLive), ParsedTrackEntry.Valid(stopped)),
            liveTrack = currentLive,
        )

        assertEquals(listOf(ParsedTrackEntry.Valid(currentLive), ParsedTrackEntry.Valid(stopped)), state.displayEntries)
        assertTrue(state.displayEntries.filterIsInstance<ParsedTrackEntry.Valid>().count { it.track.id == currentLive.id } == 1)
    }

    private fun track(fileName: String, state: TrackState, message: String = ""): Track = Track(
        id = fileName,
        fileName = fileName,
        message = message,
        movementType = MovementType.Walk,
        state = state,
        points = emptyList(),
        createdAt = Instant.parse("2026-06-08T15:30:04Z"),
    )
}
