package com.pokedaisey.app.companion

import com.pokedaisey.app.GbaControls
import com.pokedaisey.app.Hotkeys

/**
 * Bridge for the bottom-screen SETTINGS tab: lets the player tweak settings
 * from Screen-2 without leaving the game. Implemented by PokeDaiseyActivity,
 * which also live-applies changes to the running
 * [com.pokedaisey.app.EmulatorEngine] / touch-controls visibility,
 * not just persisting them to [com.pokedaisey.app.Prefs].
 *
 * Key/button rebinding here is a TAP-A-NAME-FROM-A-LIST picker, not "press the
 * button you want" — the Presentation on Screen-2 never receives physical
 * `KeyEvent`s (those go to the main Activity on the top screen), so there's no
 * way to capture a live keypress there. Picking from a list only needs touch,
 * which Screen-2 gets fine. Only single-key bindings are settable this way
 * (chords stay a top-screen-Settings-only feature).
 */
interface CompanionSettings {
    /** Fast-forward speed cap. 0 = unlimited. */
    val ffMaxSpeed: Float
    fun setFfMaxSpeed(v: Float)

    /** Whether fast-forward is currently toggled on (independent of the hold trigger). */
    val ffToggled: Boolean
    fun setFfToggled(on: Boolean)

    /** What fast-forward sounds like ([FfMusicMode]). */
    val ffMusicMode: FfMusicMode
    fun setFfMusicMode(mode: FfMusicMode)

    /** Where fast-forward applies ([FfMode]). */
    val ffMode: FfMode
    fun setFfMode(mode: FfMode)

    /** 0 = auto (only if no gamepad), 1 = always, 2 = never. */
    val touchControlsMode: Int
    fun setTouchControlsMode(v: Int)

    /** Current GBA-button bindings, by display name (e.g. "BUTTON_A", "Z"). */
    fun gbaControlBindings(): Map<GbaControls.Btn, List<String>>

    /** Rebinds a GBA button to a single key, by display name (no "KEYCODE_" prefix). */
    fun setGbaControlBinding(btn: GbaControls.Btn, keyName: String)

    /** Current hotkey bindings, by display name. */
    fun hotkeyBindings(): Map<Hotkeys.Action, List<String>>

    /** Rebinds a hotkey to a single key, by display name — replaces any existing
     * chord with just this one key. */
    fun setHotkeyBinding(action: Hotkeys.Action, keyName: String)

    /** Hard-restarts the running game: stops the core, discards any pending
     * suspend-resume state, and reboots the same ROM re-reading its save file
     * fresh from disk — like RetroArch's "Restart". This is the escape hatch
     * for "the save didn't load" (e.g. the save file changed on disk after the
     * core already had it open), since a normal in-game reset never re-reads
     * the save. Caller (the UI) is expected to confirm before calling this. */
    fun restartGame()

    /** Ends the session and returns to the ROM list — same as backing out of
     * the game normally, so progress is suspended/saved the usual way via the
     * activity lifecycle. Caller (the UI) is expected to confirm first. */
    fun closeGame()

    /** Whether battle-only strategic hints (move effectiveness chips, foe
     * Weak-to/Resists/Immune-to) are shown — see [Prefs.showHints]. */
    val showHints: Boolean
    fun setShowHints(on: Boolean)

    /** Whether the companion's buttons play the game's click - see [Prefs.clickSound]. */
    val clickSound: Boolean
    fun setClickSound(on: Boolean)

    /** Whether the top screen shows [com.pokedaisey.app.companion.ui.GameStatusBar]
     * above the game - see [Prefs.statusBar]. */
    val statusBar: Boolean
    fun setStatusBar(on: Boolean)

    /** Display name of the currently-detected game (e.g. "Pokémon Heart and
     * Soul") — see [com.pokedaisey.app.companion.data.GameKind.displayName]. */
    val gameName: String

    /** File name of the ROM currently loaded (e.g. "heart_and_soul.gba"). */
    val romFileName: String

    /** Which of [COMPANION_TABS] sit in the tab bar (at most [MAX_BAR_TABS]);
     * the rest open from SETTINGS. See [Prefs.companionTabs]. */
    val companionTabs: List<String>
    fun setCompanionTabs(tabs: List<String>)

    /** Whether the GUIDE's "written with AI help" notice was accepted for [game]
     * (a [com.pokedaisey.app.companion.data.GameKind] name). */
    fun guideNoticeAccepted(game: String): Boolean
    fun acceptGuideNotice(game: String)
}

/** Every tab the player can show or hide, in tab-bar order. BATTLE (while a
 * battle runs) and SETTINGS (the way to the hidden ones) are always there.
 * CARD (the TRAINER CARD) starts off the bar, under SETTINGS > TOOLS. */
val COMPANION_TABS = listOf("PARTY", "DEX", "MAP", "ITEMS", "GUIDE", "CARD", "STATES")
val DEFAULT_COMPANION_TABS = listOf("PARTY", "DEX", "MAP", "ITEMS", "GUIDE")

/** Tabs next to SETTINGS: six chips in all is as many as fit. During a
 * battle BATTLE takes the last chosen tab's place. */
const val MAX_BAR_TABS = 5

/** Where fast-forward applies, in the order SETTINGS lists them. */
enum class FfMode(val label: String) {
    /** Menu screens (party, bag, summary, PC, Pokédex, trainer card...) and the
     * region map run at 1x; the field, dialogue and battles stay fast - in a
     * battle only its own bag and party screens slow down ([smartSlows]). */
    SMART("SMART"),

    /** Fast everywhere. */
    NORMAL("NORMAL"),
}


/**
 * What fast-forward sounds like, in the order SETTINGS lists them. [label] is
 * what the rows show; [alpha] marks a mode that isn't finished yet.
 */
enum class FfMusicMode(val label: String, val alpha: Boolean = false) {
    /** The song at its normal tempo: a clean clip rendered from the ROM
     * (FfMusicRenderer), looped while the game races; SPED-UP until it's ready. */
    STEADY("STEADY", alpha = true),

    /** The game's own audio, sped up with it (pitch and tempo). */
    SPED_UP("SPED-UP"),

    /** Silent. */
    OFF("OFF"),
}
