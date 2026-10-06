package com.pokedaisey.app

import android.content.Context
import android.net.Uri

/** Stand-in for the frontend entry point, which the Library only forwards to. */
class LaunchActivity : androidx.activity.ComponentActivity() {
    companion object {
        fun romUri(intent: android.content.Intent?): Uri? = null
    }
}

/** Stand-in for the app's RomUris (needs a real ContentResolver / DocumentsContract). */
object RomUris {
    fun displayName(context: Context, uri: Uri): String? = null
    fun sanitizeFileName(name: String): String = name.replace(Regex("[/\\x00]"), "_").trim()
    fun originalPath(context: Context, uri: Uri): String? = uri.toString()
}
