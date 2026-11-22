package org.doom.engine

import com.google.gson.JsonParser
import org.doom.engine.runtime.*
import org.junit.Assert.*
import org.junit.Test

class SdkConformanceTest {
    private fun v4Documents(): Pair<ByteArray, ByteArray> {
        val cases = javaClass.getResourceAsStream("/v4.json")!!.bufferedReader().use { JsonParser.parseReader(it).asJsonArray }
        val fixture = cases.first { it.asJsonObject["name"].asString == "v4-valid-composed-multi-action" }.asJsonObject
        return fixture["manifest"].toString().toByteArray() to fixture["definition"].toString().toByteArray()
    }
    @Test fun sharedCliAndAndroidFixturesAgree() {
        val fixtures = javaClass.getResourceAsStream("/sdk2.json")!!.bufferedReader().use {
            JsonParser.parseReader(it).asJsonArray
        }
        for (element in fixtures) {
            val fixture = element.asJsonObject
            val result = runCatching { PackageFormat.validate(fixture["manifest"].toString().toByteArray(),
                fixture["definition"].toString().toByteArray(), setOf("doom.json", "mod.json")) }
            assertEquals(fixture["name"].asString, fixture["valid"].asBoolean, result.isSuccess)
        }
    }

    @Test fun shippedRegistryMatchesTrustedRuntimeAllowlist() {
        val registry = javaClass.getResourceAsStream("/v2/registry.json")!!.bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        assertEquals(ModEvent.entries.map { it.wireName }.toSet(), registry.getAsJsonObject("events").keySet())
        assertEquals(ModEffect.entries.map { it.wireName }.toSet(), registry.getAsJsonObject("actions").keySet())
        assertEquals(Condition.entries.map { it.wireName }.toSet(), registry.getAsJsonObject("conditions").keySet())
        assertEquals(2, CapabilityRegistry.VERSION)
        assertEquals(ModEffect.entries.map { it.wireName }.toSet(), CapabilityRegistry.actionCapabilities.keys)
        assertTrue(CapabilityRegistry.entries.all { it.contractVersion == 1 && it.requiredDoomApproval && it.minimumAndroidApi == 31 })
        assertEquals(11, CapabilityRegistry.entries.size)
        assertFalse(CapabilityRegistry.byId.getValue("app.launch.approved").implemented)
        assertEquals(setOf("overlay.render"), CapabilityRegistry.byId.getValue("overlay.preset.apply").requiredCapabilities)
        assertEquals(setOf("shizuku.diagnostic.identity"), CapabilityRegistry.adapters.single { it.id == "shizuku" }.operationIds)
        assertTrue(CapabilityRegistry.adapters.single { it.id == "shizuku" }.capabilityIds.isEmpty())
        assertEquals(setOf("system.screen_timeout.read", "system.screen_timeout.set", "system.screen_timeout.restore"),
            CapabilityRegistry.operationDefinitions.keys)
        assertTrue(CapabilityRegistry.operationDefinitions.values.none { it.modAccessible })
        assertEquals(15_000.0, CapabilityRegistry.operationDefinitions.getValue("system.screen_timeout.set")
            .parameterSchema.getValue("timeoutMs").minimum!!, 0.0)
    }

    @Test fun overlayPresetUsesTrustedRendererAndRequiresExplicitBaseOverlayCapability() {
        val fixture = javaClass.getResourceAsStream("/v4.json")!!.bufferedReader().use { JsonParser.parseReader(it).asJsonArray }
            .first { it.asJsonObject["name"].asString == "v4-valid-composed-multi-action" }.asJsonObject
        val manifest = fixture["manifest"].asJsonObject.deepCopy()
        val definition = fixture["definition"].asJsonObject.deepCopy()
        manifest.getAsJsonArray("capabilities").add("overlay.render")
        manifest.getAsJsonArray("capabilities").add("overlay.preset.apply")
        definition.getAsJsonArray("triggers").first().asJsonObject.getAsJsonArray("actions").first().asJsonObject.apply {
            addProperty("effect", "overlay.preset.apply")
            addProperty("template", "battery-ring")
        }
        PackageFormat.validate(manifest.toString().toByteArray(), definition.toString().toByteArray(), setOf("doom.json", "mod.json"))
        manifest.getAsJsonArray("capabilities").remove(com.google.gson.JsonPrimitive("overlay.render"))
        assertFalse(runCatching { PackageFormat.validate(manifest.toString().toByteArray(), definition.toString().toByteArray(),
            setOf("doom.json", "mod.json")) }.isSuccess)
    }

