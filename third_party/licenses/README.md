License texts of components the app ships that don't carry their own in this repo (mGBA, rcheevos and
mGBA's bundled libraries do: `third_party/mgba`, `third_party/rcheevos`). `app/build.gradle.kts`'s
`licensesAsset` task joins them with NOTICE, LICENSE and those into the in-app Settings > LICENSES page.

- `Apache-2.0.txt`: AndroidX, Jetpack Compose, Kotlin and kotlinx.coroutines, Apache Commons Compress and what it
  pulls in (Commons IO, Codec, Lang); `Apache-Commons-NOTICE.txt` is their NOTICE files, as Apache 2.0 §4(d) asks.
- `mGBA-shaders-MIT.txt`: the MIT notice of mGBA's LCD and Scanlines shaders, which ScreenShaders.kt ports.
- `PokeAPI-BSD-3-Clause.txt`: PokeAPI's data (the National Dex species list).
- `XZ-Java-0BSD.txt`: XZ for Java (.7z archives).
