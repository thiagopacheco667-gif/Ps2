package com.armsx2.ui.settingshub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.armsx2.R
import com.armsx2.i18n.str
import com.armsx2.navigation.AppRoute
import com.armsx2.navigation.SettingsCategory
import com.armsx2.navigation.UiNavigator
import com.armsx2.ui.common.rememberInitialFocusRequester
import com.armsx2.ui.nexus.NexusDivider
import com.armsx2.ui.nexus.NexusIconButton
import com.armsx2.ui.nexus.NexusRow
import com.armsx2.ui.nexus.NexusSectionHeader

/**
 * Nexus2 "Ajustes": one line per category, each opening its own page. The pages are the existing
 * settings tabs, grouped ([SettingsGroup]); System and Performance & diagnostics also reach the
 * file managers and the live diagnostics. The current profile sits at the bottom.
 */
@Composable
fun SettingsHomeScreen(viewModel: SettingsViewModel = viewModel()) {
    val colors = MaterialTheme.colorScheme
    // Global values, for the "current profile" line.
    LaunchedEffect(Unit) { viewModel.load(SettingsCategory.Graphics, null) }
    val firstFocus = rememberInitialFocusRequester()
    fun openGroup(g: SettingsGroup) =
        UiNavigator.openFrom(AppRoute.Settings(category = g.categories.first { it != SettingsCategory.Info }, group = g.name))

    Box(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.statusBars), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 840.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(str("nexus.nav.settings"), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.weight(1f))
                NexusIconButton(R.drawable.ic_nx_search, str("action.search")) {
                    // Settings search lives on the settings page; open it there.
                    UiNavigator.openFrom(AppRoute.Settings(SettingsCategory.Graphics))
                    SettingsSearchLauncher.pending = true
                }
            }
            Spacer(Modifier.size(12.dp))
            NexusRow(str("nexus.settings.group.graphics"), Modifier.focusRequester(firstFocus), description = str("nexus.settings.graphics.desc"),
                icon = R.drawable.ic_nx_texture, chevron = true, onClick = { openGroup(SettingsGroup.Graphics) })
            NexusDivider(inset = 74.dp)
            NexusRow(str("nexus.settings.group.emulation"), description = str("nexus.settings.emulation.desc"),
                icon = R.drawable.ic_nx_pulse, chevron = true, onClick = { openGroup(SettingsGroup.Emulation) })
            NexusDivider(inset = 74.dp)
            NexusRow(str("nexus.settings.group.audio"), description = str("nexus.settings.audio.desc"),
                icon = R.drawable.ic_nx_audio, chevron = true, onClick = { openGroup(SettingsGroup.Audio) })
            NexusDivider(inset = 74.dp)
            NexusRow(str("nexus.settings.group.controls"), description = str("nexus.settings.controls.desc"),
                icon = R.drawable.ic_nx_pad, chevron = true, onClick = { openGroup(SettingsGroup.Controls) })
            NexusDivider(inset = 74.dp)
            NexusRow(str("nexus.settings.group.system"), description = str("nexus.settings.system.desc"),
                icon = R.drawable.ic_nx_folder, chevron = true, onClick = { UiNavigator.openFrom(AppRoute.Tools) })
            NexusDivider(inset = 74.dp)
            NexusRow(str("nexus.settings.group.diagnostics"), description = str("nexus.settings.diagnostics.desc"),
                icon = R.drawable.ic_nx_gauge, chevron = true, onClick = { UiNavigator.openFrom(AppRoute.Diagnostics) })
            NexusDivider(inset = 74.dp)
            NexusRow("Nexus Performance", description = str("bench.entry.desc"),
                icon = R.drawable.ic_nx_rocket, chevron = true, onClick = { UiNavigator.openFrom(AppRoute.NexusPerformance) })

            NexusDivider(inset = 74.dp)
            NexusRow(str("nexus.about.title"), description = str("nexus.about.row.desc"),
                icon = R.drawable.ic_nx_pulse, chevron = true, onClick = { UiNavigator.openFrom(AppRoute.About) })

            NexusSectionHeader(str("nexus.settings.profile"))
            val profile = str(com.armsx2.ui.settings.currentProfileKey(viewModel.settings.value))
            NexusRow(str("nexus.settings.profile.current"), description = profile, icon = R.drawable.ic_nx_rocket, chevron = true,
                onClick = { UiNavigator.openFrom(AppRoute.Profile) })
        }
    }
}

/** Lets the Ajustes page ask the settings screen to open its search once it is up. */
internal object SettingsSearchLauncher {
    @Volatile var pending: Boolean = false
}

/** Nexus2: the global one-tap profile page (Compatibility / Balanced / Performance / Custom),
 *  plus the sharp-image and motion-blur switches. Written to the global settings. */
@Composable
fun ProfileScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { viewModel.load(SettingsCategory.Graphics, null) }
    Box(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.statusBars), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 840.dp).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                NexusIconButton(R.drawable.ic_nx_back, str("nexus.common.back"), onClick = onBack)
                Spacer(Modifier.size(14.dp))
                Text(str("nexus.settings.profile"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            }
            Text(str("nexus.settings.profile.intro"), fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant)
            Spacer(Modifier.size(12.dp))
            com.armsx2.ui.settings.QuickProfilesSection(viewModel.settings.value) { com.armsx2.ui.InGameOverlay.saveSettings(it) }
        }
    }
}
