package com.armsx2.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.armsx2.BlurRemoval
import com.armsx2.DeviceTier
import com.armsx2.config.Settings
import com.armsx2.i18n.str
import kotlin.math.abs

/**
 * Nexus2: one-tap graphics profiles and the "no motion blur" switch.
 *
 * Every profile only fills in settings that already have their own rows further down the
 * Renderer and Fixes tabs, so any of them can still be tuned by hand afterwards. A profile is
 * shown as selected only while every value it sets still matches.
 *
 * Values are chosen for phone GPUs such as the Mali-G720: blending accuracy stays at Basic or
 * below except in Quality (higher levels cost a lot on tile-based GPUs), and anisotropic
 * filtering stays off except in Quality.
 */
internal data class GraphicsProfile(
    val labelKey: String,
    val upscale: Float,
    val blending: Int,
    val anisotropy: Int,
    val mtvu: Boolean,
)

internal fun graphicsProfiles(): List<GraphicsProfile> = listOf(
    // Native resolution and the cheapest blending: least heat and longest battery.
    GraphicsProfile("nexus.profile.battery", 1.0f, 0, 0, DeviceTier.mtvuDefault()),
    // Native resolution with Basic blending: full speed in heavy games.
    GraphicsProfile("nexus.profile.performance", 1.0f, 1, 0, true),
    // 2x (720p class): the recommended default for a Dimensity 8400 / Mali-G720.
    GraphicsProfile("nexus.profile.balanced", 2.0f, 1, 0, true),
    // 3x with Medium blending and 4x anisotropic: for lighter games, runs warmer.
    GraphicsProfile("nexus.profile.quality", 3.0f, 2, 4, true),
)

internal fun GraphicsProfile.applyTo(s: Settings): Settings = s.copy(
    output = s.output.copy(upscaleFloat = upscale),
    graphics = s.graphics.copy(accurateBlendingUnit = blending),
    hwFixes = s.hwFixes.copy(maxAnisotropy = anisotropy),
    cpu = s.cpu.copy(mtvu = mtvu),
)

internal fun GraphicsProfile.matches(s: Settings): Boolean =
    abs(s.output.upscaleFloat - upscale) < 0.01f &&
        s.graphics.accurateBlendingUnit == blending &&
        s.hwFixes.maxAnisotropy == anisotropy &&
        s.cpu.mtvu == mtvu

/** The settings that make the picture sharp and steady instead of smeared:
 *  - PCRTC anti-blur on (drops the half-frame offset games use to soften the image),
 *  - interlace offset off (removes the one-line shake that reads as blur),
 *  - no-interlacing patches on (games with one render progressive frames),
 *  - adaptive deinterlacing instead of Blend (Blend mixes two fields: ghosting),
 *  - sharp bilinear for the display scaler.
 *  Game-made blur effects are handled separately by [BlurRemoval]. */
internal fun noBlurApplied(s: Settings): Settings = s.copy(
    display = s.display.copy(antiBlur = true, disableInterlaceOffset = true),
    emuCore = s.emuCore.copy(enableNoInterlacingPatches = true),
    output = s.output.copy(deinterlaceMode = DEINTERLACE_ADAPTIVE_TFF),
    graphics = s.graphics.copy(displayBilinear = DISPLAY_FILTER_SHARP),
)

/** Back to the app's own defaults for the same fields. */
internal fun noBlurRemoved(s: Settings): Settings {
    val d = Settings()
    return s.copy(
        display = s.display.copy(antiBlur = d.display.antiBlur, disableInterlaceOffset = d.display.disableInterlaceOffset),
        emuCore = s.emuCore.copy(enableNoInterlacingPatches = d.emuCore.enableNoInterlacingPatches),
        output = s.output.copy(deinterlaceMode = d.output.deinterlaceMode),
        graphics = s.graphics.copy(displayBilinear = d.graphics.displayBilinear),
    )
}

internal fun noBlurActive(s: Settings): Boolean =
    s.display.antiBlur && s.display.disableInterlaceOffset && s.emuCore.enableNoInterlacingPatches &&
        s.output.deinterlaceMode == DEINTERLACE_ADAPTIVE_TFF && s.graphics.displayBilinear == DISPLAY_FILTER_SHARP

// Index into the Deinterlacing row (Auto, Off, Weave TFF, Weave BFF, Bob TFF, Bob BFF,
// Blend TFF, Blend BFF, Adapt TFF, Adapt BFF), which is PCSX2's GSInterlaceMode order.
private const val DEINTERLACE_ADAPTIVE_TFF = 8
// Index into the Display filter row (Nearest, Smooth, Sharp).
private const val DISPLAY_FILTER_SHARP = 2

@Composable
internal fun QuickProfilesSection(s: Settings, apply: (Settings) -> Unit) {
    val profiles = graphicsProfiles()
    SegmentedRow(
        label = str("nexus.profile.label"),
        options = profiles.map { str(it.labelKey) },
        selectedIndex = profiles.indexOfFirst { it.matches(s) },
        description = str("nexus.profile.description"),
        onChange = { i -> apply(profiles[i].applyTo(s)) },
    )
    SettingsDivider()
    ToggleRow(
        str("nexus.noBlur.label"),
        noBlurActive(s),
        description = str("nexus.noBlur.description"),
    ) { on ->
        apply(if (on) noBlurApplied(s) else noBlurRemoved(s))
    }
    SettingsDivider()
    val auto = remember { mutableStateOf(BlurRemoval.isEnabled()) }
    ToggleRow(
        str("nexus.blurPatches.label"),
        auto.value,
        description = str("nexus.blurPatches.description"),
    ) { on ->
        auto.value = on
        BlurRemoval.setEnabled(on)
    }
}
