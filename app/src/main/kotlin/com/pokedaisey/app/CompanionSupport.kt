package com.pokedaisey.app

import com.pokedaisey.app.companion.data.TelemetrySampler
import java.io.File

/**
 * Whether the second-screen companion can read [rom] - the same call
 * [TelemetrySampler]'s detect() makes live, made from the file at import so
 * the library can warn before adding a ROM the companion will only show a
 * "not supported" notice for. Retail LeafGreen / Ruby / Sapphire (the
 * revisions detect() has addresses for), and FireRed/Emerald-based ROMs up to 16 MB (retail,
 * the QoL builds, small hacks) are read as FireRed/Emerald; bigger ones only
 * when their SHA1 is a hack detect() knows. Blocking (hashes up to 32 MB) -
 * never on the UI thread.
 */
object CompanionSupport {
    fun isSupported(rom: File): Boolean {
        val code = RomIdentity.gameCode(rom) ?: return false
        if (code in TelemetrySampler.OTHER_RETAIL_CODES) {
            // Retail only, and only the revisions detect() has addresses for.
            return rom.length() <= 0x1000000L && TelemetrySampler.otherRetailConfig(code, RomIdentity.revision(rom) ?: -1) != null
        }
        if (code != "BPRE" && code != "BPEE") return false
        if (rom.length() <= 0x1000000L) return true
        return RomIdentity.sha1(rom) in TelemetrySampler.SUPPORTED_HACK_SHA1S
    }
}
