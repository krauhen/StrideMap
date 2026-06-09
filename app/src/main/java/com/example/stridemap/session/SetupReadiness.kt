package com.example.stridemap.session

enum class SetupBlocker {
    MovementTypeMissing,
    PreciseLocationMissing,
    ApproximateOnlyLocation,
    BackgroundLocationMissing,
    DeviceLocationDisabled,
    NotificationPermissionMissing,
    AppDirectoriesUnavailable,
    StorageFolderUnavailable,
    ForegroundServiceUnavailable,
    ExistingLiveTrack,
}

data class SetupReadiness(
    val blockers: Set<SetupBlocker> = emptySet(),
) {
    val canStart: Boolean = blockers.isEmpty()

    fun withBlocker(blocker: SetupBlocker, blocked: Boolean): SetupReadiness = copy(
        blockers = if (blocked) blockers + blocker else blockers - blocker,
    )
}
