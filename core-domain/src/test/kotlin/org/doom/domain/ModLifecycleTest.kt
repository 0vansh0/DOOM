package org.doom.domain

import org.doom.model.ModStatus.*
import org.junit.Assert.*
import org.junit.Test

class ModLifecycleTest {
    @Test fun requiresOrderedChecksBeforeReadiness() {
        assertTrue(ModLifecycle.allows(INSTALLED_DISABLED, ENABLE_REQUESTED))
        assertTrue(ModLifecycle.allows(ENABLE_REQUESTED, CHECKING_REQUIREMENTS))
        assertTrue(ModLifecycle.allows(CHECKING_REQUIREMENTS, READY_TO_ACTIVATE))
        assertTrue(ModLifecycle.allows(CHECKING_REQUIREMENTS, PERMISSION_REQUIRED))
        assertTrue(ModLifecycle.allows(CHECKING_REQUIREMENTS, INCOMPATIBLE))
        assertFalse(ModLifecycle.allows(INSTALLED_DISABLED, READY_TO_ACTIVATE))
        assertFalse(ModLifecycle.allows(ENABLE_REQUESTED, READY_TO_ACTIVATE))
        assertFalse(ModLifecycle.allows(PERMISSION_REQUIRED, READY_TO_ACTIVATE))
    }

    @Test fun permitsCancellationAndRetryWithoutRevivingUninstallingRows() {
        assertTrue(ModLifecycle.allows(CHECKING_REQUIREMENTS, INSTALLED_DISABLED))
        assertTrue(ModLifecycle.allows(ERROR, ENABLE_REQUESTED))
        assertTrue(ModLifecycle.allows(READY_TO_ACTIVATE, UNINSTALLING))
        assertTrue(ModLifecycle.allows(UNINSTALLING, ERROR))
        assertFalse(ModLifecycle.allows(UNINSTALLING, INSTALLED_DISABLED))
        assertFalse(ModLifecycle.allows(UNINSTALLING, ENABLE_REQUESTED))
        assertFalse(ModLifecycle.allows(UNINSTALLING, UNINSTALLING))
    }
}
