package org.doom.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.doom.core.designsystem.Page
import org.doom.model.GlobalPreferences
import org.doom.model.ThemePreference
import org.doom.model.DeviceCapabilitySnapshot
import org.doom.model.CapabilityAvailability
import org.doom.model.ShizukuConnectionState
import org.doom.model.ShizukuConnectionPhase
import org.doom.model.ChangeTransaction
import org.doom.model.ChangeTransactionStatus
import org.doom.model.InstalledMod
import org.doom.model.ModRuntimeState
import org.doom.model.RuntimePhase
import org.doom.model.ExecutionDiagnostic
import org.doom.model.DiagnosticSeverity
import org.doom.model.DiagnosticFailureCategory
import org.doom.model.WorkflowMonitorEntry
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    preferences: GlobalPreferences,
    busy: Boolean,
    setPreview: (Boolean) -> Unit,
    setSafeMode: (Boolean) -> Unit,
    setTheme: (ThemePreference) -> Unit,
    setLogging: (Boolean) -> Unit,
    disableAll: () -> Unit,
    exportDiagnostics: () -> Unit,
    shizukuState: ShizukuConnectionState = ShizukuConnectionState(),
    refreshShizuku: () -> Unit = {},
    requestShizukuAuthorization: () -> Unit = {},
    openShizuku: () -> Unit = {},
    testShizukuDiagnostic: () -> Unit = {},
    screenTimeoutMillis: Long? = null,
    changeHistory: List<ChangeTransaction> = emptyList(),
    canWriteSystemSettings: Boolean = false,
    refreshRecovery: () -> Unit = {},
    openWriteSettings: () -> Unit = {},
    applyTimeoutTest: () -> Unit = {},
    restoreChange: (String) -> Unit = {},
    openFeedback: () -> Unit = {},
    openAccount: () -> Unit = {}
) = Page("Settings") {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = androidx.compose.runtime.remember(context) { context.packageManager.getPackageInfo(context.packageName, 0) }
    Text("DOOM ${version.versionName} (${version.longVersionCode})")
    OutlinedButton(onClick = openFeedback) { Text("Beta feedback") }
    OutlinedButton(onClick = openAccount) { Text("Account") }
    var confirmTimeout by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var confirmRestore by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { refreshRecovery() }
    PreferenceSwitch("Show preview when opening a mod", preferences.previewOnOpen, busy, setPreview)
    PreferenceSwitch("Safe mode", preferences.safeMode, busy, setSafeMode)
    Text("Safe mode blocks enable requests and keeps your per-mod choices.")
    PreferenceSwitch("Diagnostic logging", preferences.diagnosticLogging, busy, setLogging)
    Text("Theme", style = MaterialTheme.typography.titleMedium)
    ThemePreference.entries.forEach { theme ->
        val label = when (theme) {
            ThemePreference.DARK -> "Dark"
            ThemePreference.LIGHT -> "Light"
            ThemePreference.SYSTEM -> "System"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = preferences.theme == theme, enabled = !busy, onClick = { setTheme(theme) },
                modifier = Modifier.semantics { contentDescription = label })
            Text(label)
        }
    }
    OutlinedButton(onClick = disableAll, enabled = !busy) { Text("Disable all mods") }
    OutlinedButton(onClick = exportDiagnostics, enabled = !busy) { Text("Export diagnostics") }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text("Optional Shizuku adapter", style = MaterialTheme.typography.titleMedium)
    Text("${shizukuState.displayLabel} · ${shizukuState.privilegeLevel.name}",
        color = if (shizukuState.authorized && shizukuState.binderAlive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    Text(shizukuState.message ?: "Shizuku is optional. Standard DOOM mods work without it.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("ADB shell is not root. DOOM currently exposes no Shizuku operation to imported mods.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = refreshShizuku) { Text("Refresh") }
        OutlinedButton(onClick = openShizuku) { Text(if (shizukuState.managerInstalled) "Open Shizuku" else "Setup instructions") }
    }
    if (shizukuState.binderAlive && !shizukuState.authorized) {
        Button(onClick = requestShizukuAuthorization) { Text("Request authorization") }
    }
    if (shizukuState.authorized && shizukuState.phase in setOf(ShizukuConnectionPhase.CONNECTED_ADB, ShizukuConnectionPhase.CONNECTED_ROOT)) {
        OutlinedButton(onClick = testShizukuDiagnostic) { Text("Run read-only connection test") }
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text("System change recovery", style = MaterialTheme.typography.titleMedium)
    Text("Screen timeout writes use Android's explicit Modify system settings access. This operation is app initiated and is not exposed to mod packages or run through Shizuku.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Current screen timeout: ${screenTimeoutMillis?.let { "${it / 1000} seconds" } ?: "Unavailable"}")
    if (!canWriteSystemSettings) {
        OutlinedButton(onClick = openWriteSettings) { Text("Review Modify system settings access") }
        Text("Android Settings must grant this access. Opening that page does not grant it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        OutlinedButton(onClick = { confirmTimeout = true }) { Text("Try a 2-minute screen timeout") }
    }
    Text("Applied and unresolved changes", style = MaterialTheme.typography.titleSmall)
    if (changeHistory.isEmpty()) Text("No recorded DOOM system changes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    changeHistory.forEach { transaction ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("${transaction.operationId} · ${transaction.status}", style = MaterialTheme.typography.bodyMedium)
            Text("${transaction.originalState} ms → ${transaction.appliedState ?: transaction.requestedState} ms")
            Text("Owner: ${transaction.modId} · ${DateFormat.getDateTimeInstance().format(Date(transaction.updatedAt))}",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            transaction.failureReason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (transaction.status == ChangeTransactionStatus.APPLIED && canWriteSystemSettings) {
                OutlinedButton(onClick = { confirmRestore = transaction.transactionId }) { Text("Restore original") }
            }
        }
    }
    Text("Restoration is verified against the value DOOM applied. If you or another app changed it, DOOM reports a conflict and leaves the current value untouched.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (confirmTimeout) AlertDialog(
        onDismissRequest = { confirmTimeout = false },
        title = { Text("Change screen timeout?") },
        text = { Text("DOOM's temporary screen-timeout check can make the display stay awake longer while you inspect the recovery flow. Current: ${screenTimeoutMillis?.let { "${it / 1000} seconds" } ?: "unavailable"}. Proposed: 120 seconds. DOOM records the original value first. You can restore it later if nobody changed the setting outside DOOM.") },
        confirmButton = { TextButton(onClick = { confirmTimeout = false; applyTimeoutTest() }) { Text("Apply 2 minutes") } },
        dismissButton = { TextButton(onClick = { confirmTimeout = false }) { Text("Cancel") } }
    )
    confirmRestore?.let { transactionId -> AlertDialog(
        onDismissRequest = { confirmRestore = null },
        title = { Text("Restore original timeout?") },
        text = { Text("DOOM restores the saved value only if the current value still matches the value DOOM applied.") },
        confirmButton = { TextButton(onClick = { confirmRestore = null; restoreChange(transactionId) }) { Text("Restore") } },
        dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } }
    ) }
    Text("DOOM · Phase 1")
}

