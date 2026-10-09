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
