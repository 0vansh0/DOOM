package org.doom.engine.runtime

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID
import org.doom.model.DiagnosticFailureCategory
import org.doom.model.WorkflowMonitorEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WorkflowExecutionMode { LEGACY_FIRST_ACTION, SEQUENTIAL, PARALLEL }
enum class WorkflowFailurePolicy { STOP_ON_FAILURE, CONTINUE_ON_FAILURE }
enum class ActionExecutionStatus { SUCCEEDED, FAILED }
enum class WorkflowResultStatus { SUCCEEDED, COMPLETED_WITH_FAILURES, FAILED, CANCELLED }
enum class WorkflowCancellationReason { REQUESTED, MOD_DISABLED, PERMISSION_REVOKED, SAFE_MODE, RUNTIME_STOP, TIMEOUT }

data class WorkflowActionPlan(val id: String, val action: V4Action, val independent: Boolean = false)

data class WorkflowPlan(
    val modId: String,
    val workflowId: String,
    val runtimeSessionId: String,
    val executionMode: WorkflowExecutionMode,
    val failurePolicy: WorkflowFailurePolicy,
    val timeoutMillis: Long,
    val actions: List<WorkflowActionPlan>,
    val maxConcurrency: Int = WorkflowCoordinator.MAX_PARALLEL_ACTIONS
)

data class ActionExecutionResult(val actionId: String, val status: ActionExecutionStatus, val message: String? = null)
data class WorkflowResult(
    val workflowId: String,
    val sessionId: String,
    val status: WorkflowResultStatus,
    val actions: List<ActionExecutionResult>,
    val cancellationReason: WorkflowCancellationReason? = null
)

class WorkflowActionContext internal constructor(
    val plan: WorkflowPlan,
    val sessionId: String,
    val action: WorkflowActionPlan,
    private val ownResource: (AutoCloseable) -> AutoCloseable
) {
    /** Register immediately after allocation; cancellation can then release it even before execute() returns. */
    fun own(resource: AutoCloseable): AutoCloseable = ownResource(resource)
}

class WorkflowSession internal constructor(
    val plan: WorkflowPlan,
    val sessionId: String,
    val completion: CompletableDeferred<WorkflowResult>,
    internal val job: Job
)

