package com.armsx2.ui.home

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.armsx2.EnglishTitles
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
import com.armsx2.ui.nexus.NexusChip
import com.armsx2.ui.nexus.NexusColors
import com.armsx2.ui.nexus.NexusCompatChip
import com.armsx2.ui.nexus.NexusDivider
import com.armsx2.ui.nexus.NexusGameBackdrop
import com.armsx2.ui.nexus.NexusIconButton
import com.armsx2.ui.nexus.NexusPrimaryButton
import com.armsx2.ui.nexus.NexusRow
import com.armsx2.ui.nexus.NexusSectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Nexus2 "Início": built around the last game played. Every value comes from the app (library,
 * PlayTime, save folders, selected BIOS); a section with nothing real to show is left out.
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
    val last = recents.firstOrNull()
    val firstFocus = rememberInitialFocusRequester(state.initialized, last?.uri)

    Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 840.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            if (last != null) {
                ContinueHero(last, forceEn, viewModel, Modifier.focusRequester(firstFocus)) { viewModel.launch(last) }
            } else {
                Header(viewModel, Modifier.windowInsetsPadding(WindowInsets.statusBars))
                if (state.initialized) EmptyHero(libraryEmpty = shown.isEmpty(), Modifier.focusRequester(firstFocus))
            }

            val moreRecents = recents.drop(1)
            if (moreRecents.isNotEmpty()) {
                NexusSectionHeader(str("nexus.home.recent"), action = str("nexus.home.seeLibrary")) {
                    UiNavigator.selectTab(MainTab.Library)
                }
                RecentRow(moreRecents.take(10), forceEn)
            }

            NexusSectionHeader(str("nexus.home.system"))
            SystemStatus(state, shown.size)

            RecentActivity(recents, forceEn)
        }
    }
}

@Composable
private fun Header(viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val placeholder = str("games.search.placeholder")
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.foundation.Image(
            painterResource(R.drawable.nexus_logo),
            contentDescription = null,
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)),
        )
        Text("Nexus2", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.weight(1f))
        NexusIconButton(R.drawable.ic_nx_search, str("nexus.home.search"), translucent = true) {
            UiNavigator.selectTab(MainTab.Library)
            LibraryKeyboard.open(viewModel.state.value.query, viewModel::setQuery, placeholder)
        }
        NexusIconButton(R.drawable.ic_nx_settings, str("nexus.nav.settings"), translucent = true) {
            UiNavigator.selectTab(MainTab.Settings)
        }
    }
}

/** The main area: the last game's art, its last session and play time, CONTINUE PLAYING. */
@Composable
private fun ContinueHero(game: GameInfo, forceEn: Boolean, viewModel: HomeViewModel, playModifier: Modifier, onPlay: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val played = PlayTime.formatPlayed(PlayTime.playedSeconds(game.serial))
    val lastSession = PlayTime.formatLastPlayed(PlayTime.lastPlayedMillis(game.serial))
    NexusGameBackdrop(game, Modifier.fillMaxWidth()) {
        Column {
            Header(viewModel, Modifier.windowInsetsPadding(WindowInsets.statusBars))
            Text(
                str("nexus.home.continue").uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                color = colors.primary,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val shape = RoundedCornerShape(14.dp)
                GameCoverArt(
                    game,
                    Modifier.width(150.dp).aspectRatio(0.7f).padFocusRing(shape).clip(shape)
                        .clickable { UiNavigator.openFrom(AppRoute.GameDetail(game)) },
                )
                Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                    Text(
                        game.displayTitle(forceEn),
                        fontSize = 24.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.onBackground,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (lastSession.isNotEmpty()) {
                        Text(str("nexus.home.lastSession").replace("{date}", lastSession), fontSize = 14.sp, color = colors.onSurfaceVariant)
                    }
                    if (played.isNotEmpty()) {
                        Text(str("nexus.home.timePlayed").replace("{time}", played), fontSize = 14.sp, color = colors.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        game.region?.let { NexusChip(it) }
                        NexusCompatChip(game.compatibility)
                    }
                }
            }
            NexusPrimaryButton(
                str("nexus.home.continueButton"),
                playModifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                onClick = onPlay,
            )
        }
    }
}

@Composable
private fun EmptyHero(libraryEmpty: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp)) {
        Text(
            str(if (libraryEmpty) "nexus.home.empty.title" else "nexus.home.noHistory.title"),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            str(if (libraryEmpty) "nexus.home.empty.body" else "nexus.home.noHistory.body"),
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        if (libraryEmpty) {
            NexusPrimaryButton(str("nexus.home.addGames"), modifier, icon = R.drawable.ic_nx_folder) { MainActivityRuntime.reopenSetup() }
        } else {
            NexusPrimaryButton(str("nexus.home.openLibrary"), modifier, icon = R.drawable.ic_nx_games) { UiNavigator.selectTab(MainTab.Library) }
        }
    }
}

