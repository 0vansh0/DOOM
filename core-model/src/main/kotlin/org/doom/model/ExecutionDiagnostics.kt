package org.doom.model

/** Stable local diagnostic vocabulary. Messages must never contain event payloads or private paths. */
enum class DiagnosticSeverity { INFO, WARNING, ERROR }
enum class DiagnosticResult { SUCCESS, BLOCKED, FAILED, CANCELLED, SKIPPED, UNKNOWN, RECOVERY_REQUIRED }
enum class DiagnosticFailureCategory {
    NONE, RUNTIME_FAILURE, INVALID_PARAMETERS, CAPABILITY_DENIED, PERMISSION_DENIED,
    USER_CANCELLED, ADAPTER_DISCONNECTED, RATE_LIMITED, RESOURCE_LIMIT, TIMEOUT,
    CLEANUP_FAILURE, INTERRUPTED, RECOVERY_CONFLICT, STORAGE_FAILURE, UNKNOWN
}

data class ExecutionDiagnostic(
    val id: Long,
    val timestamp: Long,
    val modId: String,
    val runtimeSessionId: String?,
    val workflowId: String?,
    val triggerOrOperationId: String?,
    val severity: DiagnosticSeverity,
    val result: DiagnosticResult,
    val failureCategory: DiagnosticFailureCategory,
    val message: String,
    val durationMillis: Long?,
    val resourceOwner: String?
)

data class WorkflowMonitorEntry(
    val modId: String,
    val workflowId: String,
    val sessionId: String,
    val pendingActionIds: List<String>,
    val runningActionIds: List<String>,
    val startedAtMillis: Long
)
