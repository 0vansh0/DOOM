package org.doom.model

data class InstalledMod(
    val id: String,
    val title: String,
    val version: String,
    val enabled: Boolean,
    val description: String,
    val appearance: GlowAppearance,
    val overlayGranted: Boolean,
    val minAndroidApi: Int,
    val status: ModStatus = ModStatus.INSTALLED_DISABLED,
    val lastError: String? = null,
    val capabilities: List<String> = listOf("overlay"),
    val approvedCapabilities: Set<String> = if (overlayGranted) setOf("overlay") else emptySet(),
    val intensityRange: IntRange = GlowAppearance.intensities,
    val author: String? = null,
    val license: String? = null,
    val runtimeAvailable: Boolean = false,
    val optionDefinitions: List<IntegerSetting> = emptyList(),
    val settingDefinitions: Map<String, ModSettingDefinition> = emptyMap(),
    val settingValues: Map<String, ModSettingValue> = emptyMap()
) {
    val desiredEnabled: Boolean get() = enabled
}

enum class ModStatus {
    INSTALLED_DISABLED, ENABLE_REQUESTED, CHECKING_REQUIREMENTS,
    READY_TO_ACTIVATE, PERMISSION_REQUIRED, INCOMPATIBLE, ERROR, UNINSTALLING
}

enum class ThemePreference { DARK, LIGHT, SYSTEM }

data class GlobalPreferences(
    val safeMode: Boolean = false,
    val theme: ThemePreference = ThemePreference.DARK,
    val diagnosticLogging: Boolean = false,
    val previewOnOpen: Boolean = true
)
