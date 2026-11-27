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
 * below except in Compatibility (higher levels cost a lot on tile-based GPUs), and anisotropic
 * filtering stays off. "Custom" is any combination that matches none of them.
 */
internal data class GraphicsProfile(
    val labelKey: String,
    val upscale: Float,
    val blending: Int,
    val anisotropy: Int,
    val mtvu: Boolean,
)

internal fun graphicsProfiles(): List<GraphicsProfile> = listOf(
    // Accuracy first: native resolution, Medium blending, and MTVU off (the threaded VU1 is the
    // one speed option some games are known to break on).
    GraphicsProfile("nexus.profile.compatibility", 1.0f, 2, 0, false),
    // 2x (720p class) with Basic blending: the recommended default for a Dimensity 8400 / Mali-G720.
    GraphicsProfile("nexus.profile.balanced", 2.0f, 1, 0, true),
    // Native resolution, Minimum blending, MTVU on: the lightest load on the GPU and CPU.
    GraphicsProfile("nexus.profile.performance", 1.0f, 0, 0, true),
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

/** Label key of the profile [s] matches, or the Custom one. */
internal fun currentProfileKey(s: Settings): String =
    graphicsProfiles().firstOrNull { it.matches(s) }?.labelKey ?: "nexus.profile.custom"

// Index into the Deinterlacing row (Auto, Off, Weave TFF, Weave BFF, Bob TFF, Bob BFF,
// Blend TFF, Blend BFF, Adapt TFF, Adapt BFF), which is PCSX2's GSInterlaceMode order.
private const val DEINTERLACE_ADAPTIVE_TFF = 8
// Index into the Display filter row (Nearest, Smooth, Sharp).
private const val DISPLAY_FILTER_SHARP = 2

@Composable
internal fun QuickProfilesSection(
    s: Settings,
    // Nexus2: the bundled blur-patch switch is app-wide (BlurRemoval), so a per-game page must not
    // show it as if it were a setting of that game. Defaults to hidden whenever a game is in scope.
    showGlobalBlurPatches: Boolean = LocalGameOverrideScope.current == null,
    apply: (Settings) -> Unit,
) {
    val profiles = graphicsProfiles()
    // "Custom" is not a preset: it is shown whenever the values match none of the profiles (the
    // player tuned something by hand), and picking it changes nothing, so those tweaks are kept.
    val matched = profiles.indexOfFirst { it.matches(s) }
    SegmentedRow(
        label = str("nexus.profile.label"),
        options = profiles.map { str(it.labelKey) } + str("nexus.profile.custom"),
        selectedIndex = if (matched >= 0) matched else profiles.size,
        description = str("nexus.profile.description"),
        onChange = { i -> profiles.getOrNull(i)?.let { apply(it.applyTo(s)) } },
    )
    SettingsDivider()
    ToggleRow(
        str("nexus.noBlur.label"),
        noBlurActive(s),
        description = str("nexus.noBlur.description"),
    ) { on ->
        apply(if (on) noBlurApplied(s) else noBlurRemoved(s))
    }
    if (!showGlobalBlurPatches) return
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
