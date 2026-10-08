package com.pokedaisy.app.companion.data

import java.io.File
import java.io.RandomAccessFile

/**
 * Whether the game has its region map (Town Map, Fly map, a wall map, the
 * PokéNav's map) open, so fast-forward can step aside while the player moves
 * its cursor. Each map screen keeps its state in an EWRAM pointer that's only
 * set while it's open ([find] locates them per ROM); [isOpen] checks them
 * once a frame.
 *
 * - FireRed / LeafGreen and the hacks built on them: region_map.c's
 *   `sRegionMap` (Town Map, Fly map and wall maps alike), found from the
 *   literal pools that load it next to its struct offset `0x4796`.
 * - Emerald (retail and the QoL build): field_region_map.c's
 *   `sFieldRegionMapHandler`, region_map.c's `sFlyMap`, and - Emerald has no
 *   Town Map item, its map is the PokéNav's HOENN MAP - pokenav.c's
 *   `gPokenavResources->substructPtrs[POKENAV_SUBSTRUCT_REGION_MAP_STATE]`,
 *   set only while that page is up. Emerald hacks move them, so they get
 *   nothing yet.
 */
object RegionMapWatch {
    /** An EWRAM pointer that's non-null while a map is open - or, with [field] >= 0, the pointer at that offset into what it points to. */
    data class Watch(val addr: Long, val field: Int = -1)

    @Volatile
    private var watches = emptyList<Watch>()

    /** Scans [rom] in the background for its map pointers. */
    fun load(rom: File) {
        watches = emptyList()
        Thread({
            try {
                watches = RandomAccessFile(rom, "r").use { f ->
                    val n = minOf(f.length(), SCAN_END.toLong()).toInt()
                    val b = ByteArray(n)
                    f.readFully(b)
                    find(b)
                }
            } catch (t: Throwable) {
                android.util.Log.w("pokedaisy", "RegionMapWatch: scan failed", t)
            }
        }, "pokedaisy-map-watch").apply { isDaemon = true; start() }
    }

    /** True while any of this ROM's map screens is open. */
    fun isOpen(reader: MemoryReader): Boolean {
        for (w in watches) {
            var p = read32(reader, w.addr) ?: continue
            if (w.field >= 0) {
                if (p !in EWRAM) continue
                p = read32(reader, p + w.field) ?: continue
            }
            if (p != 0L) return true
        }
        return false
    }

    private fun read32(reader: MemoryReader, addr: Long): Long? {
        val b = runCatching { reader.readCoreMemory(addr, 4) }.getOrNull() ?: return null
        if (b.size < 4) return null
        return (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
            ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
    }

    private val EWRAM = 0x02000000L until 0x02040000L
    private const val SCAN_END = 4 shl 20 // the code, where the literal pools are
    private const val FR_REGION_MAP_OFFSET = 0x4796 // a struct RegionMap field the code loads beside sRegionMap
    private const val EM_FIELD_REGION_MAP = 0x0203BCD0L
    private const val EM_FLY_MAP = 0x0203A148L
    private const val EM_POKENAV_RESOURCES = 0x0203CF40L
    // Japanese Emerald's (English's literal pools, matched in its code).
    private const val JA_FIELD_REGION_MAP = 0x0203B99CL
    private const val JA_FLY_MAP = 0x02039E14L
    private const val JA_POKENAV_RESOURCES = 0x0203CC0CL
    // struct PokenavResources: 4 words of state, then substructPtrs[]; REGION_MAP_STATE is index 3.
    private const val EM_POKENAV_REGION_MAP_STATE = 0x10 + 3 * 4

    /** The map pointers to watch for the ROM whose first bytes are [rom] (at least its code). */
    fun find(rom: ByteArray): List<Watch> {
        if (rom.size < 0x100) return emptyList()
        fun u32(i: Int) = (rom[i].toLong() and 0xFF) or ((rom[i + 1].toLong() and 0xFF) shl 8) or
            ((rom[i + 2].toLong() and 0xFF) shl 16) or ((rom[i + 3].toLong() and 0xFF) shl 24)
        val end = minOf(rom.size, SCAN_END) - 8
        return when (String(rom, 0xAC, 4, Charsets.US_ASCII)) {
            // the other-language FireRed / LeafGreen too: the pattern finds their own pointer
            "BPRE", "BPGE", "BPRD", "BPRF", "BPRI", "BPRS", "BPGF", "BPGI", "BPGS" -> {
                val seen = HashMap<Long, Int>()
                for (i in 0..end step 4) {
                    val a = u32(i)
                    if (a in EWRAM && u32(i + 4) == FR_REGION_MAP_OFFSET.toLong()) seen[a] = (seen[a] ?: 0) + 1
                }
                // sRegionMap is loaded that way 3 times; another pointer twice.
                seen.filterValues { it >= 3 }.keys.map { Watch(it) }
            }
            "BPEE", "BPES", "BPED", "BPEF", "BPEI", "BPEJ" -> { // the European Emeralds keep English's RAM
                val ja = String(rom, 0xAC, 4, Charsets.US_ASCII) == "BPEJ"
                val field = if (ja) JA_FIELD_REGION_MAP else EM_FIELD_REGION_MAP
                val fly = if (ja) JA_FLY_MAP else EM_FLY_MAP
                val pokenav = if (ja) JA_POKENAV_RESOURCES else EM_POKENAV_RESOURCES
                val want = longArrayOf(field, fly, pokenav)
                val seen = IntArray(want.size)
                for (i in 0..end step 4) {
                    val a = u32(i)
                    for (k in want.indices) if (a == want[k]) seen[k]++
                }
                // Retail loads them 7, 15 and 15 times; a hack that moved them
                // might still use one of the addresses for something else.
                if (seen.all { it >= 5 }) {
                    listOf(Watch(field), Watch(fly), Watch(pokenav, EM_POKENAV_REGION_MAP_STATE))
                } else emptyList()
            }
            else -> emptyList()
        }
    }
}
