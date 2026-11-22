package org.doom.data

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ExternalOverlayState(
    val modId: String? = null,
    val persistent: Boolean = false,
    val visible: Boolean = false,
    val message: String? = null
)

@Singleton
class ExternalOverlayController @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutableState = MutableStateFlow(ExternalOverlayState())
    val state = mutableState.asStateFlow()

    fun start(activity: Activity, modId: String, persistent: Boolean) {
        if (!activity.hasWindowFocus() || activity.isFinishing) {
            message("Open DOOM before starting an external overlay.")
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            message("Allow DOOM to display over other apps in Android settings.")
            return
        }
        if (!context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
            message("Allow DOOM notifications before starting an external overlay so its Stop control is available.")
            return
        }
        if (state.value.modId != null) {
            message("Stop the current external overlay before starting another.")
            return
        }
        mutableState.value = ExternalOverlayState(modId, persistent, message = "Verifying package and capabilitiesâ€¦")
        try {
            context.startForegroundService(Intent(context, ExternalOverlayService::class.java)
                .putExtra("modId", modId).putExtra("persistent", persistent))
        } catch (error: RuntimeException) {
            mutableState.value = ExternalOverlayState(message = "Android could not start the overlay service. Keep DOOM open and retry.")
            android.util.Log.w("DoomExternal", "Foreground service start denied", error)
        }
    }

    fun stop() {
        context.stopService(Intent(context, ExternalOverlayService::class.java))
        mutableState.value = state.value.copy(message = "Stopping external overlay…")
    }

    internal fun charging(id: String, durationMillis: Long) {
        if (state.value.modId != null || durationMillis !in 1..20_000 ||
            !Settings.canDrawOverlays(context) ||
            !context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) return
        mutableState.value = ExternalOverlayState(id, message = "Keeping the charging glow visible for its remaining duration.")
        try {
            context.startForegroundService(Intent(context, ExternalOverlayService::class.java)
                .putExtra("modId", id).putExtra("chargingDuration", durationMillis))
        } catch (error: RuntimeException) {
            mutableState.value = ExternalOverlayState(message = "Android could not continue the charging glow outside DOOM.")
            android.util.Log.w("DoomExternal", "Charging service start denied", error)
        }
    }

    internal fun shown(id: String, persistent: Boolean, charging: Boolean = false) {
        mutableState.value = ExternalOverlayState(id, persistent, true,
            if (persistent) "Floating glow is on. Stop it here or from its notification."
            else if (charging) "Charging glow is visible until its deadline or charger disconnection."
            else "External test is visible for 12 seconds. Press Home or open another app.")
    }

    internal fun finished(message: String) {
        mutableState.value = ExternalOverlayState(message = message)
    }

    private fun message(message: String) { mutableState.value = state.value.copy(message = message) }
}
