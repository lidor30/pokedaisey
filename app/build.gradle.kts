import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("app.cash.paparazzi")
}

// Release signing comes from an untracked keystore.properties (storeFile, storePassword,
// keyAlias, keyPassword) - the key itself lives outside this public repo. Without it,
// release builds come out unsigned. Every GitHub release must be signed with the same
// key, or the in-app updater's APK won't install over the previous one.
val keystoreProps = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { f ->
    Properties().apply { f.inputStream().use(::load) }
}

android {
    namespace = "com.pokedaisy.app"
    compileSdk = 34
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.pokedaisy.app"
        minSdk = 26
        targetSdk = 34
        // Bump both for every GitHub release: the updater compares versionName
        // against the release tag (v<versionName>), Android needs versionCode to grow.
        versionCode = 9
        versionName = "1.1.4"

        ndk {
            // Thor is arm64; add armeabi-v7a later only if a target device needs it.
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                // mGBA core is C-only, but keep a real STL around in case a
                // transitive piece needs it; it's cheap.
                arguments += "-DANDROID_STL=c++_shared"
                cFlags += "-O2"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    androidResources {
        // Stored, so the fonts are mapped straight from the APK instead of
        // inflated into memory each time one loads (PixelTypeface.kt).
        noCompress += "ttf"
    }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreProps != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
    sourceSets["test"].java.srcDirs("src/test/kotlin")

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

tasks.withType<Test> {
    // readNativeTelemetry's bag-read throttle (NativeReader.kt) caches its
    // last result in file-level (process-global) state - harmless in the
    // real app (one NativeConfig per process) but it leaks between different
    // games' tests if they share a JVM. One fresh JVM per test class keeps
    // each game's decode test isolated without touching production code.
    forkEvery = 1
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Bottom-screen companion UI (ported from tools/android-companion).
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime")

    // ROMs in .7z archives (RomArchive; .zip is java.util.zip). xz is 7z's LZMA / LZMA2.
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.12")

    // Decode-logic regression tests (app/src/test) - see scripts/capture_fixture.sh
    // for how the fixtures they read (app/src/test/resources/fixtures/) get made.
    testImplementation("junit:junit:4.13.2")
}
