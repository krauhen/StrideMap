package com.example.stridemap.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupReadinessTest {
    @Test
    fun readinessRequiresNoBlockers() {
        val blocked = SetupReadiness(setOf(SetupBlocker.MovementTypeMissing, SetupBlocker.StorageFolderUnavailable))
        assertFalse(blocked.canStart)

        assertTrue(
            blocked
                .withBlocker(SetupBlocker.MovementTypeMissing, blocked = false)
                .withBlocker(SetupBlocker.StorageFolderUnavailable, blocked = false)
                .canStart,
        )
    }
}
