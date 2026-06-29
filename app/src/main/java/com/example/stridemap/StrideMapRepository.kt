package com.example.stridemap

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.stridemap.capture.CaptureForegroundService
import com.example.stridemap.core.LocationPoint
import com.example.stridemap.core.MalformedTrack
import com.example.stridemap.core.MovementType
import com.example.stridemap.core.ParsedTrackEntry
import com.example.stridemap.core.Track
import com.example.stridemap.core.TrackFilename
import com.example.stridemap.core.TrackSortField
import com.example.stridemap.core.TrackState
import com.example.stridemap.gpx.GpxParser
import com.example.stridemap.location.LocationRequestSpec
import com.example.stridemap.session.AppendPointResult
import com.example.stridemap.session.CaptureSessionController
import com.example.stridemap.session.CaptureSessionState
import com.example.stridemap.session.SetupBlocker
import com.example.stridemap.session.SetupReadiness
import com.example.stridemap.session.StartCaptureRequest
import com.example.stridemap.session.StopResult
import com.example.stridemap.storage.AndroidTrackStorage
import com.example.stridemap.storage.RecoveredTrackFolder
import com.example.stridemap.storage.SessionJournalEntry
import com.example.stridemap.storage.SessionJournalRecoveryPlanner
import com.example.stridemap.storage.TrackFileRef
import java.time.Instant

object StrideMapRepository {
    private const val SettingsPrefsName = "stridemap_user_settings"
    private const val GpsPollingPrefix = "gps_polling_interval_millis_"
    private const val KeyDefaultMovementType = "default_movement_type"
    private const val KeyAfterStartDestination = "after_start_destination"
    private const val KeyDefaultCaptureNote = "default_capture_note"
    private const val KeyDefaultTrackMovementFilter = "default_track_movement_filter"
    private const val KeyDefaultTrackSortField = "default_track_sort_field"
    private const val KeyDefaultTrackSortAscending = "default_track_sort_ascending"
    private const val KeyFollowLiveByDefault = "follow_live_by_default"
    private const val KeyShowSavedPointDots = "show_saved_point_dots"
    private const val KeyRouteLineWidth = "route_line_width"

    private lateinit var appContext: Context
    private lateinit var storage: AndroidTrackStorage
    private val controller = CaptureSessionController()
    private var sessionState = CaptureSessionState()
    private var activeFileRef: TrackFileRef? = null
    private var recoveredOnce = false

    var state by mutableStateOf(AppState())
        private set

