package com.pokedaisy.app.companion.data

import java.io.File
import java.security.MessageDigest

/**
 * A Game Boy Pokémon game's own art, rebuilt from the player's ROM on its first
 * launch (like [RomArt] for the GBA games - nothing of the game is bundled) into
 * the same rom-art directory: its font, as a TrueType file the companion sets every
 * text in ([BitmapTtf]), and its party icons, one two-frame sheet per species
 * (16x32, frame 1 over frame 2, like the GBA icon sheets) in the party menu's own
 * GBC colours.
 *
 * Addresses are pret/pokeyellow's symbols ([Gen1ArtTables]); the ROM is checked
 * by SHA1 first, so another cart never gets a font cut from the wrong bytes.
 */
object Gen1Art {
    const val YELLOW_FONT = "gen1/yellow-font.ttf"
    const val YELLOW_ICONS = "gen1/yellow/pokemon"
    /** The player on the town map: Red's standing sprite (MapScreen's head asset). */
    const val YELLOW_PLAYER = "regionmap/player_yellow.png"
    /** The town map, where MapScreen loads region maps from ([activeRegionMapImages] "yellow"). */
    const val YELLOW_TOWN_MAP = "regionmap/yellow.png"
    /** Bump when what [extractTo] writes changes, so a cached ROM's art is rebuilt once. */
    private const val VERSION = 3

    /** Where a Gen 1 game's own art sits in its ROM (bank, address). */
    class Gen1ArtTables(
        val sha1: String,
        val font: Pair<Int, Int>,
        val fontPath: String,
        val iconPointers: Pair<Int, Int>,
        val iconPointerCount: Int,
        val monPartyData: Pair<Int, Int>,
        val dexCount: Int,
        val iconsDir: String,
        /** The party menu's icon colours for pixel values 1-3 (0 is clear). */
        val iconColors: IntArray,
        val townMap: Pair<Int, Int>,
        val townMapTiles: Pair<Int, Int>,
        val townMapPath: String,
        /** The town map's GBC palette (pixel values 0-3). */
        val townMapColors: IntArray,
        /** The player's overworld sprite: its first 16x16 frame (facing down) is the town map's player. */
        val playerSprite: Pair<Int, Int>,
        val playerPath: String,
    )

