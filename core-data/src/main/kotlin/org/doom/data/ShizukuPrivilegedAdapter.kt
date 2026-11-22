package org.doom.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.doom.model.AdapterPrivilegeLevel
import org.doom.model.AdapterStatus
import org.doom.model.CapabilityAvailability
import org.doom.model.CapabilityAdapter
import org.doom.model.PrivilegedOperationResult
import org.doom.model.PrivilegedOperationStatus
import org.doom.model.ShizukuOperationPolicy
import rikka.shizuku.Shizuku
import kotlin.coroutines.coroutineContext

/** The only operation exposed here is a user-initiated, read-only report of Shizuku's caller UID/API version. */
class ShizukuPrivilegedAdapter internal constructor(private val connection: ShizukuConnectionManager) : CapabilityAdapter, org.doom.model.OperationAdapterGate {
    override val adapterId = ADAPTER_ID
    override val displayName = "Shizuku"

    override fun status(): AdapterStatus {
        val state = connection.refresh()
        val available = state.authorized && state.binderAlive &&
            state.privilegeLevel in setOf(AdapterPrivilegeLevel.ADB, AdapterPrivilegeLevel.ROOT)
        return AdapterStatus(
            adapterId = adapterId,
            displayName = displayName,
            connectionStatus = state.displayLabel,
            availability = when {
                available -> CapabilityAvailability.AVAILABLE
                state.phase == org.doom.model.ShizukuConnectionPhase.ERROR -> CapabilityAvailability.UNKNOWN
                state.managerInstalled || state.binderAlive -> CapabilityAvailability.DENIED
                else -> CapabilityAvailability.UNAVAILABLE
            },
            // Diagnostic-only operation; it is not a mod-format capability and is never routed from imported definitions.
            supportedCapabilities = setOf(ShizukuOperationPolicy.DIAGNOSTIC_IDENTITY),
            permissionRequirements = listOf("Explicit Shizuku authorization"),
            failureReason = state.message,
            privilegeLevel = state.privilegeLevel.name,
            supportedOperations = setOf(ShizukuOperationPolicy.DIAGNOSTIC_IDENTITY)
        )
    }

    override fun currentStatus(adapterId: String): AdapterStatus? = if (adapterId == this.adapterId) status() else null

    suspend fun runIdentityDiagnostic(
        userInitiated: Boolean,
        approvedOperationIds: Set<String>
    ): PrivilegedOperationResult = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        val before = connection.refresh()
        val expectedGeneration = connection.currentGeneration
        val decision = ShizukuOperationPolicy.authorize(
            operationId = ShizukuOperationPolicy.DIAGNOSTIC_IDENTITY,
            supported = setOf(ShizukuOperationPolicy.DIAGNOSTIC_IDENTITY),
            binderAlive = before.binderAlive,
            shizukuAuthorized = before.authorized,
            userInitiated = userInitiated,
            approvedOperationIds = approvedOperationIds,
            privilegeLevel = before.privilegeLevel
        )
        if (decision != PrivilegedOperationStatus.SUCCESS) return@withContext result(decision, "Diagnostic operation is not authorized or available.")
        try {
            // Recheck the live service gate immediately before querying the official API.
            val current = connection.refresh()
            val finalDecision = ShizukuOperationPolicy.authorize(
                ShizukuOperationPolicy.DIAGNOSTIC_IDENTITY,
                setOf(ShizukuOperationPolicy.DIAGNOSTIC_IDENTITY), current.binderAlive, current.authorized,
                userInitiated, approvedOperationIds, current.privilegeLevel
            )
            if (finalDecision != PrivilegedOperationStatus.SUCCESS) return@withContext result(finalDecision, "Authorization changed before the diagnostic ran.")
            val uid = Shizuku.getUid()
            val version = Shizuku.getVersion()
            coroutineContext.ensureActive()
            if (connection.currentGeneration != expectedGeneration) return@withContext result(PrivilegedOperationStatus.CANCELLED, "Shizuku disconnected during the diagnostic.")
            val privilege = when (uid) {
                0 -> AdapterPrivilegeLevel.ROOT
                2000 -> AdapterPrivilegeLevel.ADB
                else -> AdapterPrivilegeLevel.OTHER
            }
            if (privilege == AdapterPrivilegeLevel.OTHER) return@withContext result(PrivilegedOperationStatus.UNSUPPORTED, "Shizuku identity is neither ADB shell nor root.")
            PrivilegedOperationResult(
                ShizukuOperationPolicy.DIAGNOSTIC_IDENTITY, PrivilegedOperationStatus.SUCCESS,
                "Shizuku API $version connected with ${privilege.name} identity (UID $uid).", privilege, version
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            result(PrivilegedOperationStatus.UNAUTHORIZED, "Shizuku authorization is unavailable.")
        } catch (e: IllegalStateException) {
            result(PrivilegedOperationStatus.CANCELLED, "Shizuku disconnected before the diagnostic completed.")
        } catch (e: RuntimeException) {
            result(PrivilegedOperationStatus.ERROR, "The read-only Shizuku diagnostic failed.")
        }
    }

    private fun result(status: PrivilegedOperationStatus, message: String) =
        PrivilegedOperationResult(ShizukuOperationPolicy.DIAGNOSTIC_IDENTITY, status, message)

    companion object { const val ADAPTER_ID = "shizuku" }
}
