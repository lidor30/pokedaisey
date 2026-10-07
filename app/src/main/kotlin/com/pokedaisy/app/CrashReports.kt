package com.pokedaisy.app

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Crash reports through Firebase Crashlytics (native crashes too, via crashlytics-ndk) -
 * **opt-in**: the manifest turns collection off, and only the player's yes (Settings >
 * CRASH REPORTS, or the one-time ask in the Library) turns it on. Turning it off again
 * also drops anything not yet sent.
 *
 * A build without app/google-services.json (forks, PR checks) has no Firebase project:
 * [available] is false, the row and the ask don't show, and every call here is a no-op.
 * ui-preview has its own stand-in (it can't load Firebase).
 */
object CrashReports {
    /** Whether this build can send reports at all. */
    val available: Boolean get() = BuildConfig.CRASHLYTICS

    /** Whether to ask - once, after the third open and two days on from the install. */
    fun shouldAsk(context: Context, prefs: Prefs, now: Long = System.currentTimeMillis()): Boolean {
        if (!available || prefs.crashReportsAsked || prefs.crashReportsEnabled) return false
        val installed = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        }.getOrDefault(now)
        return shouldAsk(prefs.appOpenCount, installed, now)
    }

    /** The rule itself, for tests: [ASK_AFTER_OPENS] opens and [ASK_AFTER_MILLIS] since the install. */
    fun shouldAsk(opens: Int, installedAt: Long, now: Long): Boolean =
        opens >= ASK_AFTER_OPENS && now - installedAt >= ASK_AFTER_MILLIS

    /** The player's answer, from the ask or Settings: saved, then applied to Crashlytics. */
    fun setEnabled(prefs: Prefs, on: Boolean) {
        prefs.crashReportsEnabled = on
        apply(on)
    }

    /** Puts the saved choice in force (every process start, from [PokeDaisyApp]). */
    fun apply(on: Boolean) {
        if (!available) return
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCrashlyticsCollectionEnabled(on)
            if (on) crashlytics.setCustomKey("version", BuildConfig.VERSION_NAME)
            else crashlytics.deleteUnsentReports()
        }.onFailure { Log.w("pokedaisy", "crash reports: couldn't apply ($on)", it) }
    }

    const val ASK_AFTER_OPENS = 3
    const val ASK_AFTER_MILLIS = 48L * 60 * 60 * 1000
}
