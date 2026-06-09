package com.example.stridemap.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicTrackStorageLocationTest {
    @Test
    fun mediaStorePublicDocumentsContractUsesStrideMapTracksPath() {
        assertEquals("Documents/StrideMap/Tracks", PublicTrackStorageLocation.DisplayPath)
        assertEquals("Documents/StrideMap/Tracks/", PublicTrackStorageLocation.RelativePath)
        assertEquals("application/gpx+xml", PublicTrackStorageLocation.MimeType)
    }

    @Test
    fun exactFileQueryMatchesRelativePathAndDisplayName() {
        assertTrue(PublicTrackStorageLocation.ExactFileSelection.contains("relative_path = ?"))
        assertTrue(PublicTrackStorageLocation.ExactFileSelection.contains("_display_name = ?"))
        assertArrayEquals(arrayOf("Documents/StrideMap/Tracks/", "track.gpx"), PublicTrackStorageLocation.exactFileArgs("track.gpx"))
    }

    @Test
    fun listQueryIncludesGpxMimeTypeAndExtensionFallback() {
        assertTrue(PublicTrackStorageLocation.ListGpxSelection.contains("relative_path = ?"))
        assertTrue(PublicTrackStorageLocation.ListGpxSelection.contains("mime_type = ?"))
        assertTrue(PublicTrackStorageLocation.ListGpxSelection.contains("_display_name LIKE ?"))
        assertArrayEquals(arrayOf("Documents/StrideMap/Tracks/", "application/gpx+xml", "%.gpx"), PublicTrackStorageLocation.listGpxArgs())
    }

    @Test
    fun directRecoveryScansStrideMapTracksPath() {
        assertEquals("/storage/emulated/0/Documents/StrideMap/Tracks", DirectTrackRecoveryLocation.AbsolutePath)
        assertEquals("/storage/emulated/0/Documents/StrideMap/Tracks/*.gpx", DirectTrackRecoveryLocation.DisplayPath)
    }

    @Test
    fun directRecoveryOnlyIncludesGpxFilenames() {
        assertTrue(DirectTrackRecoveryLocation.isRecoverableGpxFile("track.gpx"))
        assertTrue(DirectTrackRecoveryLocation.isRecoverableGpxFile("TRACK.GPX"))
        assertTrue(!DirectTrackRecoveryLocation.isRecoverableGpxFile("track.txt"))
        assertTrue(!DirectTrackRecoveryLocation.isRecoverableGpxFile(null))
    }

    @Test
    fun recoveredTrackFolderDescribesDirectAllFilesScan() {
        val recovered = RecoveredTrackFolder()

        assertEquals("/storage/emulated/0/Documents/StrideMap/Tracks", recovered.uri)
        assertEquals("All files access direct scan", recovered.displayPath)
    }

    @Test
    fun recoveredTrackRefsAreReadOnlyByDefault() {
        val ref = TrackFileRef(fileName = "track.gpx", uri = "content://tree/track", canDelete = false)

        assertEquals("track.gpx", ref.fileName)
        assertTrue(!ref.canDelete)
    }
}
