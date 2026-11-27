package com.armsx2.navigation

import androidx.compose.runtime.mutableStateOf
import com.armsx2.GameInfo

sealed interface AppRoute {
    data object Home : AppRoute
    // Nexus2: the "Jogos" tab of the bottom bar (the full library).
    data object Library : AppRoute
    // Nexus2: the "Ferramentas" tab, a hub over the manager screens below.
    data object Tools : AppRoute
    // Nexus2: one game's own page (cover, play, saves, per-game settings).
    data class GameDetail(val game: GameInfo) : AppRoute
    // Nexus2: Tools sub-pages.
    data object Diagnostics : AppRoute
    data class Settings(
        val category: SettingsCategory = SettingsCategory.General,
        val game: GameInfo? = null,
    ) : AppRoute
    // Carries an optional game so the per-game BIOS picker can key on it directly
    // (from the library long-press) without the game being loaded; null = global,
    // opened from the drawer (falls back to the currently loaded game if any).
    // returnToArcade: opened from the arcade screen's BIOS step, so Back goes back there.
    data class BiosManager(val game: GameInfo? = null, val returnToArcade: Boolean = false) : AppRoute
    data class MemoryCardManager(val game: GameInfo? = null) : AppRoute
    // Every game's saves, grouped by game. focusSerial scrolls the list to one game's section (the
    // library's long-press menu); null, from the drawer, opens at the top. It is the same list either way.
    data class SaveManager(val focusSerial: String? = null) : AppRoute
    data object ControllerManager : AppRoute
    data object PatchManager : AppRoute
    data object TextureManager : AppRoute
    data object Arcade : AppRoute
    data object Achievements : AppRoute
    data object Language : AppRoute
    data object News : AppRoute
    data object Friends : AppRoute
    // Joining a friend's game. Hosting lives in the in-game menu instead, since only a running game
    // can be hosted.
    data object RemotePlay : AppRoute
    data object About : AppRoute
}

enum class SettingsCategory {
    General,
    Info,
    Performance,
    Graphics,
    Audio,
    Controls,
    Hotkeys,
    Network,
    OnScreen,
    Skins,
    Advanced,
    Patches,
    About,
}

/** Nexus2: the four sections of the bottom bar / side rail, in bar order. */
enum class MainTab(val route: AppRoute) {
    Home(AppRoute.Home),
    Library(AppRoute.Library),
    Tools(AppRoute.Tools),
    Settings(AppRoute.Settings()),
}

/** The bar section this route IS, or null for a sub-screen (managers, per-game settings, About…),
 *  which shows no bar and has its own back button. */
fun AppRoute.mainTab(): MainTab? = when (this) {
    AppRoute.Home -> MainTab.Home
    AppRoute.Library -> MainTab.Library
    AppRoute.Tools -> MainTab.Tools
    is AppRoute.Settings -> if (game == null) MainTab.Settings else null
    else -> null
}

object UiNavigator {
    val route = mutableStateOf<AppRoute>(AppRoute.Home)
    val drawerOpen = mutableStateOf(false)

    // Nexus2: where sub-screens' back buttons return to, innermost last. Filled only by [openFrom]
    // (Tools hub, game page, Home); every other way in (drawer, game menu) clears it, which means
    // Home, as before.
    private val parents = ArrayDeque<AppRoute>()
    private fun popParent(): AppRoute? = parents.removeLastOrNull()

    fun navigate(destination: AppRoute) {
        parents.clear()
        go(destination)
    }

    /** Open [destination] so that its back button returns to the screen it was opened from. */
    fun openFrom(destination: AppRoute) {
        val from = route.value
        go(destination)
        if (from != destination) parents.addLast(from)
    }

    /** Switch bar section. No sub-menu blip: it is a peer screen, not a level deeper. */
    fun selectTab(tab: MainTab) {
        parents.clear()
        route.value = tab.route
        drawerOpen.value = false
    }

    /** A sub-screen's own back button. */
    fun up() {
        route.value = popParent() ?: AppRoute.Home
    }

    private fun go(destination: AppRoute) {
        val changed = route.value != destination
        route.value = destination
        drawerOpen.value = false
        // "Entering a settings menu / sub-screen" blip — but not for just returning Home.
        if (changed && destination != AppRoute.Home) {
            com.armsx2.MenuSfx.play(com.armsx2.MenuSfx.Event.SUBMENU)
        }
    }

    fun home() = navigate(AppRoute.Home)

    fun back(): Boolean {
        if (drawerOpen.value) {
            drawerOpen.value = false
            return true
        }
        when (val current = route.value) {
            AppRoute.Language -> {
                route.value = AppRoute.Settings(SettingsCategory.General)
                return true
            }
            AppRoute.About -> {
                route.value = AppRoute.Settings(SettingsCategory.General)
                return true
            }
            AppRoute.Home -> Unit
            is AppRoute.BiosManager -> {
                route.value = if (current.returnToArcade) AppRoute.Arcade else (popParent() ?: AppRoute.Home)
                return true
            }
            else -> {
                route.value = popParent() ?: AppRoute.Home
                return true
            }
        }
        drawerOpen.value = true
        return true
    }
}
