package org.doom.engine.runtime

import org.doom.engine.ArchiveReader
import org.doom.model.*
import org.junit.Assert.*
import org.junit.Test

class DefaultModRuntimeTest {
    @Test fun displayReadRequiresApprovalAndIsRateLimitedPerMod() {
        val executor = FakeExecutor()
        var now = 0L
        val logs = mutableListOf<RuntimeDiagnostic>()
        val runtime = DefaultModRuntime(executor, { now }, { true }, logs::add)
        val caps = listOf("device.display.read")
        val mod = InstalledMod("example.display", "Display reader", "1", true, "", GlowAppearance("#AABBCC", 30), true, 31,
            ModStatus.READY_TO_ACTIVATE, capabilities = caps, approvedCapabilities = caps.toSet(), runtimeAvailable = true)
        val condition = V4Condition(V4ConditionOp.COMPARE, "event.name", V4Comparison.EQUALS,
            V4ValueType.STRING, "mod.manual.trigger")
        val action = V4Action("device.display.read", null, emptyMap(), "read")
        val definition = ValidatedPackage(
            PackageReview(mod.id, mod.title, mod.version, "", "", "", 31, caps, mod.appearance, 10..60),
            emptyMap(), V4Definition(listOf(
                V4Trigger("read-one", ModEvent.MANUAL.wireName, condition, listOf(action)),
                V4Trigger("read-two", ModEvent.MANUAL.wireName, condition, listOf(action.copy(id = "read-two")))
            ), emptyMap())
        )
        runtime.begin(false)
        assertTrue(runtime.activate(mod, definition))
        runtime.dispatch(ModEvent.MANUAL, mod.id)
        assertEquals(1, executor.displayReads)
        assertEquals("SUCCESS", logs.first().result)
        assertEquals("RATE_LIMITED", logs.last().result)
        runtime.deactivate(mod.id)
        assertFalse(runtime.activate(mod.copy(approvedCapabilities = emptySet()), definition))
    }

    @Test fun runtimeCapabilityRevocationStopsOnlyModsUsingThatCapability() {
        val availability = mutableMapOf("battery.read" to true, "overlay.render" to true)
        val executor = FakeExecutor()
        var now = 0L
        val runtime = DefaultModRuntime(executor, { now }, { true }, {}, capabilityAvailable = { availability[it] == true })
        val dependent = InstalledMod("test.dependent", "Dependent", "1", true, "", GlowAppearance("#AABBCC", 33), true, 31,
            ModStatus.READY_TO_ACTIVATE, capabilities = listOf("battery.read", "overlay.render"),
            approvedCapabilities = setOf("battery.read", "overlay.render"), runtimeAvailable = true)
        val independent = dependent.copy(id = "test.independent", capabilities = listOf("overlay.render"),
            approvedCapabilities = setOf("overlay.render"))
        fun definition(mod: InstalledMod) = ValidatedPackage(PackageReview(mod.id, mod.title, mod.version, "", "", "", 31,
            mod.capabilities, mod.appearance, 10..60), emptyMap())
        runtime.begin(false)
        assertTrue(runtime.activate(dependent, definition(dependent)))
        assertTrue(runtime.activate(independent, definition(independent)))
        availability["battery.read"] = false
        runtime.recheckPermissions()
        assertEquals(RuntimePhase.DISABLED, runtime.states.value[dependent.id]?.phase)
        assertEquals(RuntimePhase.ACTIVE, runtime.states.value[independent.id]?.phase)
        runtime.stop()
    }

    @Test fun plansRemainDisabledUntilVerifiedAndChargingFansOutWithBoundedSlots() {
        val h = Harness()
        val a = h.mod("example.first")
        val b = h.mod("example.second")
        val c = h.mod("example.third")
        assertFalse(h.runtime.activate(a, h.definition(a)))
        h.runtime.begin(false)
        listOf(a, b, c).forEach { assertTrue(h.runtime.activate(it, h.definition(it))) }
        assertEquals(RuntimePhase.ACTIVE, h.runtime.states.value[a.id]?.phase)
        assertTrue(h.executor.shown.isEmpty())
        h.runtime.batteryChanged(true)
        assertEquals(setOf(a.id, b.id), h.executor.shown.keys)
        assertEquals(setOf(0, 1), h.executor.shown.values.toSet())
        assertEquals("Overlay limit reached.", h.runtime.states.value[c.id]?.message)
        h.runtime.batteryChanged(false)
        assertTrue(h.executor.shown.isEmpty())
    }

