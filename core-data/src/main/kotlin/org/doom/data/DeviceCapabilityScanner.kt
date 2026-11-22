package org.doom.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.VibratorManager
import android.util.DisplayMetrics
import android.provider.Settings
import android.view.Display
import android.hardware.display.DisplayManager
import org.doom.engine.CapabilityRegistry
import org.doom.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Safe point-in-time probe. A device result never implies DOOM approval for an installed mod. */
class DeviceCapabilityScanner(
    private val context: Context,
    private val adapters: List<CapabilityAdapter> = emptyList(),
    private val clockMillis: () -> Long = System::currentTimeMillis
) {
    fun scan(appForeground: Boolean = true): DeviceCapabilitySnapshot {
        val errors = mutableListOf<String>()
        fun probe(label: String, block: () -> CapabilityAvailability): CapabilityAvailability = try {
            block()
        } catch (_: SecurityException) {
            errors += "$label could not be checked because Android denied access."
            CapabilityAvailability.DENIED
        } catch (_: RuntimeException) {
            errors += "$label could not be checked on this device."
            CapabilityAvailability.UNKNOWN
        }

        val overlay = probe("Overlay authorization") {
            if (Settings.canDrawOverlays(context)) CapabilityAvailability.AVAILABLE else CapabilityAvailability.DENIED
        }
        val notifications = CapabilityStatusPolicy.permission(Build.VERSION.SDK_INT, 33,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        val vibration = probe("Vibration hardware") {
            if (Build.VERSION.SDK_INT < 31) CapabilityAvailability.UNSUPPORTED
            else if (context.checkSelfPermission(Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED)
                CapabilityAvailability.DENIED
            else CapabilityStatusPolicy.hardware(
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.hasVibrator())
        }
        val battery = probe("Battery service") {
            if (context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) != null)
                CapabilityAvailability.AVAILABLE else CapabilityAvailability.UNAVAILABLE
        }
        val power = probe("Power service") {
            if (context.getSystemService(PowerManager::class.java) != null) CapabilityAvailability.AVAILABLE
            else CapabilityAvailability.UNAVAILABLE
        }
        var displayMetadata: DisplayMetadata? = null
        val display = probe("Display metadata") {
            val manager = context.getSystemService(DisplayManager::class.java)
            val defaultDisplay = manager?.getDisplay(Display.DEFAULT_DISPLAY)
            if (defaultDisplay == null) CapabilityAvailability.UNAVAILABLE else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                defaultDisplay.getRealMetrics(metrics)
                displayMetadata = DisplayMetadata(metrics.widthPixels.coerceAtLeast(0), metrics.heightPixels.coerceAtLeast(0),
                    metrics.densityDpi.coerceAtLeast(0), defaultDisplay.rotation)
                CapabilityAvailability.AVAILABLE
            }
        }
        val adaptersNow = adapters.map { adapter -> runCatching { adapter.status() }.getOrElse {
            AdapterStatus(adapter.adapterId, adapter.displayName, "ERROR", CapabilityAvailability.UNKNOWN,
                emptySet(), failureReason = "Adapter status could not be read.")
        } }
        val statuses = CapabilityRegistry.entries.map { entry ->
            val adapter = entry.requiredAdapter?.let { id -> adaptersNow.firstOrNull { it.adapterId == id } }
            val deviceStatus = when (entry.id) {
                "overlay.render", "overlay", "overlay.preset.apply" -> overlay
                "battery.read" -> battery
                "device.power.read" -> power
                "device.display.read" -> display
                "haptics.vibrate" -> vibration
                "clock.read", "ui.preview" -> CapabilityAvailability.AVAILABLE
                else -> adapter?.availability ?: CapabilityAvailability.UNSUPPORTED
            }
            val apiUnsupported = Build.VERSION.SDK_INT < entry.minimumAndroidApi
            val availability = when {
                CapabilityStatusPolicy.api(Build.VERSION.SDK_INT, entry.minimumAndroidApi) == CapabilityAvailability.UNSUPPORTED || !entry.implemented -> CapabilityAvailability.UNSUPPORTED
                else -> deviceStatus
            }
            CapabilityStatus(
                id = entry.id,
                displayName = entry.displayName,
                description = entry.description,
                availability = availability,
                requiredAndroidAccess = entry.requiredAndroidPermissions.takeIf { it.isNotEmpty() }?.joinToString(),
                requiredApi = entry.minimumAndroidApi,
                reason = when {
                    apiUnsupported -> "Requires Android API ${entry.minimumAndroidApi}."
                    availability == CapabilityAvailability.DENIED && entry.id == "overlay.render" -> "Allow display over other apps in Android Settings."
                    availability == CapabilityAvailability.UNAVAILABLE && entry.id == "haptics.vibrate" -> "This device reports no vibrator."
                    availability == CapabilityAvailability.UNKNOWN -> "Android could not reliably determine availability."
                    availability == CapabilityAvailability.DENIED && entry.requiredAndroidPermissions.isNotEmpty() -> "Requires Android authorization: ${entry.requiredAndroidPermissions.joinToString()}."
                    availability == CapabilityAvailability.UNSUPPORTED -> entry.unavailableReason ?: "No trusted executor is available."
                    else -> null
                },
                executorAvailable = entry.implemented && (entry.requiredAdapter == null || adapter?.availability == CapabilityAvailability.AVAILABLE),
                adapterId = entry.requiredAdapter,
                privilegeTier = entry.minimumPrivilegeLevel,
                requiresDoomApproval = entry.requiredDoomApproval,
                requiredDoomCapabilities = (if (entry.requiredDoomApproval) setOf(entry.id) else emptySet()) + entry.requiredCapabilities,
                risk = entry.risk,
                requiredAndroidPermissions = entry.requiredAndroidPermissions,
                requiredHardwareFeatures = entry.requiredHardwareFeatures,
                minimumPrivilegeLevel = entry.minimumPrivilegeLevel,
                resourceLimits = entry.resourceLimits,
                auditRequired = entry.auditRequired,
                unavailableReason = entry.unavailableReason
            )
        }
        return DeviceCapabilitySnapshot(
            scannedAtMillis = clockMillis(), androidApi = Build.VERSION.SDK_INT, androidRelease = Build.VERSION.RELEASE,
            manufacturer = Build.MANUFACTURER, model = Build.MODEL, appForeground = appForeground,
            capabilities = statuses,
            overlayAuthorization = overlay,
            notificationAuthorization = notifications,
            vibrationHardware = vibration,
            foregroundServiceEligibility = when {
                Build.VERSION.SDK_INT < 31 -> CapabilityAvailability.UNSUPPORTED
                appForeground -> CapabilityAvailability.AVAILABLE
                else -> CapabilityAvailability.UNKNOWN
            },
            adapterStatuses = adaptersNow,
            scanError = errors.takeIf { it.isNotEmpty() }?.joinToString(" "),
            displayMetadata = displayMetadata
        )
    }

    /** Legacy diagnostic/requirement API kept for callers already present in the app. */
    fun availability(id: String): String = scan().capabilities.firstOrNull { it.id == if (id == "overlay") "overlay.render" else id }
        ?.availability?.name ?: CapabilityAvailability.UNSUPPORTED.name

    fun snapshot(): JSONObject {
        val report = scan()
        return JSONObject().put("androidApi", report.androidApi).put("androidRelease", report.androidRelease)
            .put("manufacturer", report.manufacturer).put("model", report.model)
            .put("powerSaving", context.getSystemService(PowerManager::class.java)?.isPowerSaveMode)
            .put("capabilities", JSONArray().apply {
                report.capabilities.forEach { capability -> put(JSONObject()
                    .put("id", capability.id).put("displayName", capability.displayName)
                    .put("status", capability.availability.name).put("reason", capability.reason)
                    .put("doomApprovalRequired", CapabilityRegistry.entries.firstOrNull { it.id == capability.id }?.requiredDoomApproval == true)
                    .put("androidConsent", capability.requiredAndroidAccess ?: "none")
                    .put("privilegeTier", capability.privilegeTier)
                    .put("requiredDoomCapabilities", JSONArray(capability.requiredDoomCapabilities.toTypedArray()))
                    .put("executorAvailable", capability.executorAvailable)) }
            }).put("scanError", report.scanError)
    }
}
