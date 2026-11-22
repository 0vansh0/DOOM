package org.doom.feature.mod.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import org.json.JSONObject
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.doom.core.designsystem.Page
import org.doom.model.GlowAppearance
import org.doom.model.RingStyle
import org.doom.model.RingPosition
import org.doom.model.InstalledMod
import org.doom.model.ModRuntimeState
import org.doom.model.ModStatus
import org.doom.model.RuntimePhase
import org.doom.model.EffectSurface
import org.doom.model.EffectTemplate
import org.doom.model.ModSettingDefinition
import org.doom.model.ModSettingType
import org.doom.model.ModSettingValue
import org.doom.model.ModSettingValidator
import org.doom.model.ModCompatibilityReport
import org.doom.model.CapabilityAvailability
import org.doom.model.capabilityDescription
import kotlin.math.roundToInt

@Composable
fun ModDetailsScreen(
    mod: InstalledMod?,
    busy: Boolean,
    externalMessage: String?,
    externalRunning: Boolean,
    testExternal: () -> Unit,
    startFloating: () -> Unit,
    stopFloating: () -> Unit,
    batteryPercent: Int?,
    resetSettings: () -> Unit,
    runtime: ModRuntimeState?,
    runTest: () -> Unit,
    dismissEffect: () -> Unit,
    permissionRequired: Boolean,
    compatibility: ModCompatibilityReport?,
    safeMode: Boolean,
    showPreviewInitially: Boolean,
    back: () -> Unit,
    openOverlaySettings: () -> Unit,
    onGrant: (String, Boolean) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onConfigure: (GlowAppearance) -> Unit,
    onSettingsChange: (Map<String, ModSettingValue>) -> Unit,
    uninstall: () -> Unit
) = Page(mod?.title ?: "Mod details") {
    TextButton(onClick = back) { Text("Back") }
    if (mod == null) {
        Text("This mod is unavailable.")
    } else {
        Text("Version ${mod.version} · ${mod.status.name}")
        Text(mod.description)
        mod.author?.let { Text("Author (unverified): $it") }
        mod.license?.let { Text("License: $it") }
        mod.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Compatibility and authorization", style = MaterialTheme.typography.titleMedium)
        if (compatibility == null) Text("Compatibility scan is unavailable; refresh Device diagnostics.")
        else {
            Text(if (compatibility.compatible) "Device compatible" else "Compatibility requirements are not met",
                color = if (compatibility.compatible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Text(if (compatibility.authorized) "Required capabilities are authorized" else "One or more requirements need authorization")
            compatibility.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            compatibility.capabilities.forEach { capability ->
                val tone = if (capability.availability == CapabilityAvailability.AVAILABLE && capability.doomApproved)
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                Text("${capability.capabilityId} · ${capability.availability} · DOOM approval ${if (capability.doomApproved) "granted" else "required"}", color = tone)
                Text("Risk ${capability.risk} · Android permission ${capability.requiredAndroidPermissions.ifEmpty { listOf("none") }.joinToString()}" +
                    (capability.requiredAdapter?.let { " · adapter $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                Text(capability.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (mod.status == ModStatus.READY_TO_ACTIVATE) {
            when {
                !mod.runtimeAvailable -> Text("Import a public-format package to run this mod.")
                runtime?.phase == RuntimePhase.ACTIVE -> Text("ACTIVE · Listening for declared events")
                runtime?.phase == RuntimePhase.ACTIVATING -> Text("Verifying installed package")
            }
        }
        runtime?.message?.let { Text(it) }
        if (runtime?.manualAvailable == true) {
            OutlinedButton(onClick = runTest, enabled = !busy) { Text("Run mod test") }
        }
        runtime?.presentation?.let { frame ->
            if (frame.surface == EffectSurface.PREVIEW) {
                val color = Color(android.graphics.Color.parseColor(frame.appearance.color))
                    .copy(alpha = frame.appearance.intensity / 100f)
                Text("Active in-app preview")
                if (frame.template == EffectTemplate.BATTERY_RING) {
                    RingPreview(frame.appearance, frame.batteryPercent)
                } else {
                    Box(Modifier.size(76.dp, 56.dp).background(color, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center) { Text("DOOM") }
                }
            } else Text("Overlay requested")
            TextButton(onClick = dismissEffect) { Text("Dismiss effect") }
        }
        if (permissionRequired && mod.capabilities.any { it == "overlay" || it == "overlay.render" }) {
            Text("Android overlay access is required to display the charging glow.")
            OutlinedButton(onClick = openOverlaySettings) { Text("Open Android overlay settings") }
        }
        mod.capabilities.forEach { id ->
            val label = if (id == "overlay") "Allow this mod to draw a glow" else capabilityDescription(id)
            Toggle(label, id in mod.approvedCapabilities, !busy) { onGrant(id, it) }
        }
        Toggle("Enable mod", mod.desiredEnabled, !busy && (!safeMode || mod.desiredEnabled), onEnabled)
        if ("overlay.render" in mod.capabilities && mod.runtimeAvailable) {
            Text("External overlay", style = MaterialTheme.typography.titleMedium)
            externalMessage?.let { Text(it) }
            if (externalRunning) {
                OutlinedButton(onClick = stopFloating) { Text("Stop External Overlay") }
            } else {
                val allowed = !busy && !safeMode && mod.desiredEnabled && mod.status == ModStatus.READY_TO_ACTIVATE
                OutlinedButton(onClick = testExternal, enabled = allowed) { Text("Test External Overlay") }
                OutlinedButton(onClick = startFloating, enabled = allowed) { Text("Start Persistent Floating Glow") }
                Text("Enable the mod and approve its capabilities and Android overlay access. External test lasts 12 seconds. Floating mode stays until stopped, with a notification.")
            }
        }
        AppearanceEditor(mod, busy, showPreviewInitially, batteryPercent, onConfigure)
        DeclarativeSettingsEditor(mod.settingDefinitions, mod.settingValues, !busy) { id, value ->
            onSettingsChange(mod.settingValues + (id to value))
        }
        TextButton(onClick = resetSettings, enabled = !busy) { Text("Reset mod settings") }

        var confirming by rememberSaveable(mod.id) { mutableStateOf(false) }
        TextButton(onClick = { confirming = true }, enabled = !busy) { Text("Uninstall mod") }
        if (confirming) {
            AlertDialog(
                onDismissRequest = { confirming = false },
                title = { Text("Uninstall ${mod.title}?") },
                text = { Text("This removes the package, settings, and grant.") },
                confirmButton = {
                    TextButton(onClick = { confirming = false; uninstall() }) { Text("Uninstall") }
                },
                dismissButton = {
                    TextButton(onClick = { confirming = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun AppearanceEditor(
    mod: InstalledMod,
    busy: Boolean,
    showPreviewInitially: Boolean,
    batteryPercent: Int?,
    save: (GlowAppearance) -> Unit
) {
    var color by rememberSaveable(mod.id, mod.appearance.color) { mutableStateOf(mod.appearance.color) }
    var intensity by rememberSaveable(mod.id, mod.appearance.intensity) { mutableIntStateOf(mod.appearance.intensity) }
    var preview by rememberSaveable(mod.id, showPreviewInitially) { mutableStateOf(showPreviewInitially) }
    var options by androidx.compose.runtime.remember(mod.id, mod.appearance.options) { mutableStateOf(mod.appearance.options) }
    val defaults = mod.appearance.ring
    var thickness by rememberSaveable(mod.id, defaults) { mutableIntStateOf(defaults?.thicknessDp ?: 4) }
    var diameter by rememberSaveable(mod.id, defaults) { mutableIntStateOf(defaults?.diameterDp ?: 48) }
    var duration by rememberSaveable(mod.id, defaults) { mutableIntStateOf(defaults?.durationSeconds ?: 20) }
    var position by rememberSaveable(mod.id, defaults) { mutableStateOf((defaults?.position ?: RingPosition.BOTTOM_END).name) }
    var previewPercent by rememberSaveable(mod.id) { mutableIntStateOf(72) }
    val ring = defaults?.let { RingStyle(thickness, diameter, duration, RingPosition.valueOf(position)) }
    val valid = GlowAppearance.colorPattern.matches(color)
    val context = LocalContext.current
    Text("Appearance", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(
        value = color,
        onValueChange = { if (it.length <= 7) color = it },
        label = { Text("Glow color") },
        supportingText = { Text("Six-digit hex color, such as #B48CFF") },
        isError = !valid,
        singleLine = true
    )
    Text("Glow intensity · $intensity%")
    if (mod.intensityRange.first < mod.intensityRange.last) {
        Slider(value = intensity.toFloat(), onValueChange = { intensity = it.roundToInt() },
            valueRange = mod.intensityRange.first.toFloat()..mod.intensityRange.last.toFloat())
    }
    if (ring != null) {
        RingSlider("Ring thickness", thickness, RingStyle.thicknessRange, "dp") { thickness = it }
        RingSlider("Ring diameter", diameter, RingStyle.diameterRange, "dp") { diameter = it }
        RingSlider("Effect duration", duration, RingStyle.durationRange, "s") { duration = it }
        TextButton(onClick = {
            val choices = RingPosition.entries
            position = choices[(ring.position.ordinal + 1) % choices.size].name
        }) { Text("Overlay position: " + position.lowercase().replace('_', ' ')) }
    }
    mod.optionDefinitions.forEach { setting ->
        val value = options[setting.id] ?: setting.default
        if (setting.minimum < setting.maximum) {
            RingSlider(setting.id, value, setting.minimum..setting.maximum, "") {
                options = options + (setting.id to it)
            }
        } else Text("${setting.id}: $value")
    }
    Toggle("In-app preview", preview, true) { preview = it }
    if (preview && valid && ring != null) {
        val displayLevel = batteryPercent ?: previewPercent
        if (batteryPercent == null) {
            Text("Illustrative battery level")
            RingSlider("Preview battery", previewPercent, 0..100, "%") { previewPercent = it }
        } else Text("Live battery")
        val alignment = when (ring.position) {
            RingPosition.BOTTOM_START -> Alignment.CenterStart
            RingPosition.BOTTOM_CENTER -> Alignment.Center
            RingPosition.BOTTOM_END -> Alignment.CenterEnd
        }
        Box(Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 14.dp), contentAlignment = alignment) {
            RingPreview(GlowAppearance(color, intensity, ring), displayLevel)
        }
        TextButton(onClick = {
            val json = JSONObject()
                .put("color", JSONObject().put("type", "color").put("default", color))
                .put("intensity", JSONObject().put("type", "integer").put("min", mod.intensityRange.first)
                    .put("max", mod.intensityRange.last).put("default", intensity))
                .put("ring", JSONObject().put("thicknessDp", thickness).put("diameterDp", diameter)
                    .put("durationSeconds", duration).put("position", position))
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("DOOM settings", json.toString(2)))
        }) { Text("Copy preview settings JSON") }
    } else if (preview && valid) {
        val glow = Color(android.graphics.Color.parseColor(color)).copy(alpha = intensity / 100f)
        Box(
            Modifier.fillMaxWidth().height(88.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.width(96.dp).height(5.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, glow, Color.Transparent))))
        }
    }
    Button(onClick = { save(GlowAppearance(color, intensity, ring, options)) }, enabled = valid && !busy) {
        Text("Save appearance")
    }
}

@Composable
private fun Toggle(title: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = change, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun RingSlider(label: String, value: Int, range: IntRange, unit: String, change: (Int) -> Unit) {
    Text("$label · $value $unit")
    Slider(value.toFloat(), { change(it.roundToInt()) }, valueRange = range.first.toFloat()..range.last.toFloat(),
        modifier = Modifier.semantics { contentDescription = label })
}

@Composable
private fun DeclarativeSettingsEditor(
    definitions: Map<String, ModSettingDefinition>,
    values: Map<String, ModSettingValue>,
    enabled: Boolean,
    update: (String, ModSettingValue) -> Unit
) {
    if (definitions.isEmpty()) return
    Text("Mod settings", style = MaterialTheme.typography.titleLarge)
    definitions.values.sortedBy { it.label.lowercase() }.forEach { definition ->
        val value = values[definition.id] ?: definition.defaultValue
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (definition.type) {
                ModSettingType.BOOLEAN -> Toggle(definition.label,
                    (value as? ModSettingValue.BooleanValue)?.value ?: (definition.defaultValue as ModSettingValue.BooleanValue).value,
                    enabled) { update(definition.id, ModSettingValue.BooleanValue(it)) }
                ModSettingType.INTEGER, ModSettingType.NUMBER -> NumericSlider(definition, value, enabled, update)
                ModSettingType.DURATION -> DurationControl(definition, value, enabled, update)
                ModSettingType.COLOR -> ColorControl(definition, value, enabled, update)
                ModSettingType.ENUM -> EnumControl(definition, value, enabled, update)
                ModSettingType.TEXT -> TextControl(definition, value, enabled, update)
            }
            definition.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun NumericSlider(
    definition: ModSettingDefinition,
    value: ModSettingValue,
    enabled: Boolean,
    update: (String, ModSettingValue) -> Unit
) {
    val current = when (value) {
        is ModSettingValue.IntegerValue -> value.value.toDouble()
        is ModSettingValue.NumberValue -> value.value
        else -> (definition.defaultValue as? ModSettingValue.NumberValue)?.value ?: 0.0
    }
    var slider by rememberSaveable(definition.id, current) { mutableFloatStateOf(current.toFloat()) }
    val min = requireNotNull(definition.minimum).toFloat()
    val max = requireNotNull(definition.maximum).toFloat()
    val step = requireNotNull(definition.step)
    val divisions = ((max.toDouble() - min) / step).let { if (it in 1.0..1001.0) it.toInt() - 1 else 0 }
    Text("${definition.label} · ${formatSettingNumber(slider.toDouble())}${definition.unit?.let { " $it" } ?: ""}")
    Slider(value = slider, onValueChange = { slider = it }, valueRange = min..max,
        steps = divisions.coerceAtLeast(0), enabled = enabled,
        onValueChangeFinished = {
            val snapped = snapToSettingStep(slider.toDouble(), min.toDouble(), step)
            slider = snapped.toFloat()
            update(definition.id, if (definition.type == ModSettingType.INTEGER)
                ModSettingValue.IntegerValue(snapped.toLong()) else ModSettingValue.NumberValue(snapped))
        }, modifier = Modifier.semantics { contentDescription = definition.label })
}

@Composable
private fun DurationControl(
    definition: ModSettingDefinition,
    value: ModSettingValue,
    enabled: Boolean,
    update: (String, ModSettingValue) -> Unit
) {
    val current = (value as? ModSettingValue.DurationValue) ?: (definition.defaultValue as ModSettingValue.DurationValue)
    var text by rememberSaveable(definition.id, current.value) { mutableStateOf(formatSettingNumber(current.value)) }
    val number = text.toDoubleOrNull()
    val candidate = number?.let { ModSettingValue.DurationValue(it, requireNotNull(definition.unit)) }
    val valid = candidate != null && runCatching { ModSettingValidator.validateValue(definition, candidate) }.isSuccess
    OutlinedTextField(value = text, onValueChange = { text = it.take(24) }, enabled = enabled,
        label = { Text("${definition.label} (${definition.unit})") }, singleLine = true,
        isError = !valid,
        supportingText = { Text("${definition.minimum}–${definition.maximum} ${definition.unit}; step ${definition.step}") },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = definition.label })
    TextButton(onClick = { if (valid) update(definition.id, candidate!!) }, enabled = enabled && valid) { Text("Save ${definition.label}") }
}

@Composable
private fun ColorControl(
    definition: ModSettingDefinition,
    value: ModSettingValue,
    enabled: Boolean,
    update: (String, ModSettingValue) -> Unit
) {
    val current = (value as? ModSettingValue.ColorValue)?.value ?: (definition.defaultValue as ModSettingValue.ColorValue).value
    var open by rememberSaveable(definition.id) { mutableStateOf(false) }
    var custom by rememberSaveable(definition.id, current) { mutableStateOf(current) }
    Text(definition.label)
    OutlinedButton(onClick = { custom = current; open = true }, enabled = enabled,
        modifier = Modifier.semantics { contentDescription = "Choose ${definition.label}, current value $current" }) {
        Box(Modifier.size(24.dp).background(Color(android.graphics.Color.parseColor(current)), RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(10.dp))
        Text(current)
    }
    if (open) {
        AlertDialog(onDismissRequest = { open = false }, title = { Text("${definition.label} color") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("#B48CFF", "#A78BFA", "#56D6C9", "#FFB86B", "#FF718B").forEach { swatch ->
                            IconButton(onClick = { custom = swatch }, modifier = Modifier.size(48.dp)) {
                                Box(Modifier.fillMaxSize().background(Color(android.graphics.Color.parseColor(swatch)), RoundedCornerShape(24.dp)))
                            }
                        }
                    }
                    OutlinedTextField(value = custom, onValueChange = { custom = it.take(7) },
                        label = { Text("Hex color") }, isError = !GlowAppearance.colorPattern.matches(custom), singleLine = true)
                }
            }, confirmButton = {
                TextButton(onClick = { update(definition.id, ModSettingValue.ColorValue(custom.uppercase())); open = false },
                    enabled = GlowAppearance.colorPattern.matches(custom)) { Text("Apply") }
            }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } })
    }
}

@Composable
private fun EnumControl(
    definition: ModSettingDefinition,
    value: ModSettingValue,
    enabled: Boolean,
    update: (String, ModSettingValue) -> Unit
) {
    val current = (value as? ModSettingValue.EnumValue)?.value ?: (definition.defaultValue as ModSettingValue.EnumValue).value
    var expanded by rememberSaveable(definition.id) { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "${definition.label}: $current" }) { Text("${definition.label}: $current") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            definition.choices.forEach { choice -> DropdownMenuItem(text = { Text(choice) }, onClick = {
                update(definition.id, ModSettingValue.EnumValue(choice)); expanded = false
            }) }
        }
    }
}

@Composable
private fun TextControl(
    definition: ModSettingDefinition,
    value: ModSettingValue,
    enabled: Boolean,
    update: (String, ModSettingValue) -> Unit
) {
    val current = (value as? ModSettingValue.TextValue)?.value ?: (definition.defaultValue as ModSettingValue.TextValue).value
    var text by rememberSaveable(definition.id, current) { mutableStateOf(current) }
    val maxLength = requireNotNull(definition.maxLength)
    val valid = text.length <= maxLength && text.none(Char::isISOControl)
    OutlinedTextField(value = text, onValueChange = { text = it.take(maxLength) }, enabled = enabled,
        label = { Text(definition.label) }, singleLine = true, isError = !valid,
        supportingText = { Text("${text.length}/$maxLength characters") },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = definition.label })
    TextButton(onClick = { update(definition.id, ModSettingValue.TextValue(text)) }, enabled = enabled && valid) {
        Text("Save ${definition.label}")
    }
}

private fun snapToSettingStep(value: Double, minimum: Double, step: Double): Double =
    minimum + kotlin.math.round((value - minimum) / step) * step

private fun formatSettingNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.3f".format(value).trimEnd('0').trimEnd('.')