    @Test fun permissionGrantRevocationAndSafeModeCleanResources() {
        val h = Harness()
        val a = h.mod("example.permission")
        h.runtime.begin(true)
        val unapproved = a.copy(approvedCapabilities = setOf("battery.read"))
        assertFalse(h.runtime.activate(unapproved, h.definition(unapproved)))
        assertTrue(h.runtime.activate(a, h.definition(a)))
        assertEquals(1, h.executor.shown.size)
        h.authorized = false
        h.runtime.recheckPermissions()
        assertTrue(h.executor.shown.isEmpty())
        assertEquals(RuntimePhase.DISABLED, h.runtime.states.value[a.id]?.phase)
        h.authorized = true
        assertTrue(h.runtime.activate(a, h.definition(a)))
        h.runtime.retain(emptyList())
        assertTrue(h.executor.shown.isEmpty())
        assertTrue(h.runtime.states.value.isEmpty())
    }

    @Test fun manualPreviewUsesSettingsAndExpiresWithRateLimit() {
        val h = Harness()
        val a = h.mod("example.preview", listOf("ui.preview"))
        h.authorized = false
        h.runtime.begin(false)
        val definition = h.definition(a, mapOf(ModEvent.MANUAL to
            Subscription(Condition.ALWAYS, ModAction(ModEffect.SHOW_PREVIEW, EffectTemplate.FLOATING_CARD))))
        h.runtime.activate(a, definition)
        h.runtime.dispatch(ModEvent.MANUAL, a.id)
        h.runtime.dispatch(ModEvent.MANUAL, a.id)
        assertEquals(1, h.executor.invocations)
        assertEquals(a.appearance, h.runtime.states.value[a.id]?.presentation?.appearance)
        assertEquals(EffectTemplate.FLOATING_CARD, h.runtime.states.value[a.id]?.presentation?.template)
        h.now = 20_001
        h.runtime.expire()
        assertNull(h.runtime.states.value[a.id]?.presentation)
        assertTrue(h.executor.shown.isEmpty())
    }

    @Test fun unrelatedSharedBatteryEventDoesNotFaultManualOnlyV4Mod() {
        val executor = FakeExecutor()
        val logs = mutableListOf<RuntimeDiagnostic>()
        val runtime = DefaultModRuntime(executor, { 0L }, { true }, logs::add)
        val caps = listOf("ui.preview")
        val mod = InstalledMod("example.manual-only", "Manual only", "1", true, "", GlowAppearance("#AABBCC", 30),
            true, 31, ModStatus.READY_TO_ACTIVATE, capabilities = caps, approvedCapabilities = caps.toSet(), runtimeAvailable = true)
        val condition = V4Condition(V4ConditionOp.COMPARE, "event.name", V4Comparison.EQUALS,
            V4ValueType.STRING, ModEvent.MANUAL.wireName)
        val action = V4Action("ui.preview.show", "floating-card", emptyMap(), "preview")
        val definition = ValidatedPackage(
            PackageReview(mod.id, mod.title, mod.version, "", "", "", 31, caps, mod.appearance, 10..60),
            emptyMap(), V4Definition(listOf(V4Trigger("manual", ModEvent.MANUAL.wireName, condition, listOf(action))), emptyMap())
        )
        runtime.begin(false)
        assertTrue(runtime.activate(mod, definition))

        // A shared battery source sends this event to active runtimes even though
        // this package neither declares battery.read nor subscribes to battery events.
        runtime.batteryChanged(value = null, percent = 80)

        assertEquals(RuntimePhase.ACTIVE, runtime.states.value[mod.id]?.phase)
        assertTrue(logs.none { it.message == "Event capability is unavailable." })
        runtime.dispatch(ModEvent.MANUAL, mod.id)
        assertEquals(1, executor.invocations)
        runtime.stop()
    }

