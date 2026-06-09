package com.example.stridemap.storage

import com.example.stridemap.core.LocationPoint
import com.example.stridemap.core.MovementType
import com.example.stridemap.core.Track
import com.example.stridemap.core.TrackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SessionJournalRecoveryPlannerTest {
    private val startedAt = Instant.parse("2026-06-08T15:30:04Z")
    private val target = TrackFileRef("live.gpx", "content://tracks/live.gpx")

    @Test
    fun staleJournalWithPointsWritesInterruptedSnapshotAndClearsJournal() {
        val live = track(points = listOf(LocationPoint(52.0, 13.0, startedAt)))
        val entry = SessionJournalEntry("session-1", target, live, startedAt)

        val plan = SessionJournalRecoveryPlanner.plan(entry, activeOwnerTrackId = null)

        assertEquals(TrackState.Interrupted, plan.trackToWrite?.state)
        assertNull(plan.targetToDiscard)
        assertTrue(plan.shouldClearJournal)
    }

    @Test
    fun staleStoppedJournalWithPointsRewritesStoppedSnapshotAndClearsJournal() {
        val stopped = track(points = listOf(LocationPoint(52.0, 13.0, startedAt))).copy(state = TrackState.Stopped)
        val entry = SessionJournalEntry("session-1", target, stopped, startedAt)

        val plan = SessionJournalRecoveryPlanner.plan(entry, activeOwnerTrackId = null)

        assertEquals(TrackState.Stopped, plan.trackToWrite?.state)
        assertNull(plan.targetToDiscard)
        assertTrue(plan.shouldClearJournal)
    }

    @Test
    fun staleEmptyJournalDiscardsVisibleTargetAndClearsJournal() {
        val entry = SessionJournalEntry("session-1", target, track(points = emptyList()), null)

        val plan = SessionJournalRecoveryPlanner.plan(entry, activeOwnerTrackId = null)

        assertNull(plan.trackToWrite)
        assertEquals(target, plan.targetToDiscard)
        assertTrue(plan.shouldClearJournal)
    }

    @Test
    fun activeOwnerJournalIsLeftAlone() {
        val entry = SessionJournalEntry("session-1", target, track(points = emptyList()), null)

        val plan = SessionJournalRecoveryPlanner.plan(entry, activeOwnerTrackId = "live.gpx")

        assertNull(plan.trackToWrite)
        assertNull(plan.targetToDiscard)
        assertFalse(plan.shouldClearJournal)
    }

    private fun track(points: List<LocationPoint>): Track = Track(
        id = "live.gpx",
        fileName = "live.gpx",
        message = "",
        movementType = MovementType.Walk,
        state = TrackState.Live,
        points = points,
        createdAt = startedAt,
    )
}
