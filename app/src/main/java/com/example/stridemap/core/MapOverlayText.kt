package com.example.stridemap.core

import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object MapOverlayText {
    fun summary(
        track: Track,
        displayDurationSeconds: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String = listOf(
        track.movementType.label,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zoneId).format(track.createdAt),
        formatDistance(track.distanceMeters),
        formatDuration(displayDurationSeconds),
    ).joinToString(" • ")

    private fun formatDistance(meters: Double): String =
        if (meters >= 1000) String.format(Locale.US, "%.2f km", meters / 1000.0) else "${meters.roundToInt()} m"

    private fun formatDuration(seconds: Long): String {
        val duration = Duration.ofSeconds(seconds.coerceAtLeast(0))
        return "%02d:%02d:%02d".format(duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart())
    }
}