    @Test fun failureDoesNotStopOtherModsAndStopClearsSessionTruth() {
        val h = Harness()
        val failed = h.mod("example.failing")
        val good = h.mod("example.good")
        h.executor.failsFor = failed.id
        h.runtime.begin(false)
        listOf(failed, good).forEach { h.runtime.activate(it, h.definition(it)) }
        h.runtime.batteryChanged(true)
        assertEquals(RuntimePhase.ERROR, h.runtime.states.value[failed.id]?.phase)
        assertEquals(setOf(good.id), h.executor.shown.keys)
        assertTrue(h.logs.any { it.modId == failed.id && it.result == "ERROR" })
        h.runtime.stop()
        assertTrue(h.executor.shown.isEmpty())
        assertTrue(h.runtime.states.value.isEmpty())
    }

    @Test fun settingsAndIdentityMustMatchTheVerifiedDefinition() {
        val h = Harness()
        val a = h.mod("example.validated")
        h.runtime.begin(true)
        val narrowed = h.definition(a).let { it.copy(review = it.review.copy(intensityRange = 10..25)) }
        assertFalse(h.runtime.activate(a, narrowed))
        assertTrue(h.executor.shown.isEmpty())
    }

    @Test fun configuredRingReportsLiveBatteryAndStopsAtItsOwnDeadline() {
        val h = Harness()
        val base = h.mod("example.configured")
        val mod = base.copy(appearance = base.appearance.copy(ring = RingStyle(durationSeconds = 3)))
        h.runtime.begin(true, 72)
        h.runtime.activate(mod, h.definition(mod))
        assertEquals(72, h.runtime.states.value[mod.id]?.presentation?.batteryPercent)
        h.runtime.batteryChanged(true, 73)
        assertEquals(73, h.runtime.states.value[mod.id]?.presentation?.batteryPercent)
        h.now = 3_000
        h.runtime.expire()
        assertTrue(h.executor.shown.isEmpty())
    }

    @Test fun v4BatteryConditionFiresOncePerThresholdCrossingAndStopsWhenDisabled() {
        val h = Harness()
        val mod = h.mod("community.lowbattery", listOf("battery.read", "ui.preview"))
        val condition = V4Condition(V4ConditionOp.COMPARE, "battery.level", V4Comparison.LESS_THAN,
            V4ValueType.NUMBER, "20")
        val trigger = V4Trigger("low", "battery.level.changed", condition,
            listOf(V4Action("ui.preview.show", "floating-card", emptyMap())))
        val definition = h.definition(mod).copy(v4 = V4Definition(listOf(trigger), emptyMap()))
        h.runtime.begin(false, 21)
        assertTrue(h.runtime.activate(mod, definition))
        h.runtime.simulateBatteryReading(19, false)
        assertEquals(1, h.executor.invocations)
        h.runtime.simulateBatteryReading(19, false)
        assertEquals(1, h.executor.invocations)
        h.runtime.simulateBatteryReading(18, false)
        assertEquals(1, h.executor.invocations)
        h.runtime.simulateBatteryReading(20, false)
        h.runtime.simulateBatteryReading(19, false)
        assertEquals(2, h.executor.invocations)
        h.runtime.deactivate(mod.id)
        h.runtime.simulateBatteryReading(18, false)
        assertEquals(2, h.executor.invocations)
        assertTrue(h.executor.shown.isEmpty())
    }

    @Test fun v4CapabilityDenialAndModFailureDoNotBlockOtherSubscriptions() {
        val h = Harness()
        val denied = h.mod("community.denied", listOf("battery.read", "ui.preview"))
            .copy(approvedCapabilities = setOf("ui.preview"))
        val broken = h.mod("community.broken", listOf("battery.read", "ui.preview"))
        val healthy = h.mod("community.healthy", listOf("battery.read", "ui.preview"))
        fun v4(mod: InstalledMod) = h.definition(mod).copy(v4 = V4Definition(listOf(V4Trigger("low", "battery.level.changed",
            V4Condition(V4ConditionOp.COMPARE, "battery.level", V4Comparison.LESS_THAN, V4ValueType.NUMBER, "20"),
            listOf(V4Action("ui.preview.show", "floating-card", emptyMap())))), emptyMap()))
        h.runtime.begin(false, 21)
        assertFalse(h.runtime.activate(denied, v4(denied)))
        assertTrue(h.runtime.activate(broken, v4(broken)))
        assertTrue(h.runtime.activate(healthy, v4(healthy)))
        h.executor.failsFor = broken.id
        h.runtime.simulateBatteryReading(19, false)
        assertEquals(RuntimePhase.ERROR, h.runtime.states.value[broken.id]?.phase)
        assertEquals(setOf(healthy.id), h.executor.shown.keys)
        assertTrue(h.logs.any { it.modId == broken.id && it.result == "ERROR" })
    }

