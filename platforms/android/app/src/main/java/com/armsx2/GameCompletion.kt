package com.armsx2

import com.armsx2.runtime.MainActivityRuntime

/**
 * Nexus2: whether the player finished a game. No source the app can reach says when a PS2 campaign
 * ends (achievements measure the set, not the story), so this is only ever what the player marked,
 * shown as "manual". Unmarked = "Not verified", never "complete".
 */
object GameCompletion {
    private const val PREFIX = "nexus.completed."

    /** When the player marked [serial] as completed, or 0 when not marked. */
    fun markedAt(serial: String?): Long =
        serial?.takeIf { it.isNotEmpty() }?.let { s ->
            runCatching { MainActivityRuntime.prefs.getLong(PREFIX + s, 0L) }.getOrDefault(0L)
        } ?: 0L

    fun set(serial: String?, completed: Boolean) {
        val s = serial?.takeIf { it.isNotEmpty() } ?: return
        runCatching {
            MainActivityRuntime.prefs.edit().apply {
                if (completed) putLong(PREFIX + s, System.currentTimeMillis()) else remove(PREFIX + s)
            }.apply()
        }
        PlayTime.revision.value++
    }
}
