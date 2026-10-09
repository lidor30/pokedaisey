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

    /** [partial]: read through a PARTIAL best-effort match ([CompanionSupport.Verdict.PARTIAL]) - listed, tagged. */
    /** [tryable]: unsupported but a Gen 3 Pokémon base game - listed, tagged NOT SUPPORTED, so TRY BEST EFFORT is reachable. */
    private class Entry(
        val size: Long, val modified: Long, val supported: Boolean,
        val partial: Boolean = false, val matched: Boolean = false, val tryable: Boolean = false,
        /** Written before TRYABLE existed ("0"): checked again once. */
        val legacy: Boolean = false,
    ) {
        val listed get() = supported || partial || tryable
    }

    private fun entryOf(size: Long, modified: Long, f: File): Entry =
        when (runCatching { CompanionSupport.verdict(f) }.getOrDefault(CompanionSupport.Verdict.UNSUPPORTED)) {
            CompanionSupport.Verdict.SUPPORTED -> Entry(size, modified, true)
            CompanionSupport.Verdict.MATCHED -> Entry(size, modified, true, matched = true)
            CompanionSupport.Verdict.PARTIAL -> Entry(size, modified, false, partial = true)
            CompanionSupport.Verdict.TRYABLE -> Entry(size, modified, false, tryable = true)
            CompanionSupport.Verdict.UNSUPPORTED -> Entry(size, modified, false)
        }

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
        return load(context).filter { (path, e) -> e.listed && path.startsWith("$root/") && path !in hidden }
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
            val cached = old[f.absolutePath]?.takeIf { it.size == size && it.modified == modified && !it.legacy }
            fresh[f.absolutePath] = cached
                ?: entryOf(size, modified, f)
            onProgress(i + 1, candidates.size)
        }
        // Imported ROMs' verdicts ([checkImported]) live in the same cache.
        for ((path, e) in old) if (!path.startsWith("$root/")) fresh.putIfAbsent(path, e)
        save(context, fresh)
        return ScanResult(
            added = fresh.filter { (path, e) -> e.listed && old[path]?.listed != true }.map { File(it.key) },
            files = fresh.size,
            supported = fresh.values.count { it.supported },
        )
    }

    /**
     * Checks imported ROMs (`files/roms/`, the ones added with "add anyway?" included) into the
     * verdict cache, for the library's NOT SUPPORTED tag ([unsupported]). Blocking; only new or
     * changed files are read.
     */
    @Synchronized
    fun checkImported(context: Context) {
        val old = load(context)
        val fresh = LinkedHashMap(old)
        var changed = false
        for (f in importedRoms(context)) {
            val size = f.length()
            val modified = f.lastModified()
            if (old[f.absolutePath]?.let { it.size == size && it.modified == modified && !it.legacy } == true) continue
            fresh[f.absolutePath] = entryOf(size, modified, f)
            changed = true
        }
        if (changed) save(context, fresh)
    }

    /** Paths the cache knows the second screen can't read - cheap, never reads a ROM. */
    fun unsupported(context: Context): Set<String> =
        load(context).filterValues { !it.supported && !it.partial }.keys

    /** Paths read through a PARTIAL best-effort match - the PARTIALLY SUPPORTED tag. */
    fun partial(context: Context): Set<String> =
        load(context).filterValues { it.partial }.keys

    /**
     * A best-effort match was kept or forgotten ([com.pokedaisy.app.companion.data.BestEffortStore]):
     * drop the cached verdicts that could change (all but SUPPORTED), so the next scan reads them again.
     */
    @Synchronized
    fun forgetUnsupported(context: Context) {
        val old = load(context)
        val kept = old.filterValues { it.supported && !it.matched }
        if (kept.size != old.size) save(context, kept)
    }

    /** Where best effort keeps its matches, and the verdicts to drop when one changes. Each activity's onCreate. */
    fun useBestEffortStore(context: Context) {
        val app = context.applicationContext
        com.pokedaisy.app.companion.data.BestEffortStore.dir = app.filesDir
        com.pokedaisy.app.companion.data.BestEffortStore.onChanged = { forgetUnsupported(app) }
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
                p[0] to Entry(size, modified, p[3] == "1" || p[3] == "M", partial = p[3] == "P", matched = p[3] == "M", tryable = p[3] == "T", legacy = p[3] == "0")
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun save(context: Context, entries: Map<String, Entry>) {
        val f = cacheFile(context)
        val tmp = File(f.parentFile, "${f.name}.tmp")
        runCatching {
            tmp.writeText(entries.entries.joinToString("") { (path, e) ->
                "$path\t${e.size}\t${e.modified}\t${if (e.matched) "M" else if (e.supported) "1" else if (e.partial) "P" else if (e.tryable) "T" else "U"}\n"
            })
            tmp.renameTo(f)
        }
    }
}
