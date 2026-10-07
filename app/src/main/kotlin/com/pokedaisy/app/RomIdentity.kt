package com.pokedaisy.app

import java.io.File
import java.security.MessageDigest

/**
 * Plain, emulator-free ways to fingerprint a ROM file — a 4-byte header read
 * and a whole-file SHA1, both cheap enough to run once at import/backfill
 * time (never inside a Compose recomposition; see [SteamGridDbGames]'s doc
 * comment for why the SHA1 in particular must stay a one-shot cost). An
 * archive (`.zip` / `.7z`) answers for the ROM inside it ([RomArchive]).
 */
object RomIdentity {
    /** The 4-char ASCII game code at ROM header offset 0xAC (`BPRE`, `BPEE`,
     * …) — identifies the *base* game only. Most FireRed-based ROM hacks
     * (Unbound, Gaia, Radical Red, Odyssey, Amethyst, …) keep this exact same
     * code, so it can't tell them apart from vanilla FireRed or from each
     * other — see [sha1] for that. */
    fun gameCode(rom: File): String? =
        RomArchive.readAt(rom, 0xACL, 4)?.takeIf { it.size == 4 }?.let { String(it, Charsets.US_ASCII) }

    /** The header's 12-character internal title at 0x0A0 (`POKEMON FIRE`, …). */
    fun headerTitle(rom: File): String? =
        RomArchive.readAt(rom, 0xA0L, 12)?.takeIf { it.size == 12 }
            ?.let { String(it, Charsets.US_ASCII).trimEnd('\u0000', ' ').takeIf { s -> s.all { c -> c in ' '..'~' } } }

    /** The header's revision byte (0x0BC): Ruby / Sapphire / LeafGreen
     * addresses differ between revisions. */
    fun revision(rom: File): Int? =
        RomArchive.readAt(rom, 0xBCL, 1)?.firstOrNull()?.let { it.toInt() and 0xFF }

    /**
     * Whole-file SHA1, lowercase hex — equals a plain `shasum -a 1` on the
     * file. This is deliberately the *same* value the companion's
     * `Poller.kt` uses for live ROM-hack detection (its comments confirm
     * each of its hash constants equals a plain file hash now that its
     * chunked live-bus read masks the RTC/GPIO hole) — computing it here
     * from the file directly needs no running emulator core at all.
     */
    fun sha1(rom: File): String? = digest(rom, "SHA-1")

    /** The file's MD5 - for a GBA ROM, the hash RetroAchievements identifies it by. */
    fun md5(rom: File): String? = digest(rom, "MD5")

    private fun digest(rom: File, algorithm: String): String? = try {
        val digest = MessageDigest.getInstance(algorithm)
        RomArchive.open(rom).buffered(1 shl 16).use { s ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Throwable) {
        null
    }
}