    @Test fun explicitV4WorkflowRunsAllActionsAndDisableCleansTheirOwnedEffects() {
        val h = Harness()
        val mod = h.mod("community.workflow", listOf("battery.read", "ui.preview", "haptics.vibrate"))
        val workflow = V4WorkflowConfig("charge-flow", WorkflowExecutionMode.SEQUENTIAL, WorkflowFailurePolicy.STOP_ON_FAILURE, 10_000)
        val trigger = V4Trigger("charging", "battery.charging.started",
            V4Condition(V4ConditionOp.COMPARE, "battery.charging", V4Comparison.EQUALS, V4ValueType.BOOLEAN, "true"),
            listOf(V4Action("ui.preview.show", "floating-card", emptyMap(), "preview"),
                V4Action("haptics.pulse", null, emptyMap(), "pulse")), workflow)
        val definition = h.definition(mod).copy(v4 = V4Definition(listOf(trigger), emptyMap()))
        h.runtime.begin(false)
        assertTrue(h.runtime.activate(mod, definition))
        h.runtime.batteryChanged(true)
        assertEquals(1, h.executor.invocations)
        assertEquals(1, h.executor.pulses)
        h.runtime.deactivate(mod.id)
        assertEquals(0, h.executor.pulses)
        assertTrue(h.logs.any { it.event == "workflow:charge-flow" && it.result == "SUCCEEDED" })
    }

    @Test fun cliPackagedWorkflowImportsAndExecutesWithoutHostSpecificModLogic() {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/charging-workflow.doommod")).use { it.readBytes() }
        val packageData = ArchiveReader.read(bytes.inputStream())
        val mod = InstalledMod(packageData.review.id, packageData.review.name, packageData.review.version, true, "",
            packageData.review.appearance, true, packageData.review.minAndroidApi, ModStatus.READY_TO_ACTIVATE,
            capabilities = packageData.review.capabilities, approvedCapabilities = packageData.review.capabilities.toSet(),
            runtimeAvailable = true)
        val h = Harness()
        h.runtime.begin(false, 44)
        assertTrue(h.runtime.activate(mod, packageData))
        h.runtime.batteryChanged(true, 45)
        assertEquals(2, h.executor.invocations)
        assertTrue(h.logs.any { it.event == "workflow:charging-sequence" && it.result == "SUCCEEDED" })
        h.runtime.batteryChanged(false, 45)
        h.runtime.dispatch(ModEvent.MANUAL, mod.id)
        assertEquals(4, h.executor.invocations)
        assertTrue(h.logs.any { it.event == "workflow:manual-sequence" && it.result == "SUCCEEDED" })
        h.runtime.deactivate(mod.id)
        assertTrue(h.executor.shown.isEmpty())
    }

    @Test fun visualBuilderExportExecutesDeclaredWorkflowAndCleansUpOnDisable() {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/builder-charging-companion.doommod")).use { it.readBytes() }
        val packageData = ArchiveReader.read(bytes.inputStream())
        val settings = ModSettingValidator.validateValues(packageData.review.settingDefinitions, emptyMap()) +
            ("color" to ModSettingValue.ColorValue("#33CCAA"))
        val declared = packageData.review.capabilities.toSet()
        val mod = InstalledMod(packageData.review.id, packageData.review.name, packageData.review.version, true, "",
            packageData.review.appearance, true, packageData.review.minAndroidApi, ModStatus.READY_TO_ACTIVATE,
            capabilities = packageData.review.capabilities, approvedCapabilities = declared, runtimeAvailable = true,
            settingDefinitions = packageData.review.settingDefinitions, settingValues = settings)
        val h = Harness()
        h.runtime.begin(false)
        assertTrue(h.runtime.activate(mod, packageData))

        h.runtime.batteryChanged(true)

        assertEquals(2, h.executor.invocations)
        assertEquals("#33CCAA", h.executor.presentations.first().appearance.color)
        assertTrue(h.logs.any { it.event == "workflow:charging-sequence" && it.result == "SUCCEEDED" })
        h.runtime.deactivate(mod.id)
        assertTrue(h.executor.shown.isEmpty())
    }

