package com.example.stridemap.storage

import com.example.stridemap.core.Track
import com.example.stridemap.core.TrackState
import java.time.Instant

object PublicTrackStorageLocation {
    const val DisplayPath = "Documents/StrideMap/Tracks"
    const val RelativePath = "Documents/StrideMap/Tracks/"
    const val MimeType = "application/gpx+xml"

    const val ExactFileSelection = "relative_path = ? AND _display_name = ?"
    const val ListGpxSelection = "relative_path = ? AND (mime_type = ? OR _display_name LIKE ?)"

    fun exactFileArgs(fileName: String): Array<String> = arrayOf(RelativePath, fileName)

    fun listGpxArgs(): Array<String> = arrayOf(RelativePath, MimeType, "%.gpx")
}

object DirectTrackRecoveryLocation {
    const val AbsolutePath = "/storage/emulated/0/Documents/StrideMap/Tracks"
    const val DisplayPath = "/storage/emulated/0/Documents/StrideMap/Tracks/*.gpx"

    fun isRecoverableGpxFile(fileName: String?): Boolean = fileName?.endsWith(".gpx", ignoreCase = true) == true
}

data class TrackFolderTarget(
    val uri: String,
    val displayPath: String = "Documents/StrideMap/Tracks",
    val isWritable: Boolean,
)

data class RecoveredTrackFolder(
    val uri: String = DirectTrackRecoveryLocation.AbsolutePath,
    val displayPath: String = "All files access direct scan",
)

data class AppDirectoryScaffold(
    val internalRecoveryDirReady: Boolean,
    val mediaStoreTracksTargetReady: Boolean,
) {
    val isReady: Boolean = internalRecoveryDirReady && mediaStoreTracksTargetReady
}

data class TrackFileRef(
    val fileName: String,
    val uri: String,
    val canDelete: Boolean = true,
)

data class SessionJournalEntry(
    val sessionId: String,
    val target: TrackFileRef,
    val track: Track,
    val lastSnapshotWrittenAt: Instant?,
)

interface TrackSnapshotStore {
    fun target(): TrackFolderTarget?
    fun recoveredTrackFolder(): RecoveredTrackFolder?
    fun hasAllFilesRecoveryAccess(): Boolean
    fun writeFullSnapshot(track: Track): TrackFileRef
    fun listGpxFiles(): List<TrackFileRef>
    fun readText(file: TrackFileRef): String
    fun discardDraft(file: TrackFileRef)
}

interface AppPrivateSessionJournal {
    fun activeSession(): SessionJournalEntry?
    fun replaceActiveSession(entry: SessionJournalEntry)
    fun clearActiveSession(sessionId: String)
}

data class StorageRecoveryPlan(
    val visibleTarget: TrackFolderTarget?,
    val journalEntry: SessionJournalEntry?,
) {
    val canWriteVisibleGpx: Boolean = visibleTarget?.isWritable == true
}

data class StartupJournalRecovery(
    val trackToWrite: Track?,
    val targetToDiscard: TrackFileRef?,
    val shouldClearJournal: Boolean,
)

object SessionJournalRecoveryPlanner {
    fun plan(entry: SessionJournalEntry?, activeOwnerTrackId: String?): StartupJournalRecovery {
        if (entry == null || entry.track.id == activeOwnerTrackId) {
            return StartupJournalRecovery(trackToWrite = null, targetToDiscard = null, shouldClearJournal = false)
        }
        return if (entry.track.points.isEmpty()) {
            StartupJournalRecovery(trackToWrite = null, targetToDiscard = entry.target, shouldClearJournal = true)
        } else {
            StartupJournalRecovery(
                trackToWrite = entry.track.copy(state = if (entry.track.state == TrackState.Live) TrackState.Interrupted else entry.track.state),
                targetToDiscard = null,
                shouldClearJournal = true,
            )
        }
    }
}
