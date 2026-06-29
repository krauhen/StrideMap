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
    fun displayEntriesIncludesLiveTrackInTracksListOnly() {
        val live = track("live.gpx", TrackState.Live)

        val state = AppState(liveTrack = live)

        assertEquals(listOf(ParsedTrackEntry.Valid(live)), state.displayEntries)
        assertTrue(state.displayedTracks.isEmpty())
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

    @Test
    fun displayedTracksAreExplicitAndCanIncludeMultipleSavedTracks() {
        val first = track("first.gpx", TrackState.Stopped)
        val second = track("second.gpx", TrackState.Interrupted)
        val hidden = track("hidden.gpx", TrackState.Stopped)

        val state = AppState(
            entries = listOf(ParsedTrackEntry.Valid(first), ParsedTrackEntry.Valid(second), ParsedTrackEntry.Valid(hidden)),
            displayedTrackIds = setOf(first.id, second.id),
        )

        assertEquals(listOf(first, second), state.displayedTracks)
    }

    @Test
    fun displayedTracksCanExplicitlyIncludeLiveTrackButLiveIsNotAutomatic() {
        val live = track("live.gpx", TrackState.Live)

        val hiddenState = AppState(liveTrack = live)
        val displayedState = AppState(liveTrack = live, displayedTrackIds = setOf(live.id))

        assertTrue(hiddenState.displayedTracks.isEmpty())
        assertEquals(listOf(live), displayedState.displayedTracks)
    }

    @Test
    fun displayedTracksIgnoreMalformedAndMissingIds() {
        val displayed = track("displayed.gpx", TrackState.Stopped)
        val malformed = ParsedTrackEntry.Malformed(com.example.stridemap.core.MalformedTrack("bad.gpx", com.example.stridemap.core.ParseErrorCategory.InvalidXml, "Invalid GPX"))

        val state = AppState(
            entries = listOf(ParsedTrackEntry.Valid(displayed), malformed),
            displayedTrackIds = setOf(displayed.id, "bad.gpx", "missing.gpx"),
        )

        assertEquals(listOf(displayed), state.displayedTracks)
    }

    @Test
    fun upsertValidTrackReplacesLiveEntryWithInterruptedTrackForDisplayedRouteContinuity() {
        val live = track("live.gpx", TrackState.Live, message = "recording")
        val interrupted = live.copy(state = TrackState.Interrupted, message = "interrupted")

        val entries = listOf(ParsedTrackEntry.Valid(live)).upsertValidTrack(interrupted)
        val state = AppState(entries = entries, displayedTrackIds = setOf(interrupted.id))

        assertEquals(listOf(ParsedTrackEntry.Valid(interrupted)), entries)
        assertEquals(listOf(interrupted), state.displayedTracks)
    }

    @Test
    fun replacingTrackAfterRenameMigratesDisplayStateAndRemovesOldEntry() {
        val old = track("old-name.gpx", TrackState.Stopped, message = "old")
        val renamed = old.copy(id = "new-name.gpx", fileName = "new-name.gpx", message = "new")
        val other = track("other.gpx", TrackState.Stopped)
        val state = AppState(
            entries = listOf(ParsedTrackEntry.Valid(old), ParsedTrackEntry.Valid(other)),
            displayedTrackIds = setOf(old.id, other.id),
        )

        val updated = state.replaceTrackAfterRename(old.id, renamed)

        assertEquals(listOf(renamed, other), updated.displayEntries.filterIsInstance<ParsedTrackEntry.Valid>().map { it.track })
        assertEquals(setOf(renamed.id, other.id), updated.displayedTrackIds)
        assertEquals(listOf(renamed, other), updated.displayedTracks)
    }

    @Test
    fun removingTrackCleansDisplayStateWithoutTouchingUnrelatedRows() {
        val deleted = track("delete-me.gpx", TrackState.Stopped)
        val kept = track("keep-me.gpx", TrackState.Interrupted)
        val malformed = ParsedTrackEntry.Malformed(com.example.stridemap.core.MalformedTrack("bad.gpx", com.example.stridemap.core.ParseErrorCategory.InvalidXml, "Invalid GPX"))
        val state = AppState(
            entries = listOf(ParsedTrackEntry.Valid(deleted), ParsedTrackEntry.Valid(kept), malformed),
            displayedTrackIds = setOf(deleted.id, kept.id),
        )

        val updated = state.removeTrack(deleted.id)

        assertEquals(listOf(ParsedTrackEntry.Valid(kept), malformed), updated.entries)
        assertEquals(setOf(kept.id), updated.displayedTrackIds)
        assertEquals(listOf(kept), updated.displayedTracks)
    }

    @Test
    fun clearingDisplayedTracksRemovesOnlyMapSelection() {
        val displayed = track("displayed.gpx", TrackState.Stopped)
        val hidden = track("hidden.gpx", TrackState.Stopped)
        val state = AppState(
            entries = listOf(ParsedTrackEntry.Valid(displayed), ParsedTrackEntry.Valid(hidden)),
            displayedTrackIds = setOf(displayed.id),
        )

        val updated = state.clearDisplayedTracks()

        assertEquals(listOf(ParsedTrackEntry.Valid(displayed), ParsedTrackEntry.Valid(hidden)), updated.entries)
        assertTrue(updated.displayedTrackIds.isEmpty())
        assertTrue(updated.displayedTracks.isEmpty())
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