    @Test fun changedDeclarativeSettingsReachFutureWorkflowActionsWithoutRestartingMod() {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/charging-workflow.doommod")).use { it.readBytes() }
        val packageData = ArchiveReader.read(bytes.inputStream())
        var mod = InstalledMod(packageData.review.id, packageData.review.name, packageData.review.version, true, "",
            packageData.review.appearance, true, packageData.review.minAndroidApi, ModStatus.READY_TO_ACTIVATE,
            capabilities = packageData.review.capabilities, approvedCapabilities = packageData.review.capabilities.toSet(),
            runtimeAvailable = true, settingDefinitions = packageData.review.settingDefinitions,
            settingValues = org.doom.model.ModSettingValidator.validateValues(packageData.review.settingDefinitions, emptyMap()))
        val h = Harness()
        h.runtime.begin(false, 45)
        assertTrue(h.runtime.activate(mod, packageData))
        val updated = mod.settingValues + mapOf(
            "accentColor" to org.doom.model.ModSettingValue.ColorValue("#33CCAA"),
            "intensity" to org.doom.model.ModSettingValue.IntegerValue(55),
            "ringThickness" to org.doom.model.ModSettingValue.IntegerValue(9),
            "durationMs" to org.doom.model.ModSettingValue.DurationValue(5000.0, "ms"),
            "position" to org.doom.model.ModSettingValue.EnumValue("bottom-center"),
            "message" to org.doom.model.ModSettingValue.TextValue("Changed live")
        )
        mod = mod.copy(settingValues = updated)
        h.runtime.retain(listOf(mod))
        assertEquals(RuntimePhase.ACTIVE, h.runtime.states.value[mod.id]?.phase)
        h.runtime.dispatch(ModEvent.MANUAL, mod.id)
        assertEquals(2, h.executor.presentations.size)
        assertEquals("#33CCAA", h.executor.presentations.first().appearance.color)
        assertEquals(9, h.executor.presentations.first().appearance.ring?.thicknessDp)
        assertEquals(RingPosition.BOTTOM_CENTER, h.executor.presentations.first().appearance.ring?.position)
        assertEquals("Changed live", h.executor.presentations.last().contentText)
        assertEquals(5_000L, h.runtime.expiryDeadline.value)
        h.runtime.deactivate(mod.id)
        assertTrue(h.executor.shown.isEmpty())
    }

    @Test fun settingReferencesResolveForV4ActionsWithoutWorkflowMetadata() {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/charging-workflow.doommod")).use { it.readBytes() }
        val parsed = ArchiveReader.read(bytes.inputStream())
        val settings = org.doom.model.ModSettingValidator.validateValues(parsed.review.settingDefinitions, emptyMap()) + mapOf(
            "accentColor" to ModSettingValue.ColorValue("#33CCAA"),
            "durationMs" to ModSettingValue.DurationValue(4000.0, "ms"),
            "message" to ModSettingValue.TextValue("No workflow block")
        )
        val v4 = checkNotNull(parsed.v4)
        val originalTrigger = v4.triggers.first { it.event == "battery.charging.started" }
        val cardAction = originalTrigger.actions[1].copy(parameters = originalTrigger.actions[1].parameters +
            ("color" to """{"${'$'}setting":"accentColor"}"""))
        val chargingTrigger = originalTrigger.copy(workflow = null, actions = listOf(cardAction))
        val packageData = parsed.copy(v4 = v4.copy(triggers = listOf(chargingTrigger)))
        val mod = InstalledMod(parsed.review.id, parsed.review.name, parsed.review.version, true, "",
            parsed.review.appearance, true, parsed.review.minAndroidApi, ModStatus.READY_TO_ACTIVATE,
            capabilities = parsed.review.capabilities, approvedCapabilities = parsed.review.capabilities.toSet(),
            runtimeAvailable = true, settingDefinitions = parsed.review.settingDefinitions, settingValues = settings)
        val h = Harness()
        h.runtime.begin(false)
        assertTrue(h.runtime.activate(mod, packageData))
        h.runtime.batteryChanged(true, 45)
        assertEquals("#33CCAA", h.executor.presentations.single().appearance.color)
        assertEquals("No workflow block", h.executor.presentations.single().contentText)
        assertEquals(4_000L, h.runtime.expiryDeadline.value)
        h.runtime.deactivate(mod.id)
    }

