package com.pokedaisey.app

import android.content.Context
import java.io.File

/**
 * Where game-save (SRAM/flash) files live, and how a ROM's save file is found
 * inside that folder. The format is identical whether the file is named
 * `.sav` (mGBA/standalone convention) or `.srm` (RetroArch/libretro convention)
 * — it's the same raw save-RAM dump either way, so both are recognized.
 */
object SavesLocation {

    /** The effective saves folder: [Prefs.savesDirOverride] if the user picked
     * one (see Settings > Folders), else this app's own private files/saves/. */
    fun dir(context: Context, prefs: Prefs = Prefs(context)): File {
        val override = prefs.savesDirOverride
        val dir = if (!override.isNullOrBlank()) File(override) else File(context.getExternalFilesDir(null), "saves")
        dir.mkdirs()
        return dir
    }

    /** A folder that already holds saves, offered by first-time setup. */
    class Suggestion(val dir: File, val saves: Int)

    /**
     * Folders likely to hold the player's existing saves, with at least one
     * `.sav` / `.srm` in them: the linked ROMs folder (saves kept next to the
     * games) and its `saves/`, then RetroArch's save folders on every volume.
     * Blocking (lists folders) and needs All files access to see anything.
     */
    fun suggestions(prefs: Prefs): List<Suggestion> {
        val candidates = buildList {
            prefs.romsFolder?.let { add(File(it)); add(File(it, "saves")) }
            for (root in StorageAccess.volumeRoots()) {
                for (core in RETROARCH_SAVE_DIRS) add(File(root, "RetroArch/saves/$core"))
                add(File(root, "RetroArch/saves"))
            }
        }
        return candidates.distinctBy { it.absolutePath }.mapNotNull { dir ->
            val n = dir.listFiles { f -> f.isFile && f.extension.lowercase() in SAVE_EXTENSIONS }?.size ?: 0
            if (n > 0) Suggestion(dir, n) else null
        }
    }

    private val SAVE_EXTENSIONS = setOf("sav", "srm")
    /** RetroArch's GBA cores, for "sort saves by core" setups. */
    private val RETROARCH_SAVE_DIRS = listOf("mGBA", "gpSP", "VBA-M", "VBA Next", "Beetle GBA")

    /**
     * Resolves [romBaseName]'s save file inside [dir]. Prefers an existing
     * `.sav`, falls back to an existing `.srm` (e.g. a save copied in from
     * RetroArch), and defaults to `.sav` for a brand-new save.
     */
    fun resolve(dir: File, romBaseName: String): File {
        val sav = File(dir, "$romBaseName.sav")
        if (sav.exists()) return sav
        val srm = File(dir, "$romBaseName.srm")
        if (srm.exists()) return srm
        return sav
    }
}
