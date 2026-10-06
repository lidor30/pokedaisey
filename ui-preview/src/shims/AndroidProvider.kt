package android.provider

import android.net.Uri

object DocumentsContract {
    fun getTreeDocumentId(u: Uri): String = ""
    fun getDocumentId(u: Uri): String = ""
    fun isDocumentUri(c: android.content.Context, u: Uri) = false
}

object OpenableColumns { const val DISPLAY_NAME = "_display_name" }

object Settings {
    const val ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION = "a"
    const val ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION = "b"
    const val ACTION_MANAGE_UNKNOWN_APP_SOURCES = "c"
}
