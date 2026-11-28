package com.armsx2.ui.game

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.armsx2.Duplicates
import com.armsx2.EnglishTitles
import com.armsx2.Favorites
import com.armsx2.GameInfo
import com.armsx2.PlayTime
import com.armsx2.R
import com.armsx2.i18n.str
import com.armsx2.navigation.AppRoute
import com.armsx2.navigation.UiNavigator
import com.armsx2.ui.common.GameCoverArt
import com.armsx2.ui.common.padFocusRing
import com.armsx2.ui.common.rememberInitialFocusRequester
import com.armsx2.ui.home.HomeViewModel
import com.armsx2.ui.nexus.NexusChip
import com.armsx2.ui.nexus.NexusDivider
import com.armsx2.ui.nexus.NexusGameBackdrop
import com.armsx2.ui.nexus.NexusIconButton
import com.armsx2.ui.nexus.NexusPrimaryButton
import com.armsx2.ui.nexus.NexusRow
import com.armsx2.ui.nexus.NexusSecondaryButton
import com.armsx2.ui.nexus.compatLabelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Nexus2: one game's page. Art on top (the cover, enlarged and blurred, since box art is all the
 * library has), name and the facts the app really knows (region, format, file size), PLAY, the
 * three per-game tools, then one block of information. Sub-screens come back here.
 */
