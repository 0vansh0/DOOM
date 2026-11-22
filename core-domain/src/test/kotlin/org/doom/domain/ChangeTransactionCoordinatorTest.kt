package org.doom.domain

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.doom.model.*
import org.junit.Assert.*
import org.junit.Test

class ChangeTransactionCoordinatorTest {
    private val operations = listOf(
        op(PrivilegedOperationCatalog.SCREEN_TIMEOUT_SET, true, false),
        op(PrivilegedOperationCatalog.SCREEN_TIMEOUT_RESTORE, true, false),
        op(PrivilegedOperationCatalog.SCREEN_TIMEOUT_READ, false, true)
    ).associateBy { it.id }

    @Test fun applyPersistsSnapshotBeforeWriteAndVerifiesAppliedValue() = runBlocking {
        val store = FakeStore(); val access = FakeAccess(60_000)
        val coordinator = coordinator(store, access)
        val result = coordinator.apply(request())
        assertEquals(ChangeTransactionStatus.APPLIED, result.status)
        assertEquals("60000", result.transaction?.originalState)
        assertEquals("120000", result.transaction?.appliedState)
        assertTrue(store.insertedBeforeWrite)
    }

    @Test fun readUsesOperationContractAndItsCallsPerMinuteLimit() = runBlocking {
        val access = FakeAccess(90_000); val coordinator = coordinator(FakeStore(), access)
        repeat(10) { assertTrue(coordinator.read(PrivilegedOperationCatalog.SCREEN_TIMEOUT_READ).succeeded) }
        val limited = coordinator.read(PrivilegedOperationCatalog.SCREEN_TIMEOUT_READ)
        assertFalse(limited.succeeded)
        assertEquals(10, access.reads)
        assertFalse(coordinator.read("system.settings.read.anything").succeeded)
    }

    @Test fun missingConfirmationOrAndroidAuthorizationDoesNotWrite() = runBlocking {
        val access = FakeAccess(60_000); val coordinator = coordinator(FakeStore(), access)
        assertNull(coordinator.apply(request().copy(userConfirmed = false)).status)
        access.authorized = false
        assertNull(coordinator.apply(request()).status)
        assertEquals(0, access.writes)
    }

    @Test fun unsupportedOperationAndRevokedPerModApprovalFailClosed() = runBlocking {
        val access = FakeAccess(60_000)
        val unsupported = coordinator(FakeStore(), access).apply(request().copy(operationId = "settings.write.anything"))
        assertNull(unsupported.status)
        val modOwned = operations.toMutableMap().apply {
            put(PrivilegedOperationCatalog.SCREEN_TIMEOUT_SET, getValue(PrivilegedOperationCatalog.SCREEN_TIMEOUT_SET).copy(modAccessible = true))
        }
        val missingApproval = ChangeTransactionCoordinator(FakeStore(), access, OperationApproval { _, _ -> false }, modOwned, 36,
            now = { 100_000 })
        assertNull(missingApproval.apply(request().copy(modId = "community.mod")).status)
        assertEquals(0, access.writes)
    }

    @Test fun invalidRangeAndSafeModeAreRejected() = runBlocking {
        val access = FakeAccess(60_000); val coordinator = coordinator(FakeStore(), access)
        assertNull(coordinator.apply(request().copy(requestedValue = 5_000)).status)
        assertNull(coordinator.apply(request().copy(safeMode = true)).status)
        assertEquals(0, access.writes)
    }

    @Test fun restoreOnlyWhenCurrentValueMatchesAppliedSnapshot() = runBlocking {
        val store = FakeStore(); val access = FakeAccess(60_000); val coordinator = coordinator(store, access)
        val applied = coordinator.apply(request()).transaction!!
        val restored = coordinator.restore(applied.transactionId, true)
        assertEquals(ChangeTransactionStatus.RESTORED, restored.status)
        assertEquals(60_000L, access.value)
    }