@Composable
fun DeviceDiagnosticsScreen(
    snapshot: DeviceCapabilitySnapshot?,
    refresh: () -> Unit,
    openOverlaySettings: () -> Unit,
    requestNotifications: () -> Unit,
    openAppSettings: () -> Unit,
    installedMods: List<InstalledMod> = emptyList(),
    runtimeStates: Map<String, ModRuntimeState> = emptyMap(),
    diagnosticEvents: List<ExecutionDiagnostic> = emptyList(),
    safeMode: Boolean = false,
    retryMod: (String) -> Unit = {},
    disableMod: (String) -> Unit = {},
    clearDiagnostics: () -> Unit = {},
    runningWorkflows: List<WorkflowMonitorEntry> = emptyList(),
    changeHistory: List<ChangeTransaction> = emptyList(),
    openSettings: () -> Unit = {}
) = Page("Device diagnostics") {
    Text("A live scan of public Android APIs and DOOM's trusted runtime. Manufacturer and model are informational only.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = refresh) { Text("Refresh scan") }
    if (snapshot == null) {
        CircularProgressIndicator()
        Text("Scanning device capabilities…")
        return@Page
    }
    snapshot.scanError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Text("Device", style = MaterialTheme.typography.titleMedium)
    Text("${snapshot.manufacturer ?: "Unknown manufacturer"} ${snapshot.model ?: "Unknown model"}")
    Text("Android ${snapshot.androidRelease ?: "unknown"} · API ${snapshot.androidApi ?: "unknown"}")
    snapshot.displayMetadata?.let { display ->
        Text("Default display: ${display.widthPixels} × ${display.heightPixels} px · ${display.densityDpi} dpi · rotation ${display.rotation}")
    }
    Text("Last scan: ${DateFormat.getDateTimeInstance().format(Date(snapshot.scannedAtMillis))}")
    Text("Android access", style = MaterialTheme.typography.titleMedium)
    DiagnosticStatus("Display over other apps", snapshot.overlayAuthorization,
        if (snapshot.overlayAuthorization == CapabilityAvailability.DENIED) openOverlaySettings else null,
        "Open overlay settings")
    DiagnosticStatus("Notifications", snapshot.notificationAuthorization,
        if (snapshot.notificationAuthorization == CapabilityAvailability.DENIED) requestNotifications else null,
        "Request notification permission")
    DiagnosticStatus("Vibration hardware", snapshot.vibrationHardware)
    DiagnosticStatus("User-started foreground service", snapshot.foregroundServiceEligibility,
        message = if (snapshot.foregroundServiceEligibility == CapabilityAvailability.UNKNOWN)
            "Android restricts background service starts. DOOM does not promise background starts." else null)
    Text("Supported DOOM capabilities", style = MaterialTheme.typography.titleMedium)
    snapshot.capabilities.forEach { capability ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(capability.displayName, style = MaterialTheme.typography.titleSmall)
            Text("${capability.id} · ${capability.availability}")
            Text("Risk ${capability.risk} · permission ${capability.requiredAndroidPermissions.ifEmpty { listOf("none") }.joinToString()}")
            Text("Privilege tier: ${capability.privilegeTier} · DOOM approval: ${if (capability.requiresDoomApproval) "required" else "not required"}")
            Text(capability.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            capability.reason?.let { Text(it, color = if (capability.availability == CapabilityAvailability.DENIED)
                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("DOOM executor: ${if (capability.executorAvailable) "available" else "unavailable"}" +
                (capability.requiredAndroidAccess?.let { " · Android access: $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall)
        }
    }
    if (snapshot.adapterStatuses.isNotEmpty()) {
        Text("Optional adapters", style = MaterialTheme.typography.titleMedium)
        snapshot.adapterStatuses.forEach { adapter ->
            Text("${adapter.displayName}: ${adapter.availability} (${adapter.connectionStatus}) · ${adapter.privilegeLevel}")
            if (adapter.supportedOperations.isNotEmpty()) Text("Read-only operations: ${adapter.supportedOperations.joinToString()}")
            adapter.failureReason?.let { Text(it) }
        }
    }
    Text("Android access is separate from the approval you grant to each mod. Opening Settings does not grant access; return to DOOM after making a choice.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = openAppSettings) { Text("Open DOOM app settings") }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    SafetyCenter(safeMode, installedMods, runtimeStates, diagnosticEvents,
        retryMod, disableMod, clearDiagnostics, runningWorkflows, changeHistory, openSettings)
}

@Composable
private fun SafetyCenter(
    safeMode: Boolean,
    mods: List<InstalledMod>,
    runtimeStates: Map<String, ModRuntimeState>,
    events: List<ExecutionDiagnostic>,
    retryMod: (String) -> Unit,
    disableMod: (String) -> Unit,
    clearDiagnostics: () -> Unit,
    runningWorkflows: List<WorkflowMonitorEntry>,
    changeHistory: List<ChangeTransaction>,
    openSettings: () -> Unit
) {
    var severity by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<DiagnosticSeverity?>(null) }
    var category by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<DiagnosticFailureCategory?>(null) }
    var modFilter by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var operationFilter by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var timeRangeMillis by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    Text("Safety Center", style = MaterialTheme.typography.titleLarge)
    Text("Global safe mode: ${if (safeMode) "ON" else "OFF"}", style = MaterialTheme.typography.titleMedium)
    OutlinedButton(onClick = openSettings) { Text("Manage safety settings") }
    Text(if (safeMode) "Third-party runtime is suspended. Installed mods keep their desired enabled choices. Exit safe mode deliberately to recheck authorization and resume eligible mods."
        else "Third-party mods can run only while eligible in the foreground runtime.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (mods.isEmpty()) Text("No installed mods.")
    mods.forEach { mod ->
        val phase = runtimeStates[mod.id]?.phase
        val suspended = safeMode || mod.status.name == "ERROR" || phase == RuntimePhase.ERROR
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Text(mod.title, style = MaterialTheme.typography.titleSmall)
            Text("${if (phase == RuntimePhase.ACTIVE && !safeMode) "Active in this session" else if (suspended) "Suspended" else mod.status.name} · desired ${if (mod.desiredEnabled) "enabled" else "disabled"}")
            (mod.lastError ?: runtimeStates[mod.id]?.message)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mod.status.name == "ERROR") OutlinedButton(onClick = { retryMod(mod.id) }) { Text("Retry") }
                if (mod.desiredEnabled) OutlinedButton(onClick = { disableMod(mod.id) }) { Text("Disable") }
            }
        }
    }
    Text("Running workflows (${runningWorkflows.size})", style = MaterialTheme.typography.titleMedium)
    runningWorkflows.forEach { workflow ->
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("${workflow.modId} · ${workflow.workflowId}")
            if (workflow.runningActionIds.isNotEmpty()) Text("Running: ${workflow.runningActionIds.joinToString()}")
            if (workflow.pendingActionIds.isNotEmpty()) Text("Pending: ${workflow.pendingActionIds.joinToString()}")
        }
    }
    val unresolved = changeHistory.filter { it.status in setOf(ChangeTransactionStatus.PREPARED,
        ChangeTransactionStatus.RESTORING, ChangeTransactionStatus.UNCERTAIN, ChangeTransactionStatus.CONFLICT) }
    Text("Privileged recovery records (${unresolved.size})", style = MaterialTheme.typography.titleMedium)
    unresolved.forEach { change ->
        Text("${change.operationId} · ${change.status} · ${change.modId}", color = MaterialTheme.colorScheme.error)
        change.failureReason?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (unresolved.isNotEmpty()) OutlinedButton(onClick = openSettings) { Text("Open recovery controls") }
    Text("Execution diagnostics · newest first", style = MaterialTheme.typography.titleMedium)
    androidx.compose.material3.OutlinedTextField(value = modFilter, onValueChange = { modFilter = it.take(80) },
        label = { Text("Filter by mod ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    androidx.compose.material3.OutlinedTextField(value = operationFilter, onValueChange = { operationFilter = it.take(100) },
        label = { Text("Filter by workflow or operation") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (listOf<DiagnosticSeverity?>(null) + DiagnosticSeverity.entries).forEach { value ->
            FilterChip(selected = severity == value, onClick = { severity = value },
                label = { Text(value?.name ?: "All severity") })
        }
    }
    Column {
        (listOf<DiagnosticFailureCategory?>(null) + DiagnosticFailureCategory.entries).chunked(3).forEach { values ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                values.forEach { value -> FilterChip(selected = category == value, onClick = { category = value },
                    label = { Text(value?.name ?: "All categories") }) }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("All time" to null, "1 hour" to 3_600_000L, "24 hours" to 86_400_000L).forEach { (label, range) ->
            FilterChip(selected = timeRangeMillis == range, onClick = { timeRangeMillis = range }, label = { Text(label) })
        }
    }
    val cutoff = timeRangeMillis?.let { System.currentTimeMillis() - it }
    val filtered = events.filter { event ->
        (severity == null || event.severity == severity) &&
            (category == null || event.failureCategory == category) &&
            (modFilter.isBlank() || event.modId.contains(modFilter, ignoreCase = true)) &&
            (operationFilter.isBlank() || (event.workflowId.orEmpty() + " " + event.triggerOrOperationId.orEmpty()).contains(operationFilter, true)) &&
            (cutoff == null || event.timestamp >= cutoff)
    }
    if (filtered.isEmpty()) Text("No diagnostics match these filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    filtered.take(80).forEach { event ->
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Text("${event.severity} · ${event.result} · ${event.failureCategory}", style = MaterialTheme.typography.labelLarge)
            Text("${event.modId} · ${event.workflowId ?: event.triggerOrOperationId ?: "runtime"}")
            Text(event.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(DateFormat.getDateTimeInstance().format(Date(event.timestamp)), style = MaterialTheme.typography.bodySmall)
        }
    }
    OutlinedButton(onClick = clearDiagnostics, enabled = events.isNotEmpty()) { Text("Delete local diagnostics") }
    Text("Runtime and workflow counts are shown only when DOOM can observe them; CPU and battery consumption are not estimated.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DiagnosticStatus(
    label: String,
    status: CapabilityAvailability,
    remediation: (() -> Unit)? = null,
    remediationLabel: String? = null,
    message: String? = null
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Text(status.name, color = when (status) {
            CapabilityAvailability.AVAILABLE -> MaterialTheme.colorScheme.primary
            CapabilityAvailability.DENIED, CapabilityAvailability.UNAVAILABLE, CapabilityAvailability.UNSUPPORTED -> MaterialTheme.colorScheme.error
            CapabilityAvailability.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
        })
    }
    message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (remediation != null) OutlinedButton(onClick = remediation) { Text(remediationLabel ?: "Open settings") }
}

@Composable
private fun PreferenceSwitch(title: String, enabled: Boolean, busy: Boolean, change: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked = enabled, enabled = !busy, onCheckedChange = change,
            modifier = Modifier.semantics { contentDescription = title })
    }
}
