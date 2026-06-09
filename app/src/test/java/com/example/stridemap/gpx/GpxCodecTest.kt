package com.example.stridemap.gpx

import com.example.stridemap.core.LocationPoint
import com.example.stridemap.core.MovementType
import com.example.stridemap.core.ParseErrorCategory
import com.example.stridemap.core.ParsedTrackEntry
import com.example.stridemap.core.Track
import com.example.stridemap.core.TrackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GpxCodecTest {
    private val startedAt = Instant.parse("2026-06-08T15:30:04Z")

    @Test
    fun writerParserRoundTripsStrideMapMetadataAndPointExtensions() {
        val track = Track(
            id = "track-1",
            fileName = "2026-06-08_15-30-04Z_walk_walk-to-park.gpx",
            message = "Walk <to> park & back",
            movementType = MovementType.Walk,
            state = TrackState.Live,
            createdAt = startedAt,
            points = listOf(
                LocationPoint(
                    52.5,
                    13.4,
                    startedAt,
                    accuracyMeters = 12.5,
                    speedMetersPerSecond = 1.4,
                    elevationMeters = 44.2,
                ),
                LocationPoint(52.5001, 13.4001, startedAt.plusSeconds(7), accuracyMeters = 8.0),
            ),
        )

        val xml = GpxWriter.write(track)
        assertTrue(xml.contains("xmlns:stridemap=\"${GpxContract.StrideMapNamespace}\""))
        assertTrue(xml.contains("<stridemap:appSchemaVersion>1</stridemap:appSchemaVersion>"))

        val parsed = GpxParser.parse(xml, track.fileName) as ParsedTrackEntry.Valid
        assertEquals(track.fileName, parsed.track.fileName)
        assertEquals(track.message, parsed.track.message)
        assertEquals(MovementType.Walk, parsed.track.movementType)
        assertEquals(TrackState.Live, parsed.track.state)
        assertEquals(2, parsed.track.points.size)
        assertEquals(12.5, parsed.track.points.first().accuracyMeters!!, 0.0)
        assertEquals(1.4, parsed.track.points.first().speedMetersPerSecond!!, 0.0)
        assertEquals(44.2, parsed.track.points.first().elevationMeters!!, 0.0)
        assertEquals(null, parsed.track.points.last().elevationMeters)
    }

    @Test
    fun writerOmitsElevationWhenAbsent() {
        val track = Track(
            id = "no-elevation.gpx",
            fileName = "no-elevation.gpx",
            message = "",
            movementType = MovementType.Walk,
            state = TrackState.Stopped,
            createdAt = startedAt,
            points = listOf(LocationPoint(52.5, 13.4, startedAt)),
        )

        val xml = GpxWriter.write(track)

        assertTrue(!xml.contains("<ele>"))
        val parsed = GpxParser.parse(xml, track.fileName) as ParsedTrackEntry.Valid
        assertEquals(null, parsed.track.points.single().elevationMeters)
    }

    @Test
    fun writerOrdersElevationBeforeTimeAndExtensions() {
        val track = Track(
            id = "ordered-elevation.gpx",
            fileName = "ordered-elevation.gpx",
            message = "",
            movementType = MovementType.Walk,
            state = TrackState.Stopped,
            createdAt = startedAt,
            points = listOf(
                LocationPoint(
                    52.5,
                    13.4,
                    startedAt,
                    accuracyMeters = 3.0,
                    elevationMeters = 44.2,
                ),
            ),
        )

        val trkptXml = GpxWriter.write(track).substringAfter("<trkpt").substringBefore("</trkpt>")

        assertTrue(trkptXml.indexOf("<ele>44.2</ele>") < trkptXml.indexOf("<time>"))
        assertTrue(trkptXml.indexOf("<ele>44.2</ele>") < trkptXml.indexOf("<extensions>"))
    }

    @Test
    fun writerParserRoundTripsEmptyLiveTrack() {
        val track = Track(
            id = "empty-live.gpx",
            fileName = "empty-live.gpx",
            message = "",
            movementType = MovementType.Walk,
            state = TrackState.Live,
            createdAt = startedAt,
            points = emptyList(),
        )

        val parsed = GpxParser.parse(GpxWriter.write(track), track.fileName) as ParsedTrackEntry.Valid

        assertEquals(track.fileName, parsed.track.fileName)
        assertEquals(TrackState.Live, parsed.track.state)
        assertTrue(parsed.track.points.isEmpty())
    }

    @Test
    fun writerParserPreservesCompletedDurationForOnePointStoppedTrack() {
        val track = Track(
            id = "stationary.gpx",
            fileName = "stationary.gpx",
            message = "Stationary capture",
            movementType = MovementType.Walk,
            state = TrackState.Stopped,
            createdAt = startedAt,
            updatedAt = startedAt.plusSeconds(65),
            completedDurationSeconds = 65,
            points = listOf(LocationPoint(52.5, 13.4, startedAt.plusSeconds(2))),
        )

        val parsed = GpxParser.parse(GpxWriter.write(track), track.fileName) as ParsedTrackEntry.Valid

        assertEquals(1, parsed.track.points.size)
        assertEquals(65, parsed.track.durationSeconds)
    }

    @Test
    fun parserReturnsSafeMalformedRepresentation() {
        val result = GpxParser.parse("<not-gpx />", "bad.gpx") as ParsedTrackEntry.Malformed

        assertEquals("bad.gpx", result.error.fileName)
        assertEquals(ParseErrorCategory.InvalidXml, result.error.category)
        assertEquals("File is not a GPX document.", result.error.safeSummary)
    }

    @Test
    fun parserRejectsDoctypeBeforeXmlParser() {
        val result = GpxParser.parse("""
            <!DOCTYPE gpx [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <gpx version="1.1"></gpx>
        """.trimIndent(), "doctype.gpx") as ParsedTrackEntry.Malformed

        assertEquals(ParseErrorCategory.InvalidXml, result.error.category)
    }
}