    @Test fun repeatingAnIdenticalPresentationReschedulesItsDeadline() {
        val h = Harness()
        val a = h.mod("example.repeat", listOf("ui.preview"))
        h.runtime.begin(false)
        h.runtime.activate(a, h.definition(a, mapOf(ModEvent.MANUAL to
            Subscription(Condition.ALWAYS, ModAction(ModEffect.SHOW_PREVIEW, EffectTemplate.FLOATING_CARD)))))
        h.runtime.dispatch(ModEvent.MANUAL, a.id)
        assertEquals(20_000L, h.runtime.expiryDeadline.value)
        h.now = 600
        h.runtime.dispatch(ModEvent.MANUAL, a.id)
        assertEquals(20_600L, h.runtime.expiryDeadline.value)
        h.now = 20_000
        h.runtime.expire()
        assertNotNull(h.runtime.states.value[a.id]?.presentation)
        h.now = 20_600
        h.runtime.expire()
        assertNull(h.runtime.expiryDeadline.value)
    }

    @Test fun explicitExternalLifetimeSurvivesConfiguredDeadlineAndCleansOnDisable() {
        val h = Harness()
        val a = h.mod("example.external")
        h.runtime.begin(false)
        h.runtime.activate(a, h.definition(a))
        h.runtime.batteryChanged(true)
        h.runtime.extendPresentation(a.id, 12_000)
        assertEquals(12_000L, h.runtime.expiryDeadline.value)
        h.now = 12_000
        h.runtime.expire()
        assertTrue(h.executor.shown.isEmpty())
        h.now = 13_000
        h.runtime.batteryChanged(false)
        h.runtime.batteryChanged(true)
        h.runtime.extendPresentation(a.id, null)
        assertNull(h.runtime.expiryDeadline.value)
        h.now = 1_000_000
        h.runtime.expire()
        assertTrue(h.executor.shown.containsKey(a.id))
        h.runtime.deactivate(a.id)
        assertTrue(h.executor.shown.isEmpty())
    }

    @Test fun sdk2PowerEventsRespectConditionsAndPerModGrants() {
        val h = Harness()
        val mod = h.mod("sdk.power", listOf("device.power.read", "ui.preview"))
        val definition = h.definition(mod, mapOf(ModEvent.POWER_CHANGED to
            Subscription(Condition.POWER_SAVING, ModAction(ModEffect.SHOW_PREVIEW, EffectTemplate.FLOATING_CARD))))
        h.runtime.begin(false)
        assertFalse(h.runtime.activate(mod.copy(approvedCapabilities = setOf("ui.preview")), definition))
        assertTrue(h.runtime.activate(mod, definition))
        h.runtime.powerChanged(false)
        assertTrue(h.executor.shown.isEmpty())
        h.runtime.powerChanged(true)
        assertEquals(1, h.executor.invocations)
        h.runtime.powerChanged(true)
        assertEquals(1, h.executor.invocations)
        h.runtime.stop()
        assertTrue(h.executor.shown.isEmpty())
    }

