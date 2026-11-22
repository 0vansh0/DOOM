package org.doom.model

enum class RuntimePhase { ACTIVATING, ACTIVE, DEACTIVATING, CLEANUP, DISABLED, ERROR }
enum class EffectTemplate(val wireName: String) { BATTERY_RING("battery-ring"), FLOATING_CARD("floating-card") }
enum class EffectSurface { OVERLAY, PREVIEW }

data class RuntimePresentation(val surface: EffectSurface, val template: EffectTemplate, val appearance: GlowAppearance,
    val batteryPercent: Int? = null, val chargingBound: Boolean = false, val contentText: String? = null)

data class ModRuntimeState(
    val phase: RuntimePhase,
    val presentation: RuntimePresentation? = null,
    val manualAvailable: Boolean = false,
    val message: String? = null
)
