package com.pokedaisy.app

import java.nio.ByteBuffer

/**
 * Thin Kotlin facade over the native mGBA bridge (`libpokedaisy.so`).
 *
 * All methods must be called from the single emulator thread, and so must
 * reads of [pkVideoBuffer]'s [ByteBuffer]: the core draws into it during
 * [pkRunFrame], so the screen gets a copy made between frames
 * ([EmulatorView.publishFrame]).
 */
object MgbaCore {
    init {
        System.loadLibrary("pokedaisy")
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
    /** Resample the core's audio to [rate] samples per emulated second (default [pkSampleRate]). Emu thread. */
    external fun pkSetAudioRate(rate: Double)

    /** Runs the game's Thumb function [fn] (r0 = [a0], r1 = [a1]) to its return on the player's
     * core and gives back its r0, or -1 if it never returned. Emu thread, between frames. */
    external fun pkCall(fn: Long, a0: Int, a1: Int): Long

    external fun pkSetKeys(mask: Int)
    external fun pkRunFrame()

    /** Copies up to [out].size interleaved L/R s16 samples of the last frame; returns count. */
    external fun pkReadAudio(out: ShortArray): Int

    external fun pkSaveState(path: String): Boolean
    external fun pkLoadState(path: String): Boolean
    /** Whether the save inside the state at [path] is the save file the game has open
     * (1), isn't (0: loading it would overwrite the file with an older save), or the
     * state holds none (-1). Call after [pkInit]. */
    external fun pkStateMatchesSave(path: String): Int

    // --- memory / ROM introspection (emu-thread only) ---

    /** Reads [len] bytes of emulated bus memory from GBA address [addr]. */
    external fun pkReadBytes(addr: Long, len: Int): ByteArray?

    /** GBA address of the first occurrence of [magic] (4 bytes) in IWRAM/EWRAM, or -1. */
    external fun pkFindMagic(magic: ByteArray): Long

    /** 4-char game code from the ROM header (`BPRE`, `BPEE`, …). */
    external fun pkRomCode(): String?
    external fun pkRomSize(): Long
    /** 0 = GBA, 1 = Game Boy / Color, -1 = no core. */
    external fun pkPlatform(): Int
    /** A Game Boy / Color cart's ROM bytes (it's bank-switched, so not on the bus whole); null on GBA. */
    external fun pkRomRead(off: Long, len: Int): ByteArray?

    // --- cheats (pk_cheats.c, see cheats/Cheats.kt) ---

    /** Checks a cheat's code lines ('\n'-separated) as [type] (CheatType.native), after
     * [directive] ("" or a .cheats directive). No core needed, any thread. Returns
     * "<directive mGBA settled on>\n<'1' / '0' per line>", null on failure. */
    external fun pkCheatsCheck(code: String, type: Int, directive: String): String?

    /** Emu thread: replaces the core's cheats with [text] (mGBA .cheats, enabled
     * ones only; "" = none). Returns how many loaded, -1 if it didn't parse. */
    external fun pkCheatsApply(text: String): Int

    // --- FF-music rendering (see FfMusicRenderer) ---
    // A second, fully independent core, never the player's real session —
    // no save file, never shown, never touched by input. Same one-frame-
    // then-drain contract as pkRunFrame/pkReadAudio, just its own handle.

    external fun pkRenderInit(romPath: String): Boolean
    external fun pkRenderDeinit()
    external fun pkRenderRunFrame()
    external fun pkRenderSampleRate(): Int
    external fun pkRenderReadAudio(out: ShortArray): Int

    /** Calls the ROM's own m4aSongNumStart(songId, alt) at [addr] on the render
     * core and returns once it has, CPU state untouched (see the JNI side).
     * [addr] must be right for the ROM loaded there (M4aSongs finds it); [alt]
     * only means something to Heart and Soul's (its alternate soundtrack), but
     * it's always set: r1 was whatever the interrupted game left there. */
    external fun pkRenderForceSong(addr: Long, songId: Int, alt: Int): Boolean

    /** Parks the render core's main loop on [spin] (a Thumb `b .`) so the booted game can't
     * change the music; the sound engine keeps running from VBlank. False if it never got
     * to a safe moment (see the JNI side). */
    external fun pkRenderPark(spin: Long): Boolean

    /** A Game Boy render core's [pkRenderPark]: the main loop goes to [spin] (a `jr @` in bank 0). */
    external fun pkRenderGbPark(spin: Int): Boolean

    /** Calls the Game Boy render core's [fn] (bank 0) with A = [a], C = [c], until it returns to [ret]. */
    external fun pkRenderGbCall(fn: Int, a: Int, c: Int, ret: Int): Boolean

    /** [pkReadBytes] for the render core (which song it's really playing - see FfMusicKey). */
    external fun pkRenderReadBytes(addr: Long, len: Int): ByteArray?
}
