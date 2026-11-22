package org.doom.model

data class PackageReview(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val license: String,
    val minAndroidApi: Int,
    val capabilities: List<String>,
    val appearance: GlowAppearance,
    val intensityRange: IntRange,
    val optionDefinitions: List<IntegerSetting> = emptyList(),
    val settingDefinitions: Map<String, ModSettingDefinition> = emptyMap()
)

fun capabilityDescription(id: String): String = when (id) {
    "battery.read" -> "Read battery and charging state"
    "overlay.render", "overlay" -> "Render an overlay over other apps"
    "clock.read" -> "Read the local clock"
    "device.power.read" -> "Read Android Battery Saver state while DOOM is foregrounded"
    "haptics.vibrate" -> "Submit a bounded 50 ms vibration pulse (Android may suppress it)"
    "ui.preview" -> "Show a preview inside DOOM"
    else -> id
}