    fun initialize(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
            storage = AndroidTrackStorage(appContext)
        }
        val loadedSettings = loadSettings()
        state = state.copy(
            settings = loadedSettings,
            movementType = if (state.liveTrack == null && state.movementType == MovementType.Walk) loadedSettings.defaultMovementType else state.movementType,
            captureMessage = if (state.liveTrack == null && state.captureMessage.isBlank()) loadedSettings.defaultCaptureNote else state.captureMessage,
        )
        refreshSetup()
        if (!recoveredOnce && storage.target()?.isWritable == true) {
            recoverJournalIfStale()
            scanTracks(recoverStaleLive = !CaptureForegroundService.hasActiveOwner())
            recoveredOnce = true
        } else {
            scanTracks(recoverStaleLive = false)
        }
    }

    fun refreshSetup() {
        val appDirectoryScaffold = storage.appDirectoryScaffold()
        val trackFolder = storage.target()
        val blockers = buildSet {
            if (!state.hasMovementType) add(SetupBlocker.MovementTypeMissing)
            if (!hasFineLocation()) {
                if (hasCoarseLocation()) add(SetupBlocker.ApproximateOnlyLocation) else add(SetupBlocker.PreciseLocationMissing)
            }
            if (!hasBackgroundLocation()) add(SetupBlocker.BackgroundLocationMissing)
            if (!isLocationEnabled()) add(SetupBlocker.DeviceLocationDisabled)
            if (!hasNotificationPermission()) add(SetupBlocker.NotificationPermissionMissing)
            if (!appDirectoryScaffold.isReady) add(SetupBlocker.AppDirectoriesUnavailable)
            if (trackFolder?.isWritable != true) add(SetupBlocker.StorageFolderUnavailable)
            if (state.liveTrack != null) add(SetupBlocker.ExistingLiveTrack)
        }
        val readiness = SetupReadiness(blockers)
        sessionState = sessionState.copy(setupReadiness = readiness, liveTrack = state.liveTrack, selectedTrackId = null)
        state = state.copy(
            readiness = readiness,
            trackFolder = trackFolder,
            recoveredTrackFolder = storage.recoveredTrackFolder(),
            hasAllFilesRecoveryAccess = storage.hasAllFilesRecoveryAccess(),
            appDirectoryScaffold = appDirectoryScaffold,
        )
    }

    fun setMovementType(type: MovementType?) {
        state = state.copy(movementType = type)
        refreshSetup()
    }

    fun setMessage(value: String) {
        state = state.copy(captureMessage = value)
    }

    fun clearTransientMessage() {
        state = state.copy(transientMessage = null)
    }

    fun setGpsPollingInterval(type: MovementType, intervalMillis: Long) {
        updateSettings(state.settings.withGpsPollingInterval(type, intervalMillis))
    }

    fun setDefaultMovementType(type: MovementType) {
        updateSettings(state.settings.withDefaultMovementType(type), applyCaptureDefaults = true)
    }

    fun setAfterStartDestination(destination: AfterStartDestination) {
        updateSettings(state.settings.withAfterStartDestination(destination))
    }

    fun setDefaultCaptureNote(note: String) {
        updateSettings(state.settings.withDefaultCaptureNote(note), applyCaptureDefaults = true)
    }

    fun setDefaultTrackMovementFilter(type: MovementType?) {
        updateSettings(state.settings.withDefaultTrackMovementFilter(type))
    }

    fun setDefaultTrackSortField(field: TrackSortField) {
        updateSettings(state.settings.withDefaultTrackSortField(field))
    }

    fun setDefaultTrackSortAscending(ascending: Boolean) {
        updateSettings(state.settings.withDefaultTrackSortAscending(ascending))
    }

    fun setFollowLiveByDefault(enabled: Boolean) {
        updateSettings(state.settings.withFollowLiveByDefault(enabled))
    }

    fun setShowSavedPointDots(enabled: Boolean) {
        updateSettings(state.settings.withShowSavedPointDots(enabled))
    }

    fun setRouteLineWidth(width: Float) {
        updateSettings(state.settings.withRouteLineWidth(width))
    }

    fun locationRequestSpecFor(type: MovementType): LocationRequestSpec = LocationRequestSpec.forMovement(
        type,
        intervalMillisOverride = state.settings.gpsPollingIntervalMillis(type),
    )

    fun startCapture(): Boolean {
        refreshSetup()
        val movementType = state.movementType ?: return fail("Choose a movement type before starting")
        if (!state.readiness.canStart) return fail("Setup is not ready")
        val startedAt = Instant.now()
        val baseName = TrackFilename.buildBaseName(startedAt, movementType, state.captureMessage)
        val fileName = TrackFilename.uniqueName(baseName) { storage.exists(it) }
        val request = StartCaptureRequest(
            trackId = fileName,
            fileName = fileName,
            message = state.captureMessage.trim(),
            movementType = movementType,
            startedAt = startedAt,
        )
        val result = controller.start(sessionState.copy(liveTrack = null), request)
        val nextState = (result as? com.example.stridemap.session.SessionResult.Changed)?.state
            ?: return fail("Could not start capture")
        val liveTrack = requireNotNull(nextState.liveTrack)
        val initialTarget = TrackFileRef(fileName, "")
        return try {
            storage.replaceActiveSession(SessionJournalEntry(liveTrack.id, initialTarget, liveTrack, lastSnapshotWrittenAt = null))
            activeFileRef = storage.writeFullSnapshot(liveTrack)
            storage.replaceActiveSession(SessionJournalEntry(liveTrack.id, requireNotNull(activeFileRef), liveTrack, Instant.now()))
            sessionState = nextState
            state = state.copy(liveTrack = liveTrack, transientMessage = "Capture started")
            refreshSetup()
            val intent = Intent(appContext, CaptureForegroundService::class.java).setAction(CaptureForegroundService.ActionStart)
            ContextCompat.startForegroundService(appContext, intent)
            true
        } catch (error: Exception) {
            activeFileRef?.let { runCatching { storage.discardDraft(it) } }
            storage.clearActiveSession(liveTrack.id)
            activeFileRef = null
            state = state.copy(liveTrack = null)
            refreshSetup()
            fail("Could not start capture: ${error.safeMessage()}")
        }
    }

    fun appendLocationFromService(point: LocationPoint) {
        val result = controller.appendPoint(sessionState.copy(liveTrack = state.liveTrack), point)
        if (result is AppendPointResult.Changed) {
            val updated = requireNotNull(result.state.liveTrack)
            try {
                storage.replaceActiveSession(SessionJournalEntry(updated.id, activeFileRef ?: TrackFileRef(updated.fileName, ""), updated, lastSnapshotWrittenAt = null))
                activeFileRef = storage.writeFullSnapshot(updated)
                storage.replaceActiveSession(SessionJournalEntry(updated.id, requireNotNull(activeFileRef), updated, Instant.now()))
                sessionState = result.state
                state = state.copy(liveTrack = updated, lastAccuracyMeters = point.accuracyMeters)
            } catch (error: Exception) {
                interruptLive("Storage write failed: ${error.safeMessage()}")
            }
        } else if (state.liveTrack != null) {
            state = state.copy(lastAccuracyMeters = point.accuracyMeters)
        }
    }

    fun requestStopService() {
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, CaptureForegroundService::class.java).setAction(CaptureForegroundService.ActionStop),
        )
    }

    fun stopCaptureConfirmed(): Boolean {
        when (val result = controller.confirmStop(sessionState.copy(liveTrack = state.liveTrack))) {
            is StopResult.Stopped -> {
                try {
                    storage.replaceActiveSession(SessionJournalEntry(result.finalizedTrack.id, activeFileRef ?: TrackFileRef(result.finalizedTrack.fileName, ""), result.finalizedTrack, lastSnapshotWrittenAt = null))
                    activeFileRef = storage.writeFullSnapshot(result.finalizedTrack)
                    storage.clearActiveSession(result.finalizedTrack.id)
                } catch (error: Exception) {
                    handleFinalizationFailure(result.finalizedTrack.copy(state = TrackState.Live), "Could not save track: ${error.safeMessage()}")
                    return false
                }
                activeFileRef = null
                sessionState = result.state
                state = state.copy(
                    liveTrack = null,
                    movementType = state.settings.defaultMovementType,
                    captureMessage = state.settings.defaultCaptureNote,
                    transientMessage = "Track saved",
                )
                scanTracks(recoverStaleLive = false)
                refreshSetup()
                return true
            }
            is StopResult.Discarded -> {
                val live = state.liveTrack
                try {
                    activeFileRef?.let(storage::discardDraft)
                    live?.let { storage.clearActiveSession(it.id) }
                } catch (error: Exception) {
                    message("Could not discard empty capture: ${error.safeMessage()}")
                    return false
                }
                activeFileRef = null
                sessionState = result.state
                state = state.copy(
                    liveTrack = null,
                    movementType = state.settings.defaultMovementType,
                    captureMessage = state.settings.defaultCaptureNote,
                    transientMessage = "Empty capture discarded",
                )
                scanTracks(recoverStaleLive = false)
                refreshSetup()
                return true
            }
            else -> message("No active capture")
        }
        return false
    }

    fun interruptLive(reason: String) {
        val live = state.liveTrack ?: return
        val file = activeFileRef
        if (live.points.isEmpty()) {
            file?.let { runCatching { storage.discardDraft(it) } }
            storage.clearActiveSession(live.id)
            state = state.copy(
                liveTrack = null,
                movementType = state.settings.defaultMovementType,
                captureMessage = state.settings.defaultCaptureNote,
                transientMessage = reason,
            )
        } else {
            val interruptedAt = Instant.now()
            val interrupted = live.copy(
                state = TrackState.Interrupted,
                updatedAt = interruptedAt,
                completedDurationSeconds = live.elapsedSecondsAt(interruptedAt),
            )
            runCatching {
                storage.replaceActiveSession(SessionJournalEntry(interrupted.id, file ?: TrackFileRef(interrupted.fileName, ""), interrupted, lastSnapshotWrittenAt = null))
                activeFileRef = storage.writeFullSnapshot(interrupted)
                storage.clearActiveSession(interrupted.id)
            }
            state = state.copy(liveTrack = null, movementType = state.settings.defaultMovementType, captureMessage = state.settings.defaultCaptureNote, transientMessage = reason)
        }
        activeFileRef = null
        refreshSetup()
    }

    fun scanTracks(recoverStaleLive: Boolean = false) {
        state = state.copy(isScanning = true)
        val journalEntry = storage.activeSession()
        val files = storage.listGpxFiles()
        val parsed = files.map { file ->
            runCatching { GpxParser.parse(storage.readText(file), file.fileName) }.getOrElse {
                ParsedTrackEntry.Malformed(MalformedTrack(file.fileName, com.example.stridemap.core.ParseErrorCategory.InvalidXml, "Could not read GPX file."))
            }.let { entry ->
                if (entry is ParsedTrackEntry.Malformed) repairMalformedFromJournal(file, entry, journalEntry) else entry
            }
        }
        val recovered = if (recoverStaleLive) recoverStale(parsed) else parsed
        val validTracks = recovered.filterIsInstance<ParsedTrackEntry.Valid>().map { it.track }
        val liveFromStorage = validTracks.firstOrNull { it.state == TrackState.Live }
        val validIds = validTracks.mapTo(mutableSetOf()) { it.id }
        val liveTrack = state.liveTrack ?: liveFromStorage
        liveTrack?.let { validIds.add(it.id) }
        state = state.copy(
            entries = recovered,
            fileRefsByName = files.associateBy { it.fileName },
            liveTrack = liveTrack,
            displayedTrackIds = state.displayedTrackIds.intersect(validIds),
            isScanning = false,
            transientMessage = if (state.isScanning) state.transientMessage else state.transientMessage,
        )
        refreshSetup()
    }

    fun toggleDisplayed(track: Track) {
        val next = if (track.id in state.displayedTrackIds) {
            state.displayedTrackIds - track.id
        } else {
            state.displayedTrackIds + track.id
        }
        state = state.copy(displayedTrackIds = next, transientMessage = null)
    }

    fun clearDisplayedTracks() {
        if (state.displayedTrackIds.isEmpty()) return
        state = state.clearDisplayedTracks().copy(transientMessage = "Cleared map display")
    }

    fun editTrackMessage(track: Track, message: String): Boolean {
        if (track.state == TrackState.Live) return fail("Stop recording before editing this track")
        val ref = state.fileRefsByName[track.fileName] ?: return fail("Could not find ${track.fileName}")
        val trimmedMessage = message.trim()
        if (trimmedMessage == track.message) {
            state = state.copy(transientMessage = "No message changes to save")
            return true
        }
        val baseName = TrackFilename.buildBaseName(track.createdAt, track.movementType, trimmedMessage)
        val newFileName = TrackFilename.uniqueName(baseName) { candidate -> candidate == track.fileName || storage.exists(candidate) }
        val updatedTrack = track.copy(id = newFileName, fileName = newFileName, message = trimmedMessage)
        return try {
            val updatedRef = storage.rewriteTrackWithRename(ref, updatedTrack)
            state = state.replaceTrackAfterRename(track.id, updatedTrack).copy(
                fileRefsByName = (state.fileRefsByName - ref.fileName) + (updatedRef.fileName to updatedRef),
                transientMessage = "Track message updated",
            )
            true
        } catch (error: Exception) {
            fail("Could not edit track: ${error.safeMessage()}")
        }
    }

    fun deleteTrack(track: Track): Boolean {
        if (track.state == TrackState.Live) return fail("Stop recording before deleting this track")
        val ref = state.fileRefsByName[track.fileName] ?: return fail("Could not find ${track.fileName}")
        return try {
            storage.deleteTrack(ref)
            state = state.removeTrack(track.id).copy(
                fileRefsByName = state.fileRefsByName - ref.fileName,
                transientMessage = "Track deleted",
            )
            true
        } catch (error: Exception) {
            fail("Could not delete track: ${error.safeMessage()}")
        }
    }

    fun deleteMalformedTrack(fileName: String): Boolean {
        val ref = state.fileRefsByName[fileName] ?: return fail("Could not find $fileName")
        return try {
            storage.deleteTrack(ref)
            state = state.removeTrack(fileName).copy(
                fileRefsByName = state.fileRefsByName - ref.fileName,
                transientMessage = "Track deleted",
            )
            true
        } catch (error: Exception) {
            fail("Could not delete track: ${error.safeMessage()}")
        }
    }

    private fun recoverStale(entries: List<ParsedTrackEntry>): List<ParsedTrackEntry> = entries.mapNotNull { entry ->
        val valid = entry as? ParsedTrackEntry.Valid ?: return@mapNotNull entry
        val track = valid.track
        if (track.state != TrackState.Live) return@mapNotNull entry
        if (track.points.isEmpty()) {
            storage.listGpxFiles().firstOrNull { it.fileName == track.fileName }?.let(storage::discardDraft)
            null
        } else {
            val interrupted = track.copy(state = TrackState.Interrupted)
            runCatching {
                storage.replaceActiveSession(SessionJournalEntry(interrupted.id, TrackFileRef(interrupted.fileName, ""), interrupted, lastSnapshotWrittenAt = null))
                val file = storage.writeFullSnapshot(interrupted)
                storage.clearActiveSession(interrupted.id)
                activeFileRef = if (activeFileRef?.fileName == file.fileName) null else activeFileRef
            }
            ParsedTrackEntry.Valid(interrupted)
        }
    }

    private fun recoverJournalIfStale() {
        val entry = storage.activeSession() ?: return
        val plan = SessionJournalRecoveryPlanner.plan(entry, activeOwnerTrackId = CaptureForegroundService.activeOwnerTrackId())
        val discardResult = plan.targetToDiscard?.takeIf { it.uri.isNotBlank() }?.let { runCatching { storage.discardDraft(it) } }
        val writeResult = plan.trackToWrite?.let { runCatching { storage.writeFullSnapshot(it) } }
        val recoverySucceeded = (discardResult?.isSuccess != false) && (writeResult?.isSuccess != false)
        if (plan.shouldClearJournal && recoverySucceeded) storage.clearActiveSession(entry.sessionId)
    }

    private fun repairMalformedFromJournal(
        file: TrackFileRef,
        malformed: ParsedTrackEntry.Malformed,
        journalEntry: SessionJournalEntry?,
    ): ParsedTrackEntry {
        val entry = journalEntry ?: return malformed
        if (entry.target.fileName != file.fileName && entry.track.fileName != file.fileName) return malformed
        if (entry.track.points.isEmpty()) return malformed
        val track = if (entry.track.id == CaptureForegroundService.activeOwnerTrackId()) {
            entry.track
        } else if (entry.track.state == TrackState.Live) {
            entry.track.copy(state = TrackState.Interrupted)
        } else {
            entry.track
        }
        return runCatching {
            val rewritten = storage.writeFullSnapshot(track)
            GpxParser.parse(storage.readText(rewritten), rewritten.fileName)
        }.getOrElse { malformed }
    }

    private fun handleFinalizationFailure(live: Track, reason: String) {
        if (live.points.isEmpty()) {
            activeFileRef?.let { runCatching { storage.discardDraft(it) } }
            storage.clearActiveSession(live.id)
            state = state.copy(liveTrack = null, movementType = state.settings.defaultMovementType, captureMessage = state.settings.defaultCaptureNote, transientMessage = reason)
        } else {
            val interruptedAt = Instant.now()
            val interrupted = live.copy(
                state = TrackState.Interrupted,
                updatedAt = interruptedAt,
                completedDurationSeconds = live.elapsedSecondsAt(interruptedAt),
            )
            runCatching {
                storage.replaceActiveSession(SessionJournalEntry(interrupted.id, activeFileRef ?: TrackFileRef(interrupted.fileName, ""), interrupted, lastSnapshotWrittenAt = null))
                activeFileRef = storage.writeFullSnapshot(interrupted)
                storage.clearActiveSession(interrupted.id)
            }.onFailure {
                storage.replaceActiveSession(SessionJournalEntry(interrupted.id, activeFileRef ?: TrackFileRef(interrupted.fileName, ""), interrupted, lastSnapshotWrittenAt = null))
            }
            state = state.copy(
                entries = state.entries.upsertValidTrack(interrupted),
                liveTrack = null,
                movementType = state.settings.defaultMovementType,
                captureMessage = state.settings.defaultCaptureNote,
                transientMessage = reason,
            )
        }
        activeFileRef = null
        refreshSetup()
    }

    private fun hasFineLocation(): Boolean = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun settingsPrefs(): SharedPreferences = appContext.getSharedPreferences(SettingsPrefsName, Context.MODE_PRIVATE)

    private fun loadSettings(): UserSettings {
        val prefs = settingsPrefs()
        val base = UserSettings.default()
            .withDefaultMovementType(MovementType.fromSerialized(prefs.getString(KeyDefaultMovementType, null).orEmpty()) ?: UserSettings.default().defaultMovementType)
            .withAfterStartDestination(AfterStartDestination.fromSerialized(prefs.getString(KeyAfterStartDestination, null).orEmpty()) ?: UserSettings.default().afterStartDestination)
            .withDefaultCaptureNote(prefs.getString(KeyDefaultCaptureNote, null) ?: UserSettings.default().defaultCaptureNote)
            .withDefaultTrackMovementFilter(MovementType.fromSerialized(prefs.getString(KeyDefaultTrackMovementFilter, null).orEmpty()))
            .withDefaultTrackSortField(trackSortFieldFromSerialized(prefs.getString(KeyDefaultTrackSortField, null).orEmpty()) ?: UserSettings.default().defaultTrackSortField)
            .withDefaultTrackSortAscending(prefs.getBoolean(KeyDefaultTrackSortAscending, UserSettings.default().defaultTrackSortAscending))
            .withFollowLiveByDefault(prefs.getBoolean(KeyFollowLiveByDefault, UserSettings.default().followLiveByDefault))
            .withShowSavedPointDots(prefs.getBoolean(KeyShowSavedPointDots, UserSettings.default().showSavedPointDots))
            .withRouteLineWidth(prefs.getFloat(KeyRouteLineWidth, UserSettings.default().routeLineWidth))
        return MovementType.entries.fold(base) { settings, type ->
            val key = gpsPollingKey(type)
            if (prefs.contains(key)) settings.withGpsPollingInterval(type, prefs.getLong(key, LocationRequestSpec.defaultIntervalMillisForMovement(type))) else settings
        }
    }

    private fun saveSettings(settings: UserSettings) {
        settingsPrefs().edit().apply {
            MovementType.entries.forEach { type -> putLong(gpsPollingKey(type), settings.gpsPollingIntervalMillis(type)) }
            putString(KeyDefaultMovementType, settings.defaultMovementType.serialized)
            putString(KeyAfterStartDestination, settings.afterStartDestination.serialized)
            putString(KeyDefaultCaptureNote, settings.defaultCaptureNote)
            settings.defaultTrackMovementFilter?.let { putString(KeyDefaultTrackMovementFilter, it.serialized) } ?: remove(KeyDefaultTrackMovementFilter)
            putString(KeyDefaultTrackSortField, settings.defaultTrackSortField.name)
            putBoolean(KeyDefaultTrackSortAscending, settings.defaultTrackSortAscending)
            putBoolean(KeyFollowLiveByDefault, settings.followLiveByDefault)
            putBoolean(KeyShowSavedPointDots, settings.showSavedPointDots)
            putFloat(KeyRouteLineWidth, settings.routeLineWidth)
        }.apply()
    }

    private fun updateSettings(settings: UserSettings, applyCaptureDefaults: Boolean = false) {
        saveSettings(settings)
        state = if (applyCaptureDefaults && state.liveTrack == null) {
            state.copy(settings = settings, movementType = settings.defaultMovementType, captureMessage = settings.defaultCaptureNote)
        } else {
            state.copy(settings = settings)
        }
        refreshSetup()
    }

    private fun gpsPollingKey(type: MovementType): String = "$GpsPollingPrefix${type.serialized}"

    private fun trackSortFieldFromSerialized(value: String): TrackSortField? = TrackSortField.entries.firstOrNull { it.name == value }

    private fun hasCoarseLocation(): Boolean = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocation(): Boolean = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun isLocationEnabled(): Boolean {
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun fail(text: String): Boolean {
        message(text)
        return false
    }

    private fun message(text: String) {
        state = state.copy(transientMessage = text)
    }

    private fun Throwable.safeMessage(): String = message?.take(120) ?: javaClass.simpleName
}

