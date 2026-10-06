package com.pokedaisey.app

import java.io.File

/**
 * Shared "does this ROM have a fetchable cover, and can we get it" check —
 * used by both LibraryActivity's Refresh backfill and SettingsActivity's
 * Cover Art screen (so hitting Save can show live per-ROM progress right
 * there, instead of the user having to separately open Library and tap
 * Refresh to find out whether the key actually works).
 */
object CoverArtSync {
    enum class Status { FETCHED, REPLACED, KEPT, ALREADY_CACHED, MANUAL_COVER, UNKNOWN_GAME, FETCH_FAILED, NO_API_KEY }

    /** Blocking (hashes the ROM, and may hit the network) — call only from a
     * background thread. [replace]: fetch even over an existing (cached or
     * manual) cover - which is only swapped once the new image has fully
     * downloaded, so a ROM SteamGridDB has nothing for keeps its cover ([Status.KEPT]). */
    fun fetchOne(rom: File, coverFile: File, apiKey: String?, isManualCover: Boolean, replace: Boolean = false): Status {
        val hasCover = coverFile.isFile && coverFile.length() > 0
        if (!replace && isManualCover) return Status.MANUAL_COVER
        if (!replace && hasCover) return Status.ALREADY_CACHED
        if (apiKey.isNullOrBlank()) return Status.NO_API_KEY
        val game = SteamGridDbGames.forRom(rom) ?: return if (hasCover) Status.KEPT else Status.UNKNOWN_GAME
        return when {
            SteamGridDbClient.fetchCover(apiKey, game, coverFile) -> if (hasCover) Status.REPLACED else Status.FETCHED
            hasCover -> Status.KEPT
            else -> Status.FETCH_FAILED
        }
    }
}
