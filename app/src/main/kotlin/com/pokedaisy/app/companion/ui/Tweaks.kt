package com.pokedaisy.app.companion.ui

import androidx.compose.runtime.mutableStateOf
import com.pokedaisy.app.companion.i18n.tk

/**
 * SETTINGS > TWEAKS: small on / off preferences for the companion's motion and
 * habits, each on by default (the app as it always was). Compose state, so a flip
 * shows at once; the activity loads them from Prefs ([load]) and SETTINGS stores
 * each change through CompanionSettings.setTweak.
 */
object CompanionTweaks {
    enum class Tweak(val label: String, val key: String) {
        /** The party icons' two-frame bounce (PARTY, and the battle's POKéMON picker). */
        PARTY_ICONS_MOVE(tk("PARTY ICONS MOVE"), "party_icons_move"),
        /** The region map cursor's pulse between its two sizes. */
        MAP_CURSOR_BLINK(tk("MAP CURSOR BLINK"), "map_cursor_blink"),
        /** Tabs slide / fade into each other; off, they cut. */
        TAB_ANIMATIONS(tk("TAB ANIMATIONS"), "tab_animations"),
        /** A battle opens the BATTLE tab by itself (and the tab before it comes back after). */
        JUMP_TO_BATTLE(tk("JUMP TO BATTLE"), "jump_to_battle"),
    }

    private val states = Tweak.entries.associateWith { mutableStateOf(true) }

    operator fun get(t: Tweak): Boolean = states.getValue(t).value

    operator fun set(t: Tweak, on: Boolean) {
        states.getValue(t).value = on
    }

    /** Each tweak from [stored] (its Prefs key -> on), at the activity's start. */
    fun load(stored: (String) -> Boolean) {
        for (t in Tweak.entries) this[t] = stored(t.key)
    }
}