    @Test fun failedRestoreRemainsUncertainAndRecoverable() = runBlocking {
        val store = FakeStore(); val access = FakeAccess(60_000); val coordinator = coordinator(store, access)
        val applied = coordinator.apply(request()).transaction!!
        access.writeThrows = true
        val result = coordinator.restore(applied.transactionId, true)
        assertEquals(ChangeTransactionStatus.UNCERTAIN, result.status)
        assertEquals(ChangeTransactionStatus.UNCERTAIN, store.find(applied.transactionId)?.status)
    }

    @Test fun externalChangeCreatesConflictWithoutOverwritingIt() = runBlocking {
        val store = FakeStore(); val access = FakeAccess(60_000); val coordinator = coordinator(store, access)
        val applied = coordinator.apply(request()).transaction!!
        access.value = 300_000
        val result = coordinator.restore(applied.transactionId, true)
        assertEquals(ChangeTransactionStatus.CONFLICT, result.status)
        assertEquals(300_000L, access.value)
    }

    @Test fun postconditionMismatchAndFailedRestoreRemainRecoverable() = runBlocking {
        val store = FakeStore(); val access = FakeAccess(60_000); access.mismatch = true
        val coordinator = coordinator(store, access)
        val result = coordinator.apply(request())
        assertEquals(ChangeTransactionStatus.UNCERTAIN, result.status)
        assertNotNull(store.find(result.transaction!!.transactionId))
    }

    @Test fun interruptedPreparedRecordReconcilesToAppliedOrFailed() = runBlocking {
        val store = FakeStore(); val access = FakeAccess(120_000)
        store.insert(ChangeTransaction("tx", "__doom_manual__", PrivilegedOperationCatalog.SCREEN_TIMEOUT_SET,
            1, PrivilegedOperationCatalog.SCREEN_TIMEOUT_TARGET, "60000", "120000", null, 1, 1,
            ChangeTransactionStatus.PREPARED))
        val coordinator = coordinator(store, access)
        assertEquals(ChangeTransactionStatus.APPLIED, coordinator.reconcile().single().status)
        access.value = 60_000
        store.update(store.find("tx")!!.copy(status = ChangeTransactionStatus.PREPARED, appliedState = null))
        assertEquals(ChangeTransactionStatus.FAILED, coordinator.reconcile().single().status)
    }

    @Test fun unresolvedOwnerBlocksASecondWrite() = runBlocking {
        val store = FakeStore(); val access = FakeAccess(60_000); val coordinator = coordinator(store, access)
        assertEquals(ChangeTransactionStatus.APPLIED, coordinator.apply(request()).status)
        val second = coordinator.apply(request().copy(modId = "other.mod"))
        assertNull(second.status)
        assertEquals(1, access.writes)
    }

    @Test fun snapshotFailureAndAdapterWriteFailureDoNotClaimSuccess() = runBlocking {
        val snapshot = FakeAccess(60_000).apply { readThrows = true }
        val snapshotStore = FakeStore()
        assertNull(coordinator(snapshotStore, snapshot).apply(request()).status)
        assertTrue(snapshotStore.rows.isEmpty())

        val write = FakeAccess(60_000).apply { writeThrows = true }
        val writeStore = FakeStore()
        val uncertain = coordinator(writeStore, write).apply(request())
        assertEquals(ChangeTransactionStatus.UNCERTAIN, uncertain.status)
        assertNotNull(writeStore.find(uncertain.transaction!!.transactionId))
    }

    @Test fun permissionRevokedAfterJournalCommitPreventsExternalWrite() = runBlocking {
        val access = FakeAccess(60_000)
        val store = FakeStore().apply { onInsert = { access.authorized = false } }
        val result = coordinator(store, access).apply(request())
        assertEquals(ChangeTransactionStatus.FAILED, result.status)
        assertEquals(0, access.writes)
    }