enum class AfterStartDestination(val serialized: String, val label: String) {
    Map("map", "Open Map"),
    Capture("capture", "Stay on Capture");

    companion object {
        fun fromSerialized(value: String): AfterStartDestination? = entries.firstOrNull { it.serialized == value }
    }
}

data class AppState(
    val captureMessage: String = "",
    val movementType: MovementType? = MovementType.Walk,
    val readiness: SetupReadiness = SetupReadiness(setOf(SetupBlocker.AppDirectoriesUnavailable, SetupBlocker.StorageFolderUnavailable)),
    val trackFolder: com.example.stridemap.storage.TrackFolderTarget? = null,
    val recoveredTrackFolder: RecoveredTrackFolder? = null,
    val hasAllFilesRecoveryAccess: Boolean = false,
    val appDirectoryScaffold: com.example.stridemap.storage.AppDirectoryScaffold = com.example.stridemap.storage.AppDirectoryScaffold(
        internalRecoveryDirReady = false,
        mediaStoreTracksTargetReady = false,
    ),
    val entries: List<ParsedTrackEntry> = emptyList(),
    val fileRefsByName: Map<String, TrackFileRef> = emptyMap(),
    val displayedTrackIds: Set<String> = emptySet(),
    val liveTrack: Track? = null,
    val isScanning: Boolean = false,
    val lastAccuracyMeters: Double? = null,
    val transientMessage: String? = null,
    val settings: UserSettings = UserSettings.default(),
) {
    val hasMovementType: Boolean = movementType != null
    val displayEntries: List<ParsedTrackEntry>
        get() {
            val live = liveTrack ?: return entries
            val withoutDuplicateLive = entries.filterNot { entry ->
                when (entry) {
                    is ParsedTrackEntry.Valid -> entry.track.id == live.id
                    is ParsedTrackEntry.Malformed -> entry.error.fileName == live.fileName
                }
            }
            return listOf(ParsedTrackEntry.Valid(live)) + withoutDuplicateLive
        }
    val displayedTracks: List<Track>
        get() = displayEntries.mapNotNull { entry ->
            (entry as? ParsedTrackEntry.Valid)?.track?.takeIf { it.id in displayedTrackIds }
        }
}

