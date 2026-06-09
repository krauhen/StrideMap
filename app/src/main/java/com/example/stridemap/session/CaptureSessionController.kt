package com.example.stridemap.session

import com.example.stridemap.core.CaptureCadence
import com.example.stridemap.core.LocationPoint
import com.example.stridemap.core.MovementType
import com.example.stridemap.core.PointValidationResult
import com.example.stridemap.core.PointValidator
import com.example.stridemap.core.Track
import com.example.stridemap.core.TrackState
import java.time.Duration
import java.time.Instant

class CaptureSessionController {
    fun start(state: CaptureSessionState, request: StartCaptureRequest): SessionResult {
        if (!state.setupReadiness.canStart) return SessionResult.Rejected(SessionRejectionReason.SetupNotReady)
        if (state.liveTrack != null) return SessionResult.Rejected(SessionRejectionReason.LiveTrackAlreadyExists)
        val track = Track(
            id = request.trackId,
            fileName = request.fileName,
            message = request.message,
            movementType = request.movementType,
            state = TrackState.Live,
            points = emptyList(),
            createdAt = request.startedAt,
        )
        return SessionResult.Changed(state.copy(liveTrack = track, setupReadiness = state.setupReadiness.withBlocker(SetupBlocker.ExistingLiveTrack, true)))
    }

    fun appendPoint(state: CaptureSessionState, point: LocationPoint): AppendPointResult {
        val liveTrack = state.liveTrack ?: return AppendPointResult.Rejected(SessionRejectionReason.NoLiveTrack)
        val previous = liveTrack.points.lastOrNull()
        val validation = PointValidator.validate(point, previous)
        if (validation is PointValidationResult.Rejected) return AppendPointResult.PointRejected(validation.reason)
        if (!CaptureCadence.shouldAccept(previous, point)) return AppendPointResult.CadenceSkipped
        val updated = liveTrack.copy(points = liveTrack.points + point, updatedAt = point.timestamp)
        return AppendPointResult.Changed(state.copy(liveTrack = updated))
    }

    fun confirmStop(state: CaptureSessionState, stoppedAt: Instant = Instant.now()): StopResult {
        val liveTrack = state.liveTrack ?: return StopResult.Rejected(SessionRejectionReason.NoLiveTrack)
        if (liveTrack.points.isEmpty()) {
            return StopResult.Discarded(state.copy(liveTrack = null, setupReadiness = state.setupReadiness.withBlocker(SetupBlocker.ExistingLiveTrack, false)))
        }
        val stopped = liveTrack.copy(
            state = TrackState.Stopped,
            updatedAt = stoppedAt,
            completedDurationSeconds = Duration.between(liveTrack.createdAt, stoppedAt).seconds.coerceAtLeast(0),
        )
        return StopResult.Stopped(
            state.copy(
                liveTrack = null,
                selectedTrackId = stopped.id,
                setupReadiness = state.setupReadiness.withBlocker(SetupBlocker.ExistingLiveTrack, false),
            ),
            stopped,
        )
    }

    fun recoverStartup(storedTracks: List<Track>, activeOwnerTrackId: String?): StartupRecoveryResult {
        val recoveredTracks = mutableListOf<Track>()
        val discardedTrackIds = mutableListOf<String>()
        storedTracks.forEach { track ->
            if (track.state != TrackState.Live || track.id == activeOwnerTrackId) {
                recoveredTracks += track
            } else if (track.points.isEmpty()) {
                discardedTrackIds += track.id
            } else {
                recoveredTracks += track.copy(state = TrackState.Interrupted)
            }
        }
        return StartupRecoveryResult(recoveredTracks, discardedTrackIds)
    }
}

data class CaptureSessionState(
    val liveTrack: Track? = null,
    val selectedTrackId: String? = null,
    val setupReadiness: SetupReadiness = SetupReadiness(),
)

data class StartCaptureRequest(
    val trackId: String,
    val fileName: String,
    val message: String,
    val movementType: MovementType,
    val startedAt: Instant,
)

sealed interface SessionResult {
    data class Changed(val state: CaptureSessionState) : SessionResult
    data class Rejected(val reason: SessionRejectionReason) : SessionResult
}

sealed interface AppendPointResult {
    data class Changed(val state: CaptureSessionState) : AppendPointResult
    data class PointRejected(val reason: com.example.stridemap.core.PointRejectionReason) : AppendPointResult
    data object CadenceSkipped : AppendPointResult
    data class Rejected(val reason: SessionRejectionReason) : AppendPointResult
}

sealed interface StopResult {
    data class Stopped(val state: CaptureSessionState, val finalizedTrack: Track) : StopResult
    data class Discarded(val state: CaptureSessionState) : StopResult
    data class Rejected(val reason: SessionRejectionReason) : StopResult
}

enum class SessionRejectionReason {
    SetupNotReady,
    LiveTrackAlreadyExists,
    NoLiveTrack,
}

data class StartupRecoveryResult(
    val tracks: List<Track>,
    val discardedTrackIds: List<String>,
)
