package com.pokedaisey.app.companion.data

/**
 * Where a ROM's m4a sound engine keeps its songs, so FfMusicRenderer can play
 * any one of them on its own on a second core (no sound effects, no cries):
 * [songNumStart] is `m4aSongNumStart(u16 n)`, [songTable] is `gSongTable`
 * (8-byte `{SongHeader*, u16 ms, u16 me}` entries).
 *
 * Found by code, not address: `m4aSongNumStart` is the same agbcc-compiled
 * library code in every decomp-built game and binary hack (FireRed, LeafGreen,
 * Ruby, Sapphire, Emerald, the QoL builds, CFRU hacks, ...), and its literal
 * pool holds `gSongTable`. Emerald Rogue builds it with modern gcc - same
 * logic, other instruction order - which [SIGNATURES]' second entry matches
 * (literal offsets and the `bl` wildcarded). Newer pokeemerald-expansion
 * hacks compile it differently again and get null.
 */
class M4aSongs private constructor(
    val songNumStart: Long,
    val songTable: Long,
    /** A Thumb `b .` in the ROM (any even address holding 0xE7FE), to park the render core's main loop on. */
    val idleLoop: Long?,
    private val ids: Map<Long, Int>,
    /** Every song the table plays on the BGM player (`ms` 0), once per song, in table order. */
    val bgmSongIds: List<Int>,
) {

    /** The song number playing [key] (an [FfMusicKey]), or null if it isn't in [songTable]. */
    fun songId(key: String): Int? = key.removePrefix("song_").toLongOrNull(16)?.let { ids[it] }

    companion object {
        /**
         * m4aSongNumStart's code ("." = any nibble) and where in it the
         * `ldr rX, [pc, #imm]` that loads gSongTable sits. agbcc: its first 32
         * bytes, up to the `bl MPlayStart` (relative, so it matches too), table
         * load at +6. gcc (Emerald Rogue): the whole function, table load at +2.
         */
        private val SIGNATURES = listOf(
            "00b50004074a0849400b401883885900c918890089180a680168101c00f0a2fb" to 6,
            "00b5..4b0004400bc018828853009b18..4a9b000168d058........01bc0047" to 2,
        )
        private const val MAX_SONGS = 4096

        fun locate(rom: ByteArray): M4aSongs? {
            val (at, tableLdr) = SIGNATURES.firstNotNullOfOrNull { (sig, ldr) ->
                indexOf(rom, sig, step = 2).takeIf { it >= 0 }?.let { it to ldr }
            } ?: return null
            val table = literal(rom, at, tableLdr) ?: return null
            val t = (table - ROM).toInt()
            if (t < 0 || t >= rom.size) return null
            val ids = HashMap<Long, Int>()
            val bgm = ArrayList<Int>()
            var n = 0
            while (n < MAX_SONGS && t + n * 8 + 8 <= rom.size) {
                val header = u32(rom, t + n * 8)
                if (header !in ROM until ROM + rom.size) break
                val ms = (rom[t + n * 8 + 4].toInt() and 0xFF) or ((rom[t + n * 8 + 5].toInt() and 0xFF) shl 8)
                if (ids.putIfAbsent(header - ROM, n) == null && ms == MUSIC_PLAYER_BGM) bgm += n
                n++
            }
            val spin = indexOf(rom, byteArrayOf(0xFE.toByte(), 0xE7.toByte()), step = 2).takeIf { it >= 0 }?.let { ROM + it }
            return if (n > 0) M4aSongs(ROM + at + 1, table, spin, ids, bgm) else null // +1: Thumb
        }

        /** The word a Thumb `ldr rX, [pc, #imm]` at [fn]+[off] loads. */
        private fun literal(rom: ByteArray, fn: Int, off: Int): Long? {
            val ins = (rom[fn + off].toInt() and 0xFF) or ((rom[fn + off + 1].toInt() and 0xFF) shl 8)
            if (ins ushr 11 != 0b01001) return null
            val addr = ((fn + off + 4) and 3.inv()) + (ins and 0xFF) * 4
            return if (addr + 4 <= rom.size) u32(rom, addr) else null
        }

        private fun indexOf(hay: ByteArray, needle: ByteArray, step: Int): Int {
            var i = 0
            outer@ while (i <= hay.size - needle.size) {
                for (j in needle.indices) if (hay[i + j] != needle[j]) { i += step; continue@outer }
                return i
            }
            return -1
        }

        /** [indexOf] for a hex [pattern] whose "." nibbles match anything. */
        private fun indexOf(hay: ByteArray, pattern: String, step: Int): Int {
            val n = pattern.length / 2
            val value = IntArray(n) { pattern.substring(it * 2, it * 2 + 2).replace('.', '0').toInt(16) }
            val mask = IntArray(n) { k ->
                (if (pattern[k * 2] == '.') 0 else 0xF0) or (if (pattern[k * 2 + 1] == '.') 0 else 0x0F)
            }
            var i = 0
            outer@ while (i <= hay.size - n) {
                for (j in 0 until n) if ((hay[i + j].toInt() and mask[j]) != value[j]) { i += step; continue@outer }
                return i
            }
            return -1
        }

        private fun u32(b: ByteArray, i: Int): Long =
            (b[i].toLong() and 0xFF) or ((b[i + 1].toLong() and 0xFF) shl 8) or
                ((b[i + 2].toLong() and 0xFF) shl 16) or ((b[i + 3].toLong() and 0xFF) shl 24)


        private const val ROM = 0x08000000L
        private const val MUSIC_PLAYER_BGM = 0
    }
}
