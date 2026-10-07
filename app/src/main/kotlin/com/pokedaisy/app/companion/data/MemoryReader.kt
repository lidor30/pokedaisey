package com.pokedaisy.app.companion.data

import com.pokedaisy.app.MgbaCore

/**
 * The one memory primitive the telemetry decoders need. In android-companion
 * this was `RetroArchClient.readCoreMemory` over UDP; here it's an in-process
 * bus read straight from the embedded mGBA core.
 */
interface MemoryReader {
    /** @throws TelemetryDecodeException if the read can't be satisfied. */
    fun readCoreMemory(addr: Long, size: Int): ByteArray
}

/**
 * Reads emulated GBA memory directly from the running core. Cheap (no sockets,
 * no datagram pairing), but [readCoreMemory] must be called on the emulator
 * thread — see EmulatorEngine.onSample — so it never races a running frame.
 */
object InProcessReader : MemoryReader {
    override fun readCoreMemory(addr: Long, size: Int): ByteArray {
        return MgbaCore.pkReadBytes(addr, size)
            ?: throw TelemetryDecodeException("core not running / read of $size B @ 0x${addr.toString(16)} failed")
    }
}