/** Structured, mod-isolated workflow execution. Completed visual effects remain owned until explicit cleanup. */
class WorkflowCoordinator(
    private val diagnostic: (RuntimeDiagnostic) -> Unit = {}
) {
    companion object {
        const val MAX_ACTIONS = 16
        const val MAX_PARALLEL_ACTIONS = 3
        const val MAX_WORKFLOWS_PER_MOD = 2
        const val MAX_TIMEOUT_MILLIS = 30_000L
    }

    private data class OwnedResource(val modId: String, val workflowId: String, val sessionId: String,
        val actionId: String, val resource: AutoCloseable)
    private val lock = Any()
    private val active = linkedMapOf<String, WorkflowSession>()
    private val reasons = mutableMapOf<String, WorkflowCancellationReason>()
    private val cancelledSessions = mutableSetOf<String>()
    private val resources = linkedMapOf<String, MutableList<OwnedResource>>()
    private val mutableMonitor = MutableStateFlow<List<WorkflowMonitorEntry>>(emptyList())
    val monitor = mutableMonitor.asStateFlow()

    fun start(
        scope: CoroutineScope,
        plan: WorkflowPlan,
        executeAction: suspend (WorkflowActionContext) -> ActionExecutionResult
    ): WorkflowSession? {
        validate(plan)
        val sessionId = UUID.randomUUID().toString()
        val completion = CompletableDeferred<WorkflowResult>()
        lateinit var session: WorkflowSession
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var result: WorkflowResult? = null
            try {
                result = withTimeout(plan.timeoutMillis) { execute(plan, sessionId, executeAction) }
            } catch (_: TimeoutCancellationException) {
                result = WorkflowResult(plan.workflowId, sessionId, WorkflowResultStatus.CANCELLED,
                    emptyList(), WorkflowCancellationReason.TIMEOUT)
            } catch (_: CancellationException) {
                val reason = synchronized(lock) { reasons[sessionId] ?: WorkflowCancellationReason.REQUESTED }
                result = WorkflowResult(plan.workflowId, sessionId, WorkflowResultStatus.CANCELLED, emptyList(), reason)
            } catch (failure: Exception) {
                result = WorkflowResult(plan.workflowId, sessionId, WorkflowResultStatus.FAILED, emptyList())
                emit(plan, sessionId, "FAILED", "Workflow execution failed.", DiagnosticFailureCategory.RUNTIME_FAILURE)
            } finally {
                val terminal = result
                if (terminal?.status in setOf(WorkflowResultStatus.FAILED, WorkflowResultStatus.CANCELLED)) releaseSession(sessionId)
                synchronized(lock) {
                    active.remove(sessionId); reasons.remove(sessionId); cancelledSessions.remove(sessionId)
                    mutableMonitor.value = mutableMonitor.value.filterNot { it.sessionId == sessionId }
                }
            }
            val finalResult = result ?: WorkflowResult(plan.workflowId, sessionId, WorkflowResultStatus.FAILED, emptyList())
            completion.complete(finalResult)
            emit(plan, sessionId, finalResult.status.name, finalResult.cancellationReason?.name,
                when (finalResult.cancellationReason) {
                    WorkflowCancellationReason.TIMEOUT -> DiagnosticFailureCategory.TIMEOUT
                    WorkflowCancellationReason.PERMISSION_REVOKED -> DiagnosticFailureCategory.PERMISSION_DENIED
                    WorkflowCancellationReason.SAFE_MODE, WorkflowCancellationReason.MOD_DISABLED,
                    WorkflowCancellationReason.RUNTIME_STOP, WorkflowCancellationReason.REQUESTED -> DiagnosticFailureCategory.USER_CANCELLED
                    null -> if (finalResult.status in setOf(WorkflowResultStatus.FAILED, WorkflowResultStatus.COMPLETED_WITH_FAILURES))
                        DiagnosticFailureCategory.RUNTIME_FAILURE else DiagnosticFailureCategory.NONE
                }, "workflow.terminal")
        }
        session = WorkflowSession(plan, sessionId, completion, job)
        synchronized(lock) {
            if (active.values.count { it.plan.modId == plan.modId } >= MAX_WORKFLOWS_PER_MOD) return null
            active[sessionId] = session
            mutableMonitor.value = mutableMonitor.value + WorkflowMonitorEntry(plan.modId, plan.workflowId, sessionId,
                plan.actions.map { it.id }, emptyList(), System.currentTimeMillis())
        }
        emit(plan, sessionId, "SUCCESS", "Workflow started.", DiagnosticFailureCategory.NONE, "workflow.started")
        job.invokeOnCompletion {
            synchronized(lock) {
                active.remove(sessionId); reasons.remove(sessionId); cancelledSessions.remove(sessionId)
                mutableMonitor.value = mutableMonitor.value.filterNot { it.sessionId == sessionId }
            }
        }
        job.start()
        return session
    }

    fun cancel(sessionId: String, reason: WorkflowCancellationReason) {
        val session = synchronized(lock) {
            active.remove(sessionId)?.also { reasons[sessionId] = reason; cancelledSessions += sessionId }
        }
        session?.job?.cancel(CancellationException("Workflow cancelled: $reason"))
        releaseSession(sessionId)
        if (session != null) session.completion.complete(WorkflowResult(session.plan.workflowId, sessionId,
            WorkflowResultStatus.CANCELLED, emptyList(), reason))
    }

    fun cancelWorkflow(modId: String, workflowId: String, reason: WorkflowCancellationReason) {
        val sessions = synchronized(lock) { active.values.filter { it.plan.modId == modId && it.plan.workflowId == workflowId }.map { it.sessionId } }
        sessions.forEach { cancel(it, reason) }
        releaseMatching { it.modId == modId && it.workflowId == workflowId }
    }

    fun cancelMod(modId: String, reason: WorkflowCancellationReason) {
        val sessions = synchronized(lock) { active.values.filter { it.plan.modId == modId }.map { it.sessionId } }
        sessions.forEach { cancel(it, reason) }
        releaseMatching { it.modId == modId }
    }

    fun cancelAll(reason: WorkflowCancellationReason) {
        val sessions = synchronized(lock) { active.keys.toList() }
        sessions.forEach { cancel(it, reason) }
        releaseMatching { true }
    }

    fun activeCount(modId: String? = null): Int = synchronized(lock) {
        active.values.count { modId == null || it.plan.modId == modId }
    }

    fun ownedResourceCount(modId: String? = null): Int = synchronized(lock) {
        resources.values.flatten().count { modId == null || it.modId == modId }
    }

    private suspend fun execute(plan: WorkflowPlan, sessionId: String,
        executeAction: suspend (WorkflowActionContext) -> ActionExecutionResult): WorkflowResult {
        val results = java.util.Collections.synchronizedList(mutableListOf<ActionExecutionResult>())
        val semaphore = Semaphore(plan.maxConcurrency)
        suspend fun run(action: WorkflowActionPlan): ActionExecutionResult = try {
            updateMonitor(sessionId) { entry -> entry.copy(
                pendingActionIds = entry.pendingActionIds - action.id,
                runningActionIds = entry.runningActionIds + action.id
            ) }
            currentCoroutineContext().ensureActive()
            val result = executeAction(WorkflowActionContext(plan, sessionId, action) { resource ->
                val owned = OwnedResource(plan.modId, plan.workflowId, sessionId, action.id, resource)
                val registered = synchronized(lock) {
                    if (sessionId in cancelledSessions) false
                    else { resources.getOrPut(sessionId) { mutableListOf() }.add(owned); true }
                }
                if (!registered) {
                    runCatching { resource.close() }
                    AutoCloseable { }
                } else AutoCloseable {
                    val removed = synchronized(lock) { resources[sessionId]?.remove(owned) == true }
                    if (removed) runCatching { resource.close() }
                }
            })
            require(result.actionId == action.id) { "Action executor returned a mismatched action ID." }
            results += result
            updateMonitor(sessionId) { entry -> entry.copy(runningActionIds = entry.runningActionIds - action.id) }
            result
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            ActionExecutionResult(action.id, ActionExecutionStatus.FAILED, "Action execution failed.").also {
                results += it
                updateMonitor(sessionId) { entry -> entry.copy(runningActionIds = entry.runningActionIds - action.id) }
            }
        }

        if (plan.executionMode == WorkflowExecutionMode.SEQUENTIAL) {
            for (action in plan.actions) {
                val result = run(action)
                if (result.status == ActionExecutionStatus.FAILED && plan.failurePolicy == WorkflowFailurePolicy.STOP_ON_FAILURE) {
                    releaseSession(sessionId)
                    return WorkflowResult(plan.workflowId, sessionId, WorkflowResultStatus.FAILED, results.toList())
                }
            }
        } else if (plan.failurePolicy == WorkflowFailurePolicy.CONTINUE_ON_FAILURE) {
            supervisorScope {
                plan.actions.map { action -> async { semaphore.withPermit { run(action) } } }.awaitAll()
            }
        } else {
            try {
                coroutineScope {
                    plan.actions.map { action -> launch {
                        semaphore.withPermit {
                            val result = run(action)
                            if (result.status == ActionExecutionStatus.FAILED) throw WorkflowActionFailure(action.id)
                        }
                    } }.joinAll()
                }
            } catch (_: WorkflowActionFailure) {
                releaseSession(sessionId)
                return WorkflowResult(plan.workflowId, sessionId, WorkflowResultStatus.FAILED, results.toList())
            }
        }
        val failures = results.any { it.status == ActionExecutionStatus.FAILED }
        return WorkflowResult(plan.workflowId, sessionId,
            if (failures) WorkflowResultStatus.COMPLETED_WITH_FAILURES else WorkflowResultStatus.SUCCEEDED,
            results.toList())
    }

    private fun validate(plan: WorkflowPlan) {
        require(plan.modId.isNotBlank() && plan.workflowId.matches(Regex("[a-z][a-z0-9-]{0,31}"))) { "Invalid workflow identity." }
        require(plan.runtimeSessionId.isNotBlank()) { "Missing runtime session identity." }
        require(plan.executionMode in setOf(WorkflowExecutionMode.SEQUENTIAL, WorkflowExecutionMode.PARALLEL)) { "Unsupported workflow mode." }
        require(plan.actions.size in 1..MAX_ACTIONS) { "Workflow must contain 1..$MAX_ACTIONS actions." }
        require(plan.actions.all { it.id.matches(Regex("[a-z][a-z0-9-]{0,31}")) } && plan.actions.map { it.id }.toSet().size == plan.actions.size) {
            "Workflow action identifiers must be valid and unique."
        }
        if (plan.executionMode == WorkflowExecutionMode.PARALLEL || plan.failurePolicy == WorkflowFailurePolicy.CONTINUE_ON_FAILURE)
            require(plan.actions.drop(1).all { it.independent }) { "Parallel/continue workflows require later actions to be marked independent." }
        require(plan.timeoutMillis in 1..MAX_TIMEOUT_MILLIS) { "Workflow timeout must be 1..$MAX_TIMEOUT_MILLIS ms." }
        require(plan.maxConcurrency in 1..MAX_PARALLEL_ACTIONS) { "Workflow concurrency must be 1..$MAX_PARALLEL_ACTIONS." }
    }

    private fun releaseSession(sessionId: String) {
        val owned = synchronized(lock) { resources.remove(sessionId).orEmpty() }
        owned.asReversed().forEach { runCatching { it.resource.close() } }
    }

    private fun releaseMatching(predicate: (OwnedResource) -> Boolean) {
        val removed = synchronized(lock) {
            val emptySessions = mutableListOf<String>()
            val close = resources.entries.flatMap { (sessionId, owned) ->
                val selected = owned.filter(predicate)
                owned.removeAll(selected.toSet())
                if (owned.isEmpty()) emptySessions += sessionId
                selected
            }
            emptySessions.filter { it.isNotEmpty() }.forEach(resources::remove)
            close
        }
        removed.asReversed().forEach { runCatching { it.resource.close() } }
    }

    private fun emit(plan: WorkflowPlan, sessionId: String, result: String, message: String?, category: DiagnosticFailureCategory,
        marker: String = "workflow.terminal") {
        runCatching { diagnostic(RuntimeDiagnostic(plan.modId, "workflow:${plan.workflowId}", result, message,
            runtimeSessionId = sessionId, workflowId = plan.workflowId, triggerOrOperationId = marker,
            failureCategory = category)) }
    }

    private fun updateMonitor(sessionId: String, update: (WorkflowMonitorEntry) -> WorkflowMonitorEntry) {
        synchronized(lock) {
            mutableMonitor.value = mutableMonitor.value.map { if (it.sessionId == sessionId) update(it) else it }
        }
    }

    private class WorkflowActionFailure(val actionId: String) : RuntimeException(actionId)
}
