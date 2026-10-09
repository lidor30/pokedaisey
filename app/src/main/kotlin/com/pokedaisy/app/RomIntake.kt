package com.pokedaisy.app

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The checks every way a ROM comes in from outside goes through - the exported [LaunchActivity]
 * (frontends) and the library's VIEW / + import - since any app can hand either one a URI: never
 * this app's own files or provider (prefs hold the RetroAchievements token and API keys), at most
 * [MAX_COPY_BYTES] copied, and only a cart image kept ([RomIdentity.looksLikeRom]).
 */
object RomIntake {
    /** The largest GBA cart is 32 MB; an archive of one is smaller. */
    const val MAX_COPY_BYTES = 40L shl 20

    /** [uri] points into this app: its own provider, or a file in its private data directory. */
    fun isOwn(context: Context, uri: Uri): Boolean = when (uri.scheme) {
        "content" -> uri.authority?.startsWith(context.packageName) == true
        "file" -> uri.path?.let { isPrivate(context, File(it)) } ?: true
        else -> false
    }

    /** This app's private data directory (prefs, caches). */
    fun isPrivate(context: Context, f: File): Boolean = runCatching {
        val path = f.canonicalPath
        listOfNotNull(context.applicationInfo.dataDir, context.filesDir.parent, context.cacheDir.parent)
            .map { File(it).canonicalPath }
            .any { path == it || path.startsWith("$it/") }
    }.getOrDefault(true)

    /** [input] into [out], throwing past [MAX_COPY_BYTES]: nothing handed in fills the disk. */
    fun copyCapped(input: InputStream, out: OutputStream) {
        val buf = ByteArray(1 shl 16)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) return
            total += n
            if (total > MAX_COPY_BYTES) throw IOException("more than $MAX_COPY_BYTES bytes - not a ROM")
            out.write(buf, 0, n)
        }
    }
}
