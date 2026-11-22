package org.doom.engine

import org.doom.model.*
import org.junit.Assert.*
import org.junit.Test

class ModSettingsTest {
    private fun numeric(type: ModSettingType = ModSettingType.NUMBER, default: Double = 0.5, step: Double = 0.1) =
        ModSettingDefinition("scale", "Preview scale", type,
            if (type == ModSettingType.INTEGER) ModSettingValue.IntegerValue(default.toLong()) else ModSettingValue.NumberValue(default),
            minimum = 0.0, maximum = 1.0, step = step)

    @Test fun validatesBooleanNumericColorEnumTextAndDurationSettings() {
        val definitions = mapOf(
            "enabled" to ModSettingDefinition("enabled", "Enabled", ModSettingType.BOOLEAN, ModSettingValue.BooleanValue(true)),
            "count" to ModSettingDefinition("count", "Count", ModSettingType.INTEGER, ModSettingValue.IntegerValue(3), minimum = 1.0, maximum = 5.0, step = 1.0),
            "scale" to numeric(),
            "accent" to ModSettingDefinition("accent", "Accent", ModSettingType.COLOR, ModSettingValue.ColorValue("#A78BFA")),
            "position" to ModSettingDefinition("position", "Position", ModSettingType.ENUM, ModSettingValue.EnumValue("end"), choices = listOf("start", "end")),
            "message" to ModSettingDefinition("message", "Message", ModSettingType.TEXT, ModSettingValue.TextValue("Hello"), maxLength = 32),
            "duration" to ModSettingDefinition("duration", "Duration", ModSettingType.DURATION,
                ModSettingValue.DurationValue(1500.0, "ms"), minimum = 500.0, maximum = 5000.0, step = 500.0, unit = "ms")
        )
        assertEquals(definitions.mapValues { it.value.defaultValue }, ModSettingValidator.validateValues(definitions, emptyMap()))
    }

    @Test fun enforcesNumericBoundsAndStepAlignment() {
        val definition = numeric()
        ModSettingValidator.validateValue(definition, ModSettingValue.NumberValue(0.7))
        assertTrue(runCatching { ModSettingValidator.validateValue(definition, ModSettingValue.NumberValue(0.75)) }.isFailure)
        assertTrue(runCatching { ModSettingValidator.validateValue(definition, ModSettingValue.NumberValue(1.1)) }.isFailure)
        val integer = numeric(ModSettingType.INTEGER, 2.0, 0.5)
        assertTrue(runCatching { ModSettingValidator.validateDefinitions(mapOf("scale" to integer)) }.isFailure)
    }

    @Test fun rejectsInvalidColorEnumTextDurationAndUnknownSettings() {
        val color = ModSettingDefinition("accent", "Accent", ModSettingType.COLOR, ModSettingValue.ColorValue("#FFFFFF"))
        assertTrue(runCatching { ModSettingValidator.validateValue(color, ModSettingValue.ColorValue("red")) }.isFailure)
        val enum = ModSettingDefinition("position", "Position", ModSettingType.ENUM, ModSettingValue.EnumValue("left"), choices = listOf("left", "right"))
        assertTrue(runCatching { ModSettingValidator.validateValue(enum, ModSettingValue.EnumValue("middle")) }.isFailure)
        val text = ModSettingDefinition("caption", "Caption", ModSettingType.TEXT, ModSettingValue.TextValue("abc"), maxLength = 3)
        assertTrue(runCatching { ModSettingValidator.validateValue(text, ModSettingValue.TextValue("long")) }.isFailure)
        val duration = ModSettingDefinition("delay", "Delay", ModSettingType.DURATION, ModSettingValue.DurationValue(1.0, "s"),
            minimum = 0.0, maximum = 5.0, step = 1.0, unit = "s")
        assertTrue(runCatching { ModSettingValidator.validateValue(duration, ModSettingValue.DurationValue(1.0, "min")) }.isFailure)
        assertTrue(runCatching { ModSettingValidator.validateValues(mapOf("caption" to text), mapOf("removedSetting" to ModSettingValue.TextValue("x"))) }.isFailure)
    }

    @Test fun resolvesOmittedValuesToDefaultsAndRejectsMoreThanTwentyFourSettings() {
        val enabled = ModSettingDefinition("enabled", "Enabled", ModSettingType.BOOLEAN, ModSettingValue.BooleanValue(true))
        assertEquals(ModSettingValue.BooleanValue(true), ModSettingValidator.validateValues(mapOf("enabled" to enabled), emptyMap())["enabled"])
        val tooMany = (0..24).associate { "s$it" to enabled.copy(id = "s$it") }
        assertTrue(runCatching { ModSettingValidator.validateDefinitions(tooMany) }.isFailure)
    }
}
