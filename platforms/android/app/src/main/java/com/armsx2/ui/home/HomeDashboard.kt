package com.armsx2.ui.home

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.armsx2.EnglishTitles
import com.armsx2.Favorites
import com.armsx2.GameInfo
import com.armsx2.PlayTime
import com.armsx2.R
import com.armsx2.i18n.str
import com.armsx2.navigation.AppRoute
import com.armsx2.navigation.MainTab
import com.armsx2.navigation.UiNavigator
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.common.GameCoverArt
import com.armsx2.ui.common.padFocusRing
import com.armsx2.ui.common.rememberInitialFocusRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/**
 * Nexus2: the "Início" section. Everything on it is read from the app or the device; nothing is
 * a sample. Shares [HomeViewModel] (and so the scanned library) with the Games section.
 */
@Composable
fun HomeDashboard(viewModel: HomeViewModel = viewModel()) {
    val state = viewModel.state.value
    val directories = MainActivityRuntime.romsDirs.value
    val nativeReady = MainActivityRuntime.nativeReady.value
    // Same trigger as the library, so opening the app on Home still scans the game folders.
    LaunchedEffect(directories, nativeReady) { viewModel.load(directories, nativeReady) }

    val colors = MaterialTheme.colorScheme
    val forceEn = EnglishTitles.enabled.value
    PlayTime.revision.value // re-read play time when a session ends
    val shown = state.allGames.filter { !com.armsx2.HiddenGames.isHidden(it) && com.armsx2.ArcadeOnly.shows(it) }
    val recents = state.recentGames.filter { !com.armsx2.HiddenGames.isHidden(it) }
    Favorites.keys.value // subscribe
    val favorites = Favorites.of(shown)
    val firstFocus = rememberInitialFocusRequester(state.initialized)

    Box(
        Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.statusBars),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.fillMaxSize().widthIn(max = 840.dp).verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Header(viewModel)

            val last = recents.firstOrNull()
            when {
                last != null -> {
                    SectionLabel("nexus.home.continue")
                    ContinueCard(last, forceEn, Modifier.focusRequester(firstFocus)) { viewModel.launch(last) }
                }
                state.initialized && shown.isEmpty() -> {
                    SectionLabel("nexus.home.start")
                    EmptyLibraryCard(Modifier.focusRequester(firstFocus))
                }
            }

            val moreRecents = recents.drop(1)
            if (moreRecents.isNotEmpty()) {
                SectionLabel("nexus.home.recent", actionKey = "nexus.home.seeAll") { UiNavigator.selectTab(MainTab.Library) }
                CoverRow(moreRecents.take(10), forceEn) { viewModel.launch(it) }
            }

            SectionLabel("nexus.home.favorites")
            if (favorites.isEmpty()) {
                Text(
                    str("nexus.home.favorites.empty"),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            } else {
                CoverRow(favorites.take(12), forceEn) { viewModel.launch(it) }
            }

            SectionLabel("nexus.home.system")
            SystemCard(libraryCount = shown.size, duplicates = state.duplicateCount)

            SectionLabel("nexus.home.quick")
            QuickRow()
        }
    }
}

@Composable
private fun Header(viewModel: HomeViewModel) {
    val colors = MaterialTheme.colorScheme
    val greetingKey = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "nexus.home.greeting.morning"
            in 12..17 -> "nexus.home.greeting.afternoon"
            else -> "nexus.home.greeting.evening"
        }
    }
    val placeholder = str("games.search.placeholder")
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Image(
            painterResource(R.drawable.nexus_logo),
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(str(greetingKey), fontSize = 13.sp, color = colors.onSurfaceVariant)
            Text("Nexus2", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        }
        RoundIconButton(R.drawable.ic_nx_search, str("nexus.home.search")) {
            UiNavigator.selectTab(MainTab.Library)
            LibraryKeyboard.open(viewModel.state.value.query, viewModel::setQuery, placeholder)
        }
    }
}

