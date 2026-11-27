package com.armsx2.ui.game

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Nexus2: one game's page, opened by tapping its cover. Every action here already existed in the
 * long-press game menu (which stays); this page gives them room and adds the game's history.
 * Sub-screens opened from here come back here ([UiNavigator.openFrom]).
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
    val copies = Duplicates.groups(viewModel.state.value.allGames)
        .firstOrNull { group -> group.any { it.uri == game.uri } }?.size ?: 1
    val playFocus = rememberInitialFocusRequester(game.uri)

    Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.TopCenter) {
        // Hero wash: the accent fading into the background, behind the cover.
        Box(
            Modifier.fillMaxWidth().height(300.dp).background(
                Brush.verticalGradient(listOf(colors.primaryContainer, colors.background)),
            ),
        )
        Column(
            Modifier.fillMaxSize().widthIn(max = 720.dp).windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                CircleButton(R.drawable.ic_nx_back, str("nexus.common.back"), onClick = onBack)
                Spacer(Modifier.weight(1f))
                CircleButton(
                    if (favorite) R.drawable.ic_nx_heart_filled else R.drawable.ic_nx_heart,
                    str(if (favorite) "nexus.fav.remove" else "nexus.fav.add"),
                    tint = if (favorite) colors.primary else colors.onSurface,
                ) { viewModel.toggleFavorite(game) }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                GameCoverArt(game, Modifier.width(140.dp).aspectRatio(0.7f))
                Column(Modifier.weight(1f)) {
                    Text(
                        game.displayTitle(forceEn),
                        fontSize = 22.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.onBackground,
                    )
                    game.serial?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        game.region?.let { Chip(it) }
                        if (game.extension.isNotBlank()) Chip(game.extension)
                        if (game.compatibility > 0) Chip("★".repeat(game.compatibility) + "☆".repeat((5 - game.compatibility).coerceAtLeast(0)))
                    }
                }
            }

            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(str("action.play").uppercase(), primary = true, icon = R.drawable.ic_nx_play,
                    modifier = Modifier.focusRequester(playFocus)) { viewModel.launch(game) }
                if (hasSaves) {
                    BigButton(str("nexus.home.fromSave"), primary = false) {
                        UiNavigator.openFrom(AppRoute.SaveManager(focusSerial = game.serial))
                    }
                }
            }

            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(R.drawable.ic_nx_settings, str("nexus.game.settings"), Modifier.weight(1f)) {
                    UiNavigator.openFrom(AppRoute.Settings(game = game))
                }
                ActionTile(R.drawable.ic_nx_save, str("nexus.home.quick.saves"), Modifier.weight(1f)) {
                    UiNavigator.openFrom(AppRoute.SaveManager(focusSerial = game.serial))
                }
                ActionTile(R.drawable.ic_nx_card, str("nexus.home.quick.cards"), Modifier.weight(1f)) {
                    UiNavigator.openFrom(AppRoute.MemoryCardManager(game))
                }
                ActionTile(R.drawable.ic_nx_disc, str("nexus.home.quick.bios"), Modifier.weight(1f)) {
                    UiNavigator.openFrom(AppRoute.BiosManager(game))
                }
            }

            SectionTitle("nexus.game.history")
            val played = PlayTime.formatPlayed(PlayTime.playedSeconds(game.serial))
            val last = PlayTime.formatLastPlayed(PlayTime.lastPlayedMillis(game.serial))
            val ach = PlayTime.achievements(game.serial)
            Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(colors.surface).padding(horizontal = 18.dp, vertical = 6.dp),
            ) {
                InfoRow(str("nexus.game.playTime"), played.ifEmpty { str("nexus.game.never") })
                InfoRow(str("nexus.game.lastPlayed"), last.ifEmpty { str("nexus.game.never") })
                if (ach != null) {
                    InfoRow(
                        str("nexus.game.achievements"),
                        str("nexus.game.achievements.value")
                            .replace("{n}", ach.softcore.toString()).replace("{total}", ach.total.toString()),
                    )
                }
                if (copies > 1) InfoRow(str("nexus.game.copies"), copies.toString(), last = true)
                else InfoRow(str("nexus.game.format"), game.extension.ifBlank { "—" }, last = true)
            }
        }
    }
}

@Composable
private fun SectionTitle(key: String) {
    Text(
        str(key).uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
    )
}

@Composable
private fun Chip(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String, last: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp)) {
            Text(label, fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant.copy(alpha = 0.6f)))
    }
}

@Composable
private fun BigButton(
    label: String,
    primary: Boolean,
    @DrawableRes icon: Int? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().height(if (primary) 56.dp else 48.dp).padFocusRing(shape).clip(shape)
            .background(if (primary) colors.primary else colors.surface).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (primary) colors.onPrimary else colors.onSurface
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, fontSize = if (primary) 16.sp else 15.sp, fontWeight = FontWeight.Bold, letterSpacing = if (primary) 0.8.sp else 0.sp, color = fg)
    }
}

@Composable
private fun ActionTile(@DrawableRes icon: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.height(84.dp).padFocusRing(shape).clip(shape).background(colors.surface).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CircleButton(@DrawableRes icon: Int, description: String, tint: Color? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.size(48.dp).padFocusRing(shape).clip(shape).background(colors.background.copy(alpha = 0.85f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = tint ?: colors.onSurface, modifier = Modifier.size(22.dp))
    }
}
