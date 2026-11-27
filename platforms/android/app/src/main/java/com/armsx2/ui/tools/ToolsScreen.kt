package com.armsx2.ui.tools

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.R
import com.armsx2.i18n.str
import com.armsx2.navigation.AppRoute
import com.armsx2.navigation.UiNavigator
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.common.padFocusRing
import com.armsx2.ui.common.rememberInitialFocusRequester

/**
 * Nexus2: the "Ferramentas" section of the bottom bar. Every row opens a screen or flow that
 * already existed (until now reachable only from the side drawer); nothing here is new behaviour.
 * Rows that open a screen use [UiNavigator.openFrom], so that screen's back button returns here.
 */
private data class ToolEntry(
    val titleKey: String,
    val descKey: String,
    @DrawableRes val icon: Int,
    val open: (android.content.Context) -> Unit,
)

private fun toolEntries(): List<ToolEntry> = listOf(
    ToolEntry("setup.step.bios.title", "nexus.tools.bios.desc", R.drawable.ic_nx_disc) { UiNavigator.openFrom(AppRoute.BiosManager()) },
    ToolEntry("memcard.title", "nexus.tools.memcard.desc", R.drawable.ic_nx_card) { UiNavigator.openFrom(AppRoute.MemoryCardManager()) },
    ToolEntry("savestate.title.loadManage", "nexus.tools.saves.desc", R.drawable.ic_nx_save) { UiNavigator.openFrom(AppRoute.SaveManager()) },
    ToolEntry("ra.title", "nexus.tools.achievements.desc", R.drawable.ic_trophy) { UiNavigator.openFrom(AppRoute.Achievements) },
    ToolEntry("renderer.section.texturePacks", "nexus.tools.textures.desc", R.drawable.ic_nx_texture) { UiNavigator.openFrom(AppRoute.TextureManager) },
    ToolEntry("patches.dialog.patchesAndCheats", "nexus.tools.patches.desc", R.drawable.ic_nx_patch) { UiNavigator.openFrom(AppRoute.PatchManager) },
    ToolEntry("tab.controls", "nexus.tools.controls.desc", R.drawable.ic_nx_pad) { UiNavigator.openFrom(AppRoute.ControllerManager) },
    ToolEntry("games.overflow.setup", "nexus.tools.folders.desc", R.drawable.ic_nx_folder) { MainActivityRuntime.reopenSetup() },
    ToolEntry("arcade.title", "nexus.tools.arcade.desc", R.drawable.ic_nx_arcade) { UiNavigator.openFrom(AppRoute.Arcade) },
    ToolEntry("bugreport.menu.title", "nexus.tools.diagnostics.desc", R.drawable.ic_nx_pulse) { ctx ->
        com.armsx2.ui.bugreport.BugReportFlow.openGeneral(ctx)
    },
)

@Composable
fun ToolsScreen(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val entries = toolEntries()
    val firstFocus = rememberInitialFocusRequester()
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(bottom = 14.dp)) {
                    Text(
                        str("nexus.nav.tools"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        str("nexus.tools.subtitle"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(entries) { index, entry ->
                val shape = RoundedCornerShape(16.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .padFocusRing(shape)
                        .clip(shape)
                        .background(colors.surface)
                        .clickable { entry.open(context) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(colors.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(entry.icon),
                            contentDescription = null,
                            tint = colors.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            str(entry.titleKey),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.size(2.dp))
                        Text(
                            str(entry.descKey),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Icon(
                        painterResource(R.drawable.ic_nx_chevron),
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
