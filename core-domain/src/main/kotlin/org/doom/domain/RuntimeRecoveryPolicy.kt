package org.doom.domain

data class RuntimeRecoveryPolicy(val threshold: Int = 3, val windowMillis: Long = 60_000) {
    init { require(threshold >= 1 && windowMillis > 0) }

    fun recent(timestamps: List<Long>, now: Long) =
        timestamps.filter { it in (now - windowMillis + 1)..now }

    fun quarantined(timestamps: List<Long>, now: Long) = recent(timestamps, now).size >= threshold

    fun qualifies(category: org.doom.model.DiagnosticFailureCategory) = category in setOf(
        org.doom.model.DiagnosticFailureCategory.RUNTIME_FAILURE,
        org.doom.model.DiagnosticFailureCategory.CLEANUP_FAILURE,
        org.doom.model.DiagnosticFailureCategory.TIMEOUT,
        org.doom.model.DiagnosticFailureCategory.RESOURCE_LIMIT
    )
}
