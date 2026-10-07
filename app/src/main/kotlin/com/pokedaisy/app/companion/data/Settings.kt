package com.pokedaisy.app.companion.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Small SharedPreferences-backed override store, mirroring
 * tools/telemetry-viewer's --addr/--retroarch flags: the app ships with
 * working defaults (Config.kt), but if the ROM gets rebuilt with a different
 * gQolTelemetry address (or RetroArch is reachable somewhere other than
 * localhost:55355), the user can override it here without a new APK build.
 */
class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("fireredqol_companion", Context.MODE_PRIVATE)

    var telemetryAddrOverride: String?
        get() = prefs.getString(KEY_ADDR, null)
        set(value) = prefs.edit().putString(KEY_ADDR, value?.trim()?.ifEmpty { null }).apply()

    var retroArchHost: String
        get() = prefs.getString(KEY_HOST, DEFAULT_RETROARCH_HOST) ?: DEFAULT_RETROARCH_HOST
        set(value) = prefs.edit().putString(KEY_HOST, value).apply()

    var retroArchPort: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_RETROARCH_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    /** Which game to read: "firered" (packed gQolTelemetry struct) or
     *  "unbound" (native RAM read - no telemetry address needed). */
    var game: GameKind
        get() = parseGameKind(prefs.getString(KEY_GAME, null))
        set(value) = prefs.edit().putString(KEY_GAME, value.name.lowercase()).apply()

    fun resolveTelemetryAddr(): Long {
        val override = telemetryAddrOverride
        if (override != null) {
            val cleaned = override.removePrefix("0x").removePrefix("0X")
            cleaned.toLongOrNull(16)?.let { return it }
        }
        return DEFAULT_TELEMETRY_ADDR
    }

    companion object {
        private const val KEY_ADDR = "telemetry_addr"
        private const val KEY_HOST = "retroarch_host"
        private const val KEY_PORT = "retroarch_port"
        private const val KEY_GAME = "game"
    }
}
