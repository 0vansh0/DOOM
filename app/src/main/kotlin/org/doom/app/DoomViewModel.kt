package org.doom.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.doom.data.ForegroundEffects
import org.doom.data.ExternalOverlayController
import android.app.Activity
import org.doom.data.DiagnosticExporter
import org.doom.data.RuntimeMetrics
import org.doom.data.ShizukuConnectionManager
import org.doom.data.ShizukuPrivilegedAdapter
import org.doom.domain.ModResult
import org.doom.domain.ModUseCases
import org.doom.model.*

internal sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data object Error : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
}

@HiltViewModel
internal class DoomViewModel @Inject constructor(private val mods: ModUseCases, private val runtime: ForegroundEffects,
    private val exporter: DiagnosticExporter, private val metrics: RuntimeMetrics,
    private val external: ExternalOverlayController,
    private val capabilityScanner: org.doom.data.DeviceCapabilityScanner,
    private val shizuku: ShizukuConnectionManager,
    private val shizukuAdapter: ShizukuPrivilegedAdapter,
    private val changeTransactions: org.doom.data.RoomChangeTransactionStore,
    private val changeCoordinator: org.doom.domain.ChangeTransactionCoordinator) : ViewModel() {
    val changeHistory = changeTransactions.observeHistory()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val timeoutValue = MutableStateFlow<Long?>(null)
    val screenTimeoutMillis = timeoutValue.asStateFlow()
    fun refreshScreenTimeout() {
        viewModelScope.launch {
            timeoutValue.value = changeCoordinator.read(PrivilegedOperationCatalog.SCREEN_TIMEOUT_READ).value
        }
    }
    fun applyScreenTimeoutTest() {
        viewModelScope.launch {
            val result = changeCoordinator.apply(OperationRequest(
                modId = "__doom_manual__", operationId = PrivilegedOperationCatalog.SCREEN_TIMEOUT_SET,
                requestedValue = 120_000L, userConfirmed = true, modEnabled = true,
                safeMode = preferences.value.safeMode
            ))
            report(result.message)
            refreshScreenTimeout()
        }
    }
    fun restoreChange(transactionId: String) {
        viewModelScope.launch {
            val result = changeCoordinator.restore(transactionId, userConfirmed = true)
            report(result.message)
            refreshScreenTimeout()
        }
    }
    val shizukuState = shizuku.state
    fun refreshShizuku() = shizuku.refresh()
    fun requestShizukuAuthorization() = shizuku.requestAuthorization()
    fun testShizukuDiagnostic() {
        viewModelScope.launch {
            val result = shizukuAdapter.runIdentityDiagnostic(
                userInitiated = true,
                approvedOperationIds = setOf(ShizukuOperationPolicy.DIAGNOSTIC_IDENTITY)
            )
            report(result.message)
        }
    }
    val externalState = external.state
    fun testExternal(activity: Activity, id: String, persistent: Boolean) = external.start(activity, id, persistent)
    fun stopExternal() = external.stop()
    val runtimeStates = runtime.states
    val runningWorkflows = runtime.workflows
    val batteryPercent = runtime.batteryPercent
    val executionDiagnostics = mods.executionDiagnostics()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun foreground(eligible: Boolean) {
        if (eligible) { runtime.start(viewModelScope); refreshCapabilities(true); refreshScreenTimeout() } else runtime.stop()
    }

    private val capabilitySnapshot = MutableStateFlow<org.doom.model.DeviceCapabilitySnapshot?>(null)
    val deviceCapabilities = capabilitySnapshot.asStateFlow()
    fun refreshCapabilities(appForeground: Boolean = true) {
        capabilitySnapshot.value = runCatching { capabilityScanner.scan(appForeground) }.getOrElse { error ->
            org.doom.model.DeviceCapabilitySnapshot(
                scannedAtMillis = System.currentTimeMillis(), androidApi = null, androidRelease = null,
                manufacturer = null, model = null, appForeground = appForeground, capabilities = emptyList(),
                overlayAuthorization = org.doom.model.CapabilityAvailability.UNKNOWN,
                notificationAuthorization = org.doom.model.CapabilityAvailability.UNKNOWN,
                vibrationHardware = org.doom.model.CapabilityAvailability.UNKNOWN,
                foregroundServiceEligibility = org.doom.model.CapabilityAvailability.UNKNOWN,
                scanError = error.message?.take(180) ?: "Device capability scan failed."
            )
        }
    }
    fun compatibility(mod: InstalledMod): org.doom.model.ModCompatibilityReport {
        val snapshot = capabilitySnapshot.value ?: runCatching { capabilityScanner.scan() }.getOrNull()
        val statuses = snapshot?.capabilities.orEmpty().associateBy { it.id }.toMutableMap().apply {
            // Keep the pre-registry capability spelling readable and enforceable for old installed rows.
            get("overlay.render")?.let { put("overlay", it.copy(id = "overlay")) }
        }
        return org.doom.model.ModCompatibilityEvaluator.evaluate(
            requirements = mod.capabilities,
            approved = mod.approvedCapabilities,
            minAndroidApi = mod.minAndroidApi,
            currentAndroidApi = snapshot?.androidApi,
            definitionSupported = mod.runtimeAvailable,
            manifestSupported = mod.runtimeAvailable,
            capabilities = statuses
        )
    }
    fun manual(id: String) = runtime.manual(id)
    fun dismissEffect(id: String) = runtime.dismiss(id)

    val library = mods.installed()
        .map<List<InstalledMod>, LoadState<List<InstalledMod>>> { LoadState.Ready(it) }
        .catch { emit(LoadState.Error) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LoadState.Loading)

    val preferences = mods.globalPreferences
        .catch { report("Could not read local preferences.") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GlobalPreferences())

    private val exampleState = MutableStateFlow<LoadState<List<ModManifest>>>(LoadState.Loading)
    val examples = exampleState.asStateFlow()
    private val progress = MutableStateFlow(false)
    val busy = progress.asStateFlow()
    private val feedback = MutableStateFlow<String?>(null)
    val message = feedback.asStateFlow()

    init { shizuku.start(); loadExamples(); refreshScreenTimeout(); viewModelScope.launch { changeCoordinator.reconcile() } }

    override fun onCleared() {
        shizuku.stop()
        super.onCleared()
    }

    fun loadExamples() {
        viewModelScope.launch {
            exampleState.value = LoadState.Loading
            exampleState.value = try {
                LoadState.Ready(mods.bundledExamples())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                LoadState.Error
            }
        }
    }

    fun details(id: String) = mods.details(id).catch {
        report("Could not read mod details.")
    }

    fun refreshRequirements() {
        viewModelScope.launch {
            val result = mods.reconcileRequirements()
            if (result is ModResult.Rejected) report(result.message)
        }
    }

    fun installExample(id: String) = mutate("Example installed") { mods.installExample(id); ModResult.Success }
    fun setGrant(id: String, capabilityId: String, granted: Boolean) = mutate {
        if (granted) mods.setGrant(id, true, capabilityId).also {
            if (it == ModResult.Success) runtime.resumeMod(id)
        } else pauseWhile(id) { mods.setGrant(id, false, capabilityId) }
    }
    fun requestEnable(id: String) = mutate {
        val started = metrics.started()
        mods.requestEnable(id).also {
            runtime.resumeMod(id)
            metrics.record("enable_request", started)
        }
    }
    fun disable(id: String) = mutate { pauseWhile(id) { mods.disable(id) } }
    fun configure(id: String, appearance: GlowAppearance) = mutate("Appearance saved") { mods.configure(id, appearance) }
    fun configureSettings(id: String, values: Map<String, ModSettingValue>) = mutate("Mod settings saved") {
        mods.configureSettings(id, values)
    }
    fun uninstall(id: String) = mutate("Mod uninstalled") { pauseWhile(id) { mods.uninstall(id) } }
    fun disableAll() = mutate("All mods disabled") { mods.disableAll() }
    fun setSafeMode(enabled: Boolean) = mutate { mods.setSafeMode(enabled) }
    fun clearExecutionDiagnostics() {
        viewModelScope.launch { runCatching { mods.clearExecutionDiagnostics() }.onFailure { report("Could not clear local diagnostics.") } }
    }
    fun setTheme(theme: ThemePreference) = mutate { mods.setTheme(theme); ModResult.Success }
    fun setDiagnosticLogging(enabled: Boolean) = mutate { mods.setDiagnosticLogging(enabled); ModResult.Success }
    fun setPreviewOnOpen(enabled: Boolean) = mutate { mods.setPreviewOnOpen(enabled); ModResult.Success }

    private suspend fun pauseWhile(id: String, action: suspend () -> ModResult): ModResult {
        if (external.state.value.modId == id) external.stop()
        runtime.pauseMod(id)
        return action().also { if (it is ModResult.Rejected) runtime.resumeMod(id) }
    }

    fun resetSettings(id: String) = mutate("Settings reset") { mods.resetSettings(id) }
    fun exportDiagnostics(uri: String) = mutate("Diagnostics saved") {
        exporter.export(uri)
        ModResult.Success
    }

    fun report(message: String) { feedback.value = message }
    fun dismissMessage(message: String) { feedback.compareAndSet(message, null) }

    private fun mutate(success: String? = null, action: suspend () -> ModResult) {
        if (!progress.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                when (val result = action()) {
                    ModResult.Success -> success?.let(::report)
                    is ModResult.Rejected -> report(result.message)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                report(error.message?.take(160) ?: "The mod operation failed.")
            } finally {
                progress.value = false
            }
        }
    }
}
