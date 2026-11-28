package com.armsx2.perf

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.mutableStateOf
import com.armsx2.EmuState
import com.armsx2.Thermals
import com.armsx2.config.ConfigStore
import com.armsx2.config.Settings
import com.armsx2.config.SettingsScope
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.InGameOverlay
import com.armsx2.ui.WindowImpl
import com.armsx2.ui.settings.applyTo
import com.armsx2.ui.settings.graphicsProfiles
import com.armsx2.ui.settings.matches
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.iefriends.pcsx2.NativeApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

/**
 * Nexus2 "ANALISAR DESEMPENHO": measures the running game under controlled settings, on the same
 * scene every time.
 *
 * How a comparison is made comparable:
 *  - A save state is written to a private file at the start (the user's slots are not touched),
 *    and every configuration starts by loading it, with no input, so each run plays the same frames.
 *  - Each run waits [WARMUP_MS] first (settings changes flush the JIT and may compile shaders),
 *    then samples the core's own counters every [SAMPLE_MS] for [MEASURE_MS].
 *  - The test settings are written as this game's overrides only, never to the global settings, and
 *    the game's original override blob is restored byte-for-byte afterwards. The blob is also kept
 *    in prefs while a run is in progress, so if the app dies mid-test, [recoverIfInterrupted] puts it
 *    back on the next start.
 *
 * Only counters the core actually reports are stored; anything it cannot report is saved as null
 * and shown as "Não disponível". Nothing is estimated.
 */
object NexusBenchmark {
    private const val WARMUP_MS = 6_000L
    private const val MEASURE_MS = 20_000L
    private const val SAMPLE_MS = 250L
    private const val SUSTAINED_MS = 10 * 60_000L
    private const val SUSTAINED_WINDOW_MS = 60_000L
    private const val PREF_RECOVERY = "nexus.bench.recovery"
    private const val PREF_RESULTS = "nexus.bench.results"
    private const val KEEP_RESULTS = 12
    private const val PREF_UNDO = "nexus.opt.undo."

    enum class Kind { Compare, Sustained, Optimize }

    data class Progress(val kind: Kind, val step: Int, val steps: Int, val label: String, val phase: String, val remainingSec: Int)

