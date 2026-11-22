package org.doom.engine

import org.doom.model.GlowAppearance
import org.doom.model.InstalledMod

interface GlowRenderer {
    fun show(appearance: GlowAppearance)
    fun clear()
}

data class RuntimeState(val activeId: String? = null, val failedId: String? = null)

class GlowRuntime(private val renderer: GlowRenderer) {
    var state = RuntimeState()
        private set
    private var applied: GlowAppearance? = null
    private var attempted: Pair<String, GlowAppearance>? = null

    fun update(mods: List<InstalledMod>, api: Int, charging: Boolean, authorized: Boolean): RuntimeState {
        val mod = mods.firstOrNull { it.enabled && it.overlayGranted && api >= it.minAndroidApi }
        if (mod == null || !charging || !authorized) return stop()
        val requested = mod.id to mod.appearance
        if (state.activeId == mod.id && applied == mod.appearance || attempted == requested) return state
        stop()
        attempted = requested
        return try {
            renderer.show(mod.appearance)
            applied = mod.appearance
            RuntimeState(activeId = mod.id).also { state = it }
        } catch (_: RuntimeException) {
            renderer.clear()
            RuntimeState(failedId = mod.id).also { state = it }
        }
    }

    fun stop(): RuntimeState {
        renderer.clear()
        applied = null
        attempted = null
        return RuntimeState().also { state = it }
    }
}
