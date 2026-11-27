package com.armsx2

import androidx.compose.runtime.mutableStateOf
import com.armsx2.runtime.MainActivityRuntime

/**
 * Nexus2: games the player marked as favourite (game menu → Favourite). Stored by game URI, the
 * same key [HiddenGames] uses, so a favourite survives rescans and is lost only when the file
 * itself moves. Order is the order they were added, newest last.
 */
object Favorites {
    private const val KEY = "nexus.favorites"
    private const val SEP = "\n"
    val keys = mutableStateOf<List<String>>(emptyList())

    private fun keyOf(game: GameInfo) = game.uri.toString()

    fun load() {
        keys.value = runCatching {
            MainActivityRuntime.prefs.getString(KEY, "").orEmpty().split(SEP).filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun isFavorite(game: GameInfo): Boolean = keys.value.contains(keyOf(game))

    fun set(game: GameInfo, favorite: Boolean) {
        val k = keyOf(game)
        val next = keys.value.filter { it != k }.let { if (favorite) it + k else it }
        keys.value = next
        runCatching { MainActivityRuntime.prefs.edit().putString(KEY, next.joinToString(SEP)).apply() }
    }

    fun toggle(game: GameInfo) = set(game, !isFavorite(game))

    /** [library] filtered to favourites, most recently added first. */
    fun of(library: List<GameInfo>): List<GameInfo> {
        val order = keys.value.withIndex().associate { (i, k) -> k to i }
        return library.filter { keyOf(it) in order }.sortedByDescending { order[keyOf(it)] ?: -1 }
    }
}

/**
 * Nexus2: the same disc imported more than once (two dumps, an ISO and a CHD of one game). Games
 * are grouped by serial, the identity the core itself uses for settings and saves; entries
 * without a serial (homebrew ELFs, unidentified files) are never called duplicates.
 */
object Duplicates {
    fun groups(library: List<GameInfo>): List<List<GameInfo>> =
        library.filter { !it.serial.isNullOrBlank() }
            .groupBy { it.serial!!.trim().uppercase() }
            .values
            .filter { it.size > 1 }

    fun keysOf(library: List<GameInfo>): Set<String> =
        groups(library).flatten().map { it.uri.toString() }.toSet()
}
