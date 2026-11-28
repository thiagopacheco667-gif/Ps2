package com.armsx2.perf

import com.armsx2.config.ConfigStore
import com.armsx2.runtime.MainActivityRuntime

/**
 * Nexus2: the app-wide default for MediaTek / Mali GPUs, limited to what was measured on the
 * Mali-G720: blending accuracy at Basic. In Yakuza, Medium spent 10% of the time under full speed
 * and Basic held 100% at the same 2x. MTVU, resolution and everything else stay per game.
 * Reversible: the previous global value is kept and "Undo" puts it back. Per-game settings still win.
 */
object NexusDeviceDefaults {
    private const val PREF_PREV_BLEND = "nexus.device.prevBlend"
    const val BLEND_BASIC = 1

    fun isMali(gpu: String?): Boolean = gpu?.contains("Mali", ignoreCase = true) == true

    fun globalBlend(): Int = runCatching { ConfigStore.loadGlobal().graphics.accurateBlendingUnit }.getOrDefault(-1)

    fun canUndo(): Boolean = runCatching { MainActivityRuntime.prefs.contains(PREF_PREV_BLEND) }.getOrDefault(false)

    /** Lowers the global blending to Basic if it is above it. Returns false when nothing changed. */
    fun apply(): Boolean {
        val g = ConfigStore.loadGlobal()
        if (g.graphics.accurateBlendingUnit <= BLEND_BASIC) return false
        MainActivityRuntime.prefs.edit().putInt(PREF_PREV_BLEND, g.graphics.accurateBlendingUnit).apply()
        ConfigStore.saveGlobal(g.copy(graphics = g.graphics.copy(accurateBlendingUnit = BLEND_BASIC)))
        return true
    }

    fun undo() {
        val prev = runCatching { MainActivityRuntime.prefs.getInt(PREF_PREV_BLEND, -1) }.getOrDefault(-1)
        if (prev >= 0) {
            val g = ConfigStore.loadGlobal()
            ConfigStore.saveGlobal(g.copy(graphics = g.graphics.copy(accurateBlendingUnit = prev)))
        }
        MainActivityRuntime.prefs.edit().remove(PREF_PREV_BLEND).apply()
    }
}
