package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.CRC32

/**
 * [RomArt] rebuilds the FireRed / Emerald art from the ROM. The ROM tests
 * skip without the retail ROMs on this machine (see [RomFileReader]).
 */
class RomArtTest {
    private fun pixels(img: RomArt.Image): String {
        val crc = CRC32()
        img.argb.forEach { crc.update(byteArrayOf((it ushr 24).toByte(), (it ushr 16).toByte(), (it ushr 8).toByte(), it.toByte())) }
        return "%08x".format(crc.value)
    }

    private fun composed(path: String): Map<String, RomArt.Image> {
        val rom = File(path).takeIf { it.isFile }?.readBytes()
        assumeTrue("no ROM at $path", rom != null)
        return RomArt.compose(RomArt.find(rom!!))
    }

    private fun assertGolden(images: Map<String, RomArt.Image>, golden: Map<String, String>) {
        assertEquals(golden.keys, images.keys)
        golden.forEach { (path, crc) -> assertEquals(path, crc, pixels(images.getValue(path))) }
    }

    @Test fun fireRedSuppliesItsArt() = assertGolden(composed(RomFileReader.FIRERED_REV1_PATH), FIRERED)

    @Test fun emeraldSuppliesItsArt() = assertGolden(composed(RomFileReader.EMERALD_PATH), EMERALD)

    /** Glazed wrote its own region map (Tunod + Johto) over retail Emerald's blobs; the rest of its art is retail's. */
    @Test fun glazedSuppliesItsRegionMap() {
        val images = composed(System.getProperty("user.home") + "/Downloads/Game ROMs & Emulation/gba/Glazed (9.2.0).gba")
        assertEquals("38897c17", pixels(images.getValue("regionmap/glazed.png")))
        assertTrue("regionmap/hoenn.png" !in images)
    }

    /** Imperium's region map: Emerald's with markers for its new places, on Emerald's palette. */
    @Test fun imperiumSuppliesItsRegionMap() {
        val images = composed(System.getProperty("user.home") + "/Downloads/Game ROMs & Emulation/gba/Emerald Imperium (v1.3.1).gba")
        assertEquals("a9c4cfb6", pixels(images.getValue("regionmap/imperium.png")))
    }

    /** Quetzal's Kanto / Sevii maps: FireRed's art redrawn for Emerald's region_map.c (QTZ_*). */
    @Test fun quetzalSuppliesItsRegionMaps() {
        val images = composed(System.getProperty("user.home") + "/Downloads/Game ROMs & Emulation/gba/PokemonQuetzalEnglishAlpha9v0.gba")
        assertEquals(
            listOf("eee6e9af", "307eba25", "f356dacb", "508b250a"),
            listOf("kanto", "sevii123", "sevii45", "sevii67").map { pixels(images.getValue("regionmap/quetzal_$it.png")) },
        )
    }

    /** Heart and Soul's Johto + Kanto Pokegear map (smol, HNS_REGION_*), on Emerald's palette. */
    @Test fun heartAndSoulSuppliesItsRegionMap() {
        val images = composed(System.getProperty("user.home") + "/Downloads/Game ROMs & Emulation/gba/Pokémon Heart and Soul (v2.0.6).gba")
        assertEquals("54343313", pixels(images.getValue("regionmap/hns.png")))
    }

    /**
     * Heart and Soul's party menu (smol HNS_PARTY_* / _BALL_* / _STATUS_* / _FONT_SMALL, Emerald's slot
     * tilemaps). Its slot frames come out pixel for pixel Emerald's; all of it matched the PNGs the app
     * bundled before.
     */
    @Test fun heartAndSoulSuppliesItsPartyArt() {
        val images = composed(GBA + "Pokémon Heart and Soul (v2.0.6).gba")
        assertGolden(images.filterKeys { it.startsWith("partyhns/") || it == "partybg/hns.png" }, HEART_AND_SOUL)
    }

    /**
     * The CFRU hacks' shared party menu (CFRU_*: 112x40 slots + the grid backdrop's cell) and each
     * one's status icons; Unbound's Poke Ball is FireRed's (partyfr/pokeball.png). All of it matched
     * the PNGs the app bundled before. Some ROMs also carry another hack's status sheet (the same
     * bytes, so the same image).
     */
    @Test fun cfruHacksSupplyTheirPartyArt() {
        val roms = mapOf(
            "Pokémon Unbound (v2.1.1.1).gba" to "ub", "Pokemon - Radical Red (v4.1).gba" to "rr",
            "Pokémon Odyssey (English) (v4.1.1).gba" to "od", "Pokemon Amethyst (v1.3.0).gba" to "am",
            "Pokemon Amethyst (v1.4.1).gba" to "am",
        ).filterKeys { File(GBA + it).isFile }
        assumeTrue("no CFRU ROM here", roms.isNotEmpty())
        for ((rom, sub) in roms) {
            val images = composed(GBA + rom)
            val status = "partycfru/$sub/status_icons.png"
            assertGolden(images.filterKeys { (it.startsWith("partycfru/slot_") || it == RomArt.CFRU_BACKDROP) || it == status },
                CFRU + (status to CFRU_STATUS.getValue(sub)))
            images.filterKeys { it.startsWith("partycfru/") && it.endsWith("/status_icons.png") }.forEach { (path, img) ->
                assertEquals("$rom $path", CFRU_STATUS.getValue(path.split('/')[1]), pixels(img))
            }
        }
        if ("Pokémon Unbound (v2.1.1.1).gba" in roms) {
            assertEquals("53f5f1ea", pixels(composed(GBA + "Pokémon Unbound (v2.1.1.1).gba").getValue("partyfr/pokeball.png")))
        }
    }

