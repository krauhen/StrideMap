package com.example.stridemap.session

import com.example.stridemap.core.LocationPoint
import com.example.stridemap.core.MovementType
import com.example.stridemap.core.Track
import com.example.stridemap.core.TrackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CaptureSessionControllerTest {
    private val controller = CaptureSessionController()
    private val startedAt = Instant.parse("2026-06-08T15:30:04Z")
    private val request = StartCaptureRequest(
        trackId = "track-1",
        fileName = "track.gpx",
        message = "Walk",
        movementType = MovementType.Walk,
        startedAt = startedAt,
    )

    @Test
    fun startCreatesOneLiveTrackAndBlocksSecondStart() {
        val start = controller.start(CaptureSessionState(), request) as SessionResult.Changed
        assertEquals(TrackState.Live, start.state.liveTrack?.state)
        assertTrue(SetupBlocker.ExistingLiveTrack in start.state.setupReadiness.blockers)

        val second = controller.start(start.state, request.copy(trackId = "track-2")) as SessionResult.Rejected
        assertEquals(SessionRejectionReason.SetupNotReady, second.reason)
    }

    @Test
    fun appendPointUsesValidationAndDistanceTargetedCadence() {
        val state = (controller.start(CaptureSessionState(), request) as SessionResult.Changed).state
        val first = controller.appendPoint(state, LocationPoint(0.0, 0.0, startedAt)) as AppendPointResult.Changed
        assertEquals(1, first.state.liveTrack?.points?.size)

        assertEquals(
            AppendPointResult.CadenceSkipped,
            controller.appendPoint(first.state, LocationPoint(0.0, 0.0002, startedAt.plusMillis(999))),
        )
        assertEquals(
            AppendPointResult.CadenceSkipped,
            controller.appendPoint(first.state, LocationPoint(0.0, 0.00005, startedAt.plusSeconds(5))),
        )
        val second = controller.appendPoint(first.state, LocationPoint(0.0, 0.00009, startedAt.plusSeconds(1))) as AppendPointResult.Changed
        assertEquals(2, second.state.liveTrack?.points?.size)
    }

    @Test
    fun stopWithPointsFinalizesAndSelectsTrack() {
        val state = (controller.start(CaptureSessionState(), request) as SessionResult.Changed).state
        val withPoint = (controller.appendPoint(state, LocationPoint(52.0, 13.0, startedAt)) as AppendPointResult.Changed).state

        val stopped = controller.confirmStop(withPoint) as StopResult.Stopped

        assertNull(stopped.state.liveTrack)
        assertEquals("track-1", stopped.state.selectedTrackId)
        assertEquals(TrackState.Stopped, stopped.finalizedTrack.state)
    }

    @Test
    fun stopWithOneStationaryPointUsesStopTimeForDuration() {
        val state = (controller.start(CaptureSessionState(), request) as SessionResult.Changed).state
        val withPoint = (controller.appendPoint(state, LocationPoint(52.0, 13.0, startedAt.plusSeconds(2))) as AppendPointResult.Changed).state

        val stopped = controller.confirmStop(withPoint, stoppedAt = startedAt.plusSeconds(65)) as StopResult.Stopped

        assertEquals(65, stopped.finalizedTrack.durationSeconds)
    }

    @Test
    fun stopWithoutPointsDiscardsEmptyCapture() {
        val state = (controller.start(CaptureSessionState(), request) as SessionResult.Changed).state

        val discarded = controller.confirmStop(state) as StopResult.Discarded

        assertNull(discarded.state.liveTrack)
    }

    @Test
    fun startupRecoveryMarksStaleLiveWithPointsInterruptedAndDiscardsEmptyLive() {
        val staleWithPoint = Track(
            id = "stale",
            fileName = "stale.gpx",
            message = "",
            movementType = MovementType.Walk,
            state = TrackState.Live,
            createdAt = startedAt,
            points = listOf(LocationPoint(52.0, 13.0, startedAt)),
        )
        val staleEmpty = staleWithPoint.copy(id = "empty", fileName = "empty.gpx", points = emptyList())

        val recovery = controller.recoverStartup(listOf(staleWithPoint, staleEmpty), activeOwnerTrackId = null)

        assertEquals(listOf("empty"), recovery.discardedTrackIds)
        assertEquals(TrackState.Interrupted, recovery.tracks.single().state)
    }
}