    @Test fun interruptedApplyKeepsPreparedJournalAndReconcileDoesNotReplayWrite() = runBlocking {
        val store = FakeStore()
        val access = FakeAccess(60_000).apply { cancelAfterWrite = true }
        val coordinator = coordinator(store, access)

        assertThrows(CancellationException::class.java) { runBlocking { coordinator.apply(request()) } }
        val prepared = store.rows.values.single()
        assertEquals(ChangeTransactionStatus.PREPARED, prepared.status)
        assertEquals(1, access.writes)

        val reconciled = coordinator.reconcile().single()
        assertEquals(ChangeTransactionStatus.APPLIED, reconciled.status)
        assertEquals("120000", reconciled.appliedState)
        assertEquals(1, access.writes)
    }

    @Test fun interruptedRestoreRemainsJournaledAndReconcilesWithoutReplay() = runBlocking {
        val store = FakeStore()
        val access = FakeAccess(60_000)
        val coordinator = coordinator(store, access)
        val applied = coordinator.apply(request()).transaction!!
        access.cancelAfterWrite = true

        assertThrows(CancellationException::class.java) {
            runBlocking { coordinator.restore(applied.transactionId, userConfirmed = true) }
        }
        assertEquals(ChangeTransactionStatus.RESTORING, store.find(applied.transactionId)?.status)
        assertEquals(2, access.writes)

        val reconciled = coordinator.reconcile().single()
        assertEquals(ChangeTransactionStatus.RESTORED, reconciled.status)
        assertEquals(2, access.writes)
        assertEquals(60_000L, access.value)
    }

    @Test fun revokedAndroidAuthorizationDuringRecoveryPreservesAppliedJournal() = runBlocking {
        val store = FakeStore()
        val access = FakeAccess(60_000)
        val coordinator = coordinator(store, access)
        val applied = coordinator.apply(request()).transaction!!
        access.authorized = false

        val result = coordinator.restore(applied.transactionId, userConfirmed = true)
        assertNull(result.status)
        assertEquals(ChangeTransactionStatus.APPLIED, store.find(applied.transactionId)?.status)
        assertEquals(1, access.writes)
        assertEquals(120_000L, access.value)
    }

    @Test fun restoreIsIdempotentAndRateLimitBlocksRapidRepeat() = runBlocking {
        var clock = 100_000L
        val store = FakeStore(); val access = FakeAccess(60_000)
        val coordinator = ChangeTransactionCoordinator(store, access, OperationApproval { _, _ -> false }, operations,
            36, now = { clock }, id = { "tx-${store.rows.size}" })
        val applied = coordinator.apply(request()).transaction!!
        val restored = coordinator.restore(applied.transactionId, true)
        assertEquals(ChangeTransactionStatus.RESTORED, restored.status)
        assertEquals(ChangeTransactionStatus.RESTORED, coordinator.restore(applied.transactionId, true).status)
        assertNull(coordinator.apply(request()).status)
        clock += 60_000
        assertEquals(ChangeTransactionStatus.APPLIED, coordinator.apply(request()).status)
    }