    @Test fun romWithoutTheArtGivesNothing() {
        val rom = ByteArray(1 shl 20) { (it * 31 + (it ushr 7)).toByte() }
        assertTrue(RomArt.compose(RomArt.find(rom)).isEmpty())
    }

    @Test fun cachedPngsAreWrittenOnce() {
        val dir = File("build/rom-art-test").apply { deleteRecursively() }
        val rom = File(RomFileReader.FIRERED_REV1_PATH).takeIf { it.isFile }?.readBytes()
        assumeTrue(rom != null)
        val first = RomArt.extractTo(rom!!, dir)
        val raw = RomArt.raw(RomArt.find(rom))
        assertEquals(FIRERED.keys + raw.keys, first.toSet())
        assertTrue(first.filter { it.endsWith(".png") }.all { File(dir, it).readBytes().copyOfRange(1, 4).contentEquals("PNG".toByteArray()) })
        // The trainer card's blobs are kept as they decode.
        raw.forEach { (path, bytes) -> assertTrue(path, File(dir, path).readBytes().contentEquals(bytes)) }
        assertEquals(emptyList<String>(), RomArt.extractTo(rom, dir))
    }

    private companion object {
        val GBA = System.getProperty("user.home") + "/Downloads/Game ROMs & Emulation/gba/"
        val HEART_AND_SOUL = mapOf(
            "partybg/hns.png" to "aeda6295",
            "partyhns/font_small.png" to "10b2cfee",
            "partyhns/pokeball.png" to "53f5f1ea", // FireRed's / Emerald's ball
            // Emerald's slot frames, pixel for pixel.
            "partyhns/slot_fainted.png" to "988df89d",
            "partyhns/slot_nohp_normal.png" to "0402fa82",
            "partyhns/slot_nohp_selected.png" to "ed6e77fa",
            "partyhns/slot_normal.png" to "b868b751",
            "partyhns/slot_selected.png" to "06077c6c",
            "partyhns/slot_selected_fainted.png" to "b0393f12",
            "partyhns/status_icons.png" to "b27d09eb",
        )
        val CFRU = mapOf(
            "partybg/cfru_tile.png" to "734c1189",
            "partycfru/slot_empty.png" to "c2ed34d7",
            "partycfru/slot_fainted.png" to "2dcf7519",
            "partycfru/slot_nohp_normal.png" to "99193b63",
            "partycfru/slot_nohp_selected.png" to "6234b842",
            "partycfru/slot_normal.png" to "e9603ce2",
            "partycfru/slot_selected.png" to "ec246dad",
            "partycfru/slot_selected_fainted.png" to "f83c49af",
        )
        val CFRU_STATUS = mapOf("ub" to "2e442add", "rr" to "dddaa119", "od" to "a20e9bf7", "am" to "bc0660eb")
        // CRC32 of each image's ARGB pixels; the images matched the PNGs the app
        // used to bundle (colour rounding aside) when these were recorded.
        val FIRERED = mapOf(
            "partybg/firered.png" to "e9fcbfdc",
            "partyem/pokeball.png" to "53f5f1ea", // the same ball
            "partyfr/font_small.png" to "74bc5054",
            "partyfr/pokeball.png" to "53f5f1ea",
            "partyfr/slot_fainted.png" to "988df89d",
            "partyfr/slot_nohp_normal.png" to "0402fa82",
            "partyfr/slot_nohp_selected.png" to "ed6e77fa",
            "partyfr/slot_normal.png" to "b868b751",
            "partyfr/slot_selected.png" to "06077c6c",
            "partyfr/slot_selected_fainted.png" to "b0393f12",
            "partyfr/status_icons.png" to "bbd9145e",
            "regionmap/kanto.png" to "2ff27d4d",
            "regionmap/player_leaf.png" to "6d6f7465",
            "regionmap/player_red.png" to "547baad2",
            "regionmap/sevii123.png" to "d5d0d9cd",
            "regionmap/sevii45.png" to "05ec1562",
            "regionmap/sevii67.png" to "f94ac4e7",
        )
        val EMERALD = mapOf(
            "partybg/emerald.png" to "4eb5fbd3",
            "partyem/font_small.png" to "f8fb2855",
            "partyem/pokeball.png" to "53f5f1ea",
            "partyem/slot_fainted.png" to "988df89d",
            "partyem/slot_nohp_normal.png" to "0402fa82",
            "partyem/slot_nohp_selected.png" to "ed6e77fa",
            "partyem/slot_normal.png" to "b868b751",
            "partyem/slot_selected.png" to "06077c6c",
            "partyem/slot_selected_fainted.png" to "b0393f12",
            "partyem/status_icons.png" to "5c0fddd3",
            "partyfr/pokeball.png" to "53f5f1ea", // the same ball
            "regionmap/hoenn.png" to "27d88e99",
            "regionmap/player_brendan.png" to "ffb2b151",
            "regionmap/player_may.png" to "e7e272c8",
        )
    }
}
