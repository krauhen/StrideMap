package com.example.stridemap.gpx

import com.example.stridemap.core.LocationPoint
import com.example.stridemap.core.MalformedTrack
import com.example.stridemap.core.MovementType
import com.example.stridemap.core.ParseErrorCategory
import com.example.stridemap.core.ParsedTrackEntry
import com.example.stridemap.core.PointValidationResult
import com.example.stridemap.core.PointValidator
import com.example.stridemap.core.Track
import com.example.stridemap.core.TrackState
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.Instant
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

object GpxContract {
    const val GpxNamespace = "http://www.topografix.com/GPX/1/1"
    const val StrideMapNamespace = "https://stridemap.app/gpx/1"
    const val AppSchemaVersion = 1
}

object GpxWriter {
    fun write(track: Track): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<gpx version=\"1.1\" creator=\"StrideMap\" xmlns=\"${GpxContract.GpxNamespace}\" xmlns:stridemap=\"${GpxContract.StrideMapNamespace}\">")
        appendLine("  <metadata>")
        appendLine("    <time>${xml(track.createdAt.toString())}</time>")
        appendLine("    <extensions>")
        appendExtension("movementType", track.movementType.serialized, 3)
        appendExtension("trackState", track.state.serialized, 3)
        appendExtension("message", track.message, 3)
        appendExtension("distanceMeters", track.distanceMeters.toString(), 3)
        appendExtension("durationSeconds", track.durationSeconds.toString(), 3)
        appendExtension("appSchemaVersion", GpxContract.AppSchemaVersion.toString(), 3)
        appendLine("    </extensions>")
        appendLine("  </metadata>")
        appendLine("  <trk>")
        appendLine("    <name>${xml(track.fileName.removeSuffix(".gpx"))}</name>")
        appendLine("    <type>${xml(track.movementType.serialized)}</type>")
        appendLine("    <trkseg>")
        track.points.forEach { point ->
            appendLine("      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">")
            point.elevationMeters?.let { appendLine("        <ele>${it}</ele>") }
            appendLine("        <time>${xml(point.timestamp.toString())}</time>")
            if (point.accuracyMeters != null || point.speedMetersPerSecond != null) {
                appendLine("        <extensions>")
                point.accuracyMeters?.let { appendExtension("accuracyMeters", it.toString(), 5) }
                point.speedMetersPerSecond?.let { appendExtension("speedMetersPerSecond", it.toString(), 5) }
                appendLine("        </extensions>")
            }
            appendLine("      </trkpt>")
        }
        appendLine("    </trkseg>")
        appendLine("  </trk>")
        appendLine("</gpx>")
    }

    private fun StringBuilder.appendExtension(name: String, value: String, indentLevels: Int) {
        appendLine("  ".repeat(indentLevels) + "<stridemap:$name>${xml(value)}</stridemap:$name>")
    }

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

