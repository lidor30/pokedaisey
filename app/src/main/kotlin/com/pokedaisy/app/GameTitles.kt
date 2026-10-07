package com.pokedaisy.app

import android.content.Context
import java.io.File

/**
 * The name a game goes by in the library ("Pokémon FireRed") instead of its ROM
 * file's ("Pokemon - FireRed Version (USA, Europe) (Rev 1)") - the file name stays
 * in the list row's second line and INFO. A ROM is named by its whole-file SHA1
 * (an archive by the ROM inside, [RomIdentity]): retail by the pret decomps'
 * `*.sha1`, hacks by the hashes [com.pokedaisy.app.companion.data.TelemetrySampler]
 * detects them with. Anything else (the QoL builds - a new hash every rebuild -
 * small hacks, other games) keeps its file name, tidied by [tidy].
 *
 * Hashing is up to 32 MB per ROM, so [identify] runs off the UI thread and caches
 * each file's answer by path + size + modified time (`rom-titles.tsv`); [label]
 * only reads that cache. Order: the player's own RENAME, then this, then the file.
 */
object GameTitles {

    val BY_SHA1: Map<String, String> = mapOf(
        // Retail (USA, Europe), every revision: pret's pokefirered / pokeemerald / pokeruby.
        "41cb23d8dccc8ebd7c649cd8fbb58eeace6e2fdc" to "Pokémon FireRed",
        "dd5945db9b930750cb39d00c84da8571feebf417" to "Pokémon FireRed",
        "574fa542ffebb14be69902d1d36f1ec0a4afd71e" to "Pokémon LeafGreen",
        "7862c67bdecbe21d1d69ce082ce34327e1c6ed5e" to "Pokémon LeafGreen",
        "f3ae088181bf583e55daf962a92bb46f4f1d07b7" to "Pokémon Emerald",
        "f28b6ffc97847e94a6c21a63cacf633ee5c8df1e" to "Pokémon Ruby",
        "610b96a9c9a7d03d2bafb655e7560ccff1a6d894" to "Pokémon Ruby",
        "5b64eacf892920518db4ec664e62a086dd5f5bc8" to "Pokémon Ruby",
        "3ccbbd45f8553c36463f13b938e833f652b793e4" to "Pokémon Sapphire",
        "4722efb8cd45772ca32555b98fd3b9719f8e60a9" to "Pokémon Sapphire",
        "89b45fb172e6b55d51fc0e61989775187f6fe63c" to "Pokémon Sapphire",
        // Hacks - the versions Poller.kt's *_SHA1 constants pin.
        "b4776b82a4c7915d0fadeaa27e013523f99dfd94" to "Pokémon Unbound",
        "d5b1e77975fcda831e0e9a7b527906bf3f40ecd0" to "Pokémon Gaia",
        "964f951a0fdaf209e4ea1344883ef0d557bb3a80" to "Pokémon Radical Red",
        "8745ddbdbfadf6abaf66de4e9055923b62eb4668" to "Pokémon Odyssey",
        "00e70c0384a5f1698588034201fd5b849d3542e2" to "Pokémon Amethyst",
        "3cbd5a2e72ce60cfae1e0c28d83e01f855c51537" to "Pokémon Celia's Stupid Romhack",
        "79ee6df0869c1773c8c6a5f764afc1f8d833d8bb" to "Pokémon Heart & Soul",
        "7dcdc7e280bc4631487e13dd37e6e0cea04adea6" to "Pokémon Lazarus",
        "81bd0f4bfa1c04ab2c6faab1bddd10e8a390ea77" to "Pokémon R.O.W.E.",
        "7600af1fe08444c850c3c1227fd7dfd81336ae8e" to "Pokémon Emerald Rogue",
        "3fa8e61ec1727cc51540c54677623f736a446e1b" to "Pokémon Too Many Types 2",
        "b9f4d332d30fc88c379f9e037f9eae3b2755ead4" to "Pokémon Emerald Seaglass",
        "ea5d369cc8a31cbf1cfacb7c9470ea670f08957b" to "Pokémon SoulGold",
    )