@Composable
private fun SectionLabel(key: String, actionKey: String? = null, onAction: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            str(key).uppercase(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (actionKey != null && onAction != null) {
            val shape = RoundedCornerShape(10.dp)
            Text(
                str(actionKey),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
                modifier = Modifier.padFocusRing(shape).clip(shape).clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ContinueCard(game: GameInfo, forceEn: Boolean, playModifier: Modifier, onPlay: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val hasSaves by produceState(initialValue = false, game.serial) {
        value = withContext(Dispatchers.IO) {
            runCatching { com.armsx2.ui.saves.SaveCatalog.hasSaves(context, game.serial) }.getOrDefault(false)
        }
    }
    val played = PlayTime.formatPlayed(PlayTime.playedSeconds(game.serial))
    val details = listOfNotNull(game.region, played.takeIf { it.isNotEmpty() }?.let { str("nexus.home.played").replace("{time}", it) })
        .joinToString(" · ")
    val last = PlayTime.formatLastPlayed(PlayTime.lastPlayedMillis(game.serial))
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(colors.surface).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GameCoverArt(game, Modifier.width(112.dp).aspectRatio(0.7f))
        Column(Modifier.weight(1f).heightIn(min = 160.dp)) {
            Text(
                game.displayTitle(forceEn),
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 24.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = colors.onSurface,
            )
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(details, fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
            if (last.isNotEmpty()) {
                Text(str("nexus.home.lastPlayed").replace("{date}", last), fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(str("action.play").uppercase(), playModifier, onPlay)
            if (hasSaves) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton(str("nexus.home.fromSave")) {
                    UiNavigator.openFrom(AppRoute.SaveManager(focusSerial = game.serial))
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(colors.surface).padding(20.dp),
    ) {
        Text(str("nexus.home.empty.title"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(str("nexus.home.empty.body"), fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(str("nexus.home.addGames"), modifier) { MainActivityRuntime.reopenSetup() }
    }
}

@Composable
private fun CoverRow(games: List<GameInfo>, forceEn: Boolean, onOpen: (GameInfo) -> Unit) {
    val colors = MaterialTheme.colorScheme
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(games, key = { it.uri.toString() }) { game ->
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier.width(104.dp).padFocusRing(shape).clip(shape).clickable { onOpen(game) },
            ) {
                GameCoverArt(game, Modifier.fillMaxWidth().aspectRatio(0.7f))
                Text(
                    game.displayTitle(forceEn),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.onBackground,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/** Device numbers, re-read every few seconds while Home is on screen. */
private data class DeviceSnapshot(
    val ramUsed: Float,
    val ramTotalGb: Float,
    val storageUsed: Float,
    val storageFreeGb: Float,
    val batteryC: Float?,
)

private fun readDevice(context: Context): DeviceSnapshot {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val ramUsed = if (mi.totalMem > 0) (mi.totalMem - mi.availMem).toFloat() / mi.totalMem else 0f
    val fs = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
    val total = fs?.totalBytes ?: 0L
    val free = fs?.availableBytes ?: 0L
    val battery = runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }?.div(10f)
    }.getOrNull()
    return DeviceSnapshot(
        ramUsed = ramUsed.coerceIn(0f, 1f),
        ramTotalGb = mi.totalMem / 1_073_741_824f,
        storageUsed = if (total > 0) ((total - free).toFloat() / total).coerceIn(0f, 1f) else 0f,
        storageFreeGb = free / 1_073_741_824f,
        batteryC = battery,
    )
}

@Composable
private fun SystemCard(libraryCount: Int, duplicates: Int) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var snap by remember { mutableStateOf<DeviceSnapshot?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            snap = withContext(Dispatchers.IO) { runCatching { readDevice(context) }.getOrNull() }
            delay(5_000)
        }
    }
    val biosPath = MainActivityRuntime.bios.value
    val biosFile = remember(biosPath) { biosPath?.let(::File)?.takeIf { it.isFile } }
    val chip = remember {
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } else null
        soc ?: Build.HARDWARE
    }
    val gpu = remember { runCatching { com.armsx2.GpuInfo.rendererName() }.getOrNull() }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(colors.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padFocusRing(shape).clip(shape)
                .clickable { UiNavigator.openFrom(AppRoute.BiosManager()) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(10.dp).clip(RoundedCornerShape(5.dp))
                    .background(if (biosFile != null) Color(0xFF3DD68C) else Color(0xFFFFB454)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                str(if (biosFile != null) "nexus.home.bios.ready" else "nexus.home.bios.missing"),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                biosFile?.nameWithoutExtension ?: str("nexus.home.bios.choose"),
                fontSize = 13.sp,
                color = if (biosFile != null) colors.onSurfaceVariant else colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 170.dp),
            )
        }
        val s = snap
        if (s != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                UsageBar(str("nexus.home.ram").replace("{gb}", "%.0f".format(s.ramTotalGb)), s.ramUsed, Modifier.weight(1f))
                UsageBar(str("nexus.home.storage").replace("{gb}", "%.0f".format(s.storageFreeGb)), s.storageUsed, Modifier.weight(1f))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant.copy(alpha = 0.5f)))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoLine(str("nexus.home.chip"), listOfNotNull(chip, gpu).joinToString(" · "))
            s?.batteryC?.let { InfoLine(str("nexus.home.battery"), "%.0f °C".format(it)) }
            InfoLine(
                str("nexus.home.library"),
                str("nexus.home.library.count").replace("{n}", libraryCount.toString()) +
                    if (duplicates > 0) " · " + str("nexus.home.library.dups").replace("{n}", duplicates.toString()) else "",
            )
        }
    }
}

@Composable
private fun UsageBar(label: String, fraction: Float, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Text(label, fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(9.dp)).background(colors.surfaceVariant)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceAtLeast(0.12f))
                    .clip(RoundedCornerShape(9.dp)).background(colors.primary),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "${(fraction * 100).toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimary,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private data class Quick(val key: String, @DrawableRes val icon: Int, val open: () -> Unit)

@Composable
private fun QuickRow() {
    val colors = MaterialTheme.colorScheme
    val items = listOf(
        Quick("nexus.home.quick.add", R.drawable.ic_nx_folder) { MainActivityRuntime.reopenSetup() },
        Quick("nexus.home.quick.bios", R.drawable.ic_nx_disc) { UiNavigator.openFrom(AppRoute.BiosManager()) },
        Quick("nexus.home.quick.cards", R.drawable.ic_nx_card) { UiNavigator.openFrom(AppRoute.MemoryCardManager()) },
        Quick("nexus.home.quick.saves", R.drawable.ic_nx_save) { UiNavigator.openFrom(AppRoute.SaveManager()) },
    )
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { q ->
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.weight(1f).height(88.dp).padFocusRing(shape).clip(shape).background(colors.surface)
                    .clickable(onClick = q.open),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(colors.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(q.icon), contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(str(q.key), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.fillMaxWidth().height(48.dp).padFocusRing(shape).clip(shape).background(colors.primary)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_nx_play), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = colors.onPrimary)
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.fillMaxWidth().height(44.dp).padFocusRing(shape).clip(shape).background(colors.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
    }
}

@Composable
private fun RoundIconButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.size(48.dp).padFocusRing(shape).clip(shape).background(colors.surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = colors.onSurface, modifier = Modifier.size(22.dp))
    }
}
