package com.armsx2

import android.content.Context
import android.widget.Toast
import com.armsx2.i18n.I18n
import com.armsx2.runtime.MainActivityRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.iefriends.pcsx2.NativeApp
import java.io.File
import java.util.zip.ZipFile

/**
 * Nexus2: turns on the bundled "remove blur" patches for the game that just booted.
 *
 * The patches.zip that ships with the app already carries per-game groups named like
 * "Remove Blur", "No Motion Blur" or "Disable Blur/Bloom" (about 190 games). They are labelled,
 * so the core only applies them when their name is in the game's [Patches] Enable list, which
 * the Patch Manager fills one game at a time. This does the same thing automatically.
 *
 * Why after boot and not before: before boot there is no VM, and setEnabledPatches then writes
 * to the BASE layer when the game has no gamesettings INI yet. The base list matches by name
 * across every game, so "Remove Blur" would be armed everywhere. With the VM up it writes the
 * running game's own INI (creating it), the same path the Patch Manager uses in game.
 *
 * Patches that apply every frame take effect at once (reloadPatches). Patches that apply only
 * when the game loads (place 0) take effect from the next boot of that game, and the toast
 * says so.
 *
 * Each game is handled once: if the player later turns a blur patch off in the Patch Manager,
 * it stays off.
 */
object BlurRemoval {
    const val PREF_ENABLED = "nexus.blurRemoval"
    private const val PREF_DONE_PREFIX = "nexus.blurRemoval.done."

    /** Group names that remove a blur effect: "Remove Blur", "No Motion Blur", "Blur Removal",
     *  "Disable Blurry Bloom effect". Names that only mention blur without removing it are left
     *  alone. */
    private val NAME_RE = Regex(
        "(?i)((remove|disable|no|removal|off|without|fix)\\b.*blur|blur.*\\b(removal|off|fix|remov\\w*))",
    )

    private const val ELF_WAIT_MS = 60_000L
    private const val POLL_MS = 500L

    private var job: Job? = null

    fun isEnabled(): Boolean =
        runCatching { MainActivityRuntime.prefs.getBoolean(PREF_ENABLED, true) }.getOrDefault(true)

    fun setEnabled(on: Boolean) {
        runCatching { MainActivityRuntime.prefs.edit().putBoolean(PREF_ENABLED, on).apply() }
    }

    /** Start watching for the game's ELF; call right after a game (not BIOS, not arcade) boot is
     *  started. Cancels any previous watch. */
    fun watch(scope: CoroutineScope, context: Context) {
        stop()
        if (!isEnabled()) return
        val app = context.applicationContext
        job = scope.launch {
            var waited = 0L
            var serial = ""
            var crc = ""
            while (waited < ELF_WAIT_MS) {
                delay(POLL_MS)
                waited += POLL_MS
                if (!NativeApp.hasActiveVM()) continue
                crc = runCatching { NativeApp.getGameCRC() }.getOrNull().orEmpty().trim().uppercase()
                serial = runCatching { NativeApp.getGameSerial() }.getOrNull().orEmpty().trim()
                if (crc.length == 8 && crc != "00000000" && serial.isNotEmpty()) break
                crc = ""
            }
            if (crc.isEmpty()) return@launch
            val key = PREF_DONE_PREFIX + serial + "_" + crc
            if (runCatching { MainActivityRuntime.prefs.getBoolean(key, false) }.getOrDefault(false)) return@launch

            val found = withContext(Dispatchers.IO) { findBlurGroups(app, serial, crc) }
            // Mark as handled even when the game has no blur patch, so the zip is read once.
            runCatching { MainActivityRuntime.prefs.edit().putBoolean(key, true).apply() }
            if (found == null || found.names.isEmpty()) return@launch
            if (!NativeApp.hasActiveVM()) return@launch

            val names = found.names.toTypedArray()
            runCatching { NativeApp.setEnabledPatches(false, names, names, serial) }
            runCatching { NativeApp.reloadPatches() }
            println("@@NEXUS_BLUR@@ $serial $crc enabled=${found.names} bootOnly=${found.bootOnly}")

            withContext(Dispatchers.Main) {
                val msg = if (found.bootOnly) I18n.get("nexus.blur.toast.nextBoot") else I18n.get("nexus.blur.toast.now")
                runCatching { Toast.makeText(app, msg, Toast.LENGTH_LONG).show() }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private data class Found(val names: List<String>, val bootOnly: Boolean)

    /** Blur-removal group names in the bundled pnach for this game, and whether any of their lines
     *  apply only at load (place 0, or the combined 0/1 form, which also writes at load). */
    private fun findBlurGroups(context: Context, serial: String, crc: String): Found? = runCatching {
        val zip = File(MainActivityRuntime.assetCopyRoot(context), "resources/patches.zip")
        if (!zip.isFile) return@runCatching null
        ZipFile(zip).use { z ->
            // Same names the core looks for: <SERIAL>_<CRC>.pnach, then <CRC>.pnach.
            val wanted = listOf("${serial}_$crc.pnach", "$crc.pnach")
            val entry = wanted.firstNotNullOfOrNull { n ->
                z.entries().asSequence().firstOrNull { it.name.substringAfterLast('/').equals(n, ignoreCase = true) }
            } ?: return@runCatching null
            val text = z.getInputStream(entry).bufferedReader().readText()
            val groups = PatchRepo.parseInstalled(text, entry.name).second
                .filter { it.name != "Unlabelled" && NAME_RE.containsMatchIn(it.name) }
            val bootOnly = groups.any { g ->
                g.body.lineSequence().map { it.trim() }
                    .filter { it.startsWith("patch", ignoreCase = true) && !it.startsWith("//") }
                    .any { line -> line.substringAfter('=').substringBefore(',').trim().let { it == "0" || it == "2" } }
            }
            Found(groups.map { it.name }.distinct(), bootOnly)
        }
    }.getOrNull()
}
