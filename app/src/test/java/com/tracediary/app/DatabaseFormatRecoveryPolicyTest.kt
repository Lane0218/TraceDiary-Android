package com.tracediary.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseFormatRecoveryPolicyTest {
    @Test
    fun missingInstallMarker_preservesRestoredDatabase() {
        assertFalse(shouldDeleteDatabaseForFormat(storedVersion = 0, currentVersion = 3))
    }

    @Test
    fun explicitlyKnownIncompatibleFormat_isDeletedByFirstVersionPolicy() {
        assertTrue(shouldDeleteDatabaseForFormat(storedVersion = 2, currentVersion = 3))
        assertFalse(shouldDeleteDatabaseForFormat(storedVersion = 3, currentVersion = 3))
    }
}
