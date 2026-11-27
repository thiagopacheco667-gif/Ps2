package com.armsx2.ui.nexus

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.armsx2.CustomCovers
import com.armsx2.GameInfo
import com.armsx2.runtime.MainActivityRuntime

/**
 * Which game the backdrop follows: the one running, else the last one played, else none (the
 * static Nexus2 backdrop). Never a random or unrelated game.
 */
@Composable
fun nexusBackdropGame(recents: List<GameInfo>): GameInfo? =
    MainActivityRuntime.currentGame.value ?: recents.firstOrNull()

/**
 * Nexus2 backdrop: the game's art, enlarged, blurred and darkened under a gradient, so text keeps
 * its contrast. A static image: no animation runs while it is on screen; the only motion is a short
 * crossfade when the game changes.
 *
 * The library only has box art, so the "art" is the cover (the user's custom cover first, then the
 * cover repository). It is decoded at a small size on purpose: blurred and scaled up, a 270x400
 * bitmap looks the same as a full one and costs a fraction of the memory and decode time. Coil keeps
 * it in its memory and disk caches, so returning to the screen or a game does not download again.
 * When there is no game, or its cover cannot be loaded, the static Nexus2 backdrop stays.
 */
@Composable
fun NexusArtBackground(game: GameInfo?, modifier: Modifier = Modifier) {
    val bg = Color(0xFF090B12)
    Box(modifier.fillMaxSize().background(bg)) {
        NexusStaticBackdrop()
        Crossfade(targetState = game, animationSpec = tween(450), label = "nexus-backdrop") { g ->
            if (g != null) ArtLayer(g, bg)
        }
    }
}

@Composable
private fun ArtLayer(game: GameInfo, bg: Color) {
    val context = LocalContext.current
    val custom = remember(game.uri, CustomCovers.version.value) { CustomCovers.fileFor(context, game) }
    com.armsx2.CoverRegionIndex.region.intValue
    val primary = custom ?: game.coverUrl ?: return
    // If the region cover is missing, retry this disc's own cover once; if that fails too, nothing
    // is drawn and the static backdrop shows through.
    var model by remember(primary) { mutableStateOf<Any>(primary) }
    var loaded by remember(primary) { mutableStateOf(false) }
    val blurOk = Build.VERSION.SDK_INT >= 31
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(model).size(270, 400).crossfade(false).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            onSuccess = { loaded = true },
            onError = {
                val disc = if (custom == null) game.discCoverUrl else null
                if (disc != null && model != disc) model = disc
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f; alpha = if (blurOk) 0.62f else 0.38f }
                .then(if (blurOk) Modifier.blur(36.dp) else Modifier),
        )
        if (loaded) Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to bg.copy(alpha = 0.45f),
                    0.45f to bg.copy(alpha = 0.62f),
                    1f to bg.copy(alpha = 0.94f),
                ),
            ),
        )
    }
}

/** No game yet: near-black with two still glows in the Nexus2 violet and blue. */
@Composable
fun NexusStaticBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(Color(0xFF8257FF).copy(alpha = 0.16f), Color.Transparent),
                center = Offset(0f, 0f),
                radius = 1400f,
            ),
        ),
    )
    Box(
        modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(Color(0xFF35A8FF).copy(alpha = 0.10f), Color.Transparent),
                center = Offset(2000f, 2600f),
                radius = 1600f,
            ),
        ),
    )
}