    /** Live progress for the HUD; null when idle. */
    val progress = mutableStateOf<Progress?>(null)
    /** Last finished/aborted message for the HUD ("done" / "aborted: …"). */
    val lastOutcome = mutableStateOf<String?>(null)
    /** Bumped when a result is stored, so the screens re-read. */
    val revision = mutableStateOf(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    // ---- results -------------------------------------------------------------------------

    data class Run(
        val label: String,
        val detail: String,
        val samples: Int,
        val speedAvg: Float?,
        val speedMin: Float?,
        val speedStdev: Float?,
        val belowFullPct: Float?,
        val fpsAvg: Float?,
        val vpsAvg: Float?,
        val frameTimeAvg: Float?,
        val frameTimeMax: Float?,
        val cpuAvg: Float?,
        val gsAvg: Float?,
        val gpuAvg: Float?,
        val cpuTempStart: Float?,
        val cpuTempEnd: Float?,
        val batteryTempEnd: Float?,
        val thermalStatusEnd: Int?,
        val headroomEnd: Float?,
    )

    data class Result(
        val at: Long,
        val kind: Kind,
        val title: String,
        val serial: String,
        val renderer: String,
        val gpu: String,
        val runs: List<Run>,
        val verdict: String?,
        /** False when the core refused a save state (e.g. a disc whose CRC reads 0): the runs then
         *  follow each other on a moving scene and are not directly comparable. */
        val sameScene: Boolean = true,
        /** Optimize only: the settings applied to the game (null = nothing changed). */
        val applied: String? = null,
    )

    fun results(): List<Result> = runCatching {
        val arr = JSONArray(MainActivityRuntime.prefs.getString(PREF_RESULTS, "[]"))
        (0 until arr.length()).mapNotNull { parseResult(arr.optJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun clearResults() {
        runCatching { MainActivityRuntime.prefs.edit().remove(PREF_RESULTS).apply() }
        revision.value++
    }

    // ---- entry points --------------------------------------------------------------------

    /** Compare the current settings with two of the Nexus2 profiles, ~1.5 min. */
    fun startCompare(context: Context) = start(context, Kind.Compare)

    /** Find this game's best MTVU / blending / resolution on one scene and apply it to the game. */
    fun startOptimize(context: Context) = start(context, Kind.Optimize)

    /** Whether [serial] has an "Optimize" result that can be undone. */
    fun canUndo(serial: String): Boolean =
        runCatching { MainActivityRuntime.prefs.contains(PREF_UNDO + serial) }.getOrDefault(false)

    /** Put back the game's settings from before "Optimize" (live if that game is running). */
    fun undoOptimize(serial: String) {
        val raw = runCatching { MainActivityRuntime.prefs.getString(PREF_UNDO + serial, null) }.getOrNull() ?: return
        val json = raw.takeIf { it != "null" }
        restoreOverrides(serial, json)
        if (InGameOverlay.currentSerial.value == serial && MainActivityRuntime.eState.value != EmuState.STOPPED) {
            val scope = InGameOverlay.settingsScope.value
            runCatching { apply(ConfigStore.resolveForGame(serial)) }
            restoreOverrides(serial, json)
            InGameOverlay.settingsScope.value = scope
        }
        runCatching { MainActivityRuntime.prefs.edit().remove(PREF_UNDO + serial).apply() }
        revision.value++
    }

    /** The current settings for 10 min: first minute vs. last minute (sustained vs. initial). */
    fun startSustained(context: Context) = start(context, Kind.Sustained)

    fun cancel() {
        job?.cancel()
    }

    /** If the app was killed while a test config was active, put the game's own settings back. */
    fun recoverIfInterrupted() {
        val raw = runCatching { MainActivityRuntime.prefs.getString(PREF_RECOVERY, null) }.getOrNull() ?: return
        if (running) return
        runCatching {
            val o = JSONObject(raw)
            restoreOverrides(o.getString("serial"), o.optString("overrides").takeIf { o.has("overrides") && it.isNotEmpty() })
        }
        runCatching { MainActivityRuntime.prefs.edit().remove(PREF_RECOVERY).apply() }
    }

    private fun start(context: Context, kind: Kind) {
        if (running) return
        recoverIfInterrupted()
        val app = context.applicationContext
        job = scope.launch {
            val outcome = runCatching { execute(app, kind) }
            progress.value = null
            lastOutcome.value = outcome.exceptionOrNull()?.let { e ->
                when (e) {
                    is kotlinx.coroutines.CancellationException -> "aborted:cancel"
                    is Abort -> "aborted:" + e.reason
                    else -> "aborted:error"
                }
            } ?: "done"
            outcome.exceptionOrNull()?.let { android.util.Log.w("NexusBenchmark", "aborted", it) }
        }
    }

    // ---- the run -------------------------------------------------------------------------

    /** [reason] is a string key suffix: bench.abort.<reason>. */
    private class Abort(val reason: String) : Exception(reason)

    private suspend fun execute(context: Context, kind: Kind) {
        // Started from the pause menu: give the menu a moment to close and the game to resume.
        val deadline = System.currentTimeMillis() + 4_000L
        while (System.currentTimeMillis() < deadline &&
            (MainActivityRuntime.eState.value != EmuState.RUNNING || WindowImpl.overlayVisible.value || WindowImpl.inGameScreen.value != null)
        ) delay(100)
        delay(500)
        val game = MainActivityRuntime.currentGame.value ?: throw Abort("noGame")
        val serial = InGameOverlay.currentSerial.value?.takeIf { it.isNotBlank() } ?: throw Abort("noGame")
        if (MainActivityRuntime.eState.value != EmuState.RUNNING) throw Abort("paused")

        val original: Settings = InGameOverlay.settingsState.value
        val originalScope = InGameOverlay.settingsScope.value
        val originalOverrides = ConfigStore.loadOverrides(serial)?.toString()
        // The core only loads a state whose FILE NAME is this game's slot name ("SERIAL (CRC).NN.p2s");
        // the folder is free. So: the slot-99 name, in a private folder, never the user's slots.
        val stateFile = run {
            val s = runCatching { NativeApp.getGameSerial() }.getOrNull().orEmpty().ifBlank { serial }
            val crc = runCatching { NativeApp.getGameCRC() }.getOrNull().orEmpty()
            File(File(context.cacheDir, "nexus_bench").apply { mkdirs() }, "$s ($crc).99.p2s")
        }

        val configs: List<Triple<String, String, Settings>> = when (kind) {
            Kind.Sustained -> listOf(Triple("bench.cfg.original", describe(original), original))
            Kind.Optimize -> emptyList()
            Kind.Compare -> {
                val profiles = graphicsProfiles()
                val balanced = profiles.first { it.labelKey == "nexus.profile.balanced" }
                val performance = profiles.first { it.labelKey == "nexus.profile.performance" }
                val compat = profiles.first { it.labelKey == "nexus.profile.compatibility" }
                // "Recommended" is the profile Nexus2 ships for the Mali-G720 (Balanced); if the game
                // already runs it, Performance takes its place. The numbers decide, not the label.
                val rec = if (balanced.matches(original)) performance else balanced
                val alt = listOf(performance, compat).first { it != rec && !it.matches(original) }
                listOf(
                    Triple("bench.cfg.original", describe(original), original),
                    Triple("bench.cfg.recommended", describe(rec.applyTo(original)), rec.applyTo(original)),
                    Triple("bench.cfg.alternative", describe(alt.applyTo(original)), alt.applyTo(original)),
                )
            }
        }

        // Keep the game's own overrides somewhere that survives a crash before anything changes.
        MainActivityRuntime.prefs.edit().putString(
            PREF_RECOVERY,
            JSONObject().put("serial", serial).apply { if (originalOverrides != null) put("overrides", originalOverrides) }.toString(),
        ).apply()

        val runs = mutableListOf<Run>()
        var sameScene = kind != Kind.Sustained
        var chosen: Settings = original
        try {
            progress.value = Progress(kind, 0, configs.size, "", "state", 0)
            if (kind != Kind.Sustained) {
                // No save state (the card is busy for too long, or the core cannot name a state for
                // this disc): carry on without it, and say the scenes differ instead of comparing them.
                if (!saveState(stateFile)) sameScene = false
            }

            if (kind == Kind.Optimize) {
                if (!sameScene) throw Abort("noScene")
                chosen = optimize(context, original, stateFile, runs)
            }
            configs.forEachIndexed { index, (label, detail, settings) ->
                if (index > 0 || kind == Kind.Compare) apply(settings)
                if (kind == Kind.Compare && sameScene && !loadState(stateFile)) {
                    // Refused (e.g. the disc CRC differs from the running ELF's): go on without the
                    // scene reset, and say the runs are not comparable rather than stopping.
                    sameScene = false
                }
                awaitRunning()
                if (kind == Kind.Sustained) {
                    runs += sustained(context, label, detail)
                } else {
                    waitRunning(WARMUP_MS) { left -> progress.value = Progress(kind, index + 1, configs.size, label, "warmup", left) }
                    runs += measure(context, label, detail, MEASURE_MS) { left -> progress.value = Progress(kind, index + 1, configs.size, label, "measure", left) }
                }
            }
        } finally {
            withContext(kotlinx.coroutines.NonCancellable) {
                // Original settings back, live, then the override blob exactly as it was.
                runCatching { if (kind != Kind.Sustained) apply(original) }
                runCatching { restoreOverrides(serial, originalOverrides) }
                InGameOverlay.settingsScope.value = originalScope
                runCatching { MainActivityRuntime.prefs.edit().remove(PREF_RECOVERY).apply() }
                if (kind != Kind.Sustained && sameScene && stateFile.isFile) runCatching { loadState(stateFile) }
                runCatching { stateFile.delete() }
            }
        }
        var applied: String? = null
        if (kind == Kind.Optimize && changed(original, chosen)) {
            // Keep the game's own overrides for "Undo", then write only the winning values for this
            // game (inherit everything else) and apply them live.
            MainActivityRuntime.prefs.edit().putString(PREF_UNDO + serial, originalOverrides ?: "null").apply()
            apply(chosen)
            InGameOverlay.settingsScope.value = originalScope
            applied = describe(chosen)
        }
        val verdict = if (kind == Kind.Optimize) (if (applied != null) "bench.opt.applied" else "bench.opt.kept")
            else verdict(kind, runs, configs.map { it.third }, sameScene)
        store(Result(System.currentTimeMillis(), kind, game.title, serial, original.output.renderer, com.armsx2.GpuInfo.rendererName().orEmpty(), runs, verdict, sameScene, applied))
    }

    // ---- Optimize: one variable at a time --------------------------------------------------

    private fun changed(a: Settings, b: Settings) =
        a.cpu.mtvu != b.cpu.mtvu || a.graphics.accurateBlendingUnit != b.graphics.accurateBlendingUnit ||
            kotlin.math.abs(a.output.upscaleFloat - b.output.upscaleFloat) > 0.01f

    /** Full speed: average ≥98.5% and under 97% no more than 5% of the time. */
    private fun ok(r: Run) = (r.speedAvg ?: 0f) >= 98.5f && (r.belowFullPct ?: 100f) <= 5f

    /** The game's own frame rate (frames it actually drew), the number the player sees. */
    private fun gameFps(r: Run) = r.vpsAvg ?: 0f

    /** [a] is clearly better than [b]: reaches full speed where [b] did not, or ≥3% more game FPS. */
    private fun better(a: Run, b: Run): Boolean = when {
        ok(a) && !ok(b) -> true
        !ok(a) && ok(b) -> false
        !ok(a) && !ok(b) -> (a.speedAvg ?: 0f) > (b.speedAvg ?: 0f) + 2f
        else -> gameFps(a) > gameFps(b) * 1.03f
    }

    /** [a] costs nothing visible against [b] (for a quality increase): full speed, FPS within 2%. */
    private fun notWorse(a: Run, b: Run): Boolean =
        ok(a) && gameFps(a) >= gameFps(b) * 0.98f &&
            ((a.frameTimeMax ?: 0f) <= (b.frameTimeMax ?: 0f) * 1.15f || b.frameTimeMax == null)

    private suspend fun optimize(context: Context, original: Settings, stateFile: File, runs: MutableList<Run>): Settings {
        val steps = 4
        var step = 0
        suspend fun trial(label: String, s: Settings): Run {
            step++
            apply(s)
            if (!loadState(stateFile)) throw Abort("load")
            awaitRunning()
            waitRunning(WARMUP_MS) { left -> progress.value = Progress(Kind.Optimize, step, steps, label, "warmup", left) }
            return measure(context, label, describe(s), MEASURE_MS) { left -> progress.value = Progress(Kind.Optimize, step, steps, label, "measure", left) }
                .also { runs += it }
        }
        var best = original
        var bestRun = trial("bench.opt.base", original)

        // 1. MTVU: helps some games, costs others (Tony Hawk's Downhill Jam: 54 → 37 FPS with it on).
        val mtvu = best.copy(cpu = best.cpu.copy(mtvu = !best.cpu.mtvu))
        trial("bench.opt.mtvu", mtvu).let { if (better(it, bestRun)) { best = mtvu; bestRun = it } }

        // 2. Blending above Basic: Medium caused speed dips in Yakuza that Basic removed.
        if (best.graphics.accurateBlendingUnit > 1) {
            val basic = best.copy(graphics = best.graphics.copy(accurateBlendingUnit = 1))
            trial("bench.opt.blend", basic).let { if (better(it, bestRun)) { best = basic; bestRun = it } }
        } else step++

        // 3. Resolution: one step up when the GPU has room, one step down when speed is short.
        val scale = best.output.upscaleFloat
        when {
            ok(bestRun) && (bestRun.gpuAvg ?: 100f) < 55f && scale < 3f -> {
                val up = best.copy(output = best.output.copy(upscaleFloat = kotlin.math.floor(scale + 1f)))
                trial("bench.opt.resUp", up).let { if (notWorse(it, bestRun)) { best = up; bestRun = it } }
            }
            !ok(bestRun) && scale > 1f -> {
                val down = best.copy(output = best.output.copy(upscaleFloat = kotlin.math.max(1f, kotlin.math.ceil(scale - 1f))))
                trial("bench.opt.resDown", down).let { if (better(it, bestRun)) { best = down; bestRun = it } }
            }
        }
        return best
    }

    /** The core refuses a save while the memory card is mid-write; try for a few seconds. */
    private suspend fun saveState(f: File): Boolean {
        repeat(25) {
            if (withContext(Dispatchers.IO) { runCatching { NativeApp.saveStateToPath(f.absolutePath) }.getOrDefault(false) }) return true
            delay(400)
        }
        return false
    }

    /** 1 = loaded, 0 = transient refusal (retry), -1 = permanent failure. */
    private suspend fun loadState(f: File): Boolean {
        repeat(10) {
            when (withContext(Dispatchers.IO) { runCatching { NativeApp.loadStateFromFile(f.absolutePath) }.getOrDefault(-1) }) {
                1 -> return true
                0 -> delay(300)
                else -> return false
            }
        }
        return false
    }

    /** Apply [s] to the running game as this game's overrides (never the global settings). */
    private fun apply(s: Settings) {
        InGameOverlay.settingsScope.value = SettingsScope.Game
        InGameOverlay.saveSettings(s)
    }

    private fun restoreOverrides(serial: String, json: String?) {
        if (json == null) ConfigStore.clearOverrides(serial) else ConfigStore.saveOverrides(serial, JSONObject(json))
        if (MainActivityRuntime.eState.value != EmuState.STOPPED) runCatching { NativeApp.commitSettings() }
    }

    /** Waits while the game runs; pausing, opening the menu or stopping aborts the test. */
    private suspend fun waitRunning(ms: Long, tick: (Int) -> Unit) {
        val end = System.currentTimeMillis() + ms
        while (true) {
            checkRunning()
            val left = end - System.currentTimeMillis()
            if (left <= 0) return
            tick(((left + 999) / 1000).toInt())
            delay(minOf(250L, left))
        }
    }

    /** A settings commit or a state load can pause the VM for a moment; wait for it to run again. */
    private suspend fun awaitRunning() {
        val end = System.currentTimeMillis() + 5_000L
        while (MainActivityRuntime.eState.value != EmuState.RUNNING && System.currentTimeMillis() < end) delay(100)
    }

    private var notRunningSince = 0L

    /** The user opening the menu aborts at once; the VM not running aborts after 3 s, since a
     *  settings commit or a state load parks it briefly on its own. */
    private fun checkRunning() {
        if (MainActivityRuntime.eState.value != EmuState.RUNNING) {
            val now = System.currentTimeMillis()
            if (notRunningSince == 0L) notRunningSince = now
            if (now - notRunningSince > 3_000L) throw Abort("paused")
        } else notRunningSince = 0L
        if (WindowImpl.overlayVisible.value || WindowImpl.inGameScreen.value != null) throw Abort("menu")
    }

    private suspend fun measure(context: Context, label: String, detail: String, ms: Long, tick: (Int) -> Unit): Run {
        val speed = ArrayList<Float>(); val fps = ArrayList<Float>(); val vps = ArrayList<Float>()
        val ft = ArrayList<Float>(); val cpu = ArrayList<Float>(); val gs = ArrayList<Float>(); val gpu = ArrayList<Float>()
        Thermals.poll(context, 0)
        val cpuStart = Thermals.cpu.takeIf { it != Thermals.NONE }
        val end = System.currentTimeMillis() + ms
        while (true) {
            checkRunning()
            val left = end - System.currentTimeMillis()
            if (left <= 0) break
            tick(((left + 999) / 1000).toInt())
            runCatching { NativeApp.getEmuSpeedPercent() }.getOrNull()?.takeIf { it > 0f && it.isFinite() }?.let(speed::add)
            runCatching { NativeApp.getFPS() }.getOrNull()?.takeIf { it > 0f && it.isFinite() }?.let(fps::add)
            runCatching { NativeApp.getVPS() }.getOrNull()?.takeIf { it > 0f && it.isFinite() }?.let(vps::add)
            runCatching { NativeApp.getAverageFrameTime() }.getOrNull()?.takeIf { it > 0f && it.isFinite() }?.let(ft::add)
            runCatching { NativeApp.getCpuThreadUsage() }.getOrNull()?.takeIf { it > 0f && it.isFinite() }?.let(cpu::add)
            runCatching { NativeApp.getGsThreadUsage() }.getOrNull()?.takeIf { it > 0f && it.isFinite() }?.let(gs::add)
            runCatching { NativeApp.getGpuUsage() }.getOrNull()?.takeIf { it > 0f && it.isFinite() }?.let(gpu::add)
            delay(SAMPLE_MS)
        }
        Thermals.poll(context, 0)
        val avg = speed.avgOrNull()
        return Run(
            label = label,
            detail = detail,
            samples = speed.size,
            speedAvg = avg,
            speedMin = speed.minOrNull(),
            speedStdev = avg?.let { a -> sqrt(speed.sumOf { ((it - a) * (it - a)).toDouble() } / speed.size).toFloat() },
            belowFullPct = if (speed.isEmpty()) null else speed.count { it < 97f } * 100f / speed.size,
            fpsAvg = fps.avgOrNull(),
            vpsAvg = vps.avgOrNull(),
            frameTimeAvg = ft.avgOrNull(),
            frameTimeMax = ft.maxOrNull(),
            cpuAvg = cpu.avgOrNull(),
            gsAvg = gs.avgOrNull(),
            gpuAvg = gpu.avgOrNull(),
            cpuTempStart = cpuStart,
            cpuTempEnd = Thermals.cpu.takeIf { it != Thermals.NONE },
            batteryTempEnd = batteryTemp(context),
            thermalStatusEnd = thermalStatus(context),
            headroomEnd = headroom(context),
        )
    }

    /** Ten minutes of the current settings: the first and last minute are reported separately. */
    private suspend fun sustained(context: Context, label: String, detail: String): List<Run> {
        val steps = 2
        val first = measure(context, label, detail + " · 0–1 min", SUSTAINED_WINDOW_MS) { left ->
            progress.value = Progress(Kind.Sustained, 1, steps, label, "measure", left + ((SUSTAINED_MS - SUSTAINED_WINDOW_MS) / 1000).toInt())
        }
        waitRunning(SUSTAINED_MS - 2 * SUSTAINED_WINDOW_MS) { left ->
            progress.value = Progress(Kind.Sustained, 1, steps, label, "soak", left + (SUSTAINED_WINDOW_MS / 1000).toInt())
        }
        val last = measure(context, label, detail + " · 9–10 min", SUSTAINED_WINDOW_MS) { left ->
            progress.value = Progress(Kind.Sustained, 2, steps, label, "measure", left)
        }
        return listOf(first.copy(label = "bench.window.first"), last.copy(label = "bench.window.last"))
    }

    // ---- evidence-based verdict ---------------------------------------------------------

    /**
     * A message key (+ args after '|') for "what is limiting the game", only when the numbers show
     * it; otherwise "bench.why.unknown". Thresholds are the core's own definitions: speed under 97%
     * means the game is not running at full speed; a thread at ≥95% means it has no time left.
     */
    private fun verdict(kind: Kind, runs: List<Run>, configs: List<Settings>, sameScene: Boolean): String {
        val base = runs.firstOrNull() ?: return "bench.why.unknown"
        if (kind == Kind.Sustained && runs.size == 2) {
            val a = runs[0].speedAvg; val b = runs[1].speedAvg
            val hot = (runs[1].thermalStatusEnd ?: 0) >= PowerManager.THERMAL_STATUS_MODERATE || (runs[1].headroomEnd ?: 0f) >= 0.9f
            if (a != null && b != null && b < a - 3f && hot) return "bench.why.thermal"
            if (a != null && b != null && b < a - 3f) return "bench.why.drop"
        }
        val speed = base.speedAvg ?: return "bench.why.unknown"
        if (speed >= 97f && (base.belowFullPct ?: 100f) < 10f) {
            val f = base.vpsAvg ?: 0f
            return when {
                f in 29f..31f -> "bench.why.cap30"
                f in 24f..26f -> "bench.why.cap25"
                else -> "bench.why.fullSpeed"
            }
        }
        if ((base.thermalStatusEnd ?: 0) >= PowerManager.THERMAL_STATUS_SEVERE) return "bench.why.thermal"
        if ((base.cpuAvg ?: 0f) >= 95f) return "bench.why.cpu"
        if ((base.gpuAvg ?: 0f) >= 95f || (base.gsAvg ?: 0f) >= 95f) return "bench.why.gpu"
        // Resolution: a run at lower internal resolution, otherwise similar, clearly faster.
        if (kind == Kind.Compare && sameScene) {
            val baseScale = configs[0].output.upscaleFloat
            runs.drop(1).forEachIndexed { i, r ->
                val s = configs[i + 1].output.upscaleFloat
                val rs = r.speedAvg
                if (s < baseScale && rs != null && rs >= speed + 10f) return "bench.why.resolution"
            }
        }
        return "bench.why.unknown"
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun describe(s: Settings): String {
        val blend = listOf("Minimum", "Basic", "Medium", "High", "Full", "Maximum").getOrElse(s.graphics.accurateBlendingUnit) { s.graphics.accurateBlendingUnit.toString() }
        val scale = if (s.output.upscaleFloat % 1f == 0f) "${s.output.upscaleFloat.toInt()}x" else "${s.output.upscaleFloat}x"
        return "${s.output.renderer} · $scale · blend $blend · MTVU ${if (s.cpu.mtvu) "on" else "off"}"
    }

    private fun List<Float>.avgOrNull(): Float? = if (isEmpty()) null else (sumOf { it.toDouble() } / size).toFloat()

    private fun batteryTemp(context: Context): Float? = runCatching {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (t == Int.MIN_VALUE) null else t / 10f
    }.getOrNull()

    fun thermalStatus(context: Context): Int? = runCatching {
        if (Build.VERSION.SDK_INT < 29) null
        else (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
    }.getOrNull()

    fun headroom(context: Context): Float? = runCatching {
        if (Build.VERSION.SDK_INT < 30) null
        else (context.getSystemService(Context.POWER_SERVICE) as PowerManager).getThermalHeadroom(10).takeIf { it.isFinite() }
    }.getOrNull()

    private fun store(r: Result) {
        val arr = runCatching { JSONArray(MainActivityRuntime.prefs.getString(PREF_RESULTS, "[]")) }.getOrDefault(JSONArray())
        val out = JSONArray().put(toJson(r))
        for (i in 0 until minOf(arr.length(), KEEP_RESULTS - 1)) out.put(arr.get(i))
        MainActivityRuntime.prefs.edit().putString(PREF_RESULTS, out.toString()).apply()
        revision.value++
    }

    private fun JSONObject.putN(k: String, v: Float?): JSONObject = if (v == null) this else put(k, v.toDouble())
    private fun JSONObject.f(k: String): Float? = if (has(k)) optDouble(k).toFloat().takeIf { it.isFinite() } else null

    private fun toJson(r: Result) = JSONObject()
        .put("at", r.at).put("kind", r.kind.name).put("title", r.title).put("serial", r.serial)
        .put("renderer", r.renderer).put("gpu", r.gpu).put("verdict", r.verdict).put("sameScene", r.sameScene).apply { r.applied?.let { put("applied", it) } }
        .put("runs", JSONArray().apply {
            r.runs.forEach { u ->
                put(JSONObject().put("label", u.label).put("detail", u.detail).put("samples", u.samples)
                    .putN("speedAvg", u.speedAvg).putN("speedMin", u.speedMin).putN("speedStdev", u.speedStdev)
                    .putN("belowFullPct", u.belowFullPct).putN("fpsAvg", u.fpsAvg).putN("vpsAvg", u.vpsAvg)
                    .putN("frameTimeAvg", u.frameTimeAvg).putN("frameTimeMax", u.frameTimeMax)
                    .putN("cpuAvg", u.cpuAvg).putN("gsAvg", u.gsAvg).putN("gpuAvg", u.gpuAvg)
                    .putN("cpuTempStart", u.cpuTempStart).putN("cpuTempEnd", u.cpuTempEnd)
                    .putN("batteryTempEnd", u.batteryTempEnd).putN("headroomEnd", u.headroomEnd)
                    .apply { u.thermalStatusEnd?.let { put("thermalStatusEnd", it) } })
            }
        })

    private fun parseResult(o: JSONObject?): Result? {
        o ?: return null
        val runs = o.optJSONArray("runs") ?: return null
        return Result(
            at = o.optLong("at"),
            kind = runCatching { Kind.valueOf(o.optString("kind")) }.getOrDefault(Kind.Compare),
            title = o.optString("title"),
            serial = o.optString("serial"),
            renderer = o.optString("renderer"),
            gpu = o.optString("gpu"),
            verdict = o.optString("verdict").takeIf { it.isNotEmpty() },
            sameScene = o.optBoolean("sameScene", true),
            applied = o.optString("applied").takeIf { it.isNotEmpty() },
            runs = (0 until runs.length()).mapNotNull { i ->
                val u = runs.optJSONObject(i) ?: return@mapNotNull null
                Run(
                    u.optString("label"), u.optString("detail"), u.optInt("samples"),
                    u.f("speedAvg"), u.f("speedMin"), u.f("speedStdev"), u.f("belowFullPct"),
                    u.f("fpsAvg"), u.f("vpsAvg"), u.f("frameTimeAvg"), u.f("frameTimeMax"),
                    u.f("cpuAvg"), u.f("gsAvg"), u.f("gpuAvg"),
                    u.f("cpuTempStart"), u.f("cpuTempEnd"), u.f("batteryTempEnd"),
                    if (u.has("thermalStatusEnd")) u.optInt("thermalStatusEnd") else null,
                    u.f("headroomEnd"),
                )
            },
        )
    }
}
