package org.doom.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.doom.engine.runtime.WorkflowExecutionMode

class VisualBuilderExportTest {
    @Test fun visualBuilderExportsAreAcceptedByAndroidImporter() {
        val expected = mapOf(
            "builder-battery-glow.doommod" to Triple("Battery Glow Customization", setOf("battery.read", "overlay.render"),
                setOf("enabled", "accentColor", "intensity", "thickness", "durationMs")),
            "builder-low-battery-alert.doommod" to Triple("Low Battery Alert", setOf("battery.read", "ui.preview"),
                setOf("enabled", "durationMs", "message")),
            "builder-charging-companion.doommod" to Triple("Charging Companion", setOf("battery.read", "overlay.render"),
                setOf("enabled", "color")),
            "builder-custom-floating-card.doommod" to Triple("Custom Floating Card Workflow", setOf("ui.preview"),
                setOf("enabled", "message"))
        )
        expected.forEach { (archive, contract) ->
            val stream = requireNotNull(javaClass.getResourceAsStream("/$archive")) { "Missing builder fixture $archive." }
            stream.use {
                val parsed = ArchiveReader.read(it)
                assertEquals(contract.first, parsed.review.name)
                assertEquals(contract.second, parsed.review.capabilities.toSet())
                assertTrue(parsed.v4?.triggers?.isNotEmpty() == true)
                assertEquals(contract.third, parsed.v4?.settings?.keys)
            }
        }
    }

    @Test fun chargingCompanionExportPreservesOrderedWorkflowAndSettingReference() {
        val stream = requireNotNull(javaClass.getResourceAsStream("/builder-charging-companion.doommod"))
        stream.use {
            val parsed = ArchiveReader.read(it)
            val trigger = requireNotNull(parsed.v4?.triggers?.single())
            assertEquals(WorkflowExecutionMode.SEQUENTIAL, trigger.workflow?.executionMode)
            assertEquals(listOf("ring", "message"), trigger.actions.map { action -> action.id })
            assertTrue(trigger.actions.first().parameters.values.any { it.contains("color") })
            assertEquals(setOf("enabled", "color"), parsed.v4?.settings?.keys)
        }
    }
}
