package org.doom.engine.runtime

import org.doom.model.EffectTemplate
import org.doom.model.PackageReview

enum class ModEvent(val wireName: String, val capability: String? = null) {
    CHARGING_STARTED("battery.charging.started", "battery.read"),
    CHARGING_STOPPED("battery.charging.stopped", "battery.read"),
    BATTERY_LEVEL_CHANGED("battery.level.changed", "battery.read"),
    MANUAL("mod.manual.trigger"),
    POWER_CHANGED("device.power.changed", "device.power.read")
}

enum class ModEffect(val wireName: String, val capability: String) {
    SHOW_OVERLAY("overlay.show", "overlay.render"),
    APPLY_OVERLAY_PRESET("overlay.preset.apply", "overlay.preset.apply"),
    READ_DISPLAY("device.display.read", "device.display.read"),
    DISMISS_OVERLAY("overlay.dismiss", "overlay.render"),
    SHOW_PREVIEW("ui.preview.show", "ui.preview"),
    HAPTIC_PULSE("haptics.pulse", "haptics.vibrate")
}

enum class Condition(val wireName: String, val capability: String? = null) {
    ALWAYS("always"), CHARGING("charging", "battery.read"), NOT_CHARGING("not-charging", "battery.read"),
    POWER_SAVING("power-saving", "device.power.read"), NOT_POWER_SAVING("not-power-saving", "device.power.read"),
    BATTERY_LOW("battery-low", "battery.read"), BATTERY_NOT_LOW("battery-not-low", "battery.read"), BATTERY_BELOW_SETTING("battery-below-setting", "battery.read");

    val readsBattery get() = capability == "battery.read"
    fun matches(charging: Boolean, powerSaving: Boolean = false, percent: Int? = null, threshold: Int? = null) = when (this) {
        ALWAYS -> true
        CHARGING -> charging
        NOT_CHARGING -> !charging
        POWER_SAVING -> powerSaving
        NOT_POWER_SAVING -> !powerSaving
        BATTERY_BELOW_SETTING -> percent != null && threshold != null && percent < threshold
        BATTERY_LOW -> percent != null && percent <= 20
        BATTERY_NOT_LOW -> percent != null && percent > 20
    }
}

data class ModAction(val effect: ModEffect, val template: EffectTemplate?)
data class Subscription(val condition: Condition, val action: ModAction, val setting: String? = null)
enum class V4ConditionOp { ALL, ANY, COMPARE }
enum class V4Comparison { BELOW, ABOVE, EQUALS, LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL, NOT_EQUALS }
enum class V4ValueType { NUMBER, BOOLEAN, STRING }
data class V4Condition(
    val op: V4ConditionOp,
    val source: String? = null,
    val comparison: V4Comparison? = null,
    val valueType: V4ValueType? = null,
    val value: String? = null,
    val children: List<V4Condition> = emptyList()
)
data class V4Action(val effect: String, val template: String?, val parameters: Map<String, String>, val id: String = "",
    val independent: Boolean = false)
data class V4Trigger(val id: String, val event: String, val condition: V4Condition, val actions: List<V4Action>,
    val workflow: V4WorkflowConfig? = null)
data class V4WorkflowConfig(val id: String, val executionMode: WorkflowExecutionMode,
    val failurePolicy: WorkflowFailurePolicy, val timeoutMillis: Long = 30_000, val maxConcurrency: Int = 3)
data class V4Definition(val triggers: List<V4Trigger>, val settings: Map<String, org.doom.model.ModSettingDefinition>)
data class ValidatedPackage(
    val review: PackageReview,
    val subscriptions: Map<ModEvent, Subscription>,
    val v4: V4Definition? = null
)
