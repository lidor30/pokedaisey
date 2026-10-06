package com.pokedaisey.app

import android.content.Context
import com.pokedaisey.app.companion.FfMode
import com.pokedaisey.app.companion.FfMusicMode

/** Tiny SharedPreferences wrapper for app-level state. */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("pokedaisey", Context.MODE_PRIVATE)

    var lastRomPath: String?
        get() = p.getString("last_rom", null)
        set(v) = p.edit().putString("last_rom", v).apply()

    /**
     * Play history, most-recent-first, capped at [RECENT_CAP] ROM paths —
     * backs the Library screen's "Recently Played" row. Deliberately kept
     * separate from [lastRomPath] (which only ever tracks the single most
     * recent play, for the row's "PLAYING" badge) since the two have
     * different callers and different lifetimes.
     */
    fun recentRomPaths(): List<String> =
        p.getString("recent_roms", null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    fun pushRecentRom(path: String) {
        val updated = (listOf(path) + recentRomPaths().filter { it != path }).take(RECENT_CAP)
        p.edit().putString("recent_roms", updated.joinToString("\n")).apply()
    }

    /** 0 = list, 1 = grid — Library screen's view-mode toggle. */
    var libraryViewMode: Int
        get() = p.getInt("library_view_mode", 0)
        set(v) = p.edit().putInt("library_view_mode", v).apply()

    /** Whether a ROM's cover (keyed by file name, like [romDisplayName]) was
     * explicitly picked by the user rather than auto-captured — a manual
     * cover is never overwritten by a later auto-capture backfill. */
    fun romCoverManual(file: java.io.File): Boolean = p.getBoolean("cover_manual_${file.name}", false)
    fun setRomCoverManual(file: java.io.File, manual: Boolean) =
        p.edit().putBoolean("cover_manual_${file.name}", manual).apply()

    /** Fast-forward speed cap. 0 = unlimited. */
    var ffMaxSpeed: Float
        get() = p.getFloat("ff_max_speed", 6f)
        set(v) = p.edit().putFloat("ff_max_speed", v).apply()

    /** What fast-forward sounds like. Before the modes it was an on/off
     * "ff_music_enabled": on was today's STEADY, off stays OFF. */
    var ffMusicMode: FfMusicMode
        get() = p.getString("ff_music_mode", null)?.let { n -> FfMusicMode.entries.firstOrNull { it.name == n } }
            ?: if (p.getBoolean("ff_music_enabled", true)) FfMusicMode.STEADY else FfMusicMode.OFF
        set(v) = p.edit().putString("ff_music_mode", v.name).apply()

    /** Where fast-forward applies: SMART (1x on menus and the region map) or NORMAL. */
    var ffMode: FfMode
        get() = p.getString("ff_mode", null)?.let { n -> FfMode.entries.firstOrNull { it.name == n } } ?: FfMode.SMART
        set(v) = p.edit().putString("ff_mode", v.name).apply()

    /** What FfMenuWatch learned for a ROM (by CRC): its field's and battle's gMain.callback2, 0 = not yet. */
    fun ffMenuCallbacks(romKey: String): Pair<Long, Long> {
        val v = p.getString("ff_menu_cb2_$romKey", null)?.split(',') ?: return 0L to 0L
        return (v.getOrNull(0)?.toLongOrNull() ?: 0L) to (v.getOrNull(1)?.toLongOrNull() ?: 0L)
    }

    fun setFfMenuCallbacks(romKey: String, field: Long, battle: Long) =
        p.edit().putString("ff_menu_cb2_$romKey", "$field,$battle").apply()

    /** Fast-forward on/off, remembered across launches (restored alongside
     * [ffMaxSpeed] on every engine creation - fresh boot or resume-from-state
     * alike, since neither carries this Kotlin-side field on its own). */
    var ffToggled: Boolean
        get() = p.getBoolean("ff_toggled", false)
        set(v) = p.edit().putBoolean("ff_toggled", v).apply()

    /** Remembered speed-cycle index per ROM (keyed by CRC32). */
    fun speedIndexFor(romKey: String): Int = p.getInt("speed_$romKey", 0)
    fun setSpeedIndexFor(romKey: String, index: Int) = p.edit().putInt("speed_$romKey", index).apply()

    /** On-screen controls: 0 = auto (only if no gamepad), 1 = always, 2 = never. */
    var touchControlsMode: Int
        get() = p.getInt("touch_controls", 0)
        set(v) = p.edit().putInt("touch_controls", v).apply()

    /** Whether battle-only strategic hints (move effectiveness chips, the
     * foe's Weak-to/Resists/Immune-to summary) are shown. On by default -
     * this is an opt-out for players who'd rather figure matchups out
     * themselves. Doesn't affect the Party tab's own matchup display (that's
     * dex info, not battle metagaming) or move Power (shown regardless). */
    var showHints: Boolean
        get() = p.getBoolean("show_hints", true)
        set(v) = p.edit().putBoolean("show_hints", v).apply()

    /** Whether the companion's buttons play the game's click (GameClickSound). */
    var clickSound: Boolean
        get() = p.getBoolean("click_sound", true)
        set(v) = p.edit().putBoolean("click_sound", v).apply()

    /** Whether the game has a status bar on top (game, location, money, clock, battery). */
    var statusBar: Boolean
        get() = p.getBoolean("status_bar", false)
        set(v) = p.edit().putBoolean("status_bar", v).apply()

    /** The companion tabs shown in the bottom screen's tab bar (see
     * [com.pokedaisey.app.companion.COMPANION_TABS]); the rest are
     * reached from its SETTINGS tab. */
    var companionTabs: List<String>
        get() = p.getString("companion_tabs", null)?.split(",")?.filter { it.isNotBlank() }
            ?: com.pokedaisey.app.companion.DEFAULT_COMPANION_TABS
        set(v) = p.edit().putString("companion_tabs", v.joinToString(",")).apply()

    /** Games whose GUIDE notice ("written with AI help, may be wrong") was accepted. */
    fun guideNoticeAccepted(game: String): Boolean = game in guideNoticeGames()
    fun acceptGuideNotice(game: String) =
        p.edit().putString("guide_notice_ok", (guideNoticeGames() + game).joinToString(",")).apply()
    private fun guideNoticeGames(): Set<String> =
        p.getString("guide_notice_ok", null)?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    /** Display-only ROM name override (keyed by file name); null = use the file name. */
    fun romDisplayName(file: java.io.File): String? = p.getString("romname_${file.name}", null)
    fun setRomDisplayName(file: java.io.File, name: String?) = p.edit().apply {
        if (name.isNullOrBlank()) remove("romname_${file.name}") else putString("romname_${file.name}", name.trim())
    }.apply()

    /**
     * Where the ROM was imported *from* (its original on-device path, best-effort
     * resolved from the picker's content:// URI), so the library can show that
     * instead of the copy under this app's private storage. Null if unknown (e.g.
     * a file dropped directly into the roms/ folder, or picked from a provider
     * with no real filesystem path, like Drive).
     */
    fun romSourcePath(file: java.io.File): String? = p.getString("romsrc_${file.name}", null)
    fun setRomSourcePath(file: java.io.File, path: String?) = p.edit().apply {
        if (path.isNullOrBlank()) remove("romsrc_${file.name}") else putString("romsrc_${file.name}", path)
    }.apply()

    /** Custom folder for game saves (.sav/.srm); null = the app's own files/saves/. */
    var savesDirOverride: String?
        get() = p.getString("saves_dir_override", null)
        set(v) = p.edit().putString("saves_dir_override", v).apply()

    /** The player's own ROMs folder (first-time setup / Settings > Folders): its
     * supported games are listed in the library and played in place, and it is
     * rescanned for new ones whenever the library opens. Null = not linked. */
    var romsFolder: String?
        get() = p.getString("roms_folder", null)
        set(v) = p.edit().putString("roms_folder", v).apply()

    /** ROMs the player hid from the library (by path; the library menu's HIDE,
     * undone in Settings > HIDDEN GAMES). The file stays where it is, its name,
     * cover and saves too; a folder rescan just doesn't add it back. */
    var hiddenRoms: Set<String>
        get() = p.getString("hidden_roms", null)?.split("\n")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        set(v) = p.edit().putString("hidden_roms", v.joinToString("\n")).apply()

    /** First-time setup finished or skipped; it only shows while this is false and the library is empty. */
    var setupDone: Boolean
        get() = p.getBoolean("setup_done", false)
        set(v) = p.edit().putBoolean("setup_done", v).apply()

    /** Settings' RUN SETUP: the library shows setup again the next time it comes back. */
    var setupRequested: Boolean
        get() = p.getBoolean("setup_requested", false)
        set(v) = p.edit().putBoolean("setup_requested", v).apply()

    /** Index into APP_THEMES (Theme.kt). 0 = FireRed (default). */
    var appTheme: Int
        get() = p.getInt("app_theme", 0)
        set(v) = p.edit().putInt("app_theme", v).apply()

    /** User-supplied SteamGridDB API key (Settings > Cover Art) — see
     * [SteamGridDbClient]. Null/blank = cover-art fetching is off; nothing is
     * ever bundled in the APK, only fetched live and cached under
     * `files/covers/` once a key is set. */
    var steamGridDbApiKey: String?
        get() = p.getString("steamgriddb_api_key", null)
        set(v) = p.edit().putString("steamgriddb_api_key", v?.trim()?.takeIf { it.isNotEmpty() }).apply()

    private companion object {
        const val RECENT_CAP = 10   // more than the 3 shown, so deleted ROMs don't starve the row
    }
}
