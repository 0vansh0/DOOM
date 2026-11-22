package org.doom.engine.runtime

import kotlinx.coroutines.flow.Flow
import org.doom.model.GlowAppearance
import org.doom.model.InstalledMod
import org.doom.model.ModRuntimeState
import org.doom.model.RuntimePresentation
import org.doom.model.ModSettingValue
import org.doom.model.DisplayMetadata
import org.doom.model.DiagnosticFailureCategory
import org.doom.model.DiagnosticResult
import org.doom.model.DiagnosticSeverity
import java.time.Clock
import java.time.Instant

interface ModRuntime {
    fun activate(mod: InstalledMod, definition: ValidatedPackage): Boolean
    fun deactivate(modId: String)
    fun dispatch(event: ModEvent, targetModId: String? = null)
    fun observeRuntimeState(modId: String): Flow<ModRuntimeState>
}

interface ModEffectExecutor {
    val supportedActions: Set<ModEffect>
    fun pulse(context: ModExecutionContext): AutoCloseable? = error("Haptic adapter unavailable.")
    fun updateBattery(modId: String, percent: Int?) {}
    fun readDisplay(context: ModExecutionContext): DisplayMetadata = error("Display metadata reader unavailable.")
    fun show(context: ModExecutionContext, presentation: RuntimePresentation, slot: Int): AutoCloseable
}

class ModExecutionContext internal constructor(
    val modId: String,
    val sessionId: String,
    val grantedCapabilities: Set<String>,
    val settings: GlowAppearance,
    private val clock: Clock,
    val settingValues: Map<String, ModSettingValue> = emptyMap(),
    val effectOwnerId: String = sessionId
) {
    internal fun forEffect(ownerId: String) = ModExecutionContext(modId, sessionId, grantedCapabilities, settings, clock, settingValues, ownerId)
    internal fun withSettings(appearance: GlowAppearance, values: Map<String, ModSettingValue>) =
        ModExecutionContext(modId, sessionId, grantedCapabilities, appearance, clock, values, effectOwnerId)
    fun localTime(): Instant {
        check("clock.read" in grantedCapabilities)
        return clock.instant()
    }
}

data class RuntimeDiagnostic(
    val modId: String,
    val event: String,
    val result: String,
    val message: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val runtimeSessionId: String? = null,
    val workflowId: String? = null,
    val triggerOrOperationId: String? = event.take(120),
    val severity: DiagnosticSeverity = when (result) {
        "ERROR", "FAILED" -> DiagnosticSeverity.ERROR
        "DENIED", "BLOCKED", "RATE_LIMITED" -> DiagnosticSeverity.WARNING
        else -> DiagnosticSeverity.INFO
    },
    val diagnosticResult: DiagnosticResult = when (result) {
        "SUCCESS", "SUBMITTED", "SUCCEEDED" -> DiagnosticResult.SUCCESS
        "DENIED", "BLOCKED" -> DiagnosticResult.BLOCKED
        "ERROR", "FAILED", "COMPLETED_WITH_FAILURES" -> DiagnosticResult.FAILED
        "CANCELLED" -> DiagnosticResult.CANCELLED
        "SKIPPED", "RATE_LIMITED" -> DiagnosticResult.SKIPPED
        else -> DiagnosticResult.UNKNOWN
    },
    val failureCategory: DiagnosticFailureCategory = when {
        result == "RATE_LIMITED" -> DiagnosticFailureCategory.RATE_LIMITED
        result == "DENIED" && message.orEmpty().contains("permission", ignoreCase = true) -> DiagnosticFailureCategory.PERMISSION_DENIED
        result == "DENIED" -> DiagnosticFailureCategory.CAPABILITY_DENIED
        event == "cleanup_failed" -> DiagnosticFailureCategory.CLEANUP_FAILURE
        result == "ERROR" || result == "FAILED" -> DiagnosticFailureCategory.RUNTIME_FAILURE
        result == "CANCELLED" -> DiagnosticFailureCategory.USER_CANCELLED
        else -> DiagnosticFailureCategory.NONE
    },
    val durationMillis: Long? = null,
    val resourceOwner: String? = null
)
