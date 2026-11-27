package com.armsx2.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.BuildConfig
import com.armsx2.R
import com.armsx2.Thermals
import com.armsx2.i18n.I18n
import com.armsx2.i18n.str
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.common.padFocusRing
import com.armsx2.ui.common.rememberInitialFocusRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Nexus2: Tools → Diagnostics. Shows what the device and app report (nothing estimated), and
 * gathers the existing problem-report flows: a new report, and the history of sent ones.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var showReports by remember { mutableStateOf(false) }
    if (showReports) com.armsx2.ui.bugreport.MyReportsModal(onClose = { showReports = false })

    val gl = remember { runCatching { com.armsx2.GpuInfo.glStrings() }.getOrNull() }
    val device = remember {
        listOf(
            I18n.get("nexus.diag.model") to "${Build.MANUFACTURER} ${Build.MODEL}",
            I18n.get("nexus.diag.android") to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            I18n.get("nexus.diag.chip") to runCatching { com.armsx2.DeviceTier.socModel() }.getOrDefault(Build.HARDWARE),
            I18n.get("nexus.diag.cores") to runCatching { com.armsx2.DeviceTier.coreCount().toString() }.getOrDefault("—"),
            I18n.get("nexus.diag.gpu") to (gl?.renderer ?: "—"),
            I18n.get("nexus.diag.gl") to (gl?.version ?: "—"),
        )
    }
    val app = remember {
        listOf(
            I18n.get("nexus.diag.version") to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            I18n.get("nexus.diag.package") to BuildConfig.APPLICATION_ID,
            I18n.get("nexus.diag.data") to runCatching { MainActivityRuntime.assetCopyRoot(context) }.getOrDefault("—"),
        )
    }
    var temps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (true) {
            temps = withContext(Dispatchers.IO) {
                runCatching {
                    Thermals.poll(context, 1_000)
                    listOfNotNull(
                        Thermals.format(Thermals.cpu)?.let { I18n.get("nexus.diag.tempCpu") to "${it}C" },
                        Thermals.format(Thermals.gpu)?.let { I18n.get("nexus.diag.tempGpu") to "${it}C" },
                        Thermals.format(Thermals.battery)?.let { I18n.get("nexus.diag.tempBattery") to "${it}C" },
                    )
                }.getOrDefault(emptyList())
            }
            delay(3_000)
        }
    }
    // Nexus2: live figures from the core while a game is open (all read 0 with no VM, so the block
    // is hidden then rather than showing zeros).
    var live by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (true) {
            live = runCatching {
                val running = MainActivityRuntime.eState.value != com.armsx2.EmuState.STOPPED &&
                    kr.co.iefriends.pcsx2.NativeApp.hasActiveVM()
                if (!running) emptyList() else {
                    val fps = kr.co.iefriends.pcsx2.NativeApp.getFPS()
                    val vps = kr.co.iefriends.pcsx2.NativeApp.getVPS()
                    val speed = kr.co.iefriends.pcsx2.NativeApp.getEmuSpeedPercent()
                    val frameMs = kr.co.iefriends.pcsx2.NativeApp.getAverageFrameTime()
                    val gpu = kr.co.iefriends.pcsx2.NativeApp.getGpuUsage()
                    val s = com.armsx2.ui.InGameOverlay.settingsState.value
                    val mi = android.app.ActivityManager.MemoryInfo().also {
                        (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(it)
                    }
                    listOf(
                        I18n.get("nexus.diag.fps") to "%.0f".format(fps),
                        I18n.get("nexus.diag.vps") to "%.0f".format(vps),
                        I18n.get("nexus.diag.speed") to "%.0f%%".format(speed),
                        I18n.get("nexus.diag.frametime") to "%.1f ms".format(frameMs),
                        I18n.get("nexus.diag.gpuUsage") to "%.0f%%".format(gpu),
                        I18n.get("nexus.diag.backend") to s.output.renderer.replaceFirstChar { it.uppercase() },
                        I18n.get("nexus.diag.resolution") to "%sx".format(
                            MainActivityRuntime.upscale.value.toString().removeSuffix(".0"),
                        ),
                        I18n.get("nexus.diag.memory") to "%.1f / %.1f GB".format(
                            (mi.totalMem - mi.availMem) / 1_073_741_824f, mi.totalMem / 1_073_741_824f,
                        ),
                    )
                }
            }.getOrDefault(emptyList())
            delay(1_000)
        }
    }
    val firstFocus = rememberInitialFocusRequester()

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
                ) {
                    Icon(painterResource(R.drawable.ic_nx_back), contentDescription = str("nexus.common.back"), tint = colors.onSurface)
                }
                Spacer(Modifier.size(14.dp))
                Text(str("nexus.settings.group.diagnostics"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            }
            Text(str("nexus.diag.intro"), fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant)

            Spacer(Modifier.height(18.dp))
            ActionRow(str("nexus.diag.report"), str("nexus.diag.report.desc"), Modifier.focusRequester(firstFocus)) {
                com.armsx2.ui.bugreport.BugReportFlow.openGeneral(context)
            }
            Spacer(Modifier.height(10.dp))
            ActionRow(str("nexus.diag.history"), str("nexus.diag.history.desc")) { showReports = true }
            Spacer(Modifier.height(10.dp))
            ActionRow(str("nexus.diag.copy"), str("nexus.diag.copy.desc")) {
                copyToClipboard(context, (device + app + temps).joinToString("\n") { "${it.first}: ${it.second}" })
            }

            Spacer(Modifier.height(10.dp))
            ActionRow(str("nexus.diag.osd"), str("nexus.diag.osd.desc")) {
                com.armsx2.navigation.UiNavigator.openFrom(
                    com.armsx2.navigation.AppRoute.Settings(com.armsx2.navigation.SettingsCategory.OnScreen, group = "Diagnostics"),
                )
            }
            if (live.isNotEmpty()) Group("nexus.diag.live", live)
            else Text(str("nexus.diag.live.none"), fontSize = 13.sp, lineHeight = 18.sp,
                color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp))
            Group("nexus.diag.device", device + temps)
            Group("nexus.diag.app", app)
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Nexus2", text))
        Toast.makeText(context, I18n.get("nexus.diag.copied"), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun Group(titleKey: String, rows: List<Pair<String, String>>) {
    val colors = MaterialTheme.colorScheme
    Text(
        str(titleKey).uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)
            .padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        rows.forEachIndexed { i, (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(k, fontSize = 14.sp, color = colors.onSurfaceVariant)
                Text(v, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            if (i < rows.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant.copy(alpha = 0.6f)))
        }
    }
}

@Composable
private fun ActionRow(title: String, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().padFocusRing(shape).clip(shape).background(colors.surface).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(desc, fontSize = 13.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_nx_chevron), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
