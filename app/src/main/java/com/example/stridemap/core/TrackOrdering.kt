package com.example.stridemap.core

enum class TrackSortField { Date, Distance }

object TrackOrdering {
    fun sort(
        tracks: List<Track>,
        movementType: MovementType?,
        sortField: TrackSortField,
        ascending: Boolean,
    ): List<Track> {
        val filtered = tracks.filter { movementType == null || it.movementType == movementType }
        val liveFirst = compareByDescending<Track> { it.state == TrackState.Live }
        val comparator = when (sortField) {
            TrackSortField.Date -> compareBy<Track> { it.createdAt }
            TrackSortField.Distance -> compareBy { it.distanceMeters }
        }
        return filtered.sortedWith(liveFirst.then(if (ascending) comparator else comparator.reversed()))
    }
}
