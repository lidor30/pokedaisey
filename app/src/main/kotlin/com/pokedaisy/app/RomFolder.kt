package com.pokedaisy.app

import android.content.Context
import java.io.File

/**
 * The player's linked ROMs folder ([Prefs.romsFolder]): every ROM in it that the
 * second screen supports ([CompanionSupport]) joins the library and plays from
 * where it is - nothing is copied. Checking a ROM can mean hashing 32 MB, so each
 * file's verdict is cached by path + size + modified time and a rescan (every
 * time the library opens) only looks at new or changed files. ROMs packed in
 * `.zip` / `.7z` archives count too ([RomArchive]).
 */
object RomFolder {

    private val EXTENSIONS = RomArchive.ROM_EXTENSIONS + RomArchive.EXTENSIONS
    private const val MAX_ROM_BYTES = 32L shl 20
    /** The folder itself plus a couple of levels (e.g. `roms/gba/hacks/`). */
    private const val MAX_DEPTH = 3

    private class Entry(val size: Long, val modified: Long, val supported: Boolean)

    /** The library: imported ROMs (`files/roms/`), then the linked folder's supported
     * ones from the last scan - minus hidden ones ([Prefs.hiddenRoms]) and any whose
     * file name an imported ROM already has (covers and names are keyed by file
     * name). Cheap: reads the cache, never a ROM. */
    fun libraryRoms(context: Context, prefs: Prefs): List<File> {
        val imported = importedRoms(context)
        val names = imported.mapTo(HashSet()) { it.name }
        val linked = found(context, prefs).filter { it.name !in names }
        val hidden = prefs.hiddenRoms
        return (imported.filter { it.absolutePath !in hidden } + linked)
            .sortedWith(byLabel(context, prefs))
    }

    /** The hidden ROMs that are still there to show again (imported or in the linked folder). */
    fun hiddenRoms(context: Context, prefs: Prefs): List<File> =
        prefs.hiddenRoms.map(::File).filter { it.isFile }.sortedWith(byLabel(context, prefs))

    /** Alphabetical by the library's names, accents aside ("Pokemon" next to "Pokémon"). */
    private fun byLabel(context: Context, prefs: Prefs): Comparator<File> {
        val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
        return compareBy(collator) { GameTitles.label(context, prefs, it) }
    }

    private fun importedRoms(context: Context): List<File> =
        File(context.getExternalFilesDir(null), "roms").listFiles { f ->
            f.isFile && f.name.substringAfterLast('.', "").lowercase() in EXTENSIONS
        }?.toList().orEmpty()

    /** Supported, not hidden, still-present ROMs of the linked folder, per the last scan. */
    fun found(context: Context, prefs: Prefs): List<File> {
        val root = prefs.romsFolder ?: return emptyList()
        val hidden = prefs.hiddenRoms
        return load(context).filter { (path, e) -> e.supported && path.startsWith("$root/") && path !in hidden }
            .map { File(it.key) }.filter { it.isFile }
    }

    /** Whether [rom] came from the linked folder (the player's own file: HIDE only, no DELETE). */
    fun isLinked(prefs: Prefs, rom: File): Boolean =
        prefs.romsFolder?.let { rom.absolutePath.startsWith("$it/") } == true

    /** What a [scan] saw: the supported ROMs it found that the previous one didn't
     * have, how many ROM files it looked at and how many of those are supported;
     * [readable] is false when the folder couldn't be read at all. */
    class ScanResult(val added: List<File>, val files: Int, val supported: Int, val readable: Boolean = true)

    /**
     * Walks the linked folder and checks every new or changed ROM file. Blocking
     * (may hash several 32 MB files) - never on the UI thread. [onProgress] gets
     * (checked, total) as it goes. A folder that can't be read (no All files
     * access, SD card out) leaves the cache alone.
     */
    @Synchronized
    fun scan(context: Context, prefs: Prefs, onProgress: (Int, Int) -> Unit = { _, _ -> }): ScanResult {
        val unreadable = ScanResult(emptyList(), 0, 0, readable = false)
        val root = prefs.romsFolder?.let(::File) ?: return unreadable
        if (root.listFiles() == null) return unreadable
        val candidates = root.walkTopDown().maxDepth(MAX_DEPTH)
            .onEnter { it == root || !it.name.startsWith(".") }
            .filter { f ->
                f.isFile && f.name.substringAfterLast('.', "").lowercase() in EXTENSIONS &&
                    f.length() in 0x200L..MAX_ROM_BYTES
            }
            .toList()
        val old = load(context)
        val fresh = LinkedHashMap<String, Entry>()
        candidates.forEachIndexed { i, f ->
            val size = f.length()
            val modified = f.lastModified()
            val cached = old[f.absolutePath]?.takeIf { it.size == size && it.modified == modified }
            fresh[f.absolutePath] = cached
                ?: Entry(size, modified, runCatching { CompanionSupport.isSupported(f) }.getOrDefault(false))
            onProgress(i + 1, candidates.size)
        }
        save(context, fresh)
        return ScanResult(
            added = fresh.filter { (path, e) -> e.supported && old[path]?.supported != true }.map { File(it.key) },
            files = fresh.size,
            supported = fresh.values.count { it.supported },
        )
    }

    /** Forgets every cached verdict (a newly linked folder starts from scratch). */
    fun clearCache(context: Context) {
        cacheFile(context).delete()
    }

    private fun cacheFile(context: Context) = File(context.filesDir, "rom-folder-scan.tsv")

    private fun load(context: Context): Map<String, Entry> {
        val f = cacheFile(context)
        if (!f.isFile) return emptyMap()
        return runCatching {
            f.readLines().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size != 4) return@mapNotNull null
                val size = p[1].toLongOrNull() ?: return@mapNotNull null
                val modified = p[2].toLongOrNull() ?: return@mapNotNull null
                p[0] to Entry(size, modified, p[3] == "1")
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun save(context: Context, entries: Map<String, Entry>) {
        val f = cacheFile(context)
        val tmp = File(f.parentFile, "${f.name}.tmp")
        runCatching {
            tmp.writeText(entries.entries.joinToString("") { (path, e) ->
                "$path\t${e.size}\t${e.modified}\t${if (e.supported) 1 else 0}\n"
            })
            tmp.renameTo(f)
        }
    }
}
