package com.armsx2.ui.nexus

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.GameInfo
import com.armsx2.R
import com.armsx2.i18n.str
import com.armsx2.ui.common.GameCoverArt
import com.armsx2.ui.common.padFocusRing

/**
 * Nexus2 shared building blocks. One look across Home, Games, Saves and Settings: rows split by
 * hairline dividers rather than nested cards, section titles in sentence case, one violet primary
 * action per screen. Colours come only from the active theme (Nexus2 palette by default); the two
 * semantic colours below are for state, never decoration.
 */
object NexusColors {
    /** Success/OK, kept muted so it reads as state, not as a brand colour. */
    val Ok = Color(0xFF5BD69B)
    val Warn = Color(0xFFFFB454)
}

/** "Recent games" + optional trailing action ("See library →"). */
@Composable
fun NexusSectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            val shape = RoundedCornerShape(10.dp)
            Text(
                "$action  →",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
                modifier = Modifier.padFocusRing(shape).clip(shape).clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun NexusDivider(modifier: Modifier = Modifier, inset: Dp = 20.dp) {
    Box(
        modifier.fillMaxWidth().padding(horizontal = inset).height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    )
}

/** One line of information or navigation: label (+ optional description) and a value or chevron.
 *  Clickable only when [onClick] is set; then it gets the controller focus ring. */
@Composable
fun NexusRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    valueColor: Color? = null,
    @DrawableRes icon: Int? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.padFocusRing(shape).clip(shape).clickable(onClick = onClick) else Modifier)
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(description, fontSize = 13.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant)
            }
        }
        if (value != null) {
            Text(
                value,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor ?: colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (chevron) {
            Icon(painterResource(R.drawable.ic_nx_chevron), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** Small label pill: region, format, "Last game". [accent] tints it for a state. */
@Composable
fun NexusChip(text: String, accent: Color? = null) {
    val colors = MaterialTheme.colorScheme
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = accent ?: colors.onSurface,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(accent?.copy(alpha = 0.14f) ?: colors.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** The one big violet action of a screen. Presses scale down a touch (button feedback). */
@Composable
fun NexusPrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = R.drawable.ic_nx_play,
    height: Dp = 56.dp,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "press")
    val indication = androidx.compose.foundation.LocalIndication.current
    Row(
        modifier.fillMaxWidth().height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .padFocusRing(shape).clip(shape).background(colors.primary)
            .clickable(interactionSource = interaction, indication = indication, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = colors.onPrimary)
    }
}

/** Secondary action: same shape, quiet surface. */
@Composable
fun NexusSecondaryButton(label: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(48.dp).padFocusRing(shape).clip(shape).background(colors.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
    }
}

/** Round icon button for headers (search, settings, back, favourite). */
@Composable
fun NexusIconButton(@DrawableRes icon: Int, description: String, tint: Color? = null, translucent: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.size(48.dp).padFocusRing(shape).clip(shape)
            .background(if (translucent) colors.background.copy(alpha = 0.6f) else colors.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = tint ?: colors.onSurface, modifier = Modifier.size(22.dp))
    }
}

/**
 * Cinematic backdrop for a game. The library only has box art (portrait), so the "key art" is the
 * game's own cover, enlarged and blurred, fading into the screen background; the sharp cover is
 * drawn on top by the caller. Blur needs Android 12+; older systems get the dimmed cover instead.
 */
@Composable
fun NexusGameBackdrop(game: GameInfo, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val bg = MaterialTheme.colorScheme.background
    Box(modifier.clip(RoundedCornerShape(0.dp))) {
        // matchParentSize: the layers take the size the content gives the Box (it sits in scrolling
        // columns, where fillMaxSize would ask for infinite height).
        GameCoverArt(
            game,
            Modifier.matchParentSize()
                .graphicsLayer { scaleX = 1.35f; scaleY = 1.35f; alpha = if (Build.VERSION.SDK_INT >= 31) 0.85f else 0.45f }
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(28.dp) else Modifier),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to bg.copy(alpha = 0.35f), 0.55f to bg.copy(alpha = 0.55f), 1f to bg),
            ),
        )
        content()
    }
}

/** GameDB compatibility (0..5 after the app's shift) to a short label key, or null when unknown. */
fun compatLabelKey(compat: Int): String? = when (compat) {
    1 -> "nexus.compat.intro"
    2 -> "nexus.compat.menus"
    3 -> "nexus.compat.ingame"
    4 -> "nexus.compat.playable"
    5 -> "nexus.compat.perfect"
    else -> null
}

/** Compatibility as a chip, coloured only at the two ends of the scale. */
@Composable
fun NexusCompatChip(compat: Int) {
    val key = compatLabelKey(compat) ?: return
    val accent = when {
        compat >= 4 -> NexusColors.Ok
        compat <= 2 -> NexusColors.Warn
        else -> null
    }
    NexusChip(str(key), accent)
}
