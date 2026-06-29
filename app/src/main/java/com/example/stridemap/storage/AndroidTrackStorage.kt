package com.example.stridemap.storage

import android.content.Context
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.example.stridemap.core.ParsedTrackEntry
import com.example.stridemap.core.Track
import com.example.stridemap.gpx.GpxParser
import com.example.stridemap.gpx.GpxWriter
import java.io.File
import java.time.Instant

class AndroidTrackStorage(private val context: Context) : TrackSnapshotStore, AppPrivateSessionJournal {
    private val resolver = context.contentResolver
    private val prefs = context.getSharedPreferences("stridemap_storage", Context.MODE_PRIVATE)
    private val internalRecoveryDir = File(context.filesDir, "StrideMap/Recovery")
    private val journalFile = File(internalRecoveryDir, "active_capture_session.gpx")
    private val collectionUri: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private var appDirectoryScaffold = createAppDirectoryScaffold()

    fun appDirectoryScaffold(): AppDirectoryScaffold {
        if (!appDirectoryScaffold.isReady) appDirectoryScaffold = createAppDirectoryScaffold()
        return appDirectoryScaffold
    }

    override fun target(): TrackFolderTarget? = runCatching {
        TrackFolderTarget(
            uri = collectionUri.toString(),
            displayPath = PublicTrackStorageLocation.DisplayPath,
            isWritable = true,
        )
    }.getOrNull()

    override fun recoveredTrackFolder(): RecoveredTrackFolder? {
        if (!hasAllFilesRecoveryAccess()) return null
        return RecoveredTrackFolder()
    }

    override fun hasAllFilesRecoveryAccess(): Boolean = Environment.isExternalStorageManager()

    override fun writeFullSnapshot(track: Track): TrackFileRef {
        val xml = GpxWriter.write(track)
        require(GpxParser.parse(xml, track.fileName) is ParsedTrackEntry.Valid) { "Generated GPX did not validate" }
        val uri = findFileUri(track.fileName) ?: insertPendingFile(track.fileName)
        try {
            setPending(uri, pending = true)
            writeText(uri, track.fileName, xml)
            require(GpxParser.parse(readText(uri, track.fileName), track.fileName) is ParsedTrackEntry.Valid) { "Canonical GPX did not validate" }
            setPending(uri, pending = false)
            return TrackFileRef(track.fileName, uri.toString())
        } catch (error: Exception) {
            runCatching { setPending(uri, pending = false) }
            throw error
        }
    }

