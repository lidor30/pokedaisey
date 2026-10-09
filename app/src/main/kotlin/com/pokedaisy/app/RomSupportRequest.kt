package com.pokedaisy.app

import java.io.File
import java.net.URLEncoder

/**
 * ASK FOR SUPPORT: a new GitHub issue from `.github/ISSUE_TEMPLATE/rom_request.yml`, its fields filled
 * by id like the website's ROM check does (RomCheck.astro's requestLink) - the file's name, game code,
 * size and SHA-1. Only those: the ROM itself never leaves the device.
 */
object RomSupportRequest {
    private const val NEW_ISSUE = "https://github.com/${AppUpdater.REPO}/issues/new"

    /** The issue's address for [rom] (an archive: the ROM inside). Reads the whole ROM for its SHA-1, so off the UI thread. */
    fun url(rom: File, sha1: String? = RomIdentity.sha1(rom)): String {
        val name = RomArchive.baseName(rom)
        val code = RomIdentity.gameCode(rom)?.takeIf { c -> c.all { it in 'A'..'Z' || it in '0'..'9' } }
        val fields = linkedMapOf(
            "template" to "rom_request.yml",
            "title" to "ROM support: $name",
            "game" to name,
            "game_code" to when {
                RomIdentity.isGameBoy(rom) -> "Game Boy"
                code != null -> "$code (rev ${RomIdentity.revision(rom) ?: 0})"
                else -> ""
            },
            "size" to (runCatching { RomArchive.romSize(rom) }.getOrNull() ?: rom.length()).toString(),
            "sha1" to (sha1 ?: ""),
        )
        return NEW_ISSUE + "?" + fields.entries.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
