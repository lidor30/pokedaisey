package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * [RomRegionMap] reads a FireRed-engine hack's own region map from its ROM.
 * The hack tests skip without the ROMs on this machine. Highlights were
 * checked by eye against each map (every town's box on its town).
 */
class RomRegionMapTest {
    private fun read(name: String): RomRegionMap.Parsed {
        val f = File(ROMS, name)
        assumeTrue("no ROM at $f", f.isFile)
        return RomRegionMap.read(RomRegionMap.BytesRom(f.readBytes()))!!
    }

    @Test fun unboundHasBorrius() {
        val m = read("Pokémon Unbound (v2.1.1.1).gba")
        assertEquals(1, m.images.size) // every region slot is Borrius
        // Its 2-tile backdrop border and the missing last row are cut off, so
        // the grid's (4, 4) offset is (2, 2) here.
        assertEquals(26 * 8 to 17 * 8, m.images[0].width to m.images[0].height)
        assertEquals(MapSecInfo("Bellin Town", 0, 12, 4, 1, 1), m.sections[89])
        // Its cursor grid, from the ROM too: a tap on Bellin Town names it.
        val model = RegionMapModel(arrayOf("borrius"), m.sections, m.layouts)
        assertEquals(89, model.pick(0, 12, 4)?.mapsec)
        assertEquals(MapSecInfo("Dehara City", 0, 10, 7, 1, 3), m.sections[98])
        // Past the Sevii start FireRed had, still on Borrius.
        assertEquals(MapSecInfo("Magnolia Town", 0, 20, 4, 1, 1), m.sections[145])
        // Kept from FireRed in capitals; shown in the hack's own case.
        assertEquals("Indigo Plateau", m.sections[97]!!.name)
        // Not on the map: the map, nothing highlighted.
        assertEquals(MapSecInfo("KBT Expressway", 0, 0, 0, 0, 0), m.sections[134])
    }

    @Test fun odysseyHasItsOwnRegion() {
        val m = read("Pokémon Odyssey (English) (v4.1.1).gba")
        // Its own map plus FireRed's three Sevii screens, which it never shows.
        assertEquals(4, m.images.size)
        assertEquals(MapSecInfo("Fibernia", 0, 6, 13, 1, 1), m.sections[89])
        assertEquals(MapSecInfo("Charon", 0, 18, 10, 1, 1), m.sections[144])
    }

    @Test fun amethystKeepsSeviiScreens() {
        val m = read("Pokemon Amethyst (v1.3.0).gba")
        assertEquals(0, m.sections[0x98]!!.region)
        assertEquals(1, m.sections[0x99]!!.region)
    }

    @Test fun amethystV141KeepsSeviiScreens() {
        val m = read("Pokemon Amethyst (v1.4.1).gba")
        assertEquals(0, m.sections[0x98]!!.region)
        assertEquals(1, m.sections[0x99]!!.region)
    }

    /** Orange Islands: its archipelago over FireRed's four screens (VALENCIA on the second, PUMMELO the third). */
    @Test fun orangeIslandsHasItsArchipelago() {
        val m = read("Pokemon Orange Islands.gba")
        assertEquals(4, m.images.size)
        assertEquals(MapSecInfo("Valencia Island", 1, 6, 13, 1, 1), m.sections[0x65])
        assertEquals(MapSecInfo("Mikan Island", 1, 3, 9, 1, 1), m.sections[91])
        assertEquals(2, m.sections[147]!!.region) // PUMMELO ISLAND
    }

    /** Unbound's French translation: English's map, French names (its revision byte is 0x9E, not 0). */
    @Test fun unboundFrenchHasBorriusInFrench() {
        val m = read("Pokémon Unbound v2.1.1.1 FR.gba")
        assertEquals(1, m.images.size)
        assertEquals(MapSecInfo("Bélenbourg", 0, 12, 4, 1, 1), m.sections[89])
    }

    @Test fun gaiaIsOneMap() {
        val m = read("Pokemon - Gaia (v3.2).gba")
        assertEquals(setOf(0), m.sections.values.map { it.region }.toSet())
    }

    @Test fun otherRomsGiveNothing() {
        assertNull(RomRegionMap.read(RomRegionMap.BytesRom(ByteArray(1 shl 20))))
        // FireRed rev 1 has its code elsewhere (and its own tables anyway).
        val rev1 = File(RomFileReader.FIRERED_REV1_PATH)
        assumeTrue(rev1.isFile)
        assertNull(RomRegionMap.read(RomRegionMap.BytesRom(rev1.readBytes())))
    }

    private companion object {
        val ROMS = File(RomFileReader.FIRERED_REV1_PATH).parentFile
    }
}