    val YELLOW = Gen1ArtTables(
        sha1 = TelemetrySampler.YELLOW_SHA1,
        font = 0x04 to 0x4600,                 // FontGraphics: tiles 0x80-0xFF, 1bpp
        fontPath = YELLOW_FONT,
        iconPointers = 0x1C to 0x584D,         // MonPartySpritePointers: {ptr, count, bank, VRAM dest}
        iconPointerCount = 30,                 // 15 icon loads per animation frame
        monPartyData = 0x1C to 0x59BA,         // icon type per Dex number, 2 per byte, high first
        dexCount = 151,
        iconsDir = YELLOW_ICONS,
        // The GBC party menu (sampled headless): white, yellow, black.
        iconColors = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFF00.toInt(), 0xFF181818.toInt()),
        townMap = 0x1C to 0x518A,              // CompressedMap: RLE, a byte = tile (high) x count (low), 0 ends
        townMapTiles = 0x04 to 0x5138,         // WorldMapTileGraphics: the 16 tiles it uses, 2bpp
        townMapPath = YELLOW_TOWN_MAP,
        // CGB PAL_TOWNMAP (pokeyellow data/sgb/sgb_palettes.asm), the whole screen's.
        townMapColors = intArrayOf(0xFFFFFFFF.toInt(), 0xFF00ADFF.toInt(), 0xFF52E700.toInt(), 0xFF080808.toInt()),
        playerSprite = 0x05 to 0x4571,         // RedSprite: 16x16 frames of 4 tiles (TL, TR, BL, BR)
        playerPath = YELLOW_PLAYER,
    )
    private val GAMES = listOf(YELLOW)

    /**
     * The game's text characters for its font tiles 0x80-0xFF (constants/charmap.asm):
     * letters, digits and the punctuation the companion prints. Ligatures ('d, PK, MN)
     * and unused kana are left out; Pixel Operator stands in for anything else.
     */
    private val CHARS: Map<Int, Char> = buildMap {
        for (i in 0 until 26) { put(0x80 + i, 'A' + i); put(0xA0 + i, 'a' + i) }
        for (i in 0 until 10) put(0xF6 + i, '0' + i)
        putAll(mapOf(
            0x9A to '(', 0x9B to ')', 0x9C to ':', 0x9D to ';', 0x9E to '[', 0x9F to ']', 0xBA to 'é',
            0xE0 to '\'', 0xE3 to '-', 0xE6 to '?', 0xE7 to '!', 0xE8 to '.', 0xEC to '▷', 0xED to '▶',
            0xEE to '▼', 0xEF to '♂', 0xF0 to '¥', 0xF1 to '×', 0xF3 to '/', 0xF4 to ',', 0xF5 to '♀',
        ))
    }

    private fun off(bank: Int, addr: Int) = if (bank == 0) addr else bank * 0x4000 + (addr - 0x4000)

    /** The font for [t] from [rom]'s FontGraphics, plus an empty space. */
    fun font(rom: ByteArray, t: Gen1ArtTables): ByteArray {
        val base = off(t.font.first, t.font.second)
        val glyphs = HashMap<Char, ByteArray>()
        for ((tile, c) in CHARS) {
            val o = base + (tile - 0x80) * 8
            glyphs[c] = rom.copyOfRange(o, o + 8)
        }
        glyphs[' '] = ByteArray(8)
        return BitmapTtf.build("Gen1 Pokemon", glyphs)
    }

    private const val ICON_HELIX = 2      // the one asymmetric icon: 4 tiles, no mirroring
    private const val ICON_OFFSET = 0x40  // VRAM tiles of the second animation frame

    /**
     * Every species' party icon, keyed by Dex number: 16x32, frame 1 over frame 2. Built
     * the way the party menu builds them - MonPartySpritePointers loads the icon shapes'
     * tiles into VRAM, then each 16x16 is a tile column and its mirror image (the helix's
     * 4 tiles are drawn as they are).
     */
    fun icons(rom: ByteArray, t: Gen1ArtTables): Map<Int, RomArt.Image> {
        val vram = HashMap<Int, ByteArray>()
        val ptrBase = off(t.iconPointers.first, t.iconPointers.second)
        for (e in 0 until t.iconPointerCount) {
            val o = ptrBase + 6 * e
            val ptr = (rom[o].toInt() and 0xFF) or ((rom[o + 1].toInt() and 0xFF) shl 8)
            val count = rom[o + 2].toInt() and 0xFF
            val bank = rom[o + 3].toInt() and 0xFF
            val dest = ((rom[o + 4].toInt() and 0xFF) or ((rom[o + 5].toInt() and 0xFF) shl 8)) - 0x8000
            for (k in 0 until count) {
                val src = off(bank, ptr) + 16 * k
                vram[dest / 16 + k] = rom.copyOfRange(src, src + 16)
            }
        }
        fun pixel(slot: Int, x: Int, y: Int): Int {
            val tile = vram[slot] ?: return 0
            val lo = tile[2 * y].toInt() shr (7 - x) and 1
            val hi = tile[2 * y + 1].toInt() shr (7 - x) and 1
            return lo or (hi shl 1)
        }
        fun frame(img: RomArt.Image, base: Int, asymmetric: Boolean, top: Int) {
            for (y in 0 until 16) for (x in 0 until 16) {
                val v = if (asymmetric) {
                    pixel(base + (y / 8) * 2 + x / 8, x % 8, y % 8)
                } else {
                    val slot = base + (y / 8) * 2
                    pixel(slot, if (x < 8) x else 15 - x, y % 8)
                }
                if (v != 0) img.argb[(top + y) * 16 + x] = t.iconColors[v - 1]
            }
        }
        val types = off(t.monPartyData.first, t.monPartyData.second)
        return (1..t.dexCount).associateWith { dex ->
            val b = rom[types + (dex - 1) / 2].toInt() and 0xFF
            val type = if ((dex - 1) % 2 == 0) b shr 4 else b and 0xF
            RomArt.Image(16, 32).also { img ->
                frame(img, type shl 2, type == ICON_HELIX, 0)
                frame(img, ICON_OFFSET + (type shl 2), type == ICON_HELIX, 16)
            }
        }
    }

    /** The town map screen (160x144): LoadTownMap's RLE over WorldMapTileGraphics. */
    fun townMap(rom: ByteArray, t: Gen1ArtTables): RomArt.Image {
        val tiles = off(t.townMapTiles.first, t.townMapTiles.second)
        val img = RomArt.Image(160, 144)
        var o = off(t.townMap.first, t.townMap.second)
        var cell = 0
        while (rom[o].toInt() != 0 && cell < 20 * 18) {
            val b = rom[o].toInt() and 0xFF
            repeat(b and 0xF) {
                if (cell < 20 * 18) {
                    val cx = cell % 20
                    val cy = cell / 20
                    val tile = tiles + 16 * (b shr 4)
                    for (y in 0 until 8) for (x in 0 until 8) {
                        val lo = rom[tile + 2 * y].toInt() shr (7 - x) and 1
                        val hi = rom[tile + 2 * y + 1].toInt() shr (7 - x) and 1
                        img.argb[(cy * 8 + y) * 160 + cx * 8 + x] = t.townMapColors[lo or (hi shl 1)]
                    }
                }
                cell++
            }
            o++
        }
        return img
    }

    /** The town map's player: the sprite's first frame in the town map's own colours (0 clear), as the game draws it. */
    fun player(rom: ByteArray, t: Gen1ArtTables): RomArt.Image {
        val base = off(t.playerSprite.first, t.playerSprite.second)
        val img = RomArt.Image(16, 16)
        for (y in 0 until 16) for (x in 0 until 16) {
            val tile = base + 16 * ((y / 8) * 2 + x / 8)
            val lo = rom[tile + 2 * (y % 8)].toInt() shr (7 - x % 8) and 1
            val hi = rom[tile + 2 * (y % 8) + 1].toInt() shr (7 - x % 8) and 1
            val v = lo or (hi shl 1)
            if (v != 0) img.argb[y * 16 + x] = t.townMapColors[v]
        }
        return img
    }

    /** Writes [rom]'s art into [dir] if it's a known Gen 1 cart (once per [VERSION]); returns the paths written. */
    fun extractTo(rom: ByteArray, dir: File): List<String> {
        val sha1 = MessageDigest.getInstance("SHA-1").digest(rom).joinToString("") { "%02x".format(it) }
        val t = GAMES.firstOrNull { it.sha1 == sha1 } ?: return emptyList()
        val marker = File(dir, "gen1/scanned-v$VERSION-${sha1.take(12)}")
        if (marker.isFile) return emptyList()
        val wrote = ArrayList<String>()
        fun write(path: String, bytes: ByteArray) {
            val out = File(dir, path)
            out.parentFile?.mkdirs()
            val tmp = File(out.path + ".tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(out)
            wrote += path
        }
        write(t.fontPath, font(rom, t))
        icons(rom, t).forEach { (dex, img) -> write("${t.iconsDir}/$dex.png", img.png()) }
        write(t.townMapPath, townMap(rom, t).png())
        write(t.playerPath, player(rom, t).png())
        marker.parentFile?.mkdirs()
        marker.writeText(wrote.size.toString())
        return wrote
    }

    /** In the background, once per ROM file: a Game Boy cart is at most a few MB. */
    fun prefetch(filesDir: File, rom: File) {
        Thread({
            runCatching {
                val wrote = extractTo(rom.readBytes(), RomArt.dir(filesDir))
                if (wrote.isNotEmpty()) RomArt.announce()
            }.onFailure { android.util.Log.w("pokedaisy", "Gen1Art: failed", it) }
        }, "pokedaisy-gen1-art").apply { isDaemon = true; start() }
    }
}
