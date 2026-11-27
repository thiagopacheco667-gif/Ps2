package com.armsx2.ui.saves

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.armsx2.EnglishTitles
import com.armsx2.PlayTime
import com.armsx2.R
import com.armsx2.i18n.str
import com.armsx2.navigation.AppRoute
import com.armsx2.navigation.UiNavigator
import com.armsx2.ui.common.rememberInitialFocusRequester
import com.armsx2.ui.home.HomeViewModel
import com.armsx2.ui.nexus.NexusDivider
import com.armsx2.ui.nexus.NexusRow
import com.armsx2.ui.nexus.NexusSectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Nexus2 "Saves" section. Two things that are easy to confuse, kept apart:
 *  - Save states: snapshots the emulator takes (any moment, per slot), managed by SaveManager.
 *  - Memory cards: the PS2's own cards the game saves to, managed by MemoryCardScreen.
 * Both managers are the existing screens; this page lists the newest states from the real save
 * folders and opens the managers. Nothing here creates or deletes files by itself.
 */
@Composable
fun SavesScreen(homeViewModel: HomeViewModel = viewModel()) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val forceEn = EnglishTitles.enabled.value
    val entries by produceState<List<SaveEntry>?>(initialValue = null, Unit) {
        value = withContext(Dispatchers.IO) {
            runCatching { SaveCatalog.scan(context).filter { !it.isBackup }.sortedByDescending { it.modified } }.getOrDefault(emptyList())
        }
    }
    val games = homeViewModel.state.value.allGames
    val firstFocus = rememberInitialFocusRequester()

    Box(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.statusBars), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 840.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                str("nexus.saves.title"),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp),
            )
            Text(
                str("nexus.saves.subtitle"),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
            )

            NexusSectionHeader(str("nexus.saves.states"))
            val list = entries
            val gameCount = list?.map { it.serial }?.distinct()?.size ?: 0
            NexusRow(
                str("nexus.saves.manageStates"),
                Modifier.focusRequester(firstFocus),
                description = when {
                    list == null -> str("nexus.saves.loading")
                    list.isEmpty() -> str("nexus.saves.statesEmpty")
                    else -> str("nexus.saves.statesCount").replace("{n}", list.size.toString()).replace("{games}", gameCount.toString())
                },
                icon = R.drawable.ic_nx_save,
                chevron = true,
                onClick = { UiNavigator.openFrom(AppRoute.SaveManager()) },
            )
            list?.take(8)?.forEach { entry ->
                NexusDivider()
                val title = games.firstOrNull { it.serial.equals(entry.serial, ignoreCase = true) }?.displayTitle(forceEn) ?: entry.serial
                val slot = when (entry.id.kind) {
                    SaveKind.Autosave -> str("nexus.saves.autosave")
                    SaveKind.Slot -> str("nexus.saves.slot").replace("{n}", (entry.id.slot ?: 0).toString())
                }
                NexusRow(
                    title,
                    description = slot,
                    value = PlayTime.formatLastPlayed(entry.modified),
                    onClick = { UiNavigator.openFrom(AppRoute.SaveManager(focusSerial = entry.serial)) },
                )
            }

            NexusSectionHeader(str("nexus.saves.cards"))
            NexusRow(
                str("nexus.saves.manageCards"),
                description = str("nexus.saves.cardsDesc"),
                icon = R.drawable.ic_nx_card,
                chevron = true,
                onClick = { UiNavigator.openFrom(AppRoute.MemoryCardManager()) },
            )
            NexusDivider()
            NexusRow(
                str("nexus.saves.backup"),
                description = str("nexus.saves.backupDesc"),
                icon = R.drawable.ic_nx_folder,
                chevron = true,
                onClick = { UiNavigator.openFrom(AppRoute.Settings(com.armsx2.navigation.SettingsCategory.General)) },
            )
        }
    }
}
