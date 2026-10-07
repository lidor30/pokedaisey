package com.pokedaisy.app.companion.data

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * Party / bag icons pulled straight out of the running FireRed/Emerald QoL ROM,
 * so nothing has to be bundled. Uses the icon-table addresses the v2
 * `gQolTelemetry` struct now exports ([IconTables]) — set [tables] once per
 * poll. Sibling of [UnboundIconSource]; the difference is the decomp layout:
 *
 *  - mon icons: `gMonIconTable[species]` -> 1024 bytes of **uncompressed** 4bpp
 *    tiles (32x64, two frames — crop to the first). Palette is a separate
 *    indirection: `gMonIconPaletteIndices[species]` picks a
 *    `gMonIconPaletteTable[]` entry (`{u32 data; u16 tag}`, 8 bytes) whose
 *    `data` points at 32 bytes of BGR555.
 *  - item icons: `gItemIconTable[item][2]` = `{LZ77 tiles, LZ77 palette}`,
 *    24x24 — identical to Unbound. Newer expansion uses smol tiles ([Smol]).
 *
 * pokeemerald-expansion hacks keep the same data but inside each
 * gSpeciesInfo/gItemsInfo entry instead - [IconTables]' stride/mask fields
 * cover that without a separate code path.
 *
 * Reads go through [InProcessReader], so call from the emulator thread (which
 * the icon composables don't — accepted small race for cosmetic sprites, same
 * as [UnboundIconSource]).
 */
object DecompIconSource {

    /** The running game's icon tables. The caches are keyed by id alone, so a
     * different game's tables drop them (else its icons show the last game's). */
    @Volatile
    var tables: IconTables = IconTables()
        set(value) {
            if (value != field) {
                monCache.clear()
                itemCache.clear()
            }
            field = value
        }

    /** Swappable so tests / previews can read a ROM file instead of the core. */
    @Volatile
    var reader: MemoryReader = InProcessReader

    private const val MON_TILE_BYTES = 1024
    private const val PAL_BYTES = 32
    private const val MON_PAL_ENTRY_BYTES = 8

    private val monCache = ConcurrentHashMap<Int, Bitmap>()
    private val itemCache = ConcurrentHashMap<Int, Bitmap>()
    private val missing = java.util.Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())
    private val lock = Any()
    @Volatile private var loggedOnce = false

    /** An already-decoded icon (no ROM read), or null. */
    fun peek(species: Int): Bitmap? = if (tables.monPresent) monCache[species] else null

    fun get(species: Int): Bitmap? = cached(monCache, species, tables.monPresent) { fetchMon(species) }
    fun getItem(itemId: Int): Bitmap? = cached(itemCache, 1_000_000 + itemId, tables.itemPresent) { fetchItem(itemId) }

    private inline fun cached(cache: ConcurrentHashMap<Int, Bitmap>, key: Int, gate: Boolean, fetch: () -> Bitmap?): Bitmap? {
        if (key <= 0 || !gate) return null
        cache[key]?.let { return it }
        val sig = tables.monIconTable * 31 + key
        if (sig in missing) return null
        synchronized(lock) {
            cache[key]?.let { return it }
            val bmp = try { fetch() } catch (e: Exception) {
                android.util.Log.w("pokedaisy", "DecompIcon fetch $key failed", e); null
            }
            if (bmp == null) { missing.add(sig); return null }
            if (!loggedOnce) {
                loggedOnce = true
                android.util.Log.i("pokedaisy", "DecompIcon: first sprite decoded ${bmp.width}x${bmp.height} from tables=$tables")
            }
            cache[key] = bmp
            return bmp
        }
    }

    private fun rd(addr: Long, n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val chunk = minOf(512, n - off)
            reader.readCoreMemory(addr + off, chunk).copyInto(out, off, 0, chunk)
            off += chunk
        }
        return out
    }

    private fun fetchMon(species: Int): Bitmap? {
        val t = tables
        val tilesPtr = Gfx.u32(rd(t.monIconTable + species.toLong() * t.monIconStride, 4), 0)
        if (!Gfx.inRom(tilesPtr)) {
            android.util.Log.w("pokedaisy", "DecompIcon mon $species: tilesPtr %08x not in ROM".format(tilesPtr)); return null
        }
        val palIdx = rd(t.monIconPaletteIndices + species.toLong() * t.monPalIdxStride, 1)[0].toInt() and t.monPalIdxMask
        val entry = rd(t.monIconPaletteTable + palIdx.toLong() * MON_PAL_ENTRY_BYTES, MON_PAL_ENTRY_BYTES)
        val palDataPtr = Gfx.u32(entry, 0)
        if (!Gfx.inRom(palDataPtr)) {
            android.util.Log.w("pokedaisy", "DecompIcon mon $species: palPtr %08x not in ROM (idx=$palIdx)".format(palDataPtr)); return null
        }
        val tiles = rd(tilesPtr, MON_TILE_BYTES)
        val pal = rd(palDataPtr, PAL_BYTES)
        // 32x64 = 4x8 tiles; UI crops to the first 32x32 frame.
        return Gfx.decode4bpp(tiles, 0, pal, 0, 4, 8)
    }

    private fun fetchItem(itemId: Int): Bitmap? {
        val t = tables
        val extra = itemId - t.extraItemFirst
        val entry = if (t.extraItemIconTable != 0L && extra in 0 until t.extraItemCount) {
            rd(t.extraItemIconTable + extra.toLong() * t.extraItemStride, 8)
        } else {
            rd(t.itemIconTable + itemId.toLong() * t.itemIconStride, 8)
        }
        val tilesPtr = Gfx.u32(entry, 0)
        val palPtr = Gfx.u32(entry, 4)
        if (!Gfx.inRom(tilesPtr) || !Gfx.inRom(palPtr)) return null
        // Newer expansion (Heart and Soul) smol-compresses these instead of LZ77.
        val raw = rd(tilesPtr, 640)
        val tiles = if (Smol.isSmol(raw)) Smol.decompress(raw) else Gfx.lz77(raw)
        val pal = if (t.itemPalCompressed) Gfx.lz77(rd(palPtr, 96)) else rd(palPtr, PAL_BYTES)
        if (tiles.size < 3 * 3 * 32 || pal.size < PAL_BYTES) return null
        return Gfx.decode4bpp(tiles, 0, pal, 0, 3, 3)
    }
}