object GpxParser {
    fun parse(xml: String, fileName: String): ParsedTrackEntry {
        if (xml.contains("<!DOCTYPE", ignoreCase = true)) {
            return malformed(fileName, ParseErrorCategory.InvalidXml, "Could not read GPX XML.")
        }
        val document = try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeatureIfSupported(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeatureIfSupported("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeatureIfSupported("http://xml.org/sax/features/external-general-entities", false)
                setFeatureIfSupported("http://xml.org/sax/features/external-parameter-entities", false)
                disableXIncludeIfSupported()
                disableEntityExpansionIfSupported()
            }
            factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        } catch (_: Exception) {
            return malformed(fileName, ParseErrorCategory.InvalidXml, "Could not read GPX XML.")
        }

        val root = document.documentElement
        if (root.localName != "gpx") return malformed(fileName, ParseErrorCategory.InvalidXml, "File is not a GPX document.")

        val metadata = root.firstChildElement("metadata")
            ?: return malformed(fileName, ParseErrorCategory.MissingMetadata, "Could not read GPX metadata.")
        val extensions = metadata.firstChildElement("extensions")
            ?: return malformed(fileName, ParseErrorCategory.MissingMetadata, "Could not read StrideMap metadata.")

        val movementType = extensions.strideText("movementType")?.let(MovementType::fromSerialized)
            ?: return malformed(fileName, ParseErrorCategory.InvalidMetadata, "Could not read movement type.")
        val trackState = extensions.strideText("trackState")?.let(TrackState::fromSerialized)
            ?: return malformed(fileName, ParseErrorCategory.InvalidMetadata, "Could not read track state.")
        val schemaVersion = extensions.strideText("appSchemaVersion")?.toIntOrNull()
            ?: return malformed(fileName, ParseErrorCategory.InvalidMetadata, "Could not read schema version.")
        if (schemaVersion != GpxContract.AppSchemaVersion) {
            return malformed(fileName, ParseErrorCategory.InvalidMetadata, "Unsupported StrideMap GPX schema version.")
        }

        val pointElements = root.descendantElements("trkpt")
        val points = pointElements.mapIndexed { index, element ->
            val point = element.toLocationPoint() ?: return malformed(
                fileName,
                ParseErrorCategory.InvalidPoint,
                "Could not read GPX point ${index + 1}.",
            )
            val previous = if (index == 0) null else pointElements[index - 1].toLocationPoint()
            val validation = PointValidator.validate(point, previous)
            if (validation is PointValidationResult.Rejected) {
                return malformed(fileName, ParseErrorCategory.InvalidPoint, "GPX point ${index + 1} is invalid.")
            }
            point
        }

        val createdAt = metadata.firstChildElement("time")?.textContent?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: points.firstOrNull()?.timestamp
            ?: Instant.EPOCH
        val completedDurationSeconds = extensions.strideText("durationSeconds")
            ?.toLongOrNull()
            ?.coerceAtLeast(0)
            ?.takeIf { trackState != TrackState.Live }

        return ParsedTrackEntry.Valid(
            Track(
                id = fileName,
                fileName = fileName,
                message = extensions.strideText("message").orEmpty(),
                movementType = movementType,
                state = trackState,
                points = points,
                createdAt = createdAt,
                completedDurationSeconds = completedDurationSeconds,
            ),
        )
    }

    private fun malformed(fileName: String, category: ParseErrorCategory, summary: String): ParsedTrackEntry.Malformed =
        ParsedTrackEntry.Malformed(MalformedTrack(fileName = fileName, category = category, safeSummary = summary))

    private fun Element.toLocationPoint(): LocationPoint? {
        val latitude = getAttribute("lat").toDoubleOrNull() ?: return null
        val longitude = getAttribute("lon").toDoubleOrNull() ?: return null
        val timestamp = firstChildElement("time")?.textContent?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        val elevation = firstChildElement("ele")?.textContent?.toDoubleOrNull()?.takeIf { it.isFinite() }
        val extensions = firstChildElement("extensions")
        return LocationPoint(
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            accuracyMeters = extensions?.strideText("accuracyMeters")?.toDoubleOrNull(),
            speedMetersPerSecond = extensions?.strideText("speedMetersPerSecond")?.toDoubleOrNull(),
            elevationMeters = elevation,
        )
    }

    private fun Element.firstChildElement(localName: String): Element? = childNodes.asSequence()
        .filterIsInstance<Element>()
        .firstOrNull { it.localName == localName || it.nodeName == localName }

    private fun Element.descendantElements(localName: String): List<Element> = getElementsByTagNameNS("*", localName).asSequence()
        .filterIsInstance<Element>()
        .toList()

    private fun Element.strideText(localName: String): String? = childNodes.asSequence()
        .filterIsInstance<Element>()
        .firstOrNull { it.localName == localName && it.namespaceURI == GpxContract.StrideMapNamespace }
        ?.textContent

    private fun DocumentBuilderFactory.setFeatureIfSupported(feature: String, value: Boolean) {
        runCatching { setFeature(feature, value) }
    }

    private fun DocumentBuilderFactory.disableXIncludeIfSupported() {
        runCatching { isXIncludeAware = false }
    }

    private fun DocumentBuilderFactory.disableEntityExpansionIfSupported() {
        runCatching { isExpandEntityReferences = false }
    }

    private fun org.w3c.dom.NodeList.asSequence(): Sequence<org.w3c.dom.Node> = sequence {
        for (index in 0 until length) yield(item(index))
    }
}