internal fun List<ParsedTrackEntry>.upsertValidTrack(track: Track): List<ParsedTrackEntry> {
    val replacement = ParsedTrackEntry.Valid(track)
    var replaced = false
    val updated = map { entry ->
        val matches = when (entry) {
            is ParsedTrackEntry.Valid -> entry.track.id == track.id
            is ParsedTrackEntry.Malformed -> entry.error.fileName == track.fileName
        }
        if (matches) {
            replaced = true
            replacement
        } else {
            entry
        }
    }
    return if (replaced) updated else listOf(replacement) + updated
}

internal fun AppState.replaceTrackAfterRename(oldTrackId: String, updatedTrack: Track): AppState {
    val updatedEntries = entries
        .filterNot { entry ->
            when (entry) {
                is ParsedTrackEntry.Valid -> entry.track.id == oldTrackId || entry.track.id == updatedTrack.id
                is ParsedTrackEntry.Malformed -> entry.error.fileName == oldTrackId || entry.error.fileName == updatedTrack.fileName
            }
        }
        .let { listOf(ParsedTrackEntry.Valid(updatedTrack)) + it }
    val migratedDisplayedIds = if (oldTrackId in displayedTrackIds) {
        (displayedTrackIds - oldTrackId) + updatedTrack.id
    } else {
        displayedTrackIds
    } - oldTrackId
    return copy(
        entries = updatedEntries,
        displayedTrackIds = migratedDisplayedIds,
        liveTrack = liveTrack?.takeUnless { it.id == oldTrackId }?.let { if (it.id == updatedTrack.id) updatedTrack else it },
    )
}

