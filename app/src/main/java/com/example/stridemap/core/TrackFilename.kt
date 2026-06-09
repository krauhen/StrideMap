package com.example.stridemap.core

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object TrackFilename {
    private val TimestampFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd_HH-mm-ss'Z'")
        .withZone(ZoneOffset.UTC)

    fun buildBaseName(startedAt: Instant, movementType: MovementType, message: String): String {
        val timestamp = TimestampFormatter.format(startedAt)
        val sanitizedMessage = sanitizeMessagePrefix(message)
        return if (sanitizedMessage.isBlank()) {
            "${timestamp}_${movementType.serialized}.gpx"
        } else {
            "${timestamp}_${movementType.serialized}_$sanitizedMessage.gpx"
        }
    }

    fun uniqueName(baseName: String, exists: (String) -> Boolean): String {
        if (!exists(baseName)) return baseName
        val stem = baseName.removeSuffix(".gpx")
        var counter = 2
        while (true) {
            val candidate = "$stem-$counter.gpx"
            if (!exists(candidate)) return candidate
            counter++
        }
    }

    fun sanitizeMessagePrefix(message: String): String {
        val ascii = Normalizer.normalize(message.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return ascii
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[/\\\\\\p{Cntrl}]"), "")
            .replace(Regex("[^a-z0-9-]"), "")
            .replace(Regex("-+"), "-")
            .trim('-')
            .take(16)
            .trim('-')
    }
}
