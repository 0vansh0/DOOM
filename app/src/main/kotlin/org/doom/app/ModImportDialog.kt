package org.doom.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.doom.model.capabilityDescription

@Composable
internal fun ModImportDialog(state: ImportState, install: () -> Unit, dismiss: () -> Unit, openInstalled: () -> Unit) {
    val dismissButton: @Composable () -> Unit = { TextButton(onClick = dismiss) { Text("Cancel") } }
    when (state) {
        ImportState.Idle -> Unit
        ImportState.Validating -> AlertDialog(
            onDismissRequest = dismiss, title = { Text("Validating package") },
            text = { CircularProgressIndicator() }, confirmButton = dismissButton
        )
        ImportState.Closing, ImportState.Installing -> AlertDialog(
            onDismissRequest = {}, title = { Text(if (state == ImportState.Installing) "Installing mod" else "Closing review") },
            text = { CircularProgressIndicator() }, confirmButton = {}
        )
        is ImportState.Review -> {
            val candidate = state.candidate
            val mod = candidate.manifest
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(mod.name) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${mod.id} · ${mod.version}")
                        Text(mod.description)
                        Text("Author (unverified): ${mod.author}")
                        Text("License: ${mod.license}")
                        Text(if (candidate.compatible) "Compatible with this Android version." else "Requires Android API ${mod.minAndroidApi}.")
                        Text("Unverified local package. Review the requested access before installing.",
                            color = MaterialTheme.colorScheme.error)
                        Text("Requested capabilities", style = MaterialTheme.typography.titleSmall)
                        mod.capabilities.forEach { Text("$it · ${capabilityDescription(it)}") }
                        Text("Installation leaves the mod disabled and its capabilities unapproved.")
                    }
                },
                confirmButton = { Button(onClick = install, enabled = candidate.compatible) { Text("Install mod") } },
                dismissButton = dismissButton
            )
        }
        is ImportState.Installed -> AlertDialog(
            onDismissRequest = dismiss, title = { Text("Mod installed") },
            text = { Text("${state.name} is installed and disabled.") },
            confirmButton = { TextButton(onClick = { dismiss(); openInstalled() }) { Text("View Installed") } },
            dismissButton = { TextButton(onClick = dismiss) { Text("Done") } }
        )
        is ImportState.Failed -> AlertDialog(
            onDismissRequest = dismiss, title = { Text("Import failed") }, text = { Text(state.message) },
            confirmButton = { TextButton(onClick = dismiss) { Text("Close") } }
        )
    }
}
