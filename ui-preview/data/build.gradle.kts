import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// The app's data layer + Android shims, in its own module so its huge
// generated tables compile once instead of on every UI edit.
plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

val app = rootDir.resolve("../app/src/main/kotlin/com/pokedaisey/app")
val appSrc = fileTree(app) {
    listOf("companion/data/**", "companion/*.kt", "MgbaCore.kt", "Prefs.kt", "GbaControls.kt", "Hotkeys.kt")
        .forEach { include(it) }
}
sourceSets["main"].kotlin.srcDir("shims")
tasks.withType<KotlinCompile>().configureEach {
    source(appSrc)
    kotlinOptions.jvmTarget = "17"
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies { api(compose.desktop.currentOs) }
