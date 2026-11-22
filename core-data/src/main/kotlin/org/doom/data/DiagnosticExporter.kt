package org.doom.data

import android.content.Context
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import javax.inject.Inject

class DiagnosticExporter @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val repository: RoomModRepository,
    private val metrics: RuntimeMetrics,
    private val changes: RoomChangeTransactionStore
) {
    suspend fun export(uri: String) = withContext(Dispatchers.IO) {
        val destination = Uri.parse(uri)
        require(destination.scheme == "content")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        val snapshot = DeviceCapabilityScanner(context).scan()
        val capabilityStatuses = JSONArray().apply {
            snapshot.capabilities.forEach { capability ->
                put(JSONObject().put("id", capability.id).put("status", capability.availability.name)
                    .put("executorAvailable", capability.executorAvailable))
            }
        }
        val unresolved = JSONArray().apply {
            changes.unresolved().forEach { transaction ->
                put(JSONObject().put("transactionId", transaction.transactionId)
                    .put("modId", transaction.modId).put("operationId", transaction.operationId)
                    .put("status", transaction.status.name).put("updatedAt", transaction.updatedAt)
                    .put("failureReason", sanitizeDiagnosticMessage(transaction.failureReason).take(240)))
            }
        }
        val report = JSONObject()
            .put("formatVersion", 1)
            .put("doomVersion", version)
            .put("androidApi", Build.VERSION.SDK_INT)
            .put("capabilityStatuses", capabilityStatuses)
            .put("runtimeLatency", metrics.summary())
            .put("mods", repository.diagnostics())
            .put("executionDiagnostics", repository.diagnosticExport())
            .put("unresolvedRecovery", unresolved)
        requireNotNull(context.contentResolver.openOutputStream(destination, "wt")).use {
            it.write(report.toString(2).toByteArray(Charsets.UTF_8))
        }
    }
}
