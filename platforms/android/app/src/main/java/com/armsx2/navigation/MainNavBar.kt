package com.armsx2.navigation

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.R
import com.armsx2.i18n.str
import com.armsx2.ui.common.padFocusRing

/**
 * Nexus2: bridge for the controller's L1/R1 to switch bar sections. Non-null only while the bar
 * is on screen; MainActivityRuntime's key router uses it only when the settings tabs are not
 * claiming L1/R1 and the library (not a running game) is in front.
 */
object MainTabsNav {
    @Volatile
    var cycle: ((Int) -> Unit)? = null
}

private data class TabSpec(val tab: MainTab, val labelKey: String, @DrawableRes val icon: Int)

private val Tabs = listOf(
    TabSpec(MainTab.Home, "nexus.nav.home", R.drawable.ic_nx_home),
    TabSpec(MainTab.Library, "nexus.nav.games", R.drawable.ic_nx_games),
    TabSpec(MainTab.Tools, "nexus.nav.tools", R.drawable.ic_nx_tools),
    TabSpec(MainTab.Settings, "nexus.nav.settings", R.drawable.ic_nx_settings),
)

/** Between the screen background and the card surface (#0E1220 in the Nexus2 dark theme), so the
 *  bar reads as its own layer without a border; follows whichever theme is active. */
@Composable
private fun barColor(): Color =
    androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface, 0.35f)

@Composable
private fun RegisterTabCycle(current: MainTab) {
    DisposableEffect(current) {
        MainTabsNav.cycle = { step ->
            val all = MainTab.entries
            val next = all[(all.indexOf(current) + step + all.size) % all.size]
            UiNavigator.selectTab(next)
        }
        onDispose { MainTabsNav.cycle = null }
    }
}

/** Portrait: fixed bar at the bottom, above the system navigation bar. */
@Composable
fun MainBottomBar(current: MainTab, modifier: Modifier = Modifier) {
    RegisterTabCycle(current)
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .background(barColor())
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tabs.forEach { spec ->
            TabItem(spec, selected = spec.tab == current, modifier = Modifier.weight(1f))
        }
    }
}

/** Landscape / wide screens: the same sections as a rail on the start edge. */
@Composable
fun MainSideRail(current: MainTab, modifier: Modifier = Modifier) {
    RegisterTabCycle(current)
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxHeight()
            .background(barColor())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
            .width(88.dp)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Tabs.forEach { spec ->
            TabItem(spec, selected = spec.tab == current, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TabItem(spec: TabSpec, selected: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val pill by animateColorAsState(
        if (selected) colors.primaryContainer else Color.Transparent,
        animationSpec = tween(200),
        label = "tab-pill",
    )
    val content by animateColorAsState(
        if (selected) colors.onBackground else colors.onSurfaceVariant,
        animationSpec = tween(200),
        label = "tab-content",
    )
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .heightIn(min = 56.dp)
            .padFocusRing(shape)
            .clip(shape)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = { if (!selected) UiNavigator.selectTab(spec.tab) },
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(width = 60.dp, height = 32.dp).clip(RoundedCornerShape(16.dp)).background(pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(spec.icon),
                contentDescription = null,
                tint = if (selected) colors.onPrimaryContainer else content,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            str(spec.labelKey),
            color = content,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
