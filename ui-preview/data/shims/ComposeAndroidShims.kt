package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf

/** Android-only in real Compose. */
val LocalContext = staticCompositionLocalOf { android.content.Context() }
