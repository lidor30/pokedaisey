package com.pokedaisey.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** [RegionMapWatch] finds each ROM's map pointers; skips without the ROMs on this machine. */
class RegionMapWatchTest {
    private fun find(path: String): List<RegionMapWatch.Watch> {
        val f = File(path)
        assumeTrue("no ROM at $f", f.isFile)
        return RegionMapWatch.find(f.inputStream().use { it.readNBytes(4 shl 20) })
    }

    private fun rom(name: String) = File(File(RomFileReader.FIRERED_REV1_PATH).parentFile, name).path

    @Test fun fireRedFamilyWatchesSRegionMap() {
        for (name in listOf(
            "Pokemon - FireRed Version (USA, Europe) (Rev 1).gba", "firered-qol.gba",
            "Pokemon - LeafGreen Version (USA, Europe) (Rev 1).gba",
            "Pokémon Unbound (v2.1.1.1).gba", "Pokémon Odyssey (English) (v4.1.1).gba",
            "Pokemon - Gaia (v3.2).gba", "Pokemon - Radical Red (v4.1).gba", "Pokemon Amethyst (v1.3.0).gba",
        )) {
            assertEquals(name, listOf(RegionMapWatch.Watch(0x020399D4L)), find(rom(name)))
        }
    }

    @Test fun emeraldWatchesTownFlyAndPokenavMaps() {
        for (path in listOf(RomFileReader.EMERALD_PATH, rom("emerald-qol.gba"))) {
            assertEquals(
                path,
                listOf(
                    RegionMapWatch.Watch(0x0203BCD0L), RegionMapWatch.Watch(0x0203A148L),
                    // gPokenavResources->substructPtrs[POKENAV_SUBSTRUCT_REGION_MAP_STATE]
                    RegionMapWatch.Watch(0x0203CF40L, 0x1C),
                ),
                find(path),
            )
        }
    }

    @Test fun emeraldHacksAreLeftAlone() {
        assertEquals(emptyList<RegionMapWatch.Watch>(), find(rom("Pokemon Emerald Seaglass (v3.0).gba")))
        assertEquals(emptyList<RegionMapWatch.Watch>(), find(rom("Pokémon Heart and Soul (v2.0.6).gba")))
    }
}
