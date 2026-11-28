package com.armsx2.perf

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Nexus2: what the device and the core report about themselves, read on demand (no polling).
 * Every value is read from the system or the app's own log; anything unreadable is null.
 */
object NexusSystemInfo {
    data class Info(
        val soc: String?,
        val cores: String?,
        val gpu: String?,
        val glVersion: String?,
        val driver: String,
        val android: String,
    )

    fun read(context: Context, settings: com.armsx2.config.Settings): Info {
        val soc = if (Build.VERSION.SDK_INT >= 31) {
            listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ").ifBlank { null }
        } else null
        val gl = runCatching { com.armsx2.GpuInfo.glStrings() }.getOrNull()
        val installed = runCatching { com.armsx2.CustomDriver.listInstalled(context) }.getOrDefault(emptyList())
        val driver = installed.firstOrNull { it.id == settings.output.customDriverId }?.name
        return Info(
            soc = soc,
            cores = cores(),
            gpu = gl?.renderer?.takeIf { it.isNotBlank() },
            glVersion = gl?.version?.takeIf { it.isNotBlank() },
            driver = driver ?: "system",
            android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
    }

    /** "1× 3.25 GHz · 3× 3.00 GHz · 4× 2.10 GHz", from cpufreq's max frequency per core. */
    private fun cores(): String? = runCatching {
        val freqs = (0 until 16).mapNotNull { i ->
            File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull()
        }
        if (freqs.isEmpty()) return null
        freqs.groupingBy { it }.eachCount().entries.sortedByDescending { it.key }
            .joinToString(" · ") { (khz, n) -> "$n× " + String.format(java.util.Locale.US, "%.2f GHz", khz / 1_000_000.0) }
    }.getOrNull()

    /**
     * Lines the core writes at boot that say how it set itself up on this GPU/CPU: whether Vulkan
     * framebuffer fetch is in-tile or the barrier fallback, dual-source blending, the CPU affinity
     * mask and prime-core split, ADPF, and the Vulkan driver rules that matched. Read from this
     * app's own log (no permission needed for one's own process); empty when the log has rotated.
     */
    fun coreLogLines(): List<String> = runCatching {
        val pid = android.os.Process.myPid()
        val p = ProcessBuilder("logcat", "-d", "-v", "brief", "--pid=$pid").redirectErrorStream(true).start()
        val keys = listOf("ANDROID_SAVESTATE_PATH", "ANDROID_LOADSTATE", "framebuffer_fetch", "dualSrc", "Affinity mode", "Prime core", "ADPF", "device rules", "Vulkan driver", "GL_RENDERER")
        val found = LinkedHashMap<String, String>()
        p.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val k = keys.firstOrNull { line.contains(it, ignoreCase = true) } ?: return@forEach
                found[k] = line.substringAfter("): ").trim().take(220)
            }
        }
        p.waitFor()
        found.values.toList()
    }.getOrDefault(emptyList())
}
