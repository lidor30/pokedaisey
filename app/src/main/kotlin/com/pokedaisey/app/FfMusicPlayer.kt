package com.pokedaisey.app

import android.media.MediaPlayer
import android.os.SystemClock
import android.util.Log
import java.io.File

/**
 * Loops a cached FF background-music clip (see FfMusicCache — nothing
 * bundled, captured live on-device on first encounter) while the emulator
 * itself stays muted during fast-forward. Driven by
 * [EmulatorEngine.onFfMusicChanged], which only fires when the desired
 * clip actually changes — call [setClip] on the UI thread.
 */
class FfMusicPlayer {
    private var player: MediaPlayer? = null
    private var currentFile: File? = null

    // The clip last stopped, where it was and when: if it comes straight back
    // (the song key blinking, FF off for a moment) it carries on from where it
    // would be by now, rather than from the top.
    private var lastFile: File? = null
    private var lastPosMs = 0
    private var stoppedAtMs = 0L

    /** file == null stops playback (leaving FF, or nothing cached yet for
     * the current location/battle state). */
    fun setClip(file: File?) {
        if (file == currentFile) return
        player?.let { p ->
            lastFile = currentFile
            lastPosMs = runCatching { p.currentPosition }.getOrDefault(0)
            stoppedAtMs = SystemClock.elapsedRealtime()
            p.release()
        }
        currentFile = file
        player = null
        if (file == null) return

        player = runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                isLooping = true
                prepare()
                val away = SystemClock.elapsedRealtime() - stoppedAtMs
                if (file == lastFile && away < RESUME_WINDOW_MS && duration > 0) {
                    seekTo(((lastPosMs + away) % duration).toInt())
                }
                start()
            }
        }.onFailure {
            Log.w("pokedaisey", "ffmusic: couldn't play ${file.name}", it)
        }.getOrNull()
    }

    fun release() {
        player?.release()
        player = null
        currentFile = null
    }

    private companion object {
        const val RESUME_WINDOW_MS = 15_000L
    }
}