@Composable
fun GameDetailScreen(game: GameInfo, onBack: () -> Unit, viewModel: HomeViewModel = viewModel()) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val forceEn = EnglishTitles.enabled.value
    PlayTime.revision.value
    Favorites.keys.value
    val favorite = Favorites.isFavorite(game)
    val hasSaves by produceState(initialValue = false, game.serial) {
        value = withContext(Dispatchers.IO) {
            runCatching { com.armsx2.ui.saves.SaveCatalog.hasSaves(context, game.serial) }.getOrDefault(false)
        }
    }
    val size by produceState<Long?>(initialValue = null, game.uri) {
        value = withContext(Dispatchers.IO) { fileSize(context, game.uri) }
    }
    val copies = Duplicates.groups(viewModel.state.value.allGames)
        .firstOrNull { group -> group.any { it.uri == game.uri } }?.size ?: 1
    val playFocus = rememberInitialFocusRequester(game.uri)

    Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            NexusGameBackdrop(game, Modifier.fillMaxWidth()) {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        NexusIconButton(R.drawable.ic_nx_back, str("nexus.common.back"), translucent = true, onClick = onBack)
                        Spacer(Modifier.weight(1f))
                        NexusIconButton(
                            if (favorite) R.drawable.ic_nx_heart_filled else R.drawable.ic_nx_heart,
                            str(if (favorite) "nexus.fav.remove" else "nexus.fav.add"),
                            tint = if (favorite) colors.primary else null,
                            translucent = true,
                        ) { viewModel.toggleFavorite(game) }
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp), contentAlignment = Alignment.Center) {
                        GameCoverArt(game, Modifier.width(180.dp).aspectRatio(0.7f))
                    }
                }
            }

            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    game.displayTitle(forceEn),
                    fontSize = 28.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.onBackground,
                )
                game.serial?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 14.sp, color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    game.region?.let { NexusChip(it) }
                    if (game.extension.isNotBlank()) NexusChip(game.extension)
                    size?.let { NexusChip(formatSize(it)) }
                }
                Spacer(Modifier.height(20.dp))
                NexusPrimaryButton(str("action.play").uppercase(), Modifier.focusRequester(playFocus)) { viewModel.launch(game) }
                if (hasSaves) {
                    Spacer(Modifier.height(10.dp))
                    NexusSecondaryButton(str("nexus.home.fromSave"), Modifier.fillMaxWidth(), icon = R.drawable.ic_nx_save) {
                        UiNavigator.openFrom(AppRoute.SaveManager(focusSerial = game.serial))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(R.drawable.ic_nx_settings, str("nexus.game.settings"), Modifier.weight(1f)) {
                        UiNavigator.openFrom(AppRoute.Settings(game = game))
                    }
                    ActionTile(R.drawable.ic_nx_save, str("nexus.game.saveStates"), Modifier.weight(1f)) {
                        UiNavigator.openFrom(AppRoute.SaveManager(focusSerial = game.serial))
                    }
                    ActionTile(R.drawable.ic_nx_card, str("nexus.game.memoryCard"), Modifier.weight(1f)) {
                        UiNavigator.openFrom(AppRoute.MemoryCardManager(game))
                    }
                }
            }

            Text(
                str("nexus.game.info"),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 4.dp),
            )
            val played = PlayTime.formatPlayed(PlayTime.playedSeconds(game.serial))
            val last = PlayTime.formatLastPlayed(PlayTime.lastPlayedMillis(game.serial))
            val ach = PlayTime.achievements(game.serial)
            NexusRow(str("nexus.game.playTime"), value = played.ifEmpty { str("nexus.game.never") })
            NexusDivider()
            NexusRow(str("nexus.game.lastPlayed"), value = last.ifEmpty { str("nexus.game.never") })
            val sessions = PlayTime.sessionCount(game.serial)
            if (sessions > 0) {
                NexusDivider()
                NexusRow(str("nexus.game.sessions"), value = sessions.toString())
            }
            compatLabelKey(game.compatibility)?.let { key ->
                NexusDivider()
                NexusRow(str("nexus.game.compat"), value = str(key))
            }
            PlayTime.revision.value
            // Nexus2: four separate things, never merged: achievements unlocked, their percentage
            // (RetroAchievements), an unsupported disc version (no figures at all), and campaign
            // completion, which only the player can state (no source knows when a PS2 story ends).
            if (PlayTime.achievementsUnsupported(game.serial)) {
                NexusDivider()
                NexusRow(str("nexus.game.achievements"), value = str("nexus.ach.unsupported"), valueColor = com.armsx2.ui.nexus.NexusColors.Warn)
            } else if (ach != null) {
                NexusDivider()
                NexusRow(
                    str("nexus.game.achievements"),
                    value = str("nexus.game.achievements.value").replace("{n}", ach.softcore.toString()).replace("{total}", ach.total.toString()) +
                        " · " + (ach.softcore * 100 / ach.total) + "%",
                )
            }
            NexusDivider()
            val completedAt = GameCompletionState(game.serial)
            NexusRow(
                str("nexus.game.completion"),
                description = str(if (completedAt > 0L) "nexus.game.completion.unmark" else "nexus.game.completion.mark"),
                value = if (completedAt > 0L) str("nexus.game.completion.manual").replace("{date}", PlayTime.formatLastPlayed(completedAt))
                    else str("nexus.game.completion.unverified"),
                valueColor = if (completedAt > 0L) com.armsx2.ui.nexus.NexusColors.Ok else null,
                onClick = { com.armsx2.GameCompletion.set(game.serial, completedAt <= 0L) },
            )
            if (copies > 1) {
                NexusDivider()
                NexusRow(str("nexus.game.copies"), value = copies.toString())
            }
            NexusDivider()
            NexusRow(str("nexus.game.bios"), chevron = true, onClick = { UiNavigator.openFrom(AppRoute.BiosManager(game)) })
        }
    }
}

private fun fileSize(context: Context, uri: Uri): Long? = runCatching {
    when (uri.scheme) {
        "content" -> context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
        "file", null -> uri.path?.let(::File)?.takeIf { it.isFile }?.length()
        else -> null
    }
}.getOrNull()?.takeIf { it > 0 }

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    else -> "%.0f MB".format(bytes / 1_048_576.0)
}

@Composable
private fun ActionTile(@DrawableRes icon: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.height(88.dp).padFocusRing(shape).clip(shape).background(colors.surface).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GameCompletionState(serial: String?): Long {
    PlayTime.revision.value
    return com.armsx2.GameCompletion.markedAt(serial)
}