internal fun AppState.removeTrack(trackId: String): AppState = copy(
    entries = entries.filterNot { entry ->
        when (entry) {
            is ParsedTrackEntry.Valid -> entry.track.id == trackId
            is ParsedTrackEntry.Malformed -> entry.error.fileName == trackId
        }
    },
    displayedTrackIds = displayedTrackIds - trackId,
    liveTrack = liveTrack?.takeUnless { it.id == trackId },
)

internal fun AppState.clearDisplayedTracks(): AppState = copy(displayedTrackIds = emptySet())

data class UserSettings(
    private val gpsPollingIntervalsMillis: Map<MovementType, Long>,
    val defaultMovementType: MovementType,
    val afterStartDestination: AfterStartDestination,
    val defaultCaptureNote: String,
    val defaultTrackMovementFilter: MovementType?,
    val defaultTrackSortField: TrackSortField,
    val defaultTrackSortAscending: Boolean,
    val followLiveByDefault: Boolean,
    val showSavedPointDots: Boolean,
    val routeLineWidth: Float,
) {
    fun gpsPollingIntervalMillis(type: MovementType): Long = gpsPollingIntervalsMillis[type]
        ?: LocationRequestSpec.defaultIntervalMillisForMovement(type)

    fun withGpsPollingInterval(type: MovementType, intervalMillis: Long): UserSettings = copy(
        gpsPollingIntervalsMillis = gpsPollingIntervalsMillis + (type to LocationRequestSpec.clampIntervalMillis(intervalMillis)),
    )

    fun withDefaultMovementType(type: MovementType): UserSettings = copy(defaultMovementType = type)

    fun withAfterStartDestination(destination: AfterStartDestination): UserSettings = copy(afterStartDestination = destination)

    fun withDefaultCaptureNote(note: String): UserSettings = copy(defaultCaptureNote = note.trim().take(MaxCaptureNoteLength))

    fun withDefaultTrackMovementFilter(type: MovementType?): UserSettings = copy(defaultTrackMovementFilter = type)

    fun withDefaultTrackSortField(field: TrackSortField): UserSettings = copy(defaultTrackSortField = field)

    fun withDefaultTrackSortAscending(ascending: Boolean): UserSettings = copy(defaultTrackSortAscending = ascending)

    fun withFollowLiveByDefault(enabled: Boolean): UserSettings = copy(followLiveByDefault = enabled)

    fun withShowSavedPointDots(enabled: Boolean): UserSettings = copy(showSavedPointDots = enabled)

    fun withRouteLineWidth(width: Float): UserSettings = copy(routeLineWidth = sanitizeRouteLineWidth(width))

    companion object {
        const val MinRouteLineWidth = 4f
        const val MaxRouteLineWidth = 16f
        private const val MaxCaptureNoteLength = 120

        private fun sanitizeRouteLineWidth(width: Float): Float = if (width.isFinite()) {
            width.coerceIn(MinRouteLineWidth, MaxRouteLineWidth)
        } else {
            default().routeLineWidth
        }

        fun default(): UserSettings = UserSettings(
            gpsPollingIntervalsMillis = MovementType.entries.associateWith(LocationRequestSpec::defaultIntervalMillisForMovement),
            defaultMovementType = MovementType.Walk,
            afterStartDestination = AfterStartDestination.Map,
            defaultCaptureNote = "",
            defaultTrackMovementFilter = null,
            defaultTrackSortField = TrackSortField.Date,
            defaultTrackSortAscending = false,
            followLiveByDefault = true,
            showSavedPointDots = true,
            routeLineWidth = 8f,
        )
    }
}
