package org.doom.engine.runtime

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class WorkflowCoordinatorTest {
    private fun plan(mod: String = "community.test", workflow: String = "flow", mode: WorkflowExecutionMode = WorkflowExecutionMode.SEQUENTIAL,
        policy: WorkflowFailurePolicy = WorkflowFailurePolicy.STOP_ON_FAILURE, timeout: Long = 30_000,
        count: Int = 3, concurrency: Int = 3) = WorkflowPlan(mod, workflow, "runtime-session", mode, policy, timeout,
        (0 until count).map { index ->
            val independent = index > 0 && (mode == WorkflowExecutionMode.PARALLEL || policy == WorkflowFailurePolicy.CONTINUE_ON_FAILURE)
            WorkflowActionPlan("a$index", V4Action("ui.preview.show", "floating-card", emptyMap(), "a$index", independent), independent)
        }, concurrency)

    @Test fun sequentialActionsCompleteInDeclaredOrder() = runBlocking {
        val coordinator = WorkflowCoordinator()
        val trace = mutableListOf<String>()
        val session = coordinator.start(this, plan(count = 3)) { context ->
            trace += "start:${context.action.id}"
            yield()
            trace += "end:${context.action.id}"
            ActionExecutionResult(context.action.id, ActionExecutionStatus.SUCCEEDED)
        }!!
        assertEquals(WorkflowResultStatus.SUCCEEDED, session.completion.await().status)
        assertEquals(listOf("start:a0", "end:a0", "start:a1", "end:a1", "start:a2", "end:a2"), trace)
    }

    @Test fun liveMonitorShowsPendingAndRunningActionsThenCleansUp() = runBlocking {
        val coordinator = WorkflowCoordinator()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val session = coordinator.start(this, plan(count = 2)) { context ->
            entered.complete(Unit)
            release.await()
            ActionExecutionResult(context.action.id, ActionExecutionStatus.SUCCEEDED)
        }!!
        entered.await()
        val current = coordinator.monitor.value.single()
        assertEquals(session.sessionId, current.sessionId)
        assertEquals(listOf("a0"), current.runningActionIds)
        assertEquals(listOf("a1"), current.pendingActionIds)
        release.complete(Unit)
        assertEquals(WorkflowResultStatus.SUCCEEDED, session.completion.await().status)
        assertTrue(coordinator.monitor.value.isEmpty())
    }

    @Test fun parallelActionsRespectThreeActionConcurrencyBound() = runBlocking {
        val coordinator = WorkflowCoordinator()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val session = coordinator.start(this, plan(mode = WorkflowExecutionMode.PARALLEL, count = 12)) { context ->
            val current = active.incrementAndGet()
            peak.updateAndGet { maxOf(it, current) }
            delay(8)
            active.decrementAndGet()
            ActionExecutionResult(context.action.id, ActionExecutionStatus.SUCCEEDED)
        }!!
        assertEquals(WorkflowResultStatus.SUCCEEDED, session.completion.await().status)
        assertTrue(peak.get() in 2..WorkflowCoordinator.MAX_PARALLEL_ACTIONS)
    }

    @Test fun failurePoliciesStopOrContinueAsDeclared() = runBlocking {
        val coordinator = WorkflowCoordinator()
        val stopTrace = mutableListOf<String>()
        val stopped = coordinator.start(this, plan(policy = WorkflowFailurePolicy.STOP_ON_FAILURE)) { context ->
            stopTrace += context.action.id
            if (context.action.id == "a0") ActionExecutionResult(context.action.id, ActionExecutionStatus.FAILED, "first failed")
            else ActionExecutionResult(context.action.id, ActionExecutionStatus.SUCCEEDED)
        }!!
        assertEquals(WorkflowResultStatus.FAILED, stopped.completion.await().status)
        assertEquals(listOf("a0"), stopTrace)

        val continueTrace = mutableListOf<String>()
        val continued = coordinator.start(this, plan(workflow = "continue", policy = WorkflowFailurePolicy.CONTINUE_ON_FAILURE)) { context ->
            continueTrace += context.action.id
            if (context.action.id == "a0") ActionExecutionResult(context.action.id, ActionExecutionStatus.FAILED)
            else ActionExecutionResult(context.action.id, ActionExecutionStatus.SUCCEEDED)
        }!!
        assertEquals(WorkflowResultStatus.COMPLETED_WITH_FAILURES, continued.completion.await().status)
        assertEquals(listOf("a0", "a1", "a2"), continueTrace)
    }

    @Test fun timeoutCancelsActionAndReleasesOwnedResources() = runBlocking {
        val coordinator = WorkflowCoordinator()
        val closed = AtomicInteger()
        val session = coordinator.start(this, plan(timeout = 30, count = 1)) { context ->
            context.own(AutoCloseable { closed.incrementAndGet() })
            delay(5_000)
            ActionExecutionResult(context.action.id, ActionExecutionStatus.SUCCEEDED)
        }!!
        val result = session.completion.await()
        assertEquals(WorkflowResultStatus.CANCELLED, result.status)
        assertEquals(WorkflowCancellationReason.TIMEOUT, result.cancellationReason)
        assertEquals(1, closed.get())
        assertEquals(0, coordinator.ownedResourceCount())
    }

    @Test fun modDisableSafeModeAndPermissionRevocationCancelTheirSessions() = runBlocking {
        val coordinator = WorkflowCoordinator()
        suspend fun running(id: String, mod: String): WorkflowSession {
            val entered = CompletableDeferred<Unit>()
            return coordinator.start(this, plan(mod = mod, workflow = id, count = 1)) { context ->
                entered.complete(Unit)
                awaitCancellation()
                ActionExecutionResult(context.action.id, ActionExecutionStatus.SUCCEEDED)
            }!!.also { entered.await() }
        }
        val disabled = running("disable", "mod.disable")
        coordinator.cancelMod("mod.disable", WorkflowCancellationReason.MOD_DISABLED)
        assertEquals(WorkflowCancellationReason.MOD_DISABLED, disabled.completion.await().cancellationReason)
        val permission = running("permission", "mod.permission")
        coordinator.cancelMod("mod.permission", WorkflowCancellationReason.PERMISSION_REVOKED)
        assertEquals(WorkflowCancellationReason.PERMISSION_REVOKED, permission.completion.await().cancellationReason)
        val safe = running("safe", "mod.safe")
        coordinator.cancelAll(WorkflowCancellationReason.SAFE_MODE)
        assertEquals(WorkflowCancellationReason.SAFE_MODE, safe.completion.await().cancellationReason)
    }

    @Test fun completedEffectOwnershipIsScopedToWorkflowAndMod() = runBlocking {
        val coordinator = WorkflowCoordinator()
        val closed = mutableListOf<String>()
        suspend fun complete(id: String, mod: String) = coordinator.start(this, plan(mod = mod, workflow = id, count = 1)) { context ->
            context.own(AutoCloseable { closed += "${context.plan.modId}:${context.plan.workflowId}" })
            ActionExecutionResult(context.action.id, ActionExecutionStatus.SUCCEEDED)
        }!!.completion.await()
        complete("first", "same.mod")
        complete("second", "same.mod")
        complete("other", "other.mod")
        assertEquals(3, coordinator.ownedResourceCount())
        coordinator.cancelWorkflow("same.mod", "first", WorkflowCancellationReason.REQUESTED)
        assertEquals(listOf("same.mod:first"), closed)
        assertEquals(2, coordinator.ownedResourceCount())
        coordinator.cancelMod("same.mod", WorkflowCancellationReason.MOD_DISABLED)
        assertEquals(1, coordinator.ownedResourceCount())
        coordinator.cancelMod("other.mod", WorkflowCancellationReason.MOD_DISABLED)
        assertEquals(0, coordinator.ownedResourceCount())
    }

    @Test fun twoWorkflowsPerModAndOtherModsAreIsolated() = runBlocking {
        val coordinator = WorkflowCoordinator()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val gate = CompletableDeferred<Unit>()
        suspend fun block(p: WorkflowPlan) = coordinator.start(scope, p) { context ->
            gate.await()
            ActionExecutionResult(context.action.id, ActionExecutionStatus.SUCCEEDED)
        }
        val a = block(plan(workflow = "one", mod = "same.mod", count = 1))!!
        val b = block(plan(workflow = "two", mod = "same.mod", count = 1))!!
        assertNull(block(plan(workflow = "three", mod = "same.mod", count = 1)))
        val other = block(plan(workflow = "other", mod = "other.mod", count = 1))!!
        assertEquals(3, coordinator.activeCount())
        coordinator.cancelMod("same.mod", WorkflowCancellationReason.MOD_DISABLED)
        assertEquals(WorkflowCancellationReason.MOD_DISABLED, withTimeout(2_000) { a.completion.await() }.cancellationReason)
        assertEquals(WorkflowCancellationReason.MOD_DISABLED, withTimeout(2_000) { b.completion.await() }.cancellationReason)
        assertEquals(1, coordinator.activeCount())
        assertTrue(other.job.isActive)
        coordinator.cancelMod("other.mod", WorkflowCancellationReason.MOD_DISABLED)
        assertEquals(WorkflowCancellationReason.MOD_DISABLED, withTimeout(2_000) { other.completion.await() }.cancellationReason)
        scope.cancel()
    }

    @Test fun workflowLimitsRejectUnboundedPlans() {
        val coordinator = WorkflowCoordinator()
        assertTrue(runCatching { coordinator.start(CoroutineScope(SupervisorJob()), plan(count = 17)) { error("never") } }.isFailure)
        assertTrue(runCatching { coordinator.start(CoroutineScope(SupervisorJob()), plan(timeout = 30_001)) { error("never") } }.isFailure)
        assertTrue(runCatching { coordinator.start(CoroutineScope(SupervisorJob()), plan(mode = WorkflowExecutionMode.PARALLEL, concurrency = 4)) { error("never") } }.isFailure)
    }
}
