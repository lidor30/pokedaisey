package com.pokedaisy.app

import com.pokedaisy.app.achievements.RetroAchievements
import java.io.File

/**
 * Shared "does this ROM have a fetchable cover, and can we get it" check —
 * used by both LibraryActivity's Refresh backfill and SettingsActivity's
 * Cover Art screen (so hitting Save can show live per-ROM progress right
 * there, instead of the user having to separately open Library and tap
 * Refresh to find out whether the key actually works).
 *
 * Two sources, each with the player's own key: the game's RetroAchievements box art
 * (found by the ROM's MD5 - [RetroAchievements.gameBoxArtUrl]) wherever RA has one,
 * then SteamGridDB's icons for the games [SteamGridDbGames] knows.
 */
object CoverArtSync {
    enum class Status { FETCHED, REPLACED, KEPT, ALREADY_CACHED, MANUAL_COVER, UNKNOWN_GAME, FETCH_FAILED, NO_API_KEY }

    /** Where players get each key (Settings > Cover Art's GET KEY, setup's OPEN buttons). */
    const val STEAMGRIDDB_KEY_PAGE = "https://www.steamgriddb.com/profile/preferences/api"
    const val RA_KEY_PAGE = "https://retroachievements.org/settings?tab=applications"

    /** Where an automatic cover came from ([Prefs.romCoverSource]). */
    enum class Source { STEAMGRIDDB, RETROACHIEVEMENTS }

    /** Blocking (hashes the ROM, and may hit the network) — call only from a
     * background thread. [replace]: fetch even over an existing (cached or
     * manual) cover - which is only swapped once the new image has fully
     * downloaded, so a ROM neither source has anything for keeps its cover ([Status.KEPT]). */
    fun fetchOne(rom: File, coverFile: File, prefs: Prefs, replace: Boolean = false): Status {
        val hasCover = coverFile.isFile && coverFile.length() > 0
        if (!replace && prefs.romCoverManual(rom)) return Status.MANUAL_COVER
        if (!replace && hasCover) return Status.ALREADY_CACHED
        val apiKey = prefs.steamGridDbApiKey?.takeIf { it.isNotBlank() }
        val raKey = prefs.raWebApiKey?.takeIf { it.isNotBlank() }
        // Hashes the ROM: only when RA had nothing.
        val game by lazy { apiKey?.let { SteamGridDbGames.forRom(rom) } }
        val source = when {
            raKey != null && fetchRaBoxArt(rom, coverFile, raKey) -> Source.RETROACHIEVEMENTS
            game?.let { SteamGridDbClient.fetchCover(apiKey!!, it, coverFile) } == true -> Source.STEAMGRIDDB
            else -> null
        }
        if (source != null) {
            prefs.setRomCoverSource(rom, source.name)
            return if (hasCover) Status.REPLACED else Status.FETCHED
        }
        return when {
            hasCover -> Status.KEPT
            apiKey == null && raKey == null -> Status.NO_API_KEY
            game == null && raKey == null -> Status.UNKNOWN_GAME
            else -> Status.FETCH_FAILED
        }
    }

    /** The RA box art's URL for [rom], if RA has one (blocking: hashes, network). */
    fun raBoxArtUrl(rom: File, raKey: String): String? =
        RomIdentity.md5(rom)?.let { RetroAchievements.gameBoxArtUrl(it, raKey) }

    private fun fetchRaBoxArt(rom: File, coverFile: File, raKey: String): Boolean {
        val url = raBoxArtUrl(rom, raKey) ?: return false
        return SteamGridDbClient.download(url, coverFile)
    }
}
