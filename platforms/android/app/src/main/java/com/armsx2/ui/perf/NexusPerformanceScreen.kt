package com.armsx2.ui.perf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.R
import com.armsx2.i18n.str
import com.armsx2.perf.NexusBenchmark
import com.armsx2.perf.NexusSystemInfo
import com.armsx2.ui.common.padFocusRing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Nexus2 → Settings → Nexus Performance. System state, live counters while a game runs, the
 * results of "Analisar desempenho" and what limits the game, only when the numbers show it.
 */
@Composable
fun NexusPerformanceScreen(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val settings = com.armsx2.ui.InGameOverlay.settingsState.value
    val info by produceState<NexusSystemInfo.Info?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { NexusSystemInfo.read(context, settings) }.getOrNull() }
    }
    NexusBenchmark.revision.value
    val results = remember(NexusBenchmark.revision.value) { NexusBenchmark.results() }
    var logLines by remember { mutableStateOf<List<String>?>(null) }
    var readLog by remember { mutableStateOf(0) }
    LaunchedEffect(readLog) {
        if (readLog > 0) logLines = withContext(Dispatchers.IO) { NexusSystemInfo.coreLogLines() }
    }
    LaunchedEffect(Unit) { NexusBenchmark.recoverIfInterrupted() }

    Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().widthIn(max = 720.dp).windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val shape = RoundedCornerShape(24.dp)
                Box(
                    Modifier.size(48.dp).padFocusRing(shape).clip(shape).background(colors.surface).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_nx_back), contentDescription = str("nexus.common.back"), tint = colors.onSurface) }
                Spacer(Modifier.size(14.dp))
                Text("Nexus Performance", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            }

            val na = str("bench.na")
            val i = info
            Group(
                str("bench.sys"),
                listOf(
                    str("bench.sys.soc") to (i?.soc ?: na),
                    str("bench.sys.cores") to (i?.cores ?: na),
                    str("bench.sys.gpu") to (i?.gpu ?: na),
                    "OpenGL ES" to (i?.glVersion ?: na),
                    str("bench.sys.driver") to (i?.driver?.let { if (it == "system") str("bench.sys.driver.system") else it } ?: na),
                    str("bench.sys.backend") to settings.output.renderer.replaceFirstChar { it.uppercase() },
                    str("bench.sys.profile") to str(com.armsx2.ui.settings.currentProfileKey(settings)),
                    "Android" to (i?.android ?: na),
                ),
            )

            LiveGroup()

            Section(str("bench.run.title"))
            Note(str("bench.run.how"))

            val latest = results.firstOrNull()
            Section(str("bench.why.title"))
            Card {
                Text(
                    latest?.verdict?.let { str(it) } ?: str("bench.why.unknown"),
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface,
                )
                if (latest != null) Text(
                    str("bench.why.basis").replace("{game}", latest.title),
                    fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                )
            }

            Section(str("bench.results"))
            if (results.isEmpty()) Note(str("bench.results.none"))
            results.forEach { ResultCard(it) }
            if (results.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                SmallAction(str("bench.results.clear")) { NexusBenchmark.clearResults() }
            }

            Section(str("bench.log"))
            Note(str("bench.log.desc"))
            Spacer(Modifier.height(8.dp))
            SmallAction(str("bench.log.read")) { readLog++ }
            logLines?.let { lines ->
                Spacer(Modifier.height(8.dp))
                Card {
                    if (lines.isEmpty()) Text(str("bench.log.empty"), fontSize = 13.sp, color = colors.onSurfaceVariant)
                    lines.forEach { Text(it, fontSize = 12.sp, color = colors.onSurface, modifier = Modifier.padding(vertical = 3.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LiveGroup() {
    val context = LocalContext.current
    val running = com.armsx2.runtime.MainActivityRuntime.eState.value == com.armsx2.EmuState.RUNNING
    if (!running) return
    var rows by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val na = str("bench.na")
    val kSpeed = str("bench.m.speed"); val kFps = str("bench.m.fps"); val kVps = str("bench.m.vps")
    val kFt = str("bench.m.frametime"); val kTemp = str("bench.m.temp"); val kTherm = str("bench.m.thermal")
    val thermalNames = listOf(str("bench.th.0"), str("bench.th.1"), str("bench.th.2"), str("bench.th.3"), str("bench.th.4"), str("bench.th.5"), str("bench.th.6"))
    LaunchedEffect(Unit) {
        while (true) {
            com.armsx2.Thermals.poll(context, 2000)
            fun f(v: Float?, unit: String, d: Int = 1) = v?.takeIf { it > 0f && it.isFinite() }?.let { String.format(Locale.US, "%.${d}f", it) + unit } ?: na
            rows = listOf(
                kSpeed to f(runCatching { kr.co.iefriends.pcsx2.NativeApp.getEmuSpeedPercent() }.getOrNull(), "%", 0),
                kFps to f(runCatching { kr.co.iefriends.pcsx2.NativeApp.getFPS() }.getOrNull(), ""),
                kVps to f(runCatching { kr.co.iefriends.pcsx2.NativeApp.getVPS() }.getOrNull(), ""),
                kFt to f(runCatching { kr.co.iefriends.pcsx2.NativeApp.getAverageFrameTime() }.getOrNull(), " ms", 2),
                kTemp to (com.armsx2.Thermals.format(com.armsx2.Thermals.cpu)?.let { "CPU $it" } ?: na),
                kTherm to (NexusBenchmark.thermalStatus(context)?.let { thermalNames.getOrElse(it) { it.toString() } } ?: na),
            )
            delay(1000)
        }
    }
    Group(str("bench.live"), rows)
}

@Composable
private fun ResultCard(r: NexusBenchmark.Result) {
    val colors = MaterialTheme.colorScheme
    val na = str("bench.na")
    val date = remember(r.at) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(r.at)) }
    val thermalNames = listOf(str("bench.th.0"), str("bench.th.1"), str("bench.th.2"), str("bench.th.3"), str("bench.th.4"), str("bench.th.5"), str("bench.th.6"))
    Spacer(Modifier.height(10.dp))
    Card {
        Text(r.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Text(
            str(if (r.kind == NexusBenchmark.Kind.Sustained) "bench.kind.sustained" else "bench.kind.compare") + " · " + date + " · " + r.renderer,
            fontSize = 12.sp, color = colors.onSurfaceVariant,
        )
        val base = r.runs.firstOrNull()
        if (!r.sameScene) Text(str("bench.notSameScene"), fontSize = 12.sp, color = com.armsx2.ui.nexus.NexusColors.Warn, modifier = Modifier.padding(top = 6.dp))
        r.runs.forEach { u ->
            Spacer(Modifier.height(12.dp))
            Text(str(u.label), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
            Text(u.detail, fontSize = 12.sp, color = colors.onSurfaceVariant)
            fun f(v: Float?, unit: String, d: Int = 1) = v?.let { String.format(Locale.US, "%.${d}f", it) + unit } ?: na
            fun delta(v: Float?, b: Float?): String =
                if (!r.sameScene || u === base || v == null || b == null || b == 0f) "" else String.format(Locale.US, "  (%+.1f%%)", (v - b) / b * 100f)
            Metric(str("bench.m.speed"), f(u.speedAvg, "%", 1) + delta(u.speedAvg, base?.speedAvg))
            Metric(str("bench.m.speedMin"), f(u.speedMin, "%", 0))
            Metric(str("bench.m.below"), f(u.belowFullPct, "%", 0))
            Metric(str("bench.m.fps"), f(u.fpsAvg, "") + delta(u.fpsAvg, base?.fpsAvg))
            Metric(str("bench.m.vps"), f(u.vpsAvg, ""))
            Metric(str("bench.m.frametime"), f(u.frameTimeAvg, " ms", 2) + " / " + f(u.frameTimeMax, " ms", 2))
            Metric(str("bench.m.low1"), na)
            Metric(str("bench.m.cpu"), f(u.cpuAvg, "%", 0))
            Metric(str("bench.m.gs"), f(u.gsAvg, "%", 0))
            Metric(str("bench.m.gpu"), f(u.gpuAvg, "%", 0))
            Metric(str("bench.m.temp"), f(u.cpuTempStart, "°", 0) + " → " + f(u.cpuTempEnd, "°", 0) + " · " + str("bench.m.battery") + " " + f(u.batteryTempEnd, "°", 1))
            Metric(str("bench.m.thermal"), u.thermalStatusEnd?.let { thermalNames.getOrElse(it) { it.toString() } } ?: na)
            Metric(str("bench.m.stability"), u.speedStdev?.let { "±" + String.format(Locale.US, "%.1f", it) + " pp" } ?: na)
        }
    }
}

@Composable
private fun Metric(k: String, v: String) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(k, fontSize = 13.sp, color = colors.onSurfaceVariant)
        Text(v, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 28.dp, bottom = 10.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) { content() }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padFocusRing(shape).clip(shape).background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun Group(title: String, rows: List<Pair<String, String>>) {
    val colors = MaterialTheme.colorScheme
    Section(title)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
            .padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        rows.forEachIndexed { i, (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(k, fontSize = 14.sp, color = colors.onSurfaceVariant)
                Text(v, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
            if (i < rows.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant.copy(alpha = 0.6f)))
        }
    }
}

/** Small pill over the running game while a test runs, and its outcome for a few seconds after. */
@Composable
fun NexusBenchmarkHud() {
    val p = NexusBenchmark.progress.value
    val outcome = NexusBenchmark.lastOutcome.value
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(outcome) {
        if (outcome != null) { delay(7000); NexusBenchmark.lastOutcome.value = null }
    }
    val text: String = when {
        p != null -> {
            val phase = str("bench.phase.${p.phase}")
            val label = if (p.label.isBlank()) "" else " · " + str(p.label)
            str("bench.hud.running").replace("{i}", p.step.toString()).replace("{n}", p.steps.toString()) + label + " · " + phase +
                (if (p.remainingSec > 0) " · ${p.remainingSec}s" else "")
        }
        outcome == "done" -> str("bench.hud.done")
        outcome != null -> str("bench.hud.aborted") + " · " + str("bench.abort." + outcome.substringAfter("aborted:"))
        else -> return
    }
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xE6141A27)).padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.widthIn(max = 520.dp)) {
                Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF4F5FA))
                if (p != null) Text(str("bench.hud.hands"), fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier.padding(start = 8.dp).size(30.dp).clip(shape)
                    .clickable { if (p != null) NexusBenchmark.cancel() else NexusBenchmark.lastOutcome.value = null },
                contentAlignment = Alignment.Center,
            ) { Text("✕", color = Color(0xFFF4F5FA), fontSize = 14.sp) }
        }
    }
}
