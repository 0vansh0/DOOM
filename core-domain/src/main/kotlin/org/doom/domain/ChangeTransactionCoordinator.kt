package org.doom.domain

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.doom.model.*

/** Coordinates a single allowlisted scalar setting. No operation name or target comes from a mod. */
class ChangeTransactionCoordinator(
    private val store: ChangeTransactionStore,
    private val access: SystemSettingAccess,
    private val approval: OperationApproval,
    private val operations: Map<String, PrivilegedOperationDefinition>,
    private val androidApi: Int,
    private val adapterGate: OperationAdapterGate = OperationAdapterGate { null },
    private val safeModeEnabled: suspend () -> Boolean = { false },
    private val now: () -> Long = System::currentTimeMillis,
    private val id: () -> String = { UUID.randomUUID().toString() }
) {
    private val targetLocks = mutableMapOf<String, Mutex>()
    private val rateLock = Any()
    private val operationTimes = mutableMapOf<String, ArrayDeque<Long>>()

    suspend fun read(operationId: String): SystemSettingReadResult {
        val definition = operations[operationId]
            ?: return SystemSettingReadResult(operationId, null, "Unsupported operation.", false)
        if (operationId != PrivilegedOperationCatalog.SCREEN_TIMEOUT_READ) {
            return SystemSettingReadResult(operationId, null, "This operation is not a read operation.", false)
        }
        if (androidApi < definition.minimumAndroidApi) return SystemSettingReadResult(operationId, null,
            "Android API ${definition.minimumAndroidApi} is required.", false)
        adapterFailure(definition)?.let { return SystemSettingReadResult(operationId, null, it, false) }
        if (!withinRateLimit("__doom_manual__", definition)) return SystemSettingReadResult(operationId, null,
            "Operation rate limit reached; try again later.", false)
        return try {
            SystemSettingReadResult(operationId, withTimeout(definition.timeoutMillis) { access.read(definition.targetId) },
                "Screen timeout read.", true)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { SystemSettingReadResult(operationId, null, "Could not read screen timeout.", false) }
    }

    suspend fun apply(request: OperationRequest): ChangeOperationResult {
        val definition = operations[request.operationId]
            ?: return rejected("Unsupported operation.")
        if (request.operationId != PrivilegedOperationCatalog.SCREEN_TIMEOUT_SET) return rejected("This operation is read-only or not available for writes.")
        if (definition.modAccessible) return rejected("Operation is not approved for mod execution.")
        if (androidApi < definition.minimumAndroidApi) return rejected("Android API ${definition.minimumAndroidApi} is required.")
        adapterFailure(definition)?.let { return rejected(it) }
        if (!request.userConfirmed || !definition.confirmationRequired) return rejected("Explicit confirmation is required.")
        if (request.safeMode || safeModeEnabled()) return rejected("Safe mode blocks system changes.")
        if (definition.modAccessible && !request.modEnabled) return rejected("The requesting mod is not active.")
        if (definition.modAccessible && !approval.isApproved(request.modId, request.operationId)) return rejected("This operation is not approved for the requesting mod.")
        if (request.requestedValue !in requireNotNull(definition.minimumValue)..requireNotNull(definition.maximumValue)) {
            return rejected("Screen timeout must be between 15 seconds and 30 minutes.")
        }
        if (!access.isAuthorized(definition.targetId)) return rejected("Android write access for system settings is not granted.")

        return lock(definition.targetId).withLock {
            if (!withinRateLimit(request.modId, definition)) return@withLock rejected("Operation rate limit reached; try again later.")
            val previous = store.activeForTarget(definition.targetId)
            if (previous.isNotEmpty()) return@withLock rejected("Another DOOM change owns this setting; restore or resolve it first.")
            val original = try { withTimeout(definition.timeoutMillis) { access.read(definition.targetId) } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@withLock rejected("Could not read the original screen timeout; no change was attempted.") }
            val timestamp = now()
            val transaction = ChangeTransaction(
                transactionId = id(), modId = request.modId, operationId = request.operationId,
                operationVersion = definition.version, targetIdentity = definition.targetId,
                originalState = original.toString(), requestedState = request.requestedValue.toString(),
                createdAt = timestamp, updatedAt = timestamp, status = ChangeTransactionStatus.PREPARED,
                recoveryMetadata = "public_settings_system"
            )
            // Durable recovery state is committed before the external setting is touched.
            store.insert(transaction)
            adapterFailure(definition)?.let { reason ->
                val failed = transaction.copy(updatedAt = now(), status = ChangeTransactionStatus.FAILED, failureReason = reason)
                store.update(failed)
                return@withLock ChangeOperationResult(failed.status, failed, reason)
            }
            if (!access.isAuthorized(definition.targetId) || safeModeEnabled()) {
                val failed = transaction.copy(updatedAt = now(), status = ChangeTransactionStatus.FAILED,
                    failureReason = if (!access.isAuthorized(definition.targetId)) "Android authorization was revoked before the write; no change was attempted."
                    else "Safe mode became active before the write; no change was attempted.")
                store.update(failed)
                return@withLock ChangeOperationResult(failed.status, failed, failed.failureReason!!)
            }
            try {
                withTimeout(definition.timeoutMillis) { access.write(definition.targetId, request.requestedValue) }
                val observed = withTimeout(definition.timeoutMillis) { access.read(definition.targetId) }
                if (observed != request.requestedValue) {
                    val failed = transaction.copy(updatedAt = now(), status = ChangeTransactionStatus.UNCERTAIN,
                        failureReason = "Postcondition mismatch: requested value was not observed.")
                    store.update(failed)
                    return@withLock ChangeOperationResult(failed.status, failed, failed.failureReason!!)
                }
                val applied = transaction.copy(appliedState = observed.toString(), updatedAt = now(), status = ChangeTransactionStatus.APPLIED)
                store.update(applied)
                ChangeOperationResult(applied.status, applied, "Screen timeout changed and verified.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val uncertain = transaction.copy(updatedAt = now(), status = ChangeTransactionStatus.UNCERTAIN,
                    failureReason = "Write outcome is uncertain; inspect the setting before recovery.")
                store.update(uncertain)
                ChangeOperationResult(uncertain.status, uncertain, uncertain.failureReason!!)
            }
        }
    }

    suspend fun restore(transactionId: String, userConfirmed: Boolean): ChangeOperationResult {
        val transaction = store.find(transactionId) ?: return rejected("Recovery record not found.")
        if (transaction.status == ChangeTransactionStatus.RESTORED) return ChangeOperationResult(transaction.status, transaction, "Already restored.")
        if (transaction.status !in setOf(ChangeTransactionStatus.APPLIED, ChangeTransactionStatus.CONFLICT) || transaction.appliedState == null)
            return rejected("Only a verified applied change can be restored.")
        if (!userConfirmed) return rejected("Explicit confirmation is required to restore this setting.")
        val definition = operations[transaction.operationId]
            ?: return rejected("The operation is no longer supported.")
        val restoreDefinition = operations[PrivilegedOperationCatalog.SCREEN_TIMEOUT_RESTORE]
            ?: return rejected("Restore operation is unavailable.")
        if (definition.modAccessible && !approval.isApproved(transaction.modId, PrivilegedOperationCatalog.SCREEN_TIMEOUT_SET)) return rejected("Operation approval was revoked.")
        adapterFailure(restoreDefinition)?.let { return rejected(it) }
        if (!access.isAuthorized(transaction.targetIdentity)) return rejected("Android write access is not currently granted.")

        return lock(transaction.targetIdentity).withLock {
            if (!withinRateLimit(transaction.modId, restoreDefinition)) return@withLock rejected("Operation rate limit reached; try again later.")
            if (store.activeForTarget(transaction.targetIdentity).any { it.transactionId != transactionId }) {
                return@withLock conflict(transaction, "Another transaction owns this setting. Resolve it first.")
            }
            val current = try { withTimeout(definition.timeoutMillis) { access.read(transaction.targetIdentity) } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@withLock uncertain(transaction, "Could not read current setting before restore.") }
            if (current.toString() != transaction.appliedState) {
                return@withLock conflict(transaction, "Current value changed outside DOOM; it was not overwritten.")
            }
            if (!access.isAuthorized(transaction.targetIdentity)) {
                return@withLock rejected("Android authorization was revoked before restore.")
            }
            adapterFailure(restoreDefinition)?.let { return@withLock rejected(it) }
            val restoring = transaction.copy(updatedAt = now(), status = ChangeTransactionStatus.RESTORING, failureReason = null)
            store.update(restoring)
            try {
                withTimeout(definition.timeoutMillis) { access.write(transaction.targetIdentity, transaction.originalState.toLong()) }
                if (withTimeout(definition.timeoutMillis) { access.read(transaction.targetIdentity) }.toString() != transaction.originalState) {
                    return@withLock uncertain(restoring, "Restore postcondition could not be verified.")
                }
                val restored = restoring.copy(updatedAt = now(), status = ChangeTransactionStatus.RESTORED)
                store.update(restored)
                ChangeOperationResult(restored.status, restored, "Original value restored and verified.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { uncertain(restoring, "Restore failed; recovery record remains available.") }
        }
    }

    /** Resolve interrupted writes from live state without discarding their durable records. */
    suspend fun reconcile(): List<ChangeTransaction> = store.unresolved().map { transaction ->
        if (transaction.status !in setOf(ChangeTransactionStatus.PREPARED, ChangeTransactionStatus.RESTORING, ChangeTransactionStatus.UNCERTAIN)) return@map transaction
        val current = runCatching { access.read(transaction.targetIdentity) }.getOrNull()
        val next = when {
            current == null -> transaction.copy(status = ChangeTransactionStatus.UNCERTAIN,
                updatedAt = now(), failureReason = "Could not inspect setting during recovery.")
            transaction.status == ChangeTransactionStatus.RESTORING && current.toString() == transaction.originalState ->
                transaction.copy(status = ChangeTransactionStatus.RESTORED, updatedAt = now(), failureReason = null)
            transaction.status == ChangeTransactionStatus.RESTORING && current.toString() == transaction.appliedState ->
                transaction.copy(status = ChangeTransactionStatus.APPLIED, updatedAt = now(), failureReason = "Restore did not take effect; retry is available.")
            current.toString() == transaction.requestedState ->
                transaction.copy(status = ChangeTransactionStatus.APPLIED, appliedState = current.toString(), updatedAt = now(), failureReason = null)
            current.toString() == transaction.originalState ->
                transaction.copy(status = if (transaction.appliedState != null) ChangeTransactionStatus.RESTORED else ChangeTransactionStatus.FAILED,
                    updatedAt = now(), failureReason = null)
            else -> transaction.copy(status = ChangeTransactionStatus.CONFLICT, updatedAt = now(),
                failureReason = "Setting differs from both recorded original and requested values.")
        }
        if (next != transaction) store.update(next)
        next
    }

    private suspend fun conflict(tx: ChangeTransaction, message: String): ChangeOperationResult {
        val changed = tx.copy(status = ChangeTransactionStatus.CONFLICT, updatedAt = now(), failureReason = message)
        store.update(changed)
        return ChangeOperationResult(changed.status, changed, message)
    }

    private suspend fun uncertain(tx: ChangeTransaction, message: String): ChangeOperationResult {
        val changed = tx.copy(status = ChangeTransactionStatus.UNCERTAIN, updatedAt = now(), failureReason = message)
        store.update(changed)
        return ChangeOperationResult(changed.status, changed, message)
    }

    private fun rejected(message: String) = ChangeOperationResult(null, null, message)
    private fun adapterFailure(definition: PrivilegedOperationDefinition): String? {
        val required = definition.requiredAdapter ?: return null
        val status = adapterGate.currentStatus(required) ?: return "Required adapter $required is unavailable."
        if (status.availability != CapabilityAvailability.AVAILABLE) return status.failureReason ?: "Required adapter $required is unavailable or unauthorized."
        val currentLevel = runCatching { AdapterPrivilegeLevel.valueOf(status.privilegeLevel) }.getOrDefault(AdapterPrivilegeLevel.UNKNOWN)
        val ranks = mapOf(AdapterPrivilegeLevel.NONE to 0, AdapterPrivilegeLevel.ADB to 1, AdapterPrivilegeLevel.ROOT to 2)
        val requiredRank = ranks[definition.minimumPrivilege] ?: return "The operation requires a recognized adapter privilege level."
        val currentRank = ranks[currentLevel] ?: return "The adapter privilege level is unknown or unsupported."
        return if (currentRank < requiredRank) "The adapter does not have sufficient privilege for this operation." else null
    }
    private fun withinRateLimit(modId: String, definition: PrivilegedOperationDefinition): Boolean = synchronized(rateLock) {
        val key = "$modId:${definition.id}"
        val time = now()
        val times = operationTimes.getOrPut(key) { ArrayDeque() }
        while (times.isNotEmpty() && time - times.first() >= 60_000L) times.removeFirst()
        if (times.size >= definition.callsPerMinute.coerceAtLeast(1)) false else { times.addLast(time); true }
    }
    @Synchronized private fun lock(target: String) = targetLocks.getOrPut(target) { Mutex() }
}
