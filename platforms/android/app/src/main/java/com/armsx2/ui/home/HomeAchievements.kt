package com.armsx2.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.armsx2.GameInfo
import com.armsx2.PlayTime
import com.armsx2.R
import com.armsx2.RaLibrary
import com.armsx2.i18n.str
import com.armsx2.navigation.AppRoute
import com.armsx2.navigation.UiNavigator
import com.armsx2.runtime.MainActivityRuntime
import com.armsx2.ui.common.GameCoverArt
import com.armsx2.ui.common.padFocusRing
import com.armsx2.ui.nexus.NexusDivider
import com.armsx2.ui.nexus.NexusRow
import com.armsx2.ui.nexus.NexusSectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Gold for hardcore, the same meaning RetroAchievements gives it. */
private val Hardcore = Color(0xFFFFC94D)

private data class RaAccount(val loggedIn: Boolean, val user: String, val score: Long, val softScore: Long, val avatar: String)

private const val PREF_SCORE = "nexus.ra.lastScore"
private const val PREF_SOFT = "nexus.ra.lastSoftScore"
private const val PREF_AVATAR = "nexus.ra.lastAvatar"

/** Reads the signed-in account from the core; when the core has no session yet (app just opened,
 *  no game booted) it falls back to the last values it reported, so the figures never invent. */
private fun readAccount(): RaAccount {
    val prefs = MainActivityRuntime.prefs
    val root = runCatching { JSONObject(kr.co.iefriends.pcsx2.NativeApp.getAchievementsJSON().orEmpty()) }.getOrNull()
    val live = root?.optBoolean("loggedIn") == true
    if (live && root != null) {
        val user = root.optString("userName")
        val score = root.optLong("score").coerceAtLeast(0)
        val soft = root.optLong("softcoreScore").coerceAtLeast(0)
        val avatar = root.optString("avatarUrl")
        runCatching {
            if (user.isNotBlank()) RaLibrary.userName = user
            prefs.edit().putLong(PREF_SCORE, score).putLong(PREF_SOFT, soft).putString(PREF_AVATAR, avatar).apply()
        }
        return RaAccount(true, user, score, soft, avatar)
    }
    val user = RaLibrary.userName
    return RaAccount(
        loggedIn = user.isNotBlank(),
        user = user,
        score = runCatching { prefs.getLong(PREF_SCORE, -1L) }.getOrDefault(-1L),
        softScore = runCatching { prefs.getLong(PREF_SOFT, -1L) }.getOrDefault(-1L),
        avatar = runCatching { prefs.getString(PREF_AVATAR, "").orEmpty() }.getOrDefault(""),
    )
}

/**
 * Nexus2 "Conquistas" on Home: the RetroAchievements account, library-wide totals, the games in
 * progress and (with the Web API key) the latest PS2 unlocks. Every number is RetroAchievements'
 * own: per-game progress is what the core reported while the game ran (PlayTime), the totals are
 * their sum, and the recent list comes straight from the RA web API.
 */
@Composable
internal fun HomeAchievementsSection(games: List<GameInfo>, forceEn: Boolean) {
    PlayTime.revision.value
    val account by produceState<RaAccount?>(null) { value = withContext(Dispatchers.IO) { readAccount() } }
    val recent by produceState<List<RaLibrary.RecentUnlock>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { RaLibrary.recentUnlocks() }.getOrNull() }
    }
    val acc = account ?: return
    val openAll = { UiNavigator.openFrom(AppRoute.Achievements) }

    NexusSectionHeader(str("nexus.ach.title"), action = str("nexus.ach.seeAll"), onAction = openAll)

    if (!acc.loggedIn) {
        NexusRow(
            str("nexus.ach.signIn"),
            description = str("nexus.ach.signIn.desc"),
            icon = R.drawable.ic_trophy,
            chevron = true,
            onClick = openAll,
        )
        return
    }

    val progress = games.mapNotNull { g -> PlayTime.achievements(g.serial)?.let { g to it } }
        .sortedByDescending { (g, _) -> PlayTime.lastPlayedMillis(g.serial) }
    val unlocked = progress.sumOf { it.second.softcore }
    val total = progress.sumOf { it.second.total }
    val mastered = progress.count { it.second.masteredHardcore || it.second.masteredSoftcore }

    AccountLine(acc)
    if (progress.isNotEmpty()) {
        // One line of three figures instead of three cards: achievements unlocked (of the sets of the
        // games played), games with a set, and games completed — different things, labelled so.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Stat(str("nexus.ach.unlocked"), "$unlocked/$total", Modifier.weight(1f))
            Stat(str("nexus.ach.games"), progress.size.toString(), Modifier.weight(1f))
            Stat(str("nexus.ach.mastered"), mastered.toString(), Modifier.weight(1f))
        }
        progress.take(2).forEachIndexed { i, (game, p) ->
            if (i > 0) NexusDivider(inset = 72.dp)
            GameProgressRow(game, p, forceEn)
        }
    } else {
        Text(
            str("nexus.ach.noProgress"),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }

    val unlocks = recent
    when {
        unlocks != null && unlocks.isNotEmpty() -> {
            Text(
                str("nexus.ach.recent"),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 10.dp),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(unlocks.take(15), key = { it.date + it.title }) { UnlockTile(it) }
            }
        }
        !RaLibrary.configured() -> NexusRow(
            str("nexus.ach.recentKey"),
            description = str("nexus.ach.recentKey.desc"),
            chevron = true,
            onClick = openAll,
        )
    }
}

@Composable
private fun AccountLine(acc: RaAccount) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        if (acc.avatar.isNotBlank()) {
            AsyncImage(acc.avatar, null, Modifier.size(44.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.size(44.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_trophy), null, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(acc.user, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("RetroAchievements", fontSize = 13.sp, color = colors.onSurfaceVariant)
        }
        // Hardcore points, plus softcore when there are any. Nothing when the account has none
        // recorded yet (no game run since sign-in), rather than a "0" that may not be true.
        if (acc.score > 0 || acc.softScore > 0) Column(horizontalAlignment = Alignment.End) {
            Text(
                str("nexus.ach.points").replace("{n}", acc.score.toString()),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Hardcore,
            )
            if (acc.softScore > 0) Text(
                str("nexus.ach.softPoints").replace("{n}", acc.softScore.toString()),
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1)
        Text(label, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GameProgressRow(game: GameInfo, p: PlayTime.Progress, forceEn: Boolean) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val fraction = if (p.total > 0) p.softcore.toFloat() / p.total else 0f
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padFocusRing(shape)
            .clip(shape)
            .clickable { UiNavigator.openFrom(AppRoute.GameDetail(game)) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameCoverArt(game, Modifier.width(40.dp).height(56.dp).clip(RoundedCornerShape(6.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(game.displayTitle(forceEn), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (p.masteredHardcore) Hardcore else colors.primary,
                trackColor = colors.surfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            val hc = if (p.hardcore > 0) " · " + str("nexus.ach.hardcoreCount").replace("{n}", p.hardcore.toString()) else ""
            Text(
                "${p.softcore}/${p.total} · ${(fraction * 100).toInt()}%$hc",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnlockTile(u: RaLibrary.RecentUnlock) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.width(132.dp).clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(10.dp),
    ) {
        Box {
            AsyncImage(u.badgeUrl, u.title, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            if (u.hardcore) Box(
                Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(Hardcore),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(u.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(u.gameTitle, fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(
            str("nexus.ach.pointsShort").replace("{n}", u.points.toString()) + " · " + u.date.take(10),
            fontSize = 11.sp,
            color = if (u.hardcore) Hardcore else colors.primary,
        )
    }
}