    @Test fun displayMetadataActionHasNoCallerControlledParameters() {
        val fixture = javaClass.getResourceAsStream("/v4.json")!!.bufferedReader().use { JsonParser.parseReader(it).asJsonArray }
            .first { it.asJsonObject["name"].asString == "v4-valid-composed-multi-action" }.asJsonObject
        val manifest = fixture["manifest"].asJsonObject.deepCopy()
        val definition = fixture["definition"].asJsonObject.deepCopy()
        manifest.getAsJsonArray("capabilities").add("device.display.read")
        val action = definition.getAsJsonArray("triggers").first().asJsonObject.getAsJsonArray("actions").first().asJsonObject
        action.addProperty("effect", "device.display.read")
        action.remove("template")
        action.remove("parameters")
        PackageFormat.validate(manifest.toString().toByteArray(), definition.toString().toByteArray(), setOf("doom.json", "mod.json"))
        action.add("parameters", JsonParser.parseString("{\"target\":\"private-id\"}"))
        assertFalse(runCatching { PackageFormat.validate(manifest.toString().toByteArray(), definition.toString().toByteArray(),
            setOf("doom.json", "mod.json")) }.isSuccess)
    }

    @Test fun unknownBatteryReadingNeverMatchesThresholdConditions() {
        assertFalse(Condition.BATTERY_LOW.matches(false))
        assertFalse(Condition.BATTERY_NOT_LOW.matches(false))
        assertTrue(Condition.BATTERY_LOW.matches(false, percent = 20))
        assertFalse(Condition.BATTERY_LOW.matches(false, percent = 21))
        assertTrue(Condition.POWER_SAVING.matches(false, powerSaving = true))
    }

    @Test fun definitionV4ParsesTypedComposedConditionsAndOrderedActions() {
        val (manifest, definition) = v4Documents()
        val parsed = PackageFormat.validate(manifest, definition, setOf("doom.json", "mod.json"))
        assertEquals(1, parsed.v4!!.triggers.size)
        assertEquals(listOf("ui.preview.show", "haptics.pulse"), parsed.v4.triggers.single().actions.map { it.effect })
        assertEquals(V4ConditionOp.ALL, parsed.v4.triggers.single().condition.op)
        assertEquals(setOf("threshold", "enabled", "accent", "mode", "message"), parsed.v4.settings.keys)
    }

    @Test fun definitionV4ParsesDynamicSettingsAndResolvesReferences() {
        val cases = javaClass.getResourceAsStream("/v4.json")!!.bufferedReader().use { JsonParser.parseReader(it).asJsonArray }
        val fixture = cases.first { it.asJsonObject["name"].asString == "v4-settings-all-controls-and-references" }.asJsonObject
        val parsed = PackageFormat.validate(fixture["manifest"].toString().toByteArray(),
            fixture["definition"].toString().toByteArray(), setOf("doom.json", "mod.json"))
        val settings = parsed.v4!!.settings
        assertEquals(setOf("enabled", "threshold", "scale", "duration", "color", "position", "message"), settings.keys)
        assertEquals(org.doom.model.ModSettingType.NUMBER, settings.getValue("scale").type)
        assertEquals(org.doom.model.ModSettingType.DURATION, settings.getValue("duration").type)
        assertEquals("#A78BFA", (settings.getValue("color").defaultValue as org.doom.model.ModSettingValue.ColorValue).value)
        assertTrue(parsed.v4.triggers.single().condition.children.any { it.source == "setting.enabled" })
        assertTrue(parsed.v4.triggers.single().actions.first().parameters.getValue("title").contains("\$setting"))
    }

    @Test fun definitionV4RejectsUnsupportedVersionAndPreservesV3() {
        val (manifest, definition) = v4Documents()
        val legacyManifest = javaClass.getResourceAsStream("/legacy-v3-doom.json")!!.readBytes()
        val legacyDefinition = javaClass.getResourceAsStream("/legacy-v3-mod.json")!!.readBytes()
        val legacyParsed = PackageFormat.validate(legacyManifest, legacyDefinition, setOf("doom.json", "mod.json", "assets/glow.png"))
        assertNull(legacyParsed.v4)
        assertEquals(1, legacyParsed.subscriptions.size)
        val valid = com.google.gson.JsonParser.parseString(definition.decodeToString()).asJsonObject
        valid.addProperty("definitionVersion", 9)
        assertTrue(runCatching { PackageFormat.validate(manifest, valid.toString().toByteArray(), setOf("doom.json", "mod.json")) }.isFailure)
    }
}