    /** Only these games' ROMs are worth hashing: every entry above is one of them. */
    private val CODES = setOf("BPRE", "BPGE", "BPEE", "AXVE", "AXPE")

    /** What the library shows for [rom]: the player's RENAME, else [defaultLabel]. Cheap. */
    fun label(context: Context, prefs: Prefs, rom: File): String =
        prefs.romDisplayName(rom) ?: defaultLabel(context, rom)

    /** [rom]'s name with no RENAME: the game's ([title]) if known, else the tidied file name. */
    fun defaultLabel(context: Context, rom: File): String =
        title(context, rom) ?: tidy(RomArchive.baseName(rom))

    /** The game's name, if [identify] has already recognised this exact file. Cheap. */
    fun title(context: Context, rom: File): String? =
        cache(context)[rom.absolutePath]?.takeIf { it.matches(rom) }?.title

    /**
     * Recognises every ROM in [roms] not already cached (or changed since). Blocking
     * (hashes) - never on the UI thread. True when a new answer was cached, i.e. a
     * name may have changed and the list should redraw.
     */
    @Synchronized
    fun identify(context: Context, roms: List<File>): Boolean {
        val cache = cache(context)
        var changed = false
        for (rom in roms) {
            if (cache[rom.absolutePath]?.matches(rom) == true || !rom.isFile) continue
            val title = if (RomIdentity.gameCode(rom) in CODES) RomIdentity.sha1(rom)?.let { BY_SHA1[it] } else null
            cache[rom.absolutePath] = Entry(rom.length(), rom.lastModified(), title)
            changed = true
        }
        // Forget files that are gone, so the cache doesn't grow forever.
        val pruned = cache.keys.removeAll { !File(it).isFile }
        if (changed || pruned) save(context, cache)
        return changed
    }

    private val TAGS = Regex("""\s*[(\[][^)\]]*[)\]]""")
    private val NUMBERING = Regex("""^\d{1,5}\s*-\s+""")

    /**
     * A file name without its dump-set tags: "(USA, Europe)", "(Rev 1)", "(v1.3.1)",
     * "[!]", a leading "1636 - " release number, underscores for spaces. The whole
     * name back when nothing would be left.
     */
    fun tidy(name: String): String =
        name.replace('_', ' ')
            .replace(TAGS, "")
            .replace(NUMBERING, "")
            .replace(Regex("""\s+"""), " ")
            .trim().trimEnd('-', ' ')
            .ifEmpty { name }

    private class Entry(val size: Long, val modified: Long, val title: String?) {
        fun matches(rom: File) = size == rom.length() && modified == rom.lastModified()
    }

    private var loaded: MutableMap<String, Entry>? = null

    @Synchronized
    private fun cache(context: Context): MutableMap<String, Entry> = loaded ?: load(context).also { loaded = it }

    private fun cacheFile(context: Context) = File(context.filesDir, "rom-titles.tsv")

    private fun load(context: Context): MutableMap<String, Entry> {
        val map = java.util.concurrent.ConcurrentHashMap<String, Entry>()
        val f = cacheFile(context)
        if (!f.isFile) return map
        runCatching {
            f.forEachLine { line ->
                val p = line.split('\t')
                if (p.size != 4) return@forEachLine
                val size = p[1].toLongOrNull() ?: return@forEachLine
                val modified = p[2].toLongOrNull() ?: return@forEachLine
                map[p[0]] = Entry(size, modified, p[3].ifEmpty { null })
            }
        }
        return map
    }

    private fun save(context: Context, entries: Map<String, Entry>) {
        val f = cacheFile(context)
        val tmp = File(f.parentFile, "${f.name}.tmp")
        runCatching {
            tmp.writeText(entries.entries.joinToString("") { (path, e) ->
                "$path\t${e.size}\t${e.modified}\t${e.title.orEmpty()}\n"
            })
            tmp.renameTo(f)
        }
    }
}
