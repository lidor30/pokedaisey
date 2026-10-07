package com.pokedaisy.app

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A copy of a game's save taken every time the game starts, before the emulator opens
 * it: `<saves>/pokedaisy-backups/<game>.backup-<yyyyMMdd-HHmmss>.<ext>`. Beside the
 * save (not in the app's private folder) so a file manager reaches it, it outlives an
 * uninstall, and LOAD SAVE can pick it. A copy is only made when the save differs from
 * the newest one, and the newest [KEEP] per game are kept.
 */
object SaveBackups {

    const val DIR_NAME = "pokedaisy-backups"
    const val KEEP = 10

    fun dir(save: File): File = File(save.parentFile, DIR_NAME)

    /** [save]'s backups, newest first. */
    fun list(save: File): List<File> =
        dir(save).listFiles { f -> f.isFile && f.name.startsWith("${save.nameWithoutExtension}.backup-") }
            ?.sortedByDescending { it.name }.orEmpty()

    /** Backs [save] up if it has data the newest backup doesn't. Never throws: a
     * folder we can't write mustn't keep the game from starting. */
    fun snapshot(save: File, now: Long = System.currentTimeMillis()): File? = runCatching {
        if (!save.isFile || save.length() == 0L || save.length() > GameSaves.MAX_BYTES) return null
        val bytes = save.readBytes()
        val existing = list(save)
        if (existing.firstOrNull()?.let { sameSave(it.readBytes(), bytes) } == true) return null
        val dir = dir(save).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
        val out = File(dir, "${save.nameWithoutExtension}.backup-$stamp.${save.extension}")
        val tmp = File(dir, "${out.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(out)) { tmp.delete(); return null }
        (listOf(out) + existing).drop(KEEP).forEach { it.delete() }
        out
    }.onFailure { Log.w("pokedaisy", "save backup failed: $it") }.getOrNull()

    /** Same save data, ignoring mGBA's 16-byte RTC footer (every GBA save chip's
     * size is a multiple of 512), which changes with the clock alone. */
    internal fun sameSave(a: ByteArray, b: ByteArray): Boolean {
        val n = a.size - a.size % 512
        if (n == 0 || n != b.size - b.size % 512) return a.contentEquals(b)
        for (i in 0 until n) if (a[i] != b[i]) return false
        return true
    }
}
