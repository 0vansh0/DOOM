package org.doom.engine.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.doom.model.*
import java.time.Clock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** All entry points are serialized by the host's main thread. Effects are bounded synchronous native actions. */
class DefaultModRuntime(
    private val executor: ModEffectExecutor,
    private val nowMillis: () -> Long,
    private val overlayAuthorized: () -> Boolean,
    private val diagnostic: (RuntimeDiagnostic) -> Unit,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val capabilityAvailable: (String) -> Boolean = { true }
) : ModRuntime {
    private data class Plan(
        var mod: InstalledMod,
        val definition: ValidatedPackage,
        var execution: ModExecutionContext,
        val lastEvent: MutableMap<ModEvent, Long> = mutableMapOf(),
        val v4Edges: V4ConditionEdgeTracker = V4ConditionEdgeTracker()
    )

    private data class Resource(
        var presentation: RuntimePresentation,
        val slot: Int,
        val requiresCharging: Boolean,
        var expiresAt: Long,
        val handle: AutoCloseable
    )

    private data class WorkflowResourceOwner(val modId: String, val workflowId: String, val sessionId: String, val actionId: String)

    private val plans = linkedMapOf<String, Plan>()
    private val resources = mutableMapOf<String, Resource>()
    private val workflowResources = linkedMapOf<WorkflowResourceOwner, Resource>()
    private val workflowPulses = linkedMapOf<WorkflowResourceOwner, Pair<Long, AutoCloseable>>()
    private val workflowCoordinator = WorkflowCoordinator(diagnostic)
    val workflowMonitor = workflowCoordinator.monitor
    private var workflowScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val failed = mutableSetOf<String>()
    private val mutableStates = MutableStateFlow<Map<String, ModRuntimeState>>(emptyMap())
    val states = mutableStates.asStateFlow()
    private val nextExpiry = MutableStateFlow<Long?>(null)
    val expiryDeadline = nextExpiry.asStateFlow()
    private var foreground = false
    private var safeMode = false
    private var charging = false
    private var powerSaving = false
    private val pulses = mutableMapOf<String, Pair<Long, AutoCloseable>>()
    private val displayReadAt = mutableMapOf<String, Long>()
    private var batteryPercent: Int? = null

    fun begin(initiallyCharging: Boolean, percent: Int? = null, initiallyPowerSaving: Boolean = false) {
        stop()
        charging = initiallyCharging
        powerSaving = initiallyPowerSaving
        batteryPercent = percent
        foreground = true
    }

    fun stop() {
        workflowCoordinator.cancelAll(WorkflowCancellationReason.RUNTIME_STOP)
        foreground = false
        plans.keys.toList().forEach(::deactivate)
        failed.clear()
        mutableStates.value = emptyMap()
    }

    fun setWorkflowScope(scope: CoroutineScope) { workflowScope = scope }

    fun setGlobalSafeMode(enabled: Boolean) {
        safeMode = enabled
        if (enabled) workflowCoordinator.cancelAll(WorkflowCancellationReason.SAFE_MODE)
    }

    fun cancelWorkflows(reason: WorkflowCancellationReason) = workflowCoordinator.cancelAll(reason)

    fun eligible(mod: InstalledMod): Boolean = foreground && !safeMode && mod.runtimeAvailable &&
        mod.desiredEnabled && mod.status == ModStatus.READY_TO_ACTIVATE &&
        mod.approvedCapabilities.containsAll(mod.capabilities) &&
        mod.capabilities.all(capabilityAvailable) &&
        ("overlay.render" !in mod.capabilities || overlayAuthorized())

    fun retain(mods: List<InstalledMod>) {
        val eligible = mods.filter(::eligible).associateBy { it.id }
        plans.values.toList().forEach { plan ->
            val updated = eligible[plan.mod.id]
            if (updated == null || plan.mod.copy(appearance = updated.appearance, settingValues = updated.settingValues) != updated) {
                deactivate(plan.mod.id)
            } else if (plan.mod.appearance != updated.appearance || plan.mod.settingValues != updated.settingValues) {
                // Update snapshots for future events without restarting the mod or disturbing owned effects.
                plan.mod = updated
                plan.execution = plan.execution.withSettings(updated.appearance, updated.settingValues)
            }
        }
        val ids = mods.filter { it.desiredEnabled && it.status in setOf(ModStatus.READY_TO_ACTIVATE, ModStatus.ERROR) }
            .map { it.id }.toSet()
        failed.retainAll(ids)
        mutableStates.value = mutableStates.value.filterKeys { it in ids }
    }

    fun needsActivation(mod: InstalledMod) = eligible(mod) && mod.id !in plans && mod.id !in failed

    fun loading(modId: String) = publish(modId, ModRuntimeState(RuntimePhase.ACTIVATING))

    override fun activate(mod: InstalledMod, definition: ValidatedPackage): Boolean =
        activate(mod, definition, replayCharging = true)

    fun activate(mod: InstalledMod, definition: ValidatedPackage, replayCharging: Boolean): Boolean {
        if (!needsActivation(mod)) return false
        val manifest = definition.review
        if (manifest.id != mod.id || manifest.version != mod.version || manifest.capabilities != mod.capabilities ||
            mod.appearance.intensity !in manifest.intensityRange ||
            (mod.appearance.ring == null) != (manifest.appearance.ring == null) ||
            mod.appearance.options.keys != manifest.optionDefinitions.map { it.id }.toSet() ||
            manifest.optionDefinitions.any { mod.appearance.options[it.id] !in it.minimum..it.maximum }) {
            fail(mod.id, "Installed definition or settings do not match the reviewed package.")
            return false
        }
        val schemaSettings = definition.v4?.settings.orEmpty()
        val values = runCatching { ModSettingValidator.validateValues(schemaSettings, mod.settingValues) }.getOrNull()
        if (mod.settingDefinitions.isNotEmpty() && schemaSettings != mod.settingDefinitions || values == null) {
            fail(mod.id, "Installed mod settings do not match the validated package schema.")
            return false
        }
        plans[mod.id] = Plan(mod, definition, ModExecutionContext(mod.id, UUID.randomUUID().toString(),
            mod.approvedCapabilities.toSet(), mod.appearance, clock, values))
        refresh(mod.id)
        if (charging && replayCharging) dispatch(ModEvent.CHARGING_STARTED, mod.id)
        return mod.id in plans
    }

    override fun deactivate(modId: String) {
        workflowCoordinator.cancelMod(modId, WorkflowCancellationReason.MOD_DISABLED)
        if (plans.remove(modId) != null) {
            publish(modId, ModRuntimeState(RuntimePhase.DEACTIVATING))
            publish(modId, ModRuntimeState(RuntimePhase.CLEANUP))
            release(modId)
            publish(modId, ModRuntimeState(RuntimePhase.DISABLED))
        }
    }

    fun recheckPermissions() {
        plans.values.toList().filterNot { eligible(it.mod) }.forEach {
            workflowCoordinator.cancelMod(it.mod.id, WorkflowCancellationReason.PERMISSION_REVOKED)
            deactivate(it.mod.id)
        }
    }

    fun batteryChanged(value: Boolean?, percent: Int? = batteryPercent) {
        val levelChanged = percent != null && percent != batteryPercent
        if (levelChanged) {
            batteryPercent = percent
            resources.forEach { (id, resource) ->
                if (plans[id]?.execution?.grantedCapabilities?.contains("battery.read") == true) {
                    resource.presentation = resource.presentation.copy(batteryPercent = percent)
                    refresh(id)
                }
            }
            workflowResources.forEach { (owner, resource) ->
                if (plans[owner.modId]?.execution?.grantedCapabilities?.contains("battery.read") == true)
                    resource.presentation = resource.presentation.copy(batteryPercent = percent)
            }
            (resources.keys + workflowResources.keys.map { it.modId }).distinct().forEach { modId ->
                if (plans[modId]?.execution?.grantedCapabilities?.contains("battery.read") == true) executor.updateBattery(modId, percent)
            }
            dispatch(ModEvent.BATTERY_LEVEL_CHANGED)
        }
        if (value == null) return
        if (value == charging) return
        charging = value
        dispatch(if (value) ModEvent.CHARGING_STARTED else ModEvent.CHARGING_STOPPED)
    }

    fun powerChanged(value: Boolean) {
        if (value == powerSaving) return
        powerSaving = value
        dispatch(ModEvent.POWER_CHANGED)
    }

    /** Deterministic event injection entry point for tests and explicit in-app diagnostics. */
    fun simulateBatteryReading(percent: Int?, isCharging: Boolean?) {
        require(percent == null || percent in 0..100)
        batteryChanged(isCharging, percent)
    }

    override fun dispatch(event: ModEvent, targetModId: String?) {
        if (!foreground || safeMode) return
        recheckPermissions()
        val targets = if (targetModId == null) plans.values.toList() else listOfNotNull(plans[targetModId])
        for (plan in targets) {
            val id = plan.mod.id
            if (event == ModEvent.CHARGING_STOPPED && resources[id]?.requiresCharging == true) {
                release(id)
            }
            if (event == ModEvent.CHARGING_STOPPED) dismissChargingBound(id)
            val v4 = plan.definition.v4
            if (v4 != null) {
                // Ignore events the definition did not subscribe to before checking
                // their capabilities. Shared sources (notably battery) dispatch to
                // all active mods; unrelated events must not fault-isolate a mod.
                val matchingTriggers = v4.triggers.filter { it.event == event.wireName }
                if (matchingTriggers.isEmpty()) continue
                if (event.capability?.let { it !in plan.execution.grantedCapabilities || it !in plan.mod.approvedCapabilities } == true) {
                    fail(id, "Event capability is unavailable.")
                    continue
                }
                val snapshot = ModEventSnapshot(event.wireName, batteryPercent, charging,
                    plan.execution.settingValues.mapNotNull { (settingId, value) ->
                        value.toConditionValue()?.let { "setting.$settingId" to it }
                    }.toMap())
                for (trigger in matchingTriggers) {
                    val matches = try { V4ConditionEvaluator.evaluate(trigger.condition, snapshot) }
                    catch (_: IllegalArgumentException) { fail(id, "Condition complexity exceeded runtime limits."); break }
                    if (plan.v4Edges.shouldFire(trigger.id, matches)) {
                        val config = trigger.workflow
                        if (config == null) trigger.actions.firstOrNull()?.let { executeV4ActionLegacy(plan, event, it) }
                        else startV4Workflow(plan, event, trigger, config)
                    }
                    if (id !in plans) break
                }
                continue
            }
            val subscription = plan.definition.subscriptions[event] ?: continue
            if (!subscription.condition.matches(charging, powerSaving, batteryPercent, plan.execution.settings.options[subscription.setting])) continue
            val now = nowMillis()
            if (plan.lastEvent[event]?.let { now - it < 500 } == true) continue
            plan.lastEvent[event] = now
            val effect = subscription.action.effect
                if (effect !in executor.supportedActions || effect.capability !in plan.execution.grantedCapabilities ||
                    effect.capability !in plan.mod.approvedCapabilities || !capabilityAvailable(effect.capability)) {
                fail(id, "Effect capability or action is unavailable.")
                continue
            }
            if (listOfNotNull(event.capability, subscription.condition.capability).any {
                    it !in plan.execution.grantedCapabilities }) {
                fail(id, "Event or condition capability is unavailable.")
                continue
            }
            if (effect == ModEffect.HAPTIC_PULSE) {
                try {
                    pulses.remove(id)?.second?.close()
                    val handle = executor.pulse(plan.execution)
                    if (handle == null) {
                        diagnostic(RuntimeDiagnostic(id, effect.wireName, "SKIPPED", "Global haptic rate limit reached."))
                        continue
                    }
                    pulses[id] = now + 50 to handle
                    updateExpiry()
                    diagnostic(RuntimeDiagnostic(id, effect.wireName, "SUBMITTED", "Android may suppress haptics by policy."))
                } catch (_: RuntimeException) {
                    fail(id, "Haptic adapter is unavailable.")
                }
                continue
            }
            if (effect == ModEffect.DISMISS_OVERLAY) {
                if (resources[id]?.presentation?.surface == EffectSurface.OVERLAY) dismiss(id)
                diagnostic(RuntimeDiagnostic(id, effect.wireName, "SUCCESS"))
                continue
            }
            try {
                val surface = if (effect == ModEffect.SHOW_OVERLAY) EffectSurface.OVERLAY else EffectSurface.PREVIEW
                release(id)
                val slot = if (surface == EffectSurface.OVERLAY) (0 until 2).firstOrNull { candidate ->
                    resources.values.none { it.slot == candidate }
                } else -1
                if (slot == null) {
                    diagnostic(RuntimeDiagnostic(id, effect.wireName, "SKIPPED", "Overlay limit reached."))
                    refresh(id, "Overlay limit reached.")
                    continue
                }
                val presentation = RuntimePresentation(surface, requireNotNull(subscription.action.template),
                    plan.execution.settings,
                    batteryPercent.takeIf { "battery.read" in plan.execution.grantedCapabilities },
                    chargingBound = event == ModEvent.CHARGING_STARTED || subscription.condition == Condition.CHARGING)
                val handle = executor.show(plan.execution, presentation, slot)
                resources[id] = Resource(presentation, slot,
                    event == ModEvent.CHARGING_STARTED || subscription.condition == Condition.CHARGING, now + (presentation.appearance.ring?.durationSeconds ?: 20) * 1000, handle)
                updateExpiry()
                refresh(id)
                diagnostic(RuntimeDiagnostic(id, effect.wireName, "SUCCESS"))
            } catch (_: RuntimeException) {
                fail(id, "Could not render the requested effect.")
            }
        }
    }

    /** V4 definitions without an explicit workflow retain the Milestone 6B first-action contract. */
    private fun executeV4ActionLegacy(plan: Plan, event: ModEvent, action: V4Action) {
        val id = plan.mod.id
        val effect = ModEffect.entries.firstOrNull { it.wireName == action.effect }
        if (effect == null || effect !in executor.supportedActions || !capabilityAvailable(effect.capability) || effect.capability !in plan.mod.approvedCapabilities ||
            effect.capability !in plan.execution.grantedCapabilities ||
            listOfNotNull(event.capability).any { it !in plan.mod.approvedCapabilities || it !in plan.execution.grantedCapabilities }) {
            diagnostic(RuntimeDiagnostic(id, action.effect, "DENIED", "Action capability or adapter is unavailable."))
            return
        }
        val template = action.template?.let { name -> org.doom.model.EffectTemplate.entries.firstOrNull { it.wireName == name } }
        when (effect) {
            ModEffect.READ_DISPLAY -> try {
                if (!allowDisplayRead(id)) {
                    diagnostic(RuntimeDiagnostic(id, action.effect, "RATE_LIMITED", "Display metadata reads are limited to 10 per second."))
                    return
                }
                val display = executor.readDisplay(plan.execution)
                diagnostic(RuntimeDiagnostic(id, action.effect, "SUCCESS",
                    "${display.widthPixels}x${display.heightPixels}px, ${display.densityDpi}dpi, rotation ${display.rotation}"))
            } catch (_: RuntimeException) { fail(id, "Display metadata is unavailable.") }
            ModEffect.DISMISS_OVERLAY -> { dismiss(id); diagnostic(RuntimeDiagnostic(id, action.effect, "SUCCESS")) }
            ModEffect.HAPTIC_PULSE -> try {
                pulses.remove(id)?.second?.close()
                val handle = executor.pulse(plan.execution)
                if (handle == null) {
                    diagnostic(RuntimeDiagnostic(id, action.effect, "SKIPPED", "Global haptic rate limit reached."))
                    return
                }
                pulses[id] = nowMillis() + 50 to handle
                updateExpiry()
                diagnostic(RuntimeDiagnostic(id, action.effect, "SUBMITTED", "Android may suppress haptics by policy."))
            } catch (_: RuntimeException) { fail(id, "Haptic adapter is unavailable.") }
            ModEffect.SHOW_OVERLAY, ModEffect.APPLY_OVERLAY_PRESET, ModEffect.SHOW_PREVIEW -> {
                val externalOverlay = effect == ModEffect.SHOW_OVERLAY || effect == ModEffect.APPLY_OVERLAY_PRESET
                if (template == null || externalOverlay && (!overlayAuthorized() || "overlay.render" !in plan.execution.grantedCapabilities || "overlay.render" !in plan.mod.approvedCapabilities)) {
                    diagnostic(RuntimeDiagnostic(id, action.effect, "DENIED", "Template or overlay permission is unavailable."))
                    return
                }
                val parameters = runCatching { action.parameters.mapValues { (_, raw) -> com.google.gson.JsonParser.parseString(raw) } }
                    .getOrElse { diagnostic(RuntimeDiagnostic(id, action.effect, "DENIED", "Action parameters are malformed.")); return }
                val resolved = parameters.mapValues { (key, element) ->
                    if (!element.isJsonObject || !element.asJsonObject.has("\$setting")) element
                    else {
                        val settingId = element.asJsonObject["\$setting"].asString
                        val value = plan.execution.settingValues[settingId]
                        if (value == null) {
                            diagnostic(RuntimeDiagnostic(id, action.effect, "DENIED", "Action references an unavailable setting."))
                            return
                        }
                        runCatching { resolveSettingParameter(key, value) }.getOrElse {
                            diagnostic(RuntimeDiagnostic(id, action.effect, "DENIED", "Action setting value is invalid."))
                            return
                        }
                    }
                }
                val text = listOf("message", "title").firstNotNullOfOrNull { key ->
                    resolved[key]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                }
                val color = resolved["color"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                val intensity = resolved["intensity"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
                val thickness = resolved["ringThicknessDp"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
                val diameter = resolved["ringDiameterDp"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
                val position = resolved["position"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let {
                    runCatching { RingPosition.valueOf(it.replace('-', '_').uppercase()) }.getOrNull()
                }
                val duration = resolved["durationMs"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble?.toLong()
                if (resolved.keys.any { it !in setOf("message", "title", "durationMs", "color", "intensity", "ringThicknessDp", "ringDiameterDp", "position") } ||
                    resolved.filterKeys { it in setOf("message", "title") }.values.any { !it.isJsonPrimitive || !it.asJsonPrimitive.isString || it.asString.length !in 1..80 } ||
                    (duration != null && duration !in 1..WorkflowCoordinator.MAX_TIMEOUT_MILLIS) ||
                    (color != null && !GlowAppearance.colorPattern.matches(color)) ||
                    (intensity != null && intensity !in GlowAppearance.intensities) ||
                    (thickness != null && thickness !in RingStyle.thicknessRange) ||
                    (diameter != null && diameter !in RingStyle.diameterRange) ||
                    (resolved.containsKey("position") && position == null)) {
                    diagnostic(RuntimeDiagnostic(id, action.effect, "DENIED", "Action parameters failed runtime validation."))
                    return
                }
                val surface = if (externalOverlay) EffectSurface.OVERLAY else EffectSurface.PREVIEW
                release(id)
                val slot = if (surface == EffectSurface.OVERLAY) (0 until 2).firstOrNull { candidate -> resources.values.none { it.slot == candidate } } else -1
                if (slot == null) { diagnostic(RuntimeDiagnostic(id, action.effect, "SKIPPED", "Overlay limit reached.")); return }
                try {
                    val originalRing = plan.execution.settings.ring
                    val ring = if (thickness != null || diameter != null || position != null) {
                        (originalRing ?: RingStyle(4, 48, 20, RingPosition.BOTTOM_END)).copy(
                            thicknessDp = thickness ?: originalRing?.thicknessDp ?: 4,
                            diameterDp = diameter ?: originalRing?.diameterDp ?: 48,
                            position = position ?: originalRing?.position ?: RingPosition.BOTTOM_END)
                    } else originalRing
                    val appearance = plan.execution.settings.copy(color = color ?: plan.execution.settings.color,
                        intensity = intensity ?: plan.execution.settings.intensity, ring = ring)
                    val presentation = RuntimePresentation(surface, template, appearance,
                        batteryPercent.takeIf { "battery.read" in plan.execution.grantedCapabilities },
                        chargingBound = event == ModEvent.CHARGING_STARTED, contentText = text)
                    val handle = executor.show(plan.execution, presentation, slot)
                    resources[id] = Resource(presentation, slot, event == ModEvent.CHARGING_STARTED,
                        nowMillis() + (duration ?: (presentation.appearance.ring?.durationSeconds?.times(1000L) ?: 20_000L)), handle)
                    updateExpiry(); refresh(id)
                    diagnostic(RuntimeDiagnostic(id, action.effect, "SUCCESS"))
                } catch (_: RuntimeException) { fail(id, "Could not render the requested effect.") }
            }
        }
    }

    private fun startV4Workflow(plan: Plan, event: ModEvent, trigger: V4Trigger, config: V4WorkflowConfig) {
        val workflow = WorkflowPlan(plan.mod.id, config.id, plan.execution.sessionId, config.executionMode,
            config.failurePolicy, config.timeoutMillis,
            trigger.actions.map { WorkflowActionPlan(it.id, it, it.independent) }, config.maxConcurrency)
        val session = workflowCoordinator.start(workflowScope, workflow) { actionContext ->
            executeWorkflowAction(plan, event, actionContext)
        }
        if (session == null) diagnostic(RuntimeDiagnostic(plan.mod.id, "workflow:${config.id}", "SKIPPED",
            "Maximum active workflows for this mod reached."))
    }

    private fun executeWorkflowAction(plan: Plan, event: ModEvent, actionContext: WorkflowActionContext): ActionExecutionResult {
        val actionPlan = actionContext.action
        val action = actionPlan.action
        val id = plan.mod.id
        fun failed(message: String) = ActionExecutionResult(actionPlan.id, ActionExecutionStatus.FAILED, message)
        if (!foreground || safeMode || plans[id] !== plan || !eligible(plan.mod)) return failed("Mod runtime is no longer eligible.")
        val effect = ModEffect.entries.firstOrNull { it.wireName == action.effect }
            ?: return failed("No trusted executor is registered for this action.")
        if (effect !in executor.supportedActions) return failed("Trusted action executor is unavailable.")
        if (!capabilityAvailable(effect.capability)) return failed("Required Android capability is no longer available.")
        if (effect.capability !in plan.mod.approvedCapabilities || effect.capability !in plan.execution.grantedCapabilities)
            return failed("Action capability is not approved.")
        if (event.capability?.let { it !in plan.mod.approvedCapabilities || it !in plan.execution.grantedCapabilities } == true)
            return failed("Trigger capability is not approved.")
        val template = action.template?.let { name -> EffectTemplate.entries.firstOrNull { it.wireName == name } }
        if (effect in setOf(ModEffect.SHOW_OVERLAY, ModEffect.APPLY_OVERLAY_PRESET, ModEffect.SHOW_PREVIEW) && template == null)
            return failed("Action template is not supported.")
        val params = runCatching { action.parameters.mapValues { (_, raw) -> com.google.gson.JsonParser.parseString(raw) } }.getOrNull()
            ?: return failed("Action parameters are malformed.")
        val resolved = params.mapValues { (key, element) ->
            if (!element.isJsonObject || !element.asJsonObject.has("\$setting")) element
            else resolveSettingParameter(key, plan.execution.settingValues[element.asJsonObject["\$setting"].asString]
                ?: return failed("Action references an unavailable setting."))
        }
        val text = listOf("message", "title").firstNotNullOfOrNull { key -> resolved[key]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString }
        val color = resolved["color"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        val intensityValue = resolved["intensity"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
        val thickness = resolved["ringThicknessDp"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
        val diameter = resolved["ringDiameterDp"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
        val position = resolved["position"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let {
            runCatching { RingPosition.valueOf(it.replace('-', '_').uppercase()) }.getOrNull()
        }
        val duration = resolved["durationMs"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble?.toLong()
        if (resolved.keys.any { it !in setOf("message", "title", "durationMs", "color", "intensity", "ringThicknessDp", "ringDiameterDp", "position") } ||
            resolved.filterKeys { it in setOf("message", "title") }.values.any { !it.isJsonPrimitive || !it.asJsonPrimitive.isString || it.asString.length !in 1..80 } ||
            (duration != null && duration !in 1..WorkflowCoordinator.MAX_TIMEOUT_MILLIS) ||
            (color != null && !GlowAppearance.colorPattern.matches(color)) ||
            (intensityValue != null && intensityValue !in GlowAppearance.intensities) ||
            (thickness != null && thickness !in RingStyle.thicknessRange) ||
            (diameter != null && diameter !in RingStyle.diameterRange) ||
            (resolved.containsKey("position") && position == null)) return failed("Action parameters failed runtime validation.")

        when (effect) {
            ModEffect.READ_DISPLAY -> {
                if (!allowDisplayRead(id)) return failed("Display metadata reads are limited to 10 per second.")
                val display = try { executor.readDisplay(plan.execution.forEffect("${actionContext.sessionId}:${actionPlan.id}")) }
                    catch (_: RuntimeException) { return failed("Display metadata is unavailable.") }
                val summary = "${display.widthPixels}x${display.heightPixels}px, ${display.densityDpi}dpi, rotation ${display.rotation}"
                diagnostic(RuntimeDiagnostic(id, action.effect, "SUCCESS", summary))
                return ActionExecutionResult(actionPlan.id, ActionExecutionStatus.SUCCEEDED, summary)
            }
            ModEffect.DISMISS_OVERLAY -> {
                dismissWorkflow(actionContext.plan, actionContext.sessionId, id)
                return ActionExecutionResult(actionPlan.id, ActionExecutionStatus.SUCCEEDED)
            }
            ModEffect.HAPTIC_PULSE -> {
                val handle = try { executor.pulse(plan.execution.forEffect("${actionContext.sessionId}:${actionPlan.id}")) }
                    catch (_: RuntimeException) { return failed("Haptic action failed.") }
                if (handle != null) {
                    val owner = WorkflowResourceOwner(id, actionContext.plan.workflowId, actionContext.sessionId, actionPlan.id)
                    val closed = AtomicBoolean(false)
                    lateinit var tracked: AutoCloseable
                    val managed = AutoCloseable {
                        if (closed.compareAndSet(false, true)) {
                            workflowPulses.remove(owner)
                            runCatching { handle.close() }
                            updateExpiry()
                        }
                    }
                    tracked = actionContext.own(managed)
                    if (closed.get()) return failed("Workflow was cancelled during haptic dispatch.")
                    workflowPulses[owner] = nowMillis() + 50 to tracked
                    updateExpiry()
                }
                return ActionExecutionResult(actionPlan.id, ActionExecutionStatus.SUCCEEDED,
                    if (handle == null) "Rate limited" else null)
            }
            ModEffect.SHOW_OVERLAY, ModEffect.APPLY_OVERLAY_PRESET, ModEffect.SHOW_PREVIEW -> {
                val externalOverlay = effect == ModEffect.SHOW_OVERLAY || effect == ModEffect.APPLY_OVERLAY_PRESET
                if (externalOverlay && (!overlayAuthorized() || "overlay.render" !in plan.mod.approvedCapabilities || "overlay.render" !in plan.execution.grantedCapabilities))
                    return failed("Overlay permission is unavailable.")
                val surface = if (externalOverlay) EffectSurface.OVERLAY else EffectSurface.PREVIEW
                val slot = if (surface == EffectSurface.OVERLAY) (0..1).firstOrNull { candidate ->
                    resources.values.none { it.slot == candidate } && workflowResources.values.none { it.slot == candidate }
                } ?: return failed("Overlay resource limit reached.") else -1
                val originalRing = plan.execution.settings.ring
                val ring = if (thickness != null || diameter != null || position != null) {
                    (originalRing ?: RingStyle(4, 48, 20, RingPosition.BOTTOM_END)).copy(
                        thicknessDp = thickness ?: originalRing?.thicknessDp ?: 4,
                        diameterDp = diameter ?: originalRing?.diameterDp ?: 48,
                        position = position ?: originalRing?.position ?: RingPosition.BOTTOM_END)
                } else originalRing
                val appearance = plan.execution.settings.copy(color = color ?: plan.execution.settings.color,
                    intensity = intensityValue ?: plan.execution.settings.intensity, ring = ring)
                val presentation = RuntimePresentation(surface, requireNotNull(template), appearance,
                    batteryPercent.takeIf { "battery.read" in plan.execution.grantedCapabilities },
                    chargingBound = event == ModEvent.CHARGING_STARTED, contentText = text)
                val effectContext = plan.execution.forEffect("${actionContext.sessionId}:${actionPlan.id}")
                val visual = try { executor.show(effectContext, presentation, slot) }
                    catch (_: RuntimeException) { return failed("Action renderer failed.") }
                val owner = WorkflowResourceOwner(id, actionContext.plan.workflowId, actionContext.sessionId, actionPlan.id)
                val expiresAt = nowMillis() + (duration ?: (presentation.appearance.ring?.durationSeconds?.times(1000L) ?: 20_000L))
                val closed = AtomicBoolean(false)
                val managed = AutoCloseable {
                    if (closed.compareAndSet(false, true)) {
                        workflowResources.remove(owner)
                        runCatching { visual.close() }
                        updateExpiry()
                    }
                }
                val tracked = actionContext.own(managed)
                if (closed.get()) return failed("Workflow was cancelled during effect creation.")
                workflowResources[owner] = Resource(presentation, slot, event == ModEvent.CHARGING_STARTED, expiresAt, tracked)
                updateExpiry()
                diagnostic(RuntimeDiagnostic(id, action.effect, "SUCCESS", actionContext.plan.workflowId))
                return ActionExecutionResult(actionPlan.id, ActionExecutionStatus.SUCCEEDED)
            }
        }
    }

    fun extendPresentation(id: String, durationMillis: Long?) {
        require(durationMillis == null || durationMillis in 1..20_000)
        resources[id]?.expiresAt = durationMillis?.let { nowMillis() + it } ?: Long.MAX_VALUE
        updateExpiry()
    }

    private fun updateExpiry() { nextExpiry.value = (resources.values.map { it.expiresAt } + workflowResources.values.map { it.expiresAt } +
        pulses.values.map { it.first } + workflowPulses.values.map { it.first }).filter { it != Long.MAX_VALUE }.minOrNull() }

    fun expire() {
        recheckPermissions()
        val now = nowMillis()
        pulses.filterValues { now >= it.first }.keys.toList().forEach { id ->
            runCatching { pulses.remove(id)?.second?.close() }
        }
        workflowPulses.filterValues { now >= it.first }.keys.toList().forEach { owner ->
            runCatching { workflowPulses.remove(owner)?.second?.close() }
        }
        updateExpiry()
        resources.filterValues { now >= it.expiresAt }.keys.toList().forEach(::dismiss)
        workflowResources.filterValues { now >= it.expiresAt }.values.toList().forEach { runCatching { it.handle.close() } }
    }

    fun dismiss(modId: String) {
        val released = release(modId)
        workflowResources.filterKeys { it.modId == modId }.values.toList().forEach { runCatching { it.handle.close() } }
        workflowPulses.filterKeys { it.modId == modId }.values.toList().forEach { runCatching { it.second.close() } }
        if (released || workflowResources.keys.none { it.modId == modId }) refresh(modId)
    }

    private fun allowDisplayRead(modId: String): Boolean {
        val now = nowMillis()
        val previous = displayReadAt[modId]
        if (previous != null && now - previous < DISPLAY_READ_INTERVAL_MS) return false
        displayReadAt[modId] = now
        return true
    }

    companion object { private const val DISPLAY_READ_INTERVAL_MS = 100L }

    private fun dismissChargingBound(modId: String) {
        workflowResources.filter { it.key.modId == modId && it.value.requiresCharging }.values.toList()
            .forEach { runCatching { it.handle.close() } }
    }

    private fun dismissWorkflow(plan: WorkflowPlan, workflowSessionId: String, modId: String) {
        workflowResources.filterKeys { it.modId == modId && it.workflowId == plan.workflowId && it.sessionId == workflowSessionId }
            .values.toList().forEach { runCatching { it.handle.close() } }
        workflowPulses.filterKeys { it.modId == modId && it.workflowId == plan.workflowId && it.sessionId == workflowSessionId }
            .values.toList().forEach { runCatching { it.second.close() } }
    }

    fun fail(id: String, message: String) {
        workflowCoordinator.cancelMod(id, WorkflowCancellationReason.REQUESTED)
        plans.remove(id)
        release(id)
        failed += id
        publish(id, ModRuntimeState(RuntimePhase.ERROR, message = message))
        diagnostic(RuntimeDiagnostic(id, "runtime_failed", "ERROR", message))
    }

    override fun observeRuntimeState(modId: String) =
        states.map { it[modId] ?: ModRuntimeState(RuntimePhase.DISABLED) }.distinctUntilChanged()

    private fun release(id: String): Boolean {
        runCatching { pulses.remove(id)?.second?.close() }
        workflowPulses.filterKeys { it.modId == id }.values.toList().forEach { runCatching { it.second.close() } }
        updateExpiry()
        val resource = resources.remove(id) ?: return false
        updateExpiry()
        try {
            resource.handle.close()
            diagnostic(RuntimeDiagnostic(id, "effect_cleanup", "CANCELLED"))
        } catch (_: Exception) {
            diagnostic(RuntimeDiagnostic(id, "cleanup_failed", "ERROR", "Android effect cleanup failed."))
        }
        return true
    }

    private fun refresh(id: String, message: String? = null) {
        val plan = plans[id] ?: return
        publish(id, ModRuntimeState(RuntimePhase.ACTIVE, resources[id]?.presentation,
            ModEvent.MANUAL in plan.definition.subscriptions || plan.definition.v4?.triggers?.any { it.event == ModEvent.MANUAL.wireName } == true,
            message))
    }

    private fun ModSettingValue.toConditionValue(): ConditionValue? = when (this) {
        is ModSettingValue.BooleanValue -> ConditionValue.Flag(value)
        is ModSettingValue.IntegerValue -> ConditionValue.Number(value.toDouble())
        is ModSettingValue.NumberValue -> ConditionValue.Number(value)
        is ModSettingValue.DurationValue -> ConditionValue.Number(value)
        is ModSettingValue.ColorValue -> ConditionValue.Text(value)
        is ModSettingValue.EnumValue -> ConditionValue.Text(value)
        is ModSettingValue.TextValue -> ConditionValue.Text(value)
    }

    private fun resolveSettingParameter(key: String, value: ModSettingValue): com.google.gson.JsonElement {
        val resolved: Any = when (value) {
            is ModSettingValue.BooleanValue -> value.value
            is ModSettingValue.IntegerValue -> value.value
            is ModSettingValue.NumberValue -> value.value
            is ModSettingValue.DurationValue -> if (key == "durationMs") when (value.unit) {
                "ms" -> value.value
                "s" -> value.value * 1000
                "min" -> value.value * 60_000
                else -> error("Unsupported duration unit.")
            } else value.value
            is ModSettingValue.ColorValue -> value.value
            is ModSettingValue.EnumValue -> value.value
            is ModSettingValue.TextValue -> value.value
        }
        return when (resolved) {
            is String -> com.google.gson.JsonPrimitive(resolved)
            is Number -> com.google.gson.JsonPrimitive(resolved)
            is Boolean -> com.google.gson.JsonPrimitive(resolved)
            else -> error("Unsupported setting value.")
        }
    }

    private fun publish(id: String, state: ModRuntimeState) {
        mutableStates.value = mutableStates.value + (id to state)
    }
}