    @Test fun missingShizukuAndInsufficientPrivilegeFailClosed() = runBlocking {
        val shizukuOperation = operations.toMutableMap().apply {
            put(PrivilegedOperationCatalog.SCREEN_TIMEOUT_SET, getValue(PrivilegedOperationCatalog.SCREEN_TIMEOUT_SET)
                .copy(requiredAdapter = "shizuku", minimumPrivilege = AdapterPrivilegeLevel.ADB))
        }
        val access = FakeAccess(60_000)
        val unavailable = ChangeTransactionCoordinator(FakeStore(), access, OperationApproval { _, _ -> false },
            shizukuOperation, 36, adapterGate = OperationAdapterGate { null }, now = { 100_000 })
        assertNull(unavailable.apply(request()).status)
        assertEquals(0, access.writes)

        val insufficient = ChangeTransactionCoordinator(FakeStore(), access, OperationApproval { _, _ -> false },
            shizukuOperation.toMutableMap().apply {
                put(PrivilegedOperationCatalog.SCREEN_TIMEOUT_SET, getValue(PrivilegedOperationCatalog.SCREEN_TIMEOUT_SET)
                    .copy(minimumPrivilege = AdapterPrivilegeLevel.ROOT))
            }, 36, adapterGate = OperationAdapterGate { adapter ->
                assertEquals("shizuku", adapter)
                AdapterStatus("shizuku", "Shizuku", "Connected · ADB shell", CapabilityAvailability.AVAILABLE,
                    emptySet(), privilegeLevel = "ADB")
            }, now = { 100_000 })
        assertNull(insufficient.apply(request()).status)
        assertEquals(0, access.writes)
    }

    private fun coordinator(store: FakeStore, access: FakeAccess) = ChangeTransactionCoordinator(
        store, access, OperationApproval { _, _ -> false }, operations, 36,
        now = { 100_000L }, id = { "tx-${store.rows.size}" })

    private fun request() = OperationRequest("__doom_manual__", PrivilegedOperationCatalog.SCREEN_TIMEOUT_SET,
        120_000, true, true, false)

    private fun op(id: String, confirmation: Boolean, modAccessible: Boolean) = PrivilegedOperationDefinition(
        id, 1, "device.settings.screen_timeout.write", 31, AdapterPrivilegeLevel.NONE,
        CapabilityRisk.MEDIUM, confirmation, modAccessible, PrivilegedOperationCatalog.SCREEN_TIMEOUT_TARGET,
        callsPerMinute = if (id == PrivilegedOperationCatalog.SCREEN_TIMEOUT_READ) 10 else 1,
        minimumValue = 15_000, maximumValue = 1_800_000, restorePolicy = RestorePolicy.EXPLICIT_USER_ACTION)

    private class FakeAccess(var value: Long) : SystemSettingAccess {
        var authorized = true; var writes = 0; var reads = 0; var mismatch = false; var readThrows = false; var writeThrows = false
        var cancelAfterWrite = false
        override suspend fun read(targetIdentity: String): Long { check(targetIdentity == PrivilegedOperationCatalog.SCREEN_TIMEOUT_TARGET); reads++; check(!readThrows); return value }
        override suspend fun write(targetIdentity: String, value: Long) {
            writes++
            check(!writeThrows)
            this.value = if (mismatch) value + 1 else value
            if (cancelAfterWrite) {
                cancelAfterWrite = false
                throw CancellationException("Simulated cancellation after external write.")
            }
        }
        override fun isAuthorized(targetIdentity: String) = authorized
    }

    private class FakeStore : ChangeTransactionStore {
        val rows = linkedMapOf<String, ChangeTransaction>(); var insertedBeforeWrite = false; var onInsert: (() -> Unit)? = null
        override suspend fun insert(transaction: ChangeTransaction) { rows[transaction.transactionId] = transaction; insertedBeforeWrite = true; onInsert?.invoke() }
        override suspend fun update(transaction: ChangeTransaction) { rows[transaction.transactionId] = transaction }
        override suspend fun find(transactionId: String) = rows[transactionId]
        override suspend fun activeForTarget(targetIdentity: String) = rows.values.filter {
            it.targetIdentity == targetIdentity && it.status in setOf(ChangeTransactionStatus.APPLIED, ChangeTransactionStatus.PREPARED,
                ChangeTransactionStatus.RESTORING, ChangeTransactionStatus.UNCERTAIN, ChangeTransactionStatus.CONFLICT)
        }
        override suspend fun unresolved() = rows.values.filter { it.status in setOf(ChangeTransactionStatus.PREPARED,
            ChangeTransactionStatus.RESTORING, ChangeTransactionStatus.UNCERTAIN) }
    }
}
