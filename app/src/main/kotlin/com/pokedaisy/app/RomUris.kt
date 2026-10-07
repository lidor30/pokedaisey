package com.pokedaisy.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

/** Turning the ROM links other apps hand us (the file picker, a frontend like
 * Cocoon / iiSU / ES-DE) into names and paths. Shared by [LibraryActivity]'s
 * import and [LaunchActivity]. */
object RomUris {

    fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i) else null
            }
        }.getOrNull()
    }

    /**
     * Only strips what's genuinely illegal in an Android file name (the path
     * separator and NUL) — keeps spaces, parens, commas, etc. as-is. A prior
     * version whitelisted `[A-Za-z0-9._-]` and underscored everything else
     * (e.g. "Pokemon - FireRed (USA, Europe) (Rev 1).gba" became
     * "Pokemon_-_FireRed__USA__Europe___Rev_1_.gba"), which silently broke
     * matching a same-named `.sav`/`.srm` dropped in next to it (e.g. an
     * existing RetroArch save) — [SavesLocation.resolve] matches by exact
     * base-name equality.
     */
    fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[/\\x00]"), "_").trim()
        return cleaned.ifBlank { "imported-${System.currentTimeMillis()}.gba" }
    }

    /**
     * Best-effort real filesystem path behind [uri], so the library can show
     * "where it actually lives" instead of the private copy under this app's
     * own storage, and [LaunchActivity] can play a frontend's ROM in place.
     * Only resolvable for `file://` and `com.android.externalstorage.documents`
     * (the system Files/Downloads picker on internal or SD storage, and the
     * folder grants frontends ask for); falls back to the content:// URI itself
     * for other providers (e.g. Drive), which at least identifies the source
     * even if it's not a real path.
     */
    fun originalPath(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        if (!DocumentsContract.isDocumentUri(context, uri)) return uri.toString()
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return uri.toString()
        if (uri.authority != "com.android.externalstorage.documents") return uri.toString()
        val parts = docId.split(":", limit = 2)
        if (parts.size != 2) return uri.toString()
        val (volume, rel) = parts
        val root = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
        return "$root/$rel"
    }
}
