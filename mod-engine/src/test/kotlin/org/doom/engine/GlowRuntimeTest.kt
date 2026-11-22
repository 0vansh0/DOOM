package org.doom.engine

import org.doom.model.GlowAppearance
import org.doom.model.InstalledMod
import org.junit.Assert.*
import org.junit.Test

class GlowRuntimeTest {
    private val mod = InstalledMod("other.glow", "Other glow", "1.0.0", true, "Test",
        GlowAppearance("#112233", 20), true, 31)

    @Test fun requiresChargingAndBothGrants() {
        val renderer = FakeRenderer()
        val runtime = GlowRuntime(renderer)
        assertNull(runtime.update(listOf(mod), 35, false, true).activeId)
        assertNull(runtime.update(listOf(mod), 35, true, false).activeId)
        assertNull(runtime.update(listOf(mod.copy(overlayGranted = false)), 35, true, true).activeId)
        assertEquals(mod.id, runtime.update(listOf(mod), 35, true, true).activeId)
        assertEquals(mod.appearance, renderer.display)
        assertNull(runtime.update(listOf(mod), 35, true, false).activeId)
        assertNull(renderer.display)
    }

    @Test fun clearsPartiallyRenderedEffectOnFailure() {
        val renderer = FakeRenderer().apply { fail = true }
        val runtime = GlowRuntime(renderer)
        assertEquals(mod.id, runtime.update(listOf(mod), 35, true, true).failedId)
        assertNull(renderer.display)
        assertNull(runtime.state.activeId)
        runtime.update(listOf(mod), 35, true, true)
        assertEquals(1, renderer.attempts)
        runtime.stop()
        renderer.fail = false
        assertEquals(mod.id, runtime.update(listOf(mod), 35, true, true).activeId)
    }

    private class FakeRenderer : GlowRenderer {
        var display: GlowAppearance? = null
        var fail = false
        var attempts = 0
        override fun show(appearance: GlowAppearance) {
            display = appearance
            attempts++
            if (fail) error("Simulated native window failure")
        }
        override fun clear() {
            display = null
        }
    }
}
