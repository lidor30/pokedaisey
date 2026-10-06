package com.pokedaisey.app.companion.data

/**
 * Replays a captured real-device EWRAM+IWRAM snapshot (see
 * scripts/capture_fixture.sh, which writes app/src/test/resources/fixtures/<key>/
 * {ewram,iwram}.bin via PokeDaiseyActivity's dumpFixtureIfPending()) as a
 * [MemoryReader] - so decodeTelemetry/readNativeTelemetry can be exercised
 * against real device state with no device or emulator needed.
 *
 * Deliberately whole-region (full 256 KiB EWRAM + 32 KiB IWRAM), not a capture
 * of just the fields one decoder happens to read: this lets ANY address a
 * decoder asks for resolve correctly, including pointer chases through
 * per-boot-randomized offsets (gSaveBlock2Ptr's ASLR-style offset, bag pocket
 * pointers, ...) that a narrower capture could miss.
 */
class FixtureMemoryReader private constructor(
    private val ewram: ByteArray,
    private val iwram: ByteArray,
) : MemoryReader {
    override fun readCoreMemory(addr: Long, size: Int): ByteArray {
        val (base, buf) = regionFor(addr, size)
            ?: throw TelemetryDecodeException(
                "fixture has no data for 0x${addr.toString(16)}..+$size " +
                    "(captured EWRAM 0x${EWRAM_BASE.toString(16)}+0x${ewram.size.toString(16)} " +
                    "and IWRAM 0x${IWRAM_BASE.toString(16)}+0x${iwram.size.toString(16)} only)",
            )
        val off = (addr - base).toInt()
        return buf.copyOfRange(off, off + size)
    }

    private fun regionFor(addr: Long, size: Int): Pair<Long, ByteArray>? = when {
        addr >= EWRAM_BASE && addr + size <= EWRAM_BASE + ewram.size -> EWRAM_BASE to ewram
        addr >= IWRAM_BASE && addr + size <= IWRAM_BASE + iwram.size -> IWRAM_BASE to iwram
        else -> null
    }

    /** Mirrors MgbaCore.pkFindMagic: scans IWRAM then EWRAM for a byte
     * sequence, returning its absolute address, or -1 if not found. Used to
     * locate gQolTelemetry's "QOLT" magic the same way the app does, instead
     * of hardcoding a capture-time address that could drift on a rebuild. */
    fun findMagic(magic: ByteArray): Long {
        for ((base, buf) in listOf(IWRAM_BASE to iwram, EWRAM_BASE to ewram)) {
            var i = 0
            outer@ while (i <= buf.size - magic.size) {
                for (j in magic.indices) {
                    if (buf[i + j] != magic[j]) {
                        i++
                        continue@outer
                    }
                }
                return base + i
            }
        }
        return -1
    }

    companion object {
        private const val EWRAM_BASE = 0x02000000L
        private const val IWRAM_BASE = 0x03000000L

        /** [key] must match a directory under app/src/test/resources/fixtures/
         * (and a line in scripts/roms.conf - that's where it came from). */
        fun load(key: String): FixtureMemoryReader {
            fun read(name: String): ByteArray {
                val path = "fixtures/$key/$name"
                val stream = FixtureMemoryReader::class.java.classLoader.getResourceAsStream(path)
                    ?: error("missing test fixture $path - run scripts/capture_fixture.sh $key first")
                return stream.use { it.readBytes() }
            }
            return FixtureMemoryReader(read("ewram.bin"), read("iwram.bin"))
        }
    }
}