    private fun writeText(uri: Uri, fileName: String, text: String) {
        resolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8).use { writer ->
            requireNotNull(writer) { "Could not open $fileName" }
            writer.write(text)
        }
    }

    private fun readText(uri: Uri, fileName: String): String = resolver.openInputStream(uri)
        ?.bufferedReader(Charsets.UTF_8)
        ?.use { it.readText() }
        ?: error("Could not open $fileName")

    override fun listGpxFiles(): List<TrackFileRef> {
        val byFileName = linkedMapOf<String, TrackFileRef>()
        mediaStoreGpxFiles().forEach { byFileName[it.fileName] = it }
        recoveredGpxFiles().forEach { byFileName.putIfAbsent(it.fileName, it) }
        return byFileName.values.toList()
    }

    private fun mediaStoreGpxFiles(): List<TrackFileRef> {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val sortOrder = "${MediaStore.MediaColumns.DISPLAY_NAME} ASC"
        return resolver.query(
            collectionUri,
            projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND (${MediaStore.MediaColumns.MIME_TYPE} = ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?)",
            PublicTrackStorageLocation.listGpxArgs(),
            sortOrder,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val fileName = cursor.getString(nameColumn)
                    add(TrackFileRef(fileName, ContentUris.withAppendedId(collectionUri, id).toString()))
                }
            }
        }.orEmpty()
    }

    private fun recoveredGpxFiles(): List<TrackFileRef> = runCatching {
        if (!hasAllFilesRecoveryAccess()) return emptyList()
        File(DirectTrackRecoveryLocation.AbsolutePath)
            .listFiles()
            ?.filter { it.isFile && DirectTrackRecoveryLocation.isRecoverableGpxFile(it.name) }
            ?.sortedBy { it.name }
            ?.map { file ->
                TrackFileRef(fileName = file.name, uri = file.toURI().toString(), canDelete = runCatching { safeDirectTrackFile(file.parentFile ?: File(DirectTrackRecoveryLocation.AbsolutePath), file.name); true }.getOrDefault(false))
            }
            .orEmpty()
    }.getOrDefault(emptyList())

    override fun readText(file: TrackFileRef): String {
        val uri = Uri.parse(file.uri)
        if (uri.scheme == "file") {
            return File(requireNotNull(uri.path) { "Could not open ${file.fileName}" }).readText(Charsets.UTF_8)
        }
        return resolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("Could not open ${file.fileName}")
    }

    override fun discardDraft(file: TrackFileRef) {
        if (!file.canDelete) return
        resolver.delete(Uri.parse(file.uri), null, null)
    }

    override fun deleteTrack(file: TrackFileRef) {
        require(file.canDelete) { "Track file is not deletable" }
        val uri = Uri.parse(file.uri)
        if (uri.scheme == "file") {
            val directFile = safeDirectTrackFile(uri, file.fileName)
            require(directFile.delete() || !directFile.exists()) { "Could not delete ${file.fileName}" }
            return
        }
        require(resolver.delete(uri, null, null) > 0) { "Could not delete ${file.fileName}" }
    }

    override fun rewriteTrackWithRename(file: TrackFileRef, updatedTrack: Track): TrackFileRef {
        require(updatedTrack.state != com.example.stridemap.core.TrackState.Live) { "Live tracks cannot be edited" }
        require(file.fileName != updatedTrack.fileName) { "Edited track filename did not change" }
        val xml = GpxWriter.write(updatedTrack)
        require(GpxParser.parse(xml, updatedTrack.fileName) is ParsedTrackEntry.Valid) { "Generated GPX did not validate" }
        val uri = Uri.parse(file.uri)
        return if (uri.scheme == "file") {
            rewriteDirectFile(file, updatedTrack, xml)
        } else {
            rewriteMediaStoreFile(file, updatedTrack, xml)
        }
    }

    private fun rewriteMediaStoreFile(file: TrackFileRef, updatedTrack: Track, xml: String): TrackFileRef {
        val newUri = insertPendingFile(updatedTrack.fileName)
        val newRef = TrackFileRef(updatedTrack.fileName, newUri.toString())
        try {
            writeText(newUri, updatedTrack.fileName, xml)
            require(GpxParser.parse(readText(newUri, updatedTrack.fileName), updatedTrack.fileName) is ParsedTrackEntry.Valid) { "Canonical GPX did not validate" }
            setPending(newUri, pending = false)
            try {
                deleteTrack(file)
            } catch (error: Exception) {
                runCatching { deleteTrack(newRef) }
                throw error
            }
            return newRef
        } catch (error: Exception) {
            runCatching { setPending(newUri, pending = false) }
            runCatching { deleteTrack(newRef) }
            throw error
        }
    }

    private fun rewriteDirectFile(file: TrackFileRef, updatedTrack: Track, xml: String): TrackFileRef {
        require(file.canDelete && hasAllFilesRecoveryAccess()) { "Recovered GPX is not writable" }
        val original = safeDirectTrackFile(Uri.parse(file.uri), file.fileName)
        val target = safeDirectTrackFile(original.parentFile ?: File(DirectTrackRecoveryLocation.AbsolutePath), updatedTrack.fileName)
        require(original == target || !target.exists()) { "${updatedTrack.fileName} already exists" }
        val temp = safeDirectTrackFile(original.parentFile ?: File(DirectTrackRecoveryLocation.AbsolutePath), ".${updatedTrack.fileName.removeSuffix(".gpx")}.tmp.gpx")
        temp.writeText(xml, Charsets.UTF_8)
        try {
            require(GpxParser.parse(temp.readText(Charsets.UTF_8), updatedTrack.fileName) is ParsedTrackEntry.Valid) { "Canonical GPX did not validate" }
            if (original != target) {
                require(temp.renameTo(target)) { "Could not create ${updatedTrack.fileName}" }
                try {
                    require(original.delete() || !original.exists()) { "Could not delete ${file.fileName}" }
                } catch (error: Exception) {
                    if (original.exists()) runCatching { target.delete() }
                    throw error
                }
            } else {
                require(temp.renameTo(original)) { "Could not replace ${file.fileName}" }
            }
            return TrackFileRef(updatedTrack.fileName, target.toURI().toString(), canDelete = true)
        } catch (error: Exception) {
            runCatching { temp.delete() }
            throw error
        }
    }

    override fun activeSession(): SessionJournalEntry? {
        if (!journalFile.exists()) return null
        val sessionId = prefs.getString(KeyJournalSessionId, null) ?: return null
        val targetFileName = prefs.getString(KeyJournalTargetFileName, null) ?: return null
        val targetUri = prefs.getString(KeyJournalTargetUri, null).orEmpty()
        val lastSnapshotWrittenAt = prefs.getString(KeyJournalSnapshotAt, null)?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val parsed = GpxParser.parse(journalFile.readText(Charsets.UTF_8), targetFileName)
        val track = (parsed as? ParsedTrackEntry.Valid)?.track ?: return null
        return SessionJournalEntry(
            sessionId = sessionId,
            target = TrackFileRef(targetFileName, targetUri),
            track = track,
            lastSnapshotWrittenAt = lastSnapshotWrittenAt,
        )
    }

    override fun replaceActiveSession(entry: SessionJournalEntry) {
        journalFile.parentFile?.mkdirs()
        journalFile.writeText(GpxWriter.write(entry.track), Charsets.UTF_8)
        prefs.edit()
            .putString(KeyJournalSessionId, entry.sessionId)
            .putString(KeyJournalTargetFileName, entry.target.fileName)
            .putString(KeyJournalTargetUri, entry.target.uri)
            .putString(KeyJournalSnapshotAt, entry.lastSnapshotWrittenAt?.toString())
            .apply()
    }

    override fun clearActiveSession(sessionId: String) {
        if (prefs.getString(KeyJournalSessionId, null) != sessionId) return
        journalFile.delete()
        prefs.edit()
            .remove(KeyJournalSessionId)
            .remove(KeyJournalTargetFileName)
            .remove(KeyJournalTargetUri)
            .remove(KeyJournalSnapshotAt)
            .apply()
    }

    fun exists(fileName: String): Boolean = findFileUri(fileName) != null

    private fun createAppDirectoryScaffold(): AppDirectoryScaffold {
        val internalRecoveryReady = internalRecoveryDir.mkdirs() || internalRecoveryDir.isDirectory
        return AppDirectoryScaffold(
            internalRecoveryDirReady = internalRecoveryReady,
            mediaStoreTracksTargetReady = target()?.isWritable == true,
        )
    }

    private fun findFileUri(fileName: String): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        return resolver.query(
            collectionUri,
            projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            PublicTrackStorageLocation.exactFileArgs(fileName),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null else ContentUris.withAppendedId(collectionUri, cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)))
        }
    }

    private fun insertPendingFile(fileName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, PublicTrackStorageLocation.MimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, PublicTrackStorageLocation.RelativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return resolver.insert(collectionUri, values) ?: error("Could not create $fileName")
    }

    private fun setPending(uri: Uri, pending: Boolean) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, if (pending) 1 else 0) }
        resolver.update(uri, values, null, null)
    }

    private fun safeDirectTrackFile(uri: Uri, fileName: String): File = safeDirectTrackFile(
        File(requireNotNull(uri.path) { "Could not open $fileName" }).parentFile ?: File(DirectTrackRecoveryLocation.AbsolutePath),
        fileName,
    )

    private fun safeDirectTrackFile(parent: File, fileName: String): File {
        require(DirectTrackRecoveryLocation.isRecoverableGpxFile(fileName)) { "Invalid GPX filename" }
        require('/' !in fileName && '\\' !in fileName && fileName != "." && fileName != "..") { "Invalid GPX filename" }
        val root = File(DirectTrackRecoveryLocation.AbsolutePath).canonicalFile
        val candidate = File(parent, fileName).canonicalFile
        require(candidate.parentFile == root) { "GPX file is outside StrideMap track folder" }
        return candidate
    }

    private companion object {
        const val KeyJournalSessionId = "journal_session_id"
        const val KeyJournalTargetFileName = "journal_target_file_name"
        const val KeyJournalTargetUri = "journal_target_uri"
        const val KeyJournalSnapshotAt = "journal_snapshot_at"
    }
}
