package com.pokedaisy.app

import com.pokedaisy.app.companion.data.BestEffortStore
import com.pokedaisy.app.companion.data.TelemetrySampler
import java.io.File

/**
 * Whether the second-screen companion can read [rom] - the same call
 * [TelemetrySampler]'s detect() makes live, made from the file at import so
 * the library can warn before adding a ROM the companion will only show a
 * "not supported" notice for. Retail LeafGreen / Ruby / Sapphire (the
 * revisions detect() has addresses for), and FireRed/Emerald-based ROMs up to 16 MB (retail,
 * the QoL builds, small hacks) are read as FireRed/Emerald; bigger ones only
 * when their SHA1 is a hack detect() knows. An archive is judged by the ROM
 * inside it ([RomArchive]). Blocking (hashes up to 32 MB) - never on the UI thread.
 * The website's ROM check (`website/src/lib/compat.js`) is the same rules in JavaScript, over
 * the tables `SiteDataExportTest` exports: change this, change that.
 */
object CompanionSupport {
    /** MATCHED: supported through a FULL best-effort match - kept apart so forgetting it is noticed.
     * TRYABLE: not supported, but a Gen 3 Pokémon base game, so TRY BEST EFFORT may read it. */
    enum class Verdict { SUPPORTED, MATCHED, PARTIAL, TRYABLE, UNSUPPORTED }

    /**
     * [isSupported], then a BEST EFFORT match the player kept for this ROM ([BestEffortStore], by
     * SHA-1): every part holding counts as supported (a supported version under another hash),
     * some off as PARTIAL. Blocking, like [isSupported].
     */
    fun verdict(rom: File): Verdict {
        if (isSupported(rom)) return Verdict.SUPPORTED
        val e = BestEffortStore.load(RomIdentity.sha1(rom))
            ?: return if (RomIdentity.gameCode(rom)?.let(com.pokedaisy.app.companion.data.BestEffort::canTry) == true) Verdict.TRYABLE else Verdict.UNSUPPORTED
        return if (e.full) Verdict.MATCHED else Verdict.PARTIAL
    }

    fun isSupported(rom: File): Boolean {
        // Game Boy / Color: the exact carts the GB reader knows (Pokémon Yellow).
        if (RomIdentity.isGameBoy(rom)) return TelemetrySampler.GAME_BOY_SUPPORT && RomIdentity.sha1(rom) in TelemetrySampler.SUPPORTED_GB_SHA1S
        val code = RomIdentity.gameCode(rom) ?: return false
        val size = RomArchive.romSize(rom)
        if (code in TelemetrySampler.OTHER_RETAIL_CODES) {
            // Retail only, and only the revisions detect() has addresses for.
            return size <= 0x1000000L && TelemetrySampler.otherRetailConfig(code, RomIdentity.revision(rom) ?: -1) != null
        }
        if (code != "BPRE" && code != "BPEE") return false
        if (size <= 0x1000000L) return true
        return RomIdentity.sha1(rom) in TelemetrySampler.SUPPORTED_HACK_SHA1S
    }
}
