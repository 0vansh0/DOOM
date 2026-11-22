package org.doom.feature.installed

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import org.doom.core.designsystem.Page
import org.doom.model.InstalledMod
import org.doom.model.ModRuntimeState
import org.doom.model.ModStatus
import org.doom.model.RuntimePhase

@Composable
fun InstalledScreen(mods: List<InstalledMod>, loading: Boolean, error: Boolean, runtime: Map<String, ModRuntimeState>, select: (String) -> Unit) = Page("Installed") {
    when {
        loading -> CircularProgressIndicator()
        error -> Text("Could not load installed mods.")
        mods.isEmpty() -> Text("No mods installed.")
        else -> mods.forEach { mod ->
            Column {
                OutlinedButton(onClick = { select(mod.id) }) { Text(mod.title) }
                val live = runtime[mod.id]?.phase == RuntimePhase.ACTIVE && mod.status == ModStatus.READY_TO_ACTIVATE
                Text("Version ${mod.version} · ${if (live) "ACTIVE" else mod.status.name}")
                Text(if (mod.desiredEnabled) "Requested enabled" else "Requested disabled")
                mod.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