@Composable
private fun RecentRow(games: List<GameInfo>, forceEn: Boolean) {
    val colors = MaterialTheme.colorScheme
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(games, key = { it.uri.toString() }) { game ->
            val shape = RoundedCornerShape(14.dp)
            Column(Modifier.width(112.dp).padFocusRing(shape).clip(shape).clickable { UiNavigator.openFrom(AppRoute.GameDetail(game)) }) {
                GameCoverArt(game, Modifier.fillMaxWidth().aspectRatio(0.7f))
                Text(
                    game.displayTitle(forceEn),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    lineHeight = 17.sp,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.onBackground,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
        }
        item(key = "all") {
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier.width(112.dp).aspectRatio(0.7f).padFocusRing(shape).clip(shape).background(colors.surface)
                    .clickable { UiNavigator.selectTab(MainTab.Library) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_nx_chevron), contentDescription = null, tint = colors.primary, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(str("nexus.home.all"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                }
            }
        }
    }
}

/** Compact lines, no cards: BIOS, games, library state, last check. */
@Composable
private fun SystemStatus(state: HomeUiState, gameCount: Int) {
    val biosPath = MainActivityRuntime.bios.value
    val biosOk = biosPath?.let { File(it).isFile } == true
    val lastScan = runCatching { MainActivityRuntime.prefs.getLong("nexus.library.lastScanAt", 0L) }.getOrDefault(0L)
    NexusRow(
        str("nexus.home.bios"),
        value = str(if (biosOk) "nexus.home.bios.configured" else "nexus.home.bios.missing"),
        valueColor = if (biosOk) null else NexusColors.Warn,
        chevron = true,
        onClick = { UiNavigator.openFrom(AppRoute.BiosManager()) },
    )
    NexusDivider()
    NexusRow(
        str("nexus.home.libraryGames"),
        value = str("nexus.home.library.count").replace("{n}", gameCount.toString()),
        chevron = true,
        onClick = { UiNavigator.selectTab(MainTab.Library) },
    )
    NexusDivider()
    NexusRow(
        str("nexus.home.libraryState"),
        value = str(
            when {
                state.scanning -> "nexus.home.library.scanning"
                state.duplicateCount > 0 -> "nexus.home.library.hasDups"
                else -> "nexus.home.library.ok"
            },
        ).replace("{n}", state.duplicateCount.toString()),
    )
    if (lastScan > 0L) {
        NexusDivider()
        NexusRow(str("nexus.home.lastCheck"), value = PlayTime.formatLastPlayed(lastScan))
    }
}

/** Last save, last game started, last activity, only from real records. */
@Composable
private fun RecentActivity(recents: List<GameInfo>, forceEn: Boolean) {
    val context = LocalContext.current
    val lastSave by produceState<com.armsx2.ui.saves.SaveEntry?>(initialValue = null, recents.firstOrNull()?.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                com.armsx2.ui.saves.SaveCatalog.scan(context).filter { !it.isBackup }.maxByOrNull { it.modified }
            }.getOrNull()
        }
    }
    val lastGame = recents.firstOrNull()
    val lastPlayedAt = lastGame?.let { PlayTime.lastPlayedMillis(it.serial) } ?: 0L
    val save = lastSave
    val saveAt = save?.modified ?: 0L
    if (lastPlayedAt <= 0L && save == null) return
    NexusSectionHeader(str("nexus.home.activity"))
    if (save != null) {
        val title = recents.firstOrNull { it.serial.equals(save.serial, ignoreCase = true) }?.displayTitle(forceEn) ?: save.serial
        NexusRow(
            str("nexus.home.lastSave"),
            description = title,
            value = PlayTime.formatLastPlayed(save.modified),
            chevron = true,
            onClick = { UiNavigator.openFrom(AppRoute.SaveManager(focusSerial = save.serial)) },
        )
        NexusDivider()
    }
    if (lastGame != null && lastPlayedAt > 0L) {
        NexusRow(
            str("nexus.home.lastStarted"),
            description = lastGame.displayTitle(forceEn),
            value = PlayTime.formatLastPlayed(lastPlayedAt),
        )
        NexusDivider()
    }
    val latest = maxOf(lastPlayedAt, saveAt)
    if (latest > 0L) NexusRow(str("nexus.home.lastActivity"), value = PlayTime.formatLastPlayed(latest))
}
