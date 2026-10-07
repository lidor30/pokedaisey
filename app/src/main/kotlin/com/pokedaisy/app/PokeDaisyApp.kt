package com.pokedaisy.app

import android.app.Application

/** Process start: counts the app open (for CrashReports' one-time ask) and applies the crash-report choice. */
class PokeDaisyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs(this)
        prefs.appOpenCount = (prefs.appOpenCount + 1).coerceAtMost(1000)
        CrashReports.apply(prefs.crashReportsEnabled)
    }
}
