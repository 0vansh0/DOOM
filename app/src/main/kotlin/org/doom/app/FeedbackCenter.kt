package org.doom.app

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.doom.core.designsystem.Page
import org.doom.data.FeedbackReport
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
internal fun FeedbackCenter(
    state: FeedbackUiState,
    submit: (FeedbackReport, Boolean) -> Unit,
    openAccount: () -> Unit,
    back: () -> Unit
) = Page("Beta feedback") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resolver = context.contentResolver
    val categories = listOf("Bug", "Suggestion", "Mod compatibility", "Marketplace", "Unexpected behavior")
    var category by rememberSaveable { mutableStateOf(categories.first()) }
    var expanded by remember { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var steps by rememberSaveable { mutableStateOf("") }
    var expected by rememberSaveable { mutableStateOf("") }
    var actual by rememberSaveable { mutableStateOf("") }
    var modId by rememberSaveable { mutableStateOf("") }
    var screenshot by rememberSaveable { mutableStateOf<Uri?>(null) }
    var diagnostics by rememberSaveable { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    val android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    val report = FeedbackReport(category, title, description, steps, expected, actual,
        version.ifBlank { "unknown" }, android, modId)
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) screenshot = it }
    val diagnosticPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) diagnostics = it }
    val archive = screenshot != null || diagnostics != null
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(if (archive) "application/zip" else "application/json")) { dest ->
        if (dest != null) scope.launch {
            val report = JSONObject().put("category", category).put("title", title.trim()).put("description", description.trim())
                .put("steps", steps).put("actual", actual).put("expected", expected).put("modId", modId)
                .put("appVersion", version).put("androidVersion", android).toString(2).toByteArray()
            try {
                withContext(Dispatchers.IO) {
                    fun read(uri: Uri, max: Int): ByteArray {
                        require(uri.scheme == "content")
                        return requireNotNull(resolver.openInputStream(uri)).use { input ->
                            val buffer = ByteArray(8192)
                            val out = java.io.ByteArrayOutputStream()
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(out.size() + count <= max)
                                out.write(buffer, 0, count)
                            }
                            out.toByteArray()
                        }
                    }
                    val png = screenshot?.let {
                        read(it, 2 * 1024 * 1024).also { bytes ->
                            require(bytes.take(8) == listOf<Byte>(-119,80,78,71,13,10,26,10))
                        }
                    }
                    val summary = diagnostics?.let {
                        val json = JSONObject(read(it, 256 * 1024).decodeToString())
                        require(json.getInt("formatVersion") == 1)
                        JSONObject().put("executionEventCount", json.getJSONArray("executionDiagnostics").length())
                            .put("unresolvedRecoveryCount", json.getJSONArray("unresolvedRecovery").length()).toString().toByteArray()
                    }
                    requireNotNull(resolver.openOutputStream(dest, "wt")).use { out ->
                        if (png == null && summary == null) out.write(report)
                        else ZipOutputStream(out).use { zip ->
                            fun entry(name: String, bytes: ByteArray) {
                                zip.putNextEntry(ZipEntry(name))
                                zip.write(bytes)
                                zip.closeEntry()
                            }
                            entry("report.json", report)
                            png?.let { entry("screenshot.png", it) }
                            summary?.let { entry("diagnostic-summary.json", it) }
                        }
                    }
                }
                message = "Report saved to the chosen location."
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { message = "Could not save. Check attachment type, size and destination." }
        }
    }
    TextButton(onClick = back) { Text("Back") }
    OutlinedButton(onClick = { expanded = true }) { Text("Category · $category") }
    DropdownMenu(expanded, { expanded = false }) {
        categories.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { category = it; expanded = false }) }
    }
    FeedbackField("Short title", title, 120) { title = it }
    FeedbackField("Description", description, 2000) { description = it }
    FeedbackField("Reproduction steps", steps, 2000) { steps = it }
    FeedbackField("Expected result", expected, 2000) { expected = it }
    FeedbackField("Actual result", actual, 2000) { actual = it }
    FeedbackField("Mod ID (optional)", modId, 100) { modId = it }
    Text("DOOM $version · Android $android")
    Text("Review before sharing. Screenshots and diagnostic attachments stay local and are not uploaded.")
    Text("Submission preview", style = MaterialTheme.typography.titleMedium)
    Text("$category · ${title.ifBlank { "Untitled report" }}")
    Text(description.ifBlank { "Add a description to complete this report." })
    Text("Steps: ${steps.ifBlank { "none" }} · Expected: ${expected.ifBlank { "none" }} · Actual: ${actual.ifBlank { "none" }}")
    if (modId.isNotBlank()) Text("Mod reference: $modId")
    OutlinedButton(onClick = { if (screenshot == null) imagePicker.launch(arrayOf("image/png")) else screenshot = null }) {
        Text(if (screenshot == null) "Attach reviewed PNG (2 MiB)" else "Remove screenshot")
    }
    OutlinedButton(onClick = { if (diagnostics == null) diagnosticPicker.launch(arrayOf("application/json")) else diagnostics = null }) {
        Text(if (diagnostics == null) "Attach counts from DOOM diagnostics" else "Remove diagnostic counts")
    }
    Button(enabled = title.isNotBlank() && description.isNotBlank(), onClick = {
        export.launch(if (archive) "doom-feedback.zip" else "doom-feedback.json")
    }) { Text("Save local report") }
    if (message.isNotEmpty()) Text(message)
    if (state.session == null) {
        Text("Sign in to submit to DOOM. Local report export remains available.")
        OutlinedButton(onClick = openAccount) { Text("Sign in to DOOM") }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = consent, onCheckedChange = { consent = it })
            Text("I consent to sending the report fields shown above to DOOM. Attachments are excluded.")
        }
        Button(enabled = !state.submitting && title.isNotBlank() && description.isNotBlank(),
            onClick = { submit(report, consent) }) {
            if (state.submitting) CircularProgressIndicator()
            else Text("Submit to DOOM")
        }
    }
    when (state.status) {
        FeedbackUiStatus.SUBMITTING -> Text("Submitting feedback…")
        FeedbackUiStatus.SUBMITTED -> {
            Text(state.message ?: "DOOM confirmed receipt of your feedback.")
            state.reportId?.let { Text("Report ID: $it") }
        }
        FeedbackUiStatus.UNKNOWN_OUTCOME -> Text(
            state.message ?: "The server response was not confirmed. Do not resubmit yet; save a local copy and check the service first.",
            color = MaterialTheme.colorScheme.error)
        FeedbackUiStatus.EDITING -> Unit
        else -> state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun FeedbackField(label: String, value: String, limit: Int, change: (String) -> Unit) {
    OutlinedTextField(value, { if (it.length <= limit) change(it) }, label = { Text(label) })
}
