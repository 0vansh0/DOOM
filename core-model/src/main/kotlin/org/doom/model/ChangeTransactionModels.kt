package org.doom.model

enum class ChangeTransactionStatus {
    PREPARED, APPLIED, RESTORING, RESTORED, FAILED, CONFLICT, UNCERTAIN
}

enum class RestorePolicy { EXPLICIT_USER_ACTION, ON_MOD_DISABLE }

data class PrivilegedOperationDefinition(
    val id: String,
    val version: Int,
    val requiredCapability: String,
    val minimumAndroidApi: Int,
    val minimumPrivilege: AdapterPrivilegeLevel,
    val risk: CapabilityRisk,
    val confirmationRequired: Boolean,
    val modAccessible: Boolean,
    val targetId: String,
    val requiredAdapter: String? = null,
    val requiredAndroidPermissions: List<String> = emptyList(),
    val parameterSchema: Map<String, CapabilityParameterDefinition> = emptyMap(),
    val preconditions: List<String> = emptyList(),
    val postconditions: List<String> = emptyList(),
    val callsPerMinute: Int = 1,
    val timeoutMillis: Long = 5_000,
    val auditRequired: Boolean = true,
    val minimumValue: Long? = null,
    val maximumValue: Long? = null,
    val restorePolicy: RestorePolicy
)

data class ChangeTransaction(
    val transactionId: String,
    val modId: String,
    val operationId: String,
    val operationVersion: Int,
    val targetIdentity: String,
    val originalState: String,
    val requestedState: String,
    val appliedState: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val status: ChangeTransactionStatus,
    val failureReason: String? = null,
    val recoveryMetadata: String? = null
)

data class OperationRequest(
    val modId: String,
    val operationId: String,
    val requestedValue: Long,
    val userConfirmed: Boolean,
    val modEnabled: Boolean,
    val safeMode: Boolean
)

data class ChangeOperationResult(
    val status: ChangeTransactionStatus?,
    val transaction: ChangeTransaction? = null,
    val message: String
)

data class SystemSettingReadResult(
    val operationId: String,
    val value: Long?,
    val message: String,
    val succeeded: Boolean
)

object PrivilegedOperationCatalog {
    const val SCREEN_TIMEOUT_TARGET = "system.screen_off_timeout"
    const val SCREEN_TIMEOUT_READ = "system.screen_timeout.read"
    const val SCREEN_TIMEOUT_SET = "system.screen_timeout.set"
    const val SCREEN_TIMEOUT_RESTORE = "system.screen_timeout.restore"

}

interface ChangeTransactionStore {
    suspend fun insert(transaction: ChangeTransaction)
    suspend fun update(transaction: ChangeTransaction)
    suspend fun find(transactionId: String): ChangeTransaction?
    suspend fun activeForTarget(targetIdentity: String): List<ChangeTransaction>
    suspend fun unresolved(): List<ChangeTransaction>
}

interface SystemSettingAccess {
    suspend fun read(targetIdentity: String): Long
    suspend fun write(targetIdentity: String, value: Long)
    fun isAuthorized(targetIdentity: String): Boolean
}

fun interface OperationApproval {
    fun isApproved(modId: String, operationId: String): Boolean
}

fun interface OperationAdapterGate {
    fun currentStatus(adapterId: String): AdapterStatus?
}
