package org.doom.model

enum class ModSettingType { BOOLEAN, INTEGER, NUMBER, COLOR, ENUM, TEXT, DURATION }

sealed interface ModSettingValue {
    data class BooleanValue(val value: Boolean) : ModSettingValue
    data class IntegerValue(val value: Long) : ModSettingValue
    data class NumberValue(val value: Double) : ModSettingValue
    data class ColorValue(val value: String) : ModSettingValue
    data class EnumValue(val value: String) : ModSettingValue
    data class TextValue(val value: String) : ModSettingValue
    data class DurationValue(val value: Double, val unit: String) : ModSettingValue
}

data class ModSettingDefinition(
    val id: String,
    val label: String,
    val type: ModSettingType,
    val defaultValue: ModSettingValue,
    val description: String? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val step: Double? = null,
    val unit: String? = null,
    val choices: List<String> = emptyList(),
    val maxLength: Int? = null
)

/** Shared validator for package parsing, persistence, UI submissions, and runtime snapshots. */
object ModSettingValidator {
    const val MAX_SETTINGS = 24
    private val idPattern = Regex("[a-z][a-zA-Z0-9]{0,31}")
    private val colorPattern = Regex("#[0-9a-fA-F]{6}")
    private val units = setOf("ms", "s", "min")

    fun validateDefinitions(definitions: Map<String, ModSettingDefinition>) {
        require(definitions.size <= MAX_SETTINGS) { "A mod may declare at most $MAX_SETTINGS settings." }
        definitions.forEach { (id, setting) ->
            require(id == setting.id && idPattern.matches(id)) { "Invalid setting ID: $id." }
            require(setting.label.length in 1..48 && setting.label.none(Char::isISOControl)) { "Invalid setting label: $id." }
            require(setting.description == null || setting.description.length <= 120 && setting.description.none(Char::isISOControl)) {
                "Invalid setting description: $id."
            }
            when (setting.type) {
                ModSettingType.BOOLEAN -> require(setting.defaultValue is ModSettingValue.BooleanValue)
                ModSettingType.INTEGER -> {
                    require(setting.defaultValue is ModSettingValue.IntegerValue)
                    validateRange(setting, requireIntegral = true)
                    validateValue(setting, setting.defaultValue)
                }
                ModSettingType.NUMBER -> {
                    require(setting.defaultValue is ModSettingValue.NumberValue)
                    validateRange(setting, requireIntegral = false)
                    validateValue(setting, setting.defaultValue)
                }
                ModSettingType.DURATION -> {
                    require(setting.defaultValue is ModSettingValue.DurationValue)
                    require(setting.unit in units && setting.defaultValue.unit == setting.unit) { "Invalid duration unit: $id." }
                    validateRange(setting, requireIntegral = false)
                    validateValue(setting, setting.defaultValue)
                }
                ModSettingType.COLOR -> require(setting.defaultValue is ModSettingValue.ColorValue && colorPattern.matches(setting.defaultValue.value)) {
                    "Invalid color default: $id."
                }
                ModSettingType.ENUM -> {
                    require(setting.choices.size in 1..16 && setting.choices.distinct().size == setting.choices.size &&
                        setting.choices.all { it.length in 1..64 && it.none(Char::isISOControl) }) { "Invalid enum choices: $id." }
                    require(setting.defaultValue is ModSettingValue.EnumValue && setting.defaultValue.value in setting.choices) {
                        "Invalid enum default: $id."
                    }
                }
                ModSettingType.TEXT -> {
                    require(setting.maxLength in 1..256 && setting.defaultValue is ModSettingValue.TextValue &&
                        setting.defaultValue.value.length <= setting.maxLength!! && setting.defaultValue.value.none(Char::isISOControl)) {
                        "Invalid text bounds or default: $id."
                    }
                }
            }
        }
    }

    fun validateValues(definitions: Map<String, ModSettingDefinition>, values: Map<String, ModSettingValue>): Map<String, ModSettingValue> {
        validateDefinitions(definitions)
        require(values.keys.all { it in definitions }) { "Settings contain unknown IDs." }
        val resolved = definitions.mapValues { (id, definition) -> values[id] ?: definition.defaultValue }
        resolved.forEach { (id, value) -> validateValue(definitions.getValue(id), value) }
        return resolved
    }

    fun validateValue(definition: ModSettingDefinition, value: ModSettingValue) {
        val valid = when (definition.type) {
            ModSettingType.BOOLEAN -> value is ModSettingValue.BooleanValue
            ModSettingType.INTEGER -> value is ModSettingValue.IntegerValue && inRange(definition, value.value.toDouble()) && aligned(definition, value.value.toDouble())
            ModSettingType.NUMBER -> value is ModSettingValue.NumberValue && value.value.isFinite() && inRange(definition, value.value) && aligned(definition, value.value)
            ModSettingType.DURATION -> value is ModSettingValue.DurationValue && value.unit == definition.unit && value.value.isFinite() &&
                inRange(definition, value.value) && aligned(definition, value.value)
            ModSettingType.COLOR -> value is ModSettingValue.ColorValue && colorPattern.matches(value.value)
            ModSettingType.ENUM -> value is ModSettingValue.EnumValue && value.value in definition.choices
            ModSettingType.TEXT -> value is ModSettingValue.TextValue && value.value.length <= definition.maxLength!! && value.value.none(Char::isISOControl)
        }
        require(valid) { "Invalid value for setting ${definition.id}." }
    }

    private fun validateRange(setting: ModSettingDefinition, requireIntegral: Boolean) {
        val min = requireNotNull(setting.minimum) { "Setting ${setting.id} needs a minimum." }
        val max = requireNotNull(setting.maximum) { "Setting ${setting.id} needs a maximum." }
        val step = requireNotNull(setting.step) { "Setting ${setting.id} needs a step." }
        require(min.isFinite() && max.isFinite() && step.isFinite() && min < max && step > 0.0 && max - min <= 1_000_000.0) {
            "Invalid numeric range for setting ${setting.id}."
        }
        if (requireIntegral) require(min % 1.0 == 0.0 && max % 1.0 == 0.0 && step % 1.0 == 0.0) {
            "Integer setting bounds and steps must be integers: ${setting.id}."
        }
    }

    private fun inRange(setting: ModSettingDefinition, value: Double) = value.isFinite() && value >= setting.minimum!! && value <= setting.maximum!!
    private fun aligned(setting: ModSettingDefinition, value: Double): Boolean {
        val unitsFromMin = (value - setting.minimum!!) / setting.step!!
        return kotlin.math.abs(unitsFromMin - kotlin.math.round(unitsFromMin)) < 1e-8
    }
}
