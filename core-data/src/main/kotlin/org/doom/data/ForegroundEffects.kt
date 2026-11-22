package org.doom.data

import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.doom.domain.DoomPreferences
import org.doom.engine.runtime.DefaultModRuntime
import org.doom.engine.runtime.ModEvent
import org.doom.engine.runtime.RuntimeDiagnostic
import org.doom.model.ModStatus
import javax.inject.Inject

/** The activity calls this on the main thread and owns foreground eligibility. */
class ForegroundEffects @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val repository: RoomModRepository,
    private val preferences: DoomPreferences,
    private val recovery: RuntimeRecovery,
    private val metrics: RuntimeMetrics,
    private val external: ExternalOverlayController,
    private val capabilityScanner: DeviceCapabilityScanner
) {
    private val diagnostics = Channel<RuntimeDiagnostic>(32, BufferOverflow.DROP_OLDEST)
    private val runtime = DefaultModRuntime(AndroidEffects(context), SystemClock::elapsedRealtime,
        { Settings.canDrawOverlays(context) }, { diagnostics.trySend(it) },
        capabilityAvailable = { capabilityScanner.availability(it) == "AVAILABLE" })
    val states = runtime.states
    val workflows = runtime.workflowMonitor
    private val level = MutableStateFlow<Int?>(null)
    val batteryPercent = level.asStateFlow()
    private var worker: Job? = null
    private var owner: Any? = null
    private var logger: Job? = null
    private val authorization = MutableStateFlow(false)
    private val externalOwners = mutableSetOf<String>()
    private val held = MutableStateFlow<Set<String>>(emptySet())

    fun start(scope: CoroutineScope) {
        if (worker?.isActive == true) return
        runtime.setWorkflowScope(scope)
        if (logger?.isActive != true) logger = scope.launch {
            for (event in diagnostics) repository.recordRuntimeDiagnostic(event)
        }
        val session = Any()
        owner = session
        worker = scope.launch {
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            val power = context.getSystemService(PowerManager::class.java)
            val ops = context.getSystemService(AppOpsManager::class.java)
            val watcher = AppOpsManager.OnOpChangedListener { _, _ ->
                context.mainExecutor.execute { if (owner === session) refreshAuthorization() }
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (owner === session && intent.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                        runtime.powerChanged(power?.isPowerSaveMode ?: false)
                    }
                    if (owner === session && intent.action == Intent.ACTION_BATTERY_CHANGED) {
                        val reading = batteryReading(intent)
                        if (reading.percent != null) level.value = reading.percent
                        runtime.batteryChanged(reading.charging, reading.percent ?: level.value)
                    }
                }
            }
            var listening = false
            var watching = false
            try {
                if (recovery.begin()) {
                    repository.setSafeMode(true)
                    return@launch
                }
                val battery = context.registerReceiver(null, batteryFilter)
                val initialReading = batteryReading(battery)
                level.value = initialReading.percent
                runtime.begin(initialReading.charging ?: false, initialReading.percent, power?.isPowerSaveMode ?: false)
                authorization.value = Settings.canDrawOverlays(context)
                if (Build.VERSION.SDK_INT >= 33) {
                    context.registerReceiver(receiver, batteryFilter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(receiver, batteryFilter)
                }
                listening = true
                ops.startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, context.packageName, watcher)
                watching = true
                recovery.ready()
                coroutineScope {
                    launch {
                        states.collect { rows ->
                            val id = rows.entries.firstOrNull { (_, state) ->
                                state.presentation?.surface == org.doom.model.EffectSurface.OVERLAY &&
                                    state.presentation?.chargingBound == true && state.phase == org.doom.model.RuntimePhase.ACTIVE
                            }?.key
                            val deadline = runtime.expiryDeadline.value
                            if (id != null && deadline != null && external.state.value.modId == null) {
                                // This worker exists only while the Activity is resumed and focused.
                                external.charging(id, (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1))
                            }
                        }
                    }
                    launch {
                        runtime.expiryDeadline.collectLatest { deadline ->
                            if (deadline != null) {
                                delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1))
                                runtime.expire()
                            }
                        }
                    }
                    launch {
                        authorization.drop(1).collect { repository.reconcileRequirements() }
                    }
                    combine(repository.observeInstalled(), preferences.values.map { it.safeMode }.distinctUntilChanged(),
                        authorization, held, external.state) { mods, safeMode, _, paused, outside ->
                        runtime.setGlobalSafeMode(safeMode)
                        outside.modId?.let { externalOwners.add(it) }
                        val relevant = paused.intersect(mods.filter { it.desiredEnabled && it.status == ModStatus.READY_TO_ACTIVATE }
                            .map { it.id }.toSet())
                        if (held.value == paused && relevant != paused) held.value = relevant
                        if (safeMode) emptyList() else mods.filter { it.id !in paused && it.id != outside.modId }
                    }.collectLatest { mods ->
                            val eligible = mods.filter(runtime::eligible).take(16)
                            runtime.retain(eligible + mods.filter { it.status == ModStatus.ERROR })
                            for (mod in eligible.filter(runtime::needsActivation)) {
                                runtime.loading(mod.id)
                                try {
                                    val definition = withTimeout(5_000) { repository.loadRuntimeDefinition(mod.id) }
                                    ensureActive()
                                    if (mod.id !in held.value) runtime.activate(mod, definition, replayCharging = mod.id !in externalOwners)
                                } catch (_: TimeoutCancellationException) {
                                    ensureActive()
                                    runtime.fail(mod.id, "Package verification timed out.")
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    runtime.fail(mod.id, "Could not verify the installed package. Reimport it to retry.")
                                }
                            }
                        }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                runCatching { if (recovery.failed()) repository.setSafeMode(true) }
                android.util.Log.w("DoomRuntime", "Foreground session could not start.")
            } finally {
                if (watching) ops.stopWatchingMode(watcher)
                if (listening) context.unregisterReceiver(receiver)
            }
        }
        // Lifecycle callbacks below close windows synchronously. Completion covers exceptional termination.
        val current = worker
        current?.invokeOnCompletion {
            context.mainExecutor.execute {
                if (worker === current) stop()
            }
        }
    }

    fun stop() {
        val current = worker
        worker = null
        owner = null
        level.value = null
        runtime.stop()
        current?.cancel()
    }

    fun pauseMod(modId: String) {
        held.value = held.value + modId
        runtime.deactivate(modId)
    }

    fun resumeMod(modId: String) {
        held.value = held.value - modId
    }

    fun manual(modId: String) {
        val started = metrics.started()
        runtime.dispatch(ModEvent.MANUAL, modId)
        metrics.record("manual_dispatch", started)
    }
    fun dismiss(modId: String) = runtime.dismiss(modId)

    private fun refreshAuthorization() {
        authorization.value = Settings.canDrawOverlays(context)
        runtime.recheckPermissions()
    }

    private fun batteryReading(intent: Intent?) = org.doom.engine.runtime.BatteryReading.fromExtras(
        intent?.takeIf { it.hasExtra(BatteryManager.EXTRA_LEVEL) }?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
        intent?.takeIf { it.hasExtra(BatteryManager.EXTRA_SCALE) }?.getIntExtra(BatteryManager.EXTRA_SCALE, 0),
        intent?.takeIf { it.hasExtra(BatteryManager.EXTRA_STATUS) }?.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
        intent?.takeIf { it.hasExtra(BatteryManager.EXTRA_PLUGGED) }?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    )
}
