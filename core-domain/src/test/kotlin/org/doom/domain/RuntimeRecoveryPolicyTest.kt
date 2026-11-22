package org.doom.domain

import org.junit.Assert.*
import org.junit.Test
import org.doom.model.DiagnosticFailureCategory

class RuntimeRecoveryPolicyTest {
    @Test fun quarantinesThreeRecentFailuresWithoutCountingExpiredOrFutureRecords() {
        val policy = RuntimeRecoveryPolicy()
        val now = 1_000_000L
        assertFalse(policy.quarantined(listOf(now - 60_000, now - 2, now - 1, now + 1), now))
        assertTrue(policy.quarantined(listOf(now - 59_999, now - 2, now), now))
    }

    @Test fun onlyUnexpectedExecutionFaultsQualifyForSuspension() {
        val policy = RuntimeRecoveryPolicy()
        assertTrue(policy.qualifies(DiagnosticFailureCategory.RUNTIME_FAILURE))
        assertTrue(policy.qualifies(DiagnosticFailureCategory.TIMEOUT))
        assertFalse(policy.qualifies(DiagnosticFailureCategory.PERMISSION_DENIED))
        assertFalse(policy.qualifies(DiagnosticFailureCategory.USER_CANCELLED))
        assertFalse(policy.qualifies(DiagnosticFailureCategory.ADAPTER_DISCONNECTED))
    }
}
