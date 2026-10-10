package com.pokedaisy.app.companion.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** SETTINGS > DARK MODE, stored by [ordinal] (Prefs.darkMode). AUTO follows the device's own setting. */
enum class DarkModeSetting(val label: String) { OFF("OFF"), ON("ON"), AUTO("AUTO") }

/**
 * Dark mode, for both screens at once: the OPTION windows ([com.pokedaisy.app.companion.ui.OptionColors]),
 * the app's backdrops and the bags' palettes switch to their dark versions. Game art (party slots, the
 * map, the trainer card, battle buttons) stays the game's; only what's behind it is dimmed. Compose state
 * in a process-wide object like [QolColors], so a pick on either screen redraws both.
 */
object DarkMode {
    var setting by mutableStateOf(DarkModeSetting.OFF)
    /** The device is in its dark theme (AUTO follows it); set by the activities and QolTheme. */
    var systemDark by mutableStateOf(false)

    /** Screenshot tests / ui-preview: forced on or off, whatever the setting. */
    var override: Boolean? = null

    val on: Boolean get() = override ?: (setting == DarkModeSetting.ON || setting == DarkModeSetting.AUTO && systemDark)

    fun load(context: Context) {
        setting = runCatching { DarkModeSetting.entries[com.pokedaisy.app.Prefs(context).darkMode] }.getOrDefault(DarkModeSetting.OFF)
        systemDark = isSystemDark(context)
    }

    fun set(context: Context, s: DarkModeSetting) {
        com.pokedaisy.app.Prefs(context).darkMode = s.ordinal
        setting = s
    }

    fun isSystemDark(context: Context): Boolean = runCatching {
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }.getOrDefault(false)
}
