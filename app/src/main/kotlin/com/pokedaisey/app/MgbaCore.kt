package com.pokedaisey.app

import java.nio.ByteBuffer

/**
 * Thin Kotlin facade over the native mGBA bridge (`libpokedaisey.so`).
 *
 * All methods must be called from the single emulator thread, with one
 * exception: [videoBuffer]'s returned [ByteBuffer] may be read from the GL
 * thread while the emu thread writes it (tearing is possible and, for Phase 0,
 * acceptable).
 */
object MgbaCore {
    init {
        System.loadLibrary("pokedaisey")
    }

    /** GBA key bit positions, matching the native bridge. */
    object Key {
        const val A = 1 shl 0
        const val B = 1 shl 1
        const val SELECT = 1 shl 2
        const val START = 1 shl 3
        const val RIGHT = 1 shl 4
        const val LEFT = 1 shl 5
        const val UP = 1 shl 6
        const val DOWN = 1 shl 7
        const val R = 1 shl 8
        const val L = 1 shl 9
    }

    /** Loads [romPath], attaches [savePath] as persistent SRAM, resets. */
    external fun pkInit(romPath: String, savePath: String?): Boolean
    external fun pkDeinit()

    /** Direct buffer over the native RGBA8888 framebuffer, [pkVideoWidth] x [pkVideoHeight]. */
    external fun pkVideoBuffer(): ByteBuffer?
    external fun pkVideoWidth(): Int
    external fun pkVideoHeight(): Int
    external fun pkSampleRate(): Int

    external fun pkSetKeys(mask: Int)
    external fun pkRunFrame()

    /** Copies up to [out].size interleaved L/R s16 samples of the last frame; returns count. */
    external fun pkReadAudio(out: ShortArray): Int

    external fun pkSaveState(path: String): Boolean
    external fun pkLoadState(path: String): Boolean

    // --- memory / ROM introspection (emu-thread only) ---

    /** Reads [len] bytes of emulated bus memory from GBA address [addr]. */
    external fun pkReadBytes(addr: Long, len: Int): ByteArray?

    /** GBA address of the first occurrence of [magic] (4 bytes) in IWRAM/EWRAM, or -1. */
    external fun pkFindMagic(magic: ByteArray): Long

    /** 4-char game code from the ROM header (`BPRE`, `BPEE`, …). */
    external fun pkRomCode(): String?
    external fun pkRomSize(): Long

    // --- FF-music rendering (see FfMusicRenderer) ---
    // A second, fully independent core, never the player's real session —
    // no save file, never shown, never touched by input. Same one-frame-
    // then-drain contract as pkRunFrame/pkReadAudio, just its own handle.

    external fun pkRenderInit(romPath: String): Boolean
    external fun pkRenderDeinit()
    external fun pkRenderRunFrame()
    external fun pkRenderSampleRate(): Int
    external fun pkRenderReadAudio(out: ShortArray): Int

    /** Calls the ROM's own m4aSongNumStart(songId) at [addr] on the render
     * core and returns once it has, CPU state untouched (see the JNI side).
     * [addr] must be right for the ROM loaded there (M4aSongs finds it). */
    external fun pkRenderForceSong(addr: Long, songId: Int): Boolean

    /** Parks the render core's main loop on [spin] (a Thumb `b .`) so the booted game can't
     * change the music; the sound engine keeps running from VBlank. False if it never got
     * to a safe moment (see the JNI side). */
    external fun pkRenderPark(spin: Long): Boolean

    /** [pkReadBytes] for the render core (which song it's really playing - see FfMusicKey). */
    external fun pkRenderReadBytes(addr: Long, len: Int): ByteArray?
}
