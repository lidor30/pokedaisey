package com.pokedaisey.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Map tab's taps, places and player tile on the bundled FireRed / Emerald tables (no ROM needed). */
class RegionMapModelTest {
    private val fireRed = RegionMapModel(regionMapImages, mapSecData, regionLayoutsFireRed)
    private val emerald = RegionMapModel(regionMapImagesEmerald, mapSecDataEmerald, regionLayoutsEmerald)

    @Test fun tapNamesWhatTheGameCursorWould() {
        assertEquals(MapPick(0x58, -1), fireRed.pick(0, 8, 15)) // Pallet Town
        // Mt. Moon's icon sits on Route 4: both names, like FireRed's two strips.
        assertEquals(MapPick(0x68, 0x7F), fireRed.pick(0, 13, 7))
        assertNull(fireRed.pick(0, 5, 5)) // sea
        assertEquals(MapPick(11, -1), emerald.pick(0, 13, 2)) // Fortree City
    }

    @Test fun everyGridSectionIsNamed() {
        for ((model, layouts) in listOf(fireRed to regionLayoutsFireRed, emerald to regionLayoutsEmerald)) {
            for (l in layouts) for (layer in l.layers) for (b in layer) {
                val m = b.toInt() and 0xFF
                assertTrue("mapsec $m", m == l.none || m in model.sections)
            }
        }
    }

    @Test fun dungeonsAreFoundOnTheGrid() {
        // The tables leave them off the map; the dungeon layer has them.
        assertEquals(listOf(MapTiles(13, 7, 1, 1)), fireRed.tilesOf(0x7F)) // Mt. Moon
        assertEquals(2, fireRed.tilesOf(0x83).size) // Diglett's Cave: both ends
        assertEquals(listOf(MapTiles(12, 7, 6, 1)), fireRed.tilesOf(0x68)) // Route 4's own rect
        assertEquals(emptyList<MapTiles>(), fireRed.tilesOf(0xC4)) // MAPSEC_SPECIAL_AREA
    }

    @Test fun playerTileFollowsTheGame() {
        // Route 4: 6 tiles wide; x 40 of a 90-wide map is the third (90 / 6 = 15 per tile).
        assertEquals(14 to 7, PlayerMapTile.of(fireRed, 0x68, 40, 8, 90, 20, 3))
        // Indoors in the same section: where the player was last outside.
        assertEquals(14 to 7, PlayerMapTile.of(fireRed, 0x68, 3, 3, 10, 10, 8))
        // Indoors anywhere else: the section's first tile.
        assertEquals(8 to 15, PlayerMapTile.of(fireRed, 0x58, 3, 3, 10, 10, 8))
        assertNull(PlayerMapTile.of(fireRed, 0xC4, 0, 0, 10, 10, 8))
    }

    @Test fun placesAreGrouped() {
        val groups = fireRed.places().toMap()
        val name = { id: Int -> mapSecData.getValue(id).name }
        assertEquals("Pallet Town", name(groups.getValue("TOWNS & CITIES").first()))
        assertTrue(groups.getValue("TOWNS & CITIES").map(name).containsAll(listOf("Indigo Plateau", "Seven Island")))
        val routes = groups.getValue("ROUTES").map(name)
        assertEquals(listOf("Route 1", "Route 2", "Route 3"), routes.take(3))
        assertTrue(routes.indexOf("Route 9") < routes.indexOf("Route 10"))
        // Dungeons are places too, at their grid spot.
        assertTrue("Mt Moon" in groups.getValue("OTHER PLACES").map(name))
        assertEquals("Littleroot Town", mapSecDataEmerald.getValue(emerald.places().first().second.first()).name)
    }
}
