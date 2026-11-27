package com.armsx2.ui.settingshub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.armsx2.GameInfo
import com.armsx2.i18n.str
import com.armsx2.navigation.SettingsCategory
import com.armsx2.ui.common.ArmsBackdrop
import com.armsx2.ui.common.ArmsTopBar
import com.armsx2.ui.common.GlassPanel
import com.armsx2.ui.common.RoundAction
import com.armsx2.ui.common.SectionTitle
import com.armsx2.ui.settings.controllerFocusable
import com.armsx2.ui.settings.AppTab
import com.armsx2.ui.settings.AudioTab
import com.armsx2.ui.settings.FixesTab
import com.armsx2.ui.settings.HotkeysTab
import com.armsx2.ui.settings.NetworkTab
import com.armsx2.ui.settings.OverlayTab
import com.armsx2.ui.settings.PadTab
import com.armsx2.ui.settings.PerformanceTab
import com.armsx2.ui.settings.RendererTab
import com.armsx2.ui.settings.SegmentedRow
import com.armsx2.ui.settings.SkinsTab
import com.armsx2.ui.settings.LocalSettingsScrollState
import com.armsx2.ui.settings.LocalSettingsSearchOpenSections

private data class SettingsSection(val category: SettingsCategory, val titleKey: String, val glyph: String)
private data class PendingSettingsJump(val category: SettingsCategory, val label: String, val sections: List<String>)

/**
 * Lets L1/R1 flick between settings tabs, the way the old Refresh UI did.
 *
 * The callback is registered by [SettingsScreen] while it is composed and cleared on dispose,
 * so in-game shoulder presses still reach the pad untouched. It lives here rather than in
 * Compose focus handling because a shoulder button never reaches a Composable — the overlay
 * nav in MainActivityRuntime.dispatchKeyEvent consumes gamepad keys first, which is the same
 * reason hotkey and pad-bind capture are handled there.
 */
object SettingsCategoryNav {
    /** -1 = previous tab, +1 = next. Null whenever the settings screen isn't showing. */
    @Volatile
    var cycle: ((Int) -> Unit)? = null
}

/** Retains the settings page's scroll offset across close/reopen. The selected category is
 *  remembered by SettingsViewModel (#729), so restoring this one offset returns you to where you were (esp.
 *  useful in long lists like Fixes) instead of snapping to the top every time. */
private object SettingsScrollMemory {
    var lastOffset = 0
}

