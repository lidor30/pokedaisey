plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Screenshot tests for the Compose UI (app/src/test/.../ui/*ScreenshotTest.kt).
    // 1.3.4 is the last release built on Kotlin 1.9.24 - newer ones need Kotlin 2.
    id("app.cash.paparazzi") version "1.3.4" apply false
    // Crash reports (opt-in, off by default): applied by app/ only when its
    // google-services.json is there - see CrashReports.kt.
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}