    @Test fun sdk2SettingsAffectConditionAndInvalidValuesCannotActivate() {
        val h = Harness()
        val options = listOf(IntegerSetting("threshold", 1, 100, 25))
        val base = h.mod("sdk.threshold", listOf("battery.read", "ui.preview"))
        val mod = base.copy(appearance = base.appearance.copy(options = mapOf("threshold" to 25)), optionDefinitions = options)
        val definition = h.definition(mod, mapOf(ModEvent.MANUAL to
            Subscription(Condition.BATTERY_BELOW_SETTING, ModAction(ModEffect.SHOW_PREVIEW, EffectTemplate.FLOATING_CARD), "threshold")))
            .let { it.copy(review = it.review.copy(optionDefinitions = options)) }
        h.runtime.begin(false, 30)
        assertTrue(h.runtime.activate(mod, definition))
        h.runtime.dispatch(ModEvent.MANUAL, mod.id)
        assertTrue(h.executor.shown.isEmpty())
        h.runtime.deactivate(mod.id)
        val configured = mod.copy(appearance = mod.appearance.copy(options = mapOf("threshold" to 40)))
        assertTrue(h.runtime.activate(configured, definition))
        h.runtime.dispatch(ModEvent.MANUAL, mod.id)
        assertEquals(1, h.executor.invocations)
        h.runtime.deactivate(mod.id)
        assertFalse(h.runtime.activate(mod.copy(appearance = mod.appearance.copy(options = emptyMap())), definition))
    }

    @Test fun sdk2HapticPulseIsBoundedCancelledAndFailureIsIsolated() {
        val h = Harness()
        val mod = h.mod("sdk.haptic", listOf("haptics.vibrate"))
        val definition = h.definition(mod, mapOf(ModEvent.MANUAL to
            Subscription(Condition.ALWAYS, ModAction(ModEffect.HAPTIC_PULSE, null))))
        h.runtime.begin(false)
        h.runtime.activate(mod, definition)
        h.runtime.dispatch(ModEvent.MANUAL, mod.id)
        assertEquals(1, h.executor.pulses)
        assertEquals(50L, h.runtime.expiryDeadline.value)
        h.now = 50
        h.runtime.expire()
        assertEquals(0, h.executor.pulses)
        h.now = 600
        h.runtime.dispatch(ModEvent.MANUAL, mod.id)
        h.runtime.deactivate(mod.id)
        assertEquals(0, h.executor.pulses)
    }

    private class Harness {
        val executor = FakeExecutor()
        var now = 0L
        var authorized = true
        val logs = mutableListOf<RuntimeDiagnostic>()
        val runtime = DefaultModRuntime(executor, { now }, { authorized }, { logs.add(it) })

        fun mod(id: String, caps: List<String> = listOf("battery.read", "overlay.render")) =
            InstalledMod(id, id, "1.0.0", true, "", GlowAppearance("#AABBCC", 33), true,
                31, ModStatus.READY_TO_ACTIVATE, capabilities = caps, approvedCapabilities = caps.toSet(),
                runtimeAvailable = true)

        fun definition(mod: InstalledMod, bindings: Map<ModEvent, Subscription> = mapOf(
            ModEvent.CHARGING_STARTED to Subscription(Condition.CHARGING, ModAction(ModEffect.SHOW_OVERLAY, EffectTemplate.BATTERY_RING)),
            ModEvent.CHARGING_STOPPED to Subscription(Condition.ALWAYS, ModAction(ModEffect.DISMISS_OVERLAY, null))
        )) = ValidatedPackage(PackageReview(mod.id, mod.title, mod.version, "", "", "", 31,
            mod.capabilities, mod.appearance, 10..60), bindings)
    }

    private class FakeExecutor : ModEffectExecutor {
        override val supportedActions = ModEffect.entries.toSet()
        val shown = mutableMapOf<String, Int>()
        var failsFor: String? = null
        var invocations = 0
        var pulses = 0
        var displayReads = 0
        val presentations = mutableListOf<RuntimePresentation>()
        override fun pulse(context: ModExecutionContext): AutoCloseable {
            check("haptics.vibrate" in context.grantedCapabilities)
            pulses++
            return AutoCloseable { pulses-- }
        }
        override fun readDisplay(context: ModExecutionContext): DisplayMetadata {
            check("device.display.read" in context.grantedCapabilities)
            displayReads++
            return DisplayMetadata(1080, 2400, 420, 0)
        }
        override fun show(context: ModExecutionContext, presentation: RuntimePresentation, slot: Int): AutoCloseable {
            if (context.modId == failsFor) error("Synthetic renderer failure")
            invocations++
            presentations += presentation
            shown[context.modId] = slot
            return AutoCloseable { shown.remove(context.modId) }
        }
    }
}