@Composable
fun SettingsScreen(
    initialCategory: SettingsCategory,
    game: GameInfo?,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit = {},
    /** Nexus2: name of the [SettingsGroup] page this screen shows; null = every group (the
     *  in-game and legacy entries). */
    group: String? = null,
    viewModel: SettingsViewModel = viewModel(),
) {
    val onlyGroup = group?.let { g -> SettingsGroup.entries.firstOrNull { it.name == g } }
    // Effective settings scope. Starts on the scope we were opened for (per-game when a game was
    // passed, else global). A running game — even when opened from the global entry — becomes the
    // per-game target so the top toggle can switch between Global and that game. Switching just
    // re-load()s the ViewModel, which re-hydrates settingsState/scope/serial together (no bleed).
    val scopeContext = game ?: com.armsx2.runtime.MainActivityRuntime.currentGame.value
    var scopeGame by remember(game?.uri) { mutableStateOf(game) }
    // Launch the game whose per-game settings are on screen (the "Play" action in the top bar).
    // Return to Home first so the settings route isn't left on the back stack behind the game.
    val playScoped: (com.armsx2.GameInfo) -> Unit = { g ->
        com.armsx2.navigation.UiNavigator.navigate(com.armsx2.navigation.AppRoute.Home)
        // Match the working library cover-tap launch: a file:// game must be passed as a
        // bare filesystem path, not "file:///…", or the native boot rejects the path and
        // kicks straight back to the library (the flash-then-library symptom). Same
        // conversion HomeViewModel.launch / HomeShortcuts use.
        val launchPath = if (g.uri.scheme == "file") g.uri.path ?: g.uri.toString() else g.uri.toString()
        com.armsx2.runtime.MainActivityRuntime.launchGame(launchPath, g)
    }
    var showReset by remember { mutableStateOf(false) }
    var showResetGame by remember { mutableStateOf(false) }
    // Settings-search "jump to control": holds the resolved label of the target row while the
    // freshly-switched tab composes + lays out, then selects it (highlight + scroll into view).
    var pendingJump by remember { mutableStateOf<PendingSettingsJump?>(null) }
    // Nexus2: Simple (one-tap profiles) or Advanced (every tab). Kept across visits.
    var simpleMode by remember { mutableStateOf(false) }
    val setSimple: (Boolean) -> Unit = { v -> simpleMode = v; NexusSettingsMode.setSimple(v) }
    val openSearch = {
        SettingsSearch.open { category, label, sections ->
            // A search result is a row on an Advanced tab.
            setSimple(false)
            viewModel.selectCategory(category)
            pendingJump = PendingSettingsJump(category, label, sections)
        }
    }
    // Nexus2: the Ajustes page's search button opens this screen and asks for the search.
    LaunchedEffect(Unit) {
        if (SettingsSearchLauncher.pending) {
            SettingsSearchLauncher.pending = false
            openSearch()
        }
    }
    val screenScroll = rememberScrollState(initial = SettingsScrollMemory.lastOffset)
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { SettingsScrollMemory.lastOffset = screenScroll.value }
    }
    LaunchedEffect(initialCategory, game?.uri) { viewModel.load(initialCategory, game) }
    // When the controller focus returns to the category-chip row (the top-most
    // navigable element), snap the whole page back to the top so the title bar +
    // chips are fully visible — per-row bringIntoView otherwise leaves the header
    // scrolled off after diving deep into a tab and coming back up.
    // ★ Skip the FIRST run. A LaunchedEffect also fires on initial composition, and on open the
    // selected item IS the chip row (index 0) — so this snapped straight back to the top and threw
    // away the offset SettingsScrollMemory had just restored. That is the reported "menu reopens at
    // the topmost setting instead of where you left it" (confirmed by bmdhacks). Only an actual
    // selection CHANGE, once the screen is up, should pull the header back into view.
    val chipSnapArmed = remember { mutableStateOf(false) }
    LaunchedEffect(com.armsx2.ui.settings.SettingsControllerNav.selectedIndex.intValue) {
        if (!chipSnapArmed.value) {
            chipSnapArmed.value = true
            return@LaunchedEffect
        }
        if (com.armsx2.ui.settings.SettingsControllerNav.currentSelectedId()?.startsWith("settings.chip.") == true) {
            screenScroll.animateScrollTo(0)
        }
    }
    val ui = viewModel.uiState.value
    val contentReady = ui.game?.uri?.toString() == scopeGame?.uri?.toString()
    val displayedCategory = if (scopeGame != null && ui.category == SettingsCategory.General) {
        SettingsCategory.Performance
    } else {
        ui.category
    }
    // Wait for the destination tab, not merely for a row with the same label in the old tab.
    // The jump's section chain (LocalSettingsSearchOpenSections, below) opens the sections that
    // wrap the target; once the row composes and registers, its own bringIntoView places the
    // selected control on-screen.
    LaunchedEffect(pendingJump, displayedCategory, contentReady) {
        val jump = pendingJump ?: return@LaunchedEffect
        if (!contentReady || displayedCategory != jump.category) return@LaunchedEffect
        var tries = 0
        while (tries < 40 && !com.armsx2.ui.settings.SettingsControllerNav.selectByLabel(jump.label)) {
            kotlinx.coroutines.delay(25)
            tries++
        }
        pendingJump = null
    }

    // L1/R1 tab cycling — registered only while this screen is composed. The visible-tab
    // filter has to match SettingsCategoryBar's, or the shoulders would land on a tab the
    // chip row doesn't show. About is always skipped: it navigates to another screen, which
    // is not what flicking a shoulder button should do.
    val gameSpecific = scopeGame != null
    DisposableEffect(displayedCategory, gameSpecific, simpleMode) {
        SettingsCategoryNav.cycle = if (simpleMode) null else ({ direction ->
            val tabs = settingsSections().map { it.category }.filterNot {
                it == SettingsCategory.About ||
                    (gameSpecific && it == SettingsCategory.General) ||
                    (!gameSpecific && it == SettingsCategory.Info)
            }.filter { c ->
                (onlyGroup == null || c in onlyGroup.categories) &&
                    (!gameSpecific || groupOf(c)?.perGame == true)
            }
            val current = tabs.indexOf(displayedCategory)
            if (current >= 0 && tabs.isNotEmpty()) {
                viewModel.selectCategory(tabs[(current + direction + tabs.size) % tabs.size])
            }
        })
        onDispose { SettingsCategoryNav.cycle = null }
    }

    ArmsBackdrop {
        CompositionLocalProvider(LocalSettingsScrollState provides screenScroll) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(screenScroll)
                    .padding(bottom = 8.dp),
            ) {
                ArmsTopBar(
                    title = scopeGame?.title ?: onlyGroup?.let { str(it.titleKey) } ?: str("action.settings"),
                    subtitle = if (scopeGame == null) str("scope.global") else str("scope.game"),
                    leading = {
                        // Registered in the settings nav so a controller reaches it (up from the chips).
                        Box(Modifier.controllerFocusable("settings.action.back", CircleShape, onConfirm = onBack)) {
                            RoundAction("←", str("action.back"), onBack)
                        }
                    },
                    actions = {
                        // Top-bar actions registered in the settings nav so a controller can reach them
                        // (D-pad up from the category chips, then left/right across the bar), not just touch.
                        // Play: only in per-game scope — launches the game these settings belong to.
                        scopeGame?.let { g ->
                            Box(Modifier.controllerFocusable("settings.action.play", CircleShape, onConfirm = { playScoped(g) })) {
                                RoundAction(
                                    "▶", str("action.play"), { playScoped(g) },
                                    glyphColor = androidx.compose.ui.graphics.Color(0xFF3DDC84),
                                )
                            }
                        }
                        Box(Modifier.controllerFocusable("settings.action.search", CircleShape, onConfirm = openSearch)) {
                            RoundAction("⌕", str("action.search"), openSearch)
                        }
                        // Only offer Reset where there is something to reset. Controls owns no
                        // Settings fields (its state lives in ControllerMappings) but IS
                        // resettable via resetAllControls — it was previously excluded here AND
                        // its own resetTunables had no call site, so controls had no working
                        // reset at all. Hotkeys/Skins still have none of their own.
                        if (categoryHasResettableSettings(displayedCategory) ||
                            displayedCategory == SettingsCategory.Controls) {
                            Box(Modifier.controllerFocusable("settings.action.reset", CircleShape, onConfirm = { showReset = true })) {
                                RoundAction("↺", str("action.reset"), { showReset = true })
                            }
                        }
                    },
                )
                if (scopeContext != null) {
                    // Global ↔ Per-Game switch. Re-load()s the ViewModel for the chosen scope, which
                    // re-hydrates the shared settingsState/scope/serial together (every tab follows).
                    Box(Modifier.padding(horizontal = 8.dp)) {
                        SegmentedRow(
                            label = str("settings.scope.label"),
                            options = listOf(str("scope.global"), str("scope.game")),
                            selectedIndex = if (scopeGame == null) 0 else 1,
                            description = scopeContext.title,
                            onChange = { idx ->
                                val next = if (idx == 0) null else scopeContext
                                if ((next == null) != (scopeGame == null)) {
                                    scopeGame = next
                                    viewModel.load(ui.category, next)
                                }
                            },
                        )
                    }
                }
                if (contentReady && simpleMode) {
                    Spacer(Modifier.height(10.dp))
                    GlassPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), contentPadding = 16.dp) {
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                            SectionTitle(str("nexus.settings.simple.title"))
                            Spacer(Modifier.height(12.dp))
                            com.armsx2.ui.settings.QuickProfilesSection(viewModel.settings.value) {
                                com.armsx2.ui.InGameOverlay.saveSettings(it)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if (contentReady && !simpleMode && scopeGame != null) {
                    NexusGameSettingsHeader(
                        game = scopeGame!!,
                        settings = viewModel.settings.value,
                        onResetAll = { showResetGame = true },
                    )
                }
                if (contentReady && !simpleMode) {
                    SettingsCategoryBar(
                        selected = displayedCategory,
                        gameSpecific = scopeGame != null,
                        onlyGroup = onlyGroup,
                        onSelect = { category ->
                            if (category == SettingsCategory.About) onOpenAbout() else viewModel.selectCategory(category)
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    // Nexus2: in a game's scope, rows this game overrides get "↺ Global".
                    val overriddenFields = remember(viewModel.settings.value, scopeGame) { viewModel.overriddenFields() }
                    CompositionLocalProvider(
                        com.armsx2.ui.settings.LocalGameOverrideScope provides scopeGame?.let {
                            com.armsx2.ui.settings.GameOverrideScope(overriddenFields) { f -> viewModel.revertFieldToGlobal(f) }
                        },
                        LocalSettingsSearchOpenSections provides (
                            pendingJump?.takeIf { it.category == displayedCategory }?.sections?.toSet()
                                ?: emptySet()
                        ),
                    ) {
                        SettingsPanel(displayedCategory, viewModel, Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        // Jump-to-top for long pages — a game's giant cheat list especially. Floats over the
        // content once you're well down the page and taps smooth-scroll home. Touch affordance;
        // controller users already get the chip-row snap-to-top + D-pad scrolling.
        SettingsScrollTopButton(screenScroll)
        // Controller-native search overlay (own keyboard + result nav); inside ArmsBackdrop's
        // BoxScope so its keyboard can dock to the bottom edge over everything.
        SettingsSearchOverlay(this, scopeGame != null)
    }

    if (showResetGame) {
        com.armsx2.ui.common.ConfirmOverlay(
            title = str("nexus.gameSettings.resetAll"),
            message = str("nexus.gameSettings.resetAll.desc"),
            confirmLabel = str("action.reset"),
            destructive = true,
            idPrefix = "settings-reset-game",
            onConfirm = {
                viewModel.resetWholeGame()
                showResetGame = false
            },
            onDismiss = { showResetGame = false },
        )
    }
    if (showReset) {
        // ConfirmOverlay, not AlertDialog: a Compose dialog is its own window and swallows
        // controller keys, so the old prompt could only be answered by touch.
        com.armsx2.ui.common.ConfirmOverlay(
            title = str("action.reset"),
            // Name the TAB being reset. The old dialog said only "Global"/"Game", so users
            // reasonably assumed Reset applied to the page they were on — and it didn't.
            message = categoryTitle(displayedCategory) + " · " +
                (if (scopeGame == null) str("scope.global") else str("scope.game")),
            confirmLabel = str("action.reset"),
            destructive = true,
            idPrefix = "settings-reset",
            onConfirm = {
                if (displayedCategory == SettingsCategory.Controls) {
                    // Controls lives in ControllerMappings, outside the Settings object, so it
                    // resets through its own path. Per-game scope drops that game's overrides.
                    com.armsx2.input.ControllerMappings.resetAllControls(scopeGame?.settingsKey)
                } else {
                    viewModel.resetCurrentScope(displayedCategory)
                }
                showReset = false
            },
            onDismiss = { showReset = false },
        )
    }
}

/** Floating "back to top" affordance for long settings pages — a game's giant cheat list is the
 *  worst case. Reads the scroll position through a derivedStateOf so only THIS button recomposes
 *  as you scroll (never the already-heavy page below it), and only appears once you're a good way
 *  down. Touch-only by design: controller users reach the top via the chip-row snap + D-pad. */
@Composable
private fun BoxScope.SettingsScrollTopButton(scroll: ScrollState) {
    val scope = rememberCoroutineScope()
    // ~1.5 screenfuls down before it shows, so short pages never get a floating button.
    val show by remember { derivedStateOf { scroll.value > 1500 } }
    if (show) {
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 22.dp)) {
            RoundAction(
                glyph = "↑",
                description = str("action.scrollTop"),
                onClick = { scope.launch { scroll.animateScrollTo(0) } },
                buttonSize = 52.dp,
            )
        }
    }
}

@Composable
private fun SettingsPanel(
    category: SettingsCategory,
    viewModel: SettingsViewModel,
    modifier: Modifier,
) {
    GlassPanel(modifier = modifier.padding(horizontal = 8.dp), contentPadding = 16.dp) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            SectionTitle(categoryTitle(category))
            Spacer(Modifier.height(12.dp))
            // Only the tabs that own game settings have rows that take a mark.
            if (categoryHasResettableSettings(category)) {
                com.armsx2.ui.settings.OverrideLegend()
                Spacer(Modifier.height(8.dp))
            }
            Box(Modifier.fillMaxWidth()) {
                CategoryContent(category, viewModel)
            }
        }
    }
}

@Composable
private fun SettingsCategoryBar(
    selected: SettingsCategory,
    gameSpecific: Boolean,
    onlyGroup: SettingsGroup? = null,
    onSelect: (SettingsCategory) -> Unit,
) {
    // Row + horizontalScroll (NOT LazyRow): controllerFocusable registers each tab via a
    // SideEffect that only runs for COMPOSED children. A LazyRow leaves every off-screen
    // tab (Skins / Advanced, past On-Screen) unregistered and unreachable, so
    // the controller got stuck at the last visible tab. A plain Row composes them all;
    // each selected chip's bringIntoView then scrolls it into view as the selector moves.
    val sections = settingsSections().filterNot {
        // General is redundant per-game (redirects to Performance); Info only makes
        // sense for a specific game, so hide it in the global settings.
        (gameSpecific && it.category == SettingsCategory.General) ||
            (!gameSpecific && it.category == SettingsCategory.Info) ||
            (gameSpecific && it.category == SettingsCategory.About) ||
            // Nexus2: a game's page has Graphics, Emulation, Audio and Controls only; a group
            // page only its own tabs.
            (gameSpecific && groupOf(it.category)?.perGame != true) ||
            (onlyGroup != null && it.category !in onlyGroup.categories)
    }
    // Nexus2: first the groups (skipped on a group page), then (only when the group holds several) its tabs.
    val visible = sections.map { it.category }.toSet()
    val groups = SettingsGroup.entries.filter { g -> g.categories.any { it in visible } }
    val selectedGroup = groupOf(selected)
    if (onlyGroup == null) Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.forEach { group ->
                    val active = group == selectedGroup
                    val target = group.categories.first { it in visible }
                    val bringIntoView = remember { BringIntoViewRequester() }
                    LaunchedEffect(active) {
                        if (active) runCatching { bringIntoView.bringIntoView() }
                    }
                    FilterChip(
                        modifier = Modifier.height(40.dp)
                            .bringIntoViewRequester(bringIntoView)
                            .controllerFocusable(
                                "settings.chip.group.${group.name}",
                                RoundedCornerShape(12.dp),
                                onConfirm = { if (!active) onSelect(target) },
                            ),
                        selected = active,
                        onClick = { if (!active) onSelect(target) },
                        label = { Text(str(group.titleKey), maxLines = 1, style = MaterialTheme.typography.labelLarge) },
                        leadingIcon = {
                            Box(Modifier.size(17.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    group.glyph,
                                    fontSize = 13.sp,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
    val subSections = sections.filter { selectedGroup != null && it.category in selectedGroup.categories }
    if (subSections.size > 1) Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Edge spacing belongs to the scrollable CONTENT, not its viewport. This
            // lets the final chip scroll fully into view before the trailing inset.
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                subSections.forEach { section ->
                    val active = section.category == selected
                    // Keep the SELECTED chip on-screen. controllerFocusable's bringIntoView
                    // fires on FOCUS, but L1/R1 changes the selection without moving focus —
                    // so past roughly Network the active tab scrolled off the right edge and
                    // you were flicking blind. Requesting it whenever selection lands here
                    // covers both the shoulder path and a touch tap.
                    val bringIntoView = remember { BringIntoViewRequester() }
                    LaunchedEffect(active) {
                        if (active) runCatching { bringIntoView.bringIntoView() }
                    }
                    FilterChip(
                        modifier = Modifier.height(36.dp)
                            .bringIntoViewRequester(bringIntoView)
                            .controllerFocusable(
                                "settings.chip.${section.category.name}",
                                RoundedCornerShape(11.dp),
                                onConfirm = { onSelect(section.category) },
                            ),
                        selected = active,
                        onClick = { onSelect(section.category) },
                        label = { Text(str(section.titleKey), maxLines = 1, style = MaterialTheme.typography.labelLarge) },
                        leadingIcon = {
                            Box(Modifier.size(17.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    section.glyph,
                                    fontSize = 13.sp,
                                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        shape = RoundedCornerShape(11.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconColor = MaterialTheme.colorScheme.primary,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

/** Nexus2: the seven sections of Settings. Each holds one or more of the existing tabs, which keep
 *  every option they had; the order here is also the L1/R1 order. */
internal enum class SettingsGroup(
    val titleKey: String,
    val glyph: String,
    val categories: List<SettingsCategory>,
    /** Offered in a game's own settings page. */
    val perGame: Boolean,
) {
    Graphics("nexus.settings.group.graphics", "◫", listOf(SettingsCategory.Graphics, SettingsCategory.Patches), true),
    Emulation("nexus.settings.group.emulation", "↯", listOf(SettingsCategory.Performance, SettingsCategory.Advanced), true),
    Audio("nexus.settings.group.audio", "♫", listOf(SettingsCategory.Audio), true),
    Controls("nexus.settings.group.controls", "⌁", listOf(SettingsCategory.Controls, SettingsCategory.Hotkeys, SettingsCategory.Skins), true),
    System("nexus.settings.group.system", "ⓘ", listOf(SettingsCategory.General, SettingsCategory.Info, SettingsCategory.Network), false),
    Diagnostics("nexus.settings.group.diagnostics", "⊕", listOf(SettingsCategory.OnScreen), false),
}

private val SectionByCategory = mapOf(
    SettingsCategory.General to SettingsSection(SettingsCategory.General, "tab.app", "⌂"),
    SettingsCategory.Info to SettingsSection(SettingsCategory.Info, "tab.info", "ⓘ"),
    SettingsCategory.Performance to SettingsSection(SettingsCategory.Performance, "tab.performance", "↯"),
    SettingsCategory.Graphics to SettingsSection(SettingsCategory.Graphics, "tab.renderer", "◫"),
    SettingsCategory.Audio to SettingsSection(SettingsCategory.Audio, "tab.audio", "♫"),
    SettingsCategory.Controls to SettingsSection(SettingsCategory.Controls, "tab.controls", "⌁"),
    SettingsCategory.Hotkeys to SettingsSection(SettingsCategory.Hotkeys, "tab.hotkeys", "⌘"),
    SettingsCategory.Skins to SettingsSection(SettingsCategory.Skins, "tab.skins", "◈"),
    SettingsCategory.Network to SettingsSection(SettingsCategory.Network, "tab.network", "◎"),
    SettingsCategory.OnScreen to SettingsSection(SettingsCategory.OnScreen, "tab.overlay", "⊕"),
    SettingsCategory.Advanced to SettingsSection(SettingsCategory.Advanced, "tab.fixes", "⌘"),
    SettingsCategory.Patches to SettingsSection(SettingsCategory.Patches, "tab.patches", "✦"),
)

private fun settingsSections() = SettingsGroup.entries.flatMap { g -> g.categories.mapNotNull { SectionByCategory[it] } }

internal fun groupOf(category: SettingsCategory): SettingsGroup? =
    SettingsGroup.entries.firstOrNull { category in it.categories }

/** Nexus2: whether Settings opens in Simple mode (profiles only). */
internal object NexusSettingsMode {
    private const val KEY = "nexus.settings.simple"
    fun isSimple(): Boolean = runCatching { com.armsx2.runtime.MainActivityRuntime.prefs.getBoolean(KEY, true) }.getOrDefault(true)
    fun setSimple(v: Boolean) {
        runCatching { com.armsx2.runtime.MainActivityRuntime.prefs.edit().putBoolean(KEY, v).apply() }
    }
}

@Composable
private fun CategoryContent(category: SettingsCategory, viewModel: SettingsViewModel) {
    when (category) {
        SettingsCategory.General -> AppTab()
        SettingsCategory.Info -> com.armsx2.ui.settings.InfoTab(viewModel.uiState.value.game)
        SettingsCategory.Performance -> PerformanceTab(viewModel.settings)
        SettingsCategory.Graphics -> RendererTab(viewModel.settings)
        SettingsCategory.Audio -> AudioTab(viewModel.settings)
        SettingsCategory.Controls -> PadTab(viewModel.settings)
        SettingsCategory.Hotkeys -> HotkeysTab(viewModel.settings)
        SettingsCategory.Network -> NetworkTab(viewModel.settings)
        SettingsCategory.OnScreen -> OverlayTab(viewModel.settings)
        SettingsCategory.Skins -> SkinsTab(viewModel.settings)
        SettingsCategory.Advanced -> FixesTab(viewModel.settings)
        SettingsCategory.Patches -> com.armsx2.ui.patches.PatchesSettingsTab(viewModel.uiState.value.game)
        SettingsCategory.About -> Unit
    }
}

@Composable
internal fun categoryTitle(category: SettingsCategory): String = when (category) {
    SettingsCategory.General -> str("tab.app")
    SettingsCategory.Info -> str("tab.info")
    SettingsCategory.Performance -> str("tab.performance")
    SettingsCategory.Graphics -> str("tab.renderer")
    SettingsCategory.Audio -> str("tab.audio")
    SettingsCategory.Controls -> str("tab.controls")
    SettingsCategory.Hotkeys -> str("tab.hotkeys")
    SettingsCategory.Network -> str("tab.network")
    SettingsCategory.OnScreen -> str("tab.overlay")
    SettingsCategory.Skins -> str("tab.skins")
    SettingsCategory.Advanced -> str("tab.fixes")
    SettingsCategory.Patches -> str("patches.dialog.patchesAndCheats")
    SettingsCategory.About -> str("about.title")
}


/**
 * Nexus2: header of a game's own settings: cover, name, region, the profile its values match,
 * which rows are inherited vs. custom, and "restore this game's defaults" (back to Global).
 */
@Composable
private fun NexusGameSettingsHeader(game: com.armsx2.GameInfo, settings: com.armsx2.config.Settings, onResetAll: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.armsx2.ui.common.GameCoverArt(game, Modifier.size(width = 72.dp, height = 102.dp))
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    game.displayTitle(com.armsx2.EnglishTitles.enabled.value),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    game.region?.let { com.armsx2.ui.nexus.NexusChip(it) }
                    com.armsx2.ui.nexus.NexusChip(
                        str(com.armsx2.ui.settings.currentProfileKey(settings)),
                        colors.primary,
                    )
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(str("nexus.gameSettings.inherit"), fontSize = 13.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant)
        Spacer(Modifier.size(10.dp))
        com.armsx2.ui.nexus.NexusSecondaryButton(str("nexus.gameSettings.resetAll"), Modifier.fillMaxWidth(), onClick = onResetAll)
        Spacer(Modifier.size(4.dp))
        // The same one-tap profiles as the global page, written to this game only.
        com.armsx2.ui.settings.QuickProfilesSection(settings, showGlobalBlurPatches = false) { com.armsx2.ui.InGameOverlay.saveSettings(it) }
    }
}
