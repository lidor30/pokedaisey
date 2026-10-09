package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The DEX page's EVOLVE / AREA / MOVES data ([DexDetails]) read from the
 * player's ROMs (each test skips when its ROM isn't on this machine): every
 * game's evolution table decodes with each method named, the families come
 * out both ways, the wild index finds species on the right map sections, and
 * the learnsets read in each game's format.
 */
class DexDetailsTest {
    private fun load(cfg: NativeConfig, kind: GameKind, romFile: String): Boolean {
        val rom = RomFileReader.load(RETAIL_ROM_DIR + romFile) ?: return false
        activeGame = kind
        amethystV141 = false
        romLanguage = cfg.language
        romGameCode = cfg.gameCode
        PokedexSource.reader = rom
        return true
    }

    /** A species id by its name in the running game's own table. */
    private fun byName(name: String): Int = activeSpeciesNames.entries.first { it.value.equals(name, ignoreCase = true) }.key

    private fun links(t: PokedexTables, from: String): List<EvoLink> = DexDetails.evolutions(t)!![byName(from)].orEmpty()

    private fun reqsTo(t: PokedexTables, from: String, to: String): List<List<EvoReq>> =
        links(t, from).filter { it.to == byName(to) }.map { it.reqs }

    @Test fun `FireRed - families both ways, learnsets, TMs, wild maps`() {
        assumeTrue(load(NATIVE_FIRERED_REV1, GameKind.FIRERED, "Pokemon - FireRed Version (USA, Europe) (Rev 1).gba"))
        val t = POKEDEX_FIRERED_REV1
        val g = GUIDE_TABLES_FIRERED_REV1
        // IVYSAUR's page shows where it comes from and what it becomes.
        assertEquals(
            listOf(EvoLink(1, 2, listOf(EvoReq.Level(16))), EvoLink(2, 3, listOf(EvoReq.Level(32)))),
            DexDetails.family(t, 2),
        )
        assertEquals(listOf(listOf(EvoReq.LevelUp, EvoReq.Friendship, EvoReq.Time(EvoTime.DAY))), reqsTo(t, "EEVEE", "ESPEON"))
        assertEquals(listOf(listOf(EvoReq.Trade, EvoReq.Hold(199))), reqsTo(t, "ONIX", "STEELIX")) // METAL COAT
        assertEquals(listOf(listOf(EvoReq.Level(7), EvoReq.Random)), reqsTo(t, "WURMPLE", "SILCOON"))
        assertEquals(listOf(listOf(EvoReq.Level(20), EvoReq.SpareSlot)), reqsTo(t, "NINCADA", "SHEDINJA"))
        assertTrue(DexDetails.evolutions(t)!!.values.flatten().none { EvoReq.Special in it.reqs })
        // A family member's page: EEVEE's five, from VAPOREON's page too.
        assertEquals(5, DexDetails.family(t, byName("VAPOREON"))!!.size)
        assertEquals(emptyList<EvoLink>(), DexDetails.family(t, byName("TAUROS")))

        assertEquals(listOf(LevelMove(1, 33), LevelMove(4, 45), LevelMove(7, 73)), DexDetails.levelUp(t, g, 1)!!.take(3))
        val tms = DexDetails.teachable(t, g, 1)!!
        assertEquals(19, tms.size)
        assertEquals(TeachMove("TM06", 92), tms.first()) // TOXIC
        assertEquals(TeachMove("HM06", 249), tms.last()) // ROCK SMASH

        // PIDGEY: Route 1's grass first (MAPSEC_ROUTE_1 = 0x65), Lv 2-5, half the encounters.
        val pidgey = DexDetails.catchSpots(g, 16)!!
        assertEquals(CatchSpot(0x65, WildMethod.GRASS, 2, 5, 50, 0), pidgey.first())
        assertEquals("Route 1", lookupLocation(pidgey.first().mapsec).mapSecName)
        // MAGIKARP: every rod, surfing nowhere.
        val karp = DexDetails.catchSpots(g, 129)!!.map { it.method }.toSet()
        assertTrue(karp.containsAll(listOf(WildMethod.OLD_ROD, WildMethod.GOOD_ROD, WildMethod.SUPER_ROD)))
        assertEquals(emptyList<CatchSpot>(), DexDetails.catchSpots(g, 150)) // MEWTWO: a static encounter
    }

    @Test fun `gMapGroups by shape when the config has none`() {
        assumeTrue(load(NATIVE_FIRERED_REV1, GameKind.FIRERED, "Pokemon - FireRed Version (USA, Europe) (Rev 1).gba"))
        val g = GUIDE_TABLES_FIRERED_REV1.copy(mapGroups = 0x08000000L) // wrong, as a port copying English's would be
        assertTrue(MapSections.known(g))
        assertEquals(0x65, MapSections.mapsec(g, 3, 19)) // MAP_ROUTE1
    }

    @Test fun `Emerald - Hoenn maps and beauty`() {
        assumeTrue(load(NATIVE_EMERALD_RETAIL, GameKind.EMERALD, "Pokemon - Emerald Version (USA, Europe).gba"))
        val t = POKEDEX_EMERALD
        assertEquals(listOf(listOf(EvoReq.LevelUp, EvoReq.Beauty(170))), reqsTo(t, "FEEBAS", "MILOTIC"))
        val zigzagoon = DexDetails.catchSpots(GUIDE_TABLES_EMERALD, byName("ZIGZAGOON"))!!
        assertTrue(zigzagoon.any { lookupLocation(it.mapsec).mapSecName == "Route 101" && it.method == WildMethod.GRASS })
        assertEquals(19, DexDetails.teachable(t, GUIDE_TABLES_EMERALD, 1)!!.size)
    }

    /** The reporter's game (GitHub #28): expansion 1.15's methods + conditions, its Johto maps, its learnsets. */
    @Test fun `SoulGold - evolution conditions, maps and moves`() {
        assumeTrue(load(NATIVE_SOULGOLD, GameKind.SOULGOLD, "Pokemon-SoulGold-v1.1.4.gba"))
        val t = POKEDEX_SOULGOLD
        val g = GUIDE_TABLES_SOULGOLD
        val all = DexDetails.evolutions(t)!!
        assertTrue(all.size > 300)
        assertTrue(all.values.flatten().none { EvoReq.Special in it.reqs })
        assertEquals(listOf(listOf(EvoReq.LevelUp, EvoReq.Friendship, EvoReq.Time(EvoTime.DAY))), reqsTo(t, "Eevee", "Espeon"))
        assertEquals(listOf(listOf(EvoReq.LevelUp, EvoReq.Friendship, EvoReq.KnowsMoveType(19))), reqsTo(t, "Eevee", "Sylveon")) // FAIRY
        assertTrue(listOf(EvoReq.LevelUp, EvoReq.Time(EvoTime.NIGHT), EvoReq.Hold(493)) in reqsTo(t, "Gligar", "Gliscor"))
        assertEquals(listOf(listOf(EvoReq.UseItem(220), EvoReq.Gender(female = false))), reqsTo(t, "Kirlia", "Gallade"))
        assertEquals(listOf(listOf(EvoReq.LevelUp, EvoReq.SpeciesInParty(223))), reqsTo(t, "Mantyke", "Mantine"))
        assertEquals(listOf(listOf(EvoReq.SplitFrom(byName("Ninjask")), EvoReq.BagCount(1, 1))), reqsTo(t, "Nincada", "Shedinja"))
        assertTrue(reqsTo(t, "Magneton", "Magnezone").any { it.first() == EvoReq.LevelUp && it[1] is EvoReq.AtPlace })
        assertEquals(listOf(listOf(EvoReq.Level(30), EvoReq.Nature(1))), reqsTo(t, "Toxel", "Toxtricity").take(1))
        // Both ways: PIKACHU's page has PICHU (friendship) and RAICHU (THUNDER STONE).
        val pikachu = DexDetails.family(t, byName("Pikachu"))!!
        assertTrue(pikachu.any { it.from == byName("Pichu") } && pikachu.any { it.to == byName("Raichu") })

        // PIDGEY on Route 29 / 30 (sections are u16 here), by day's set.
        val pidgey = DexDetails.catchSpots(g, byName("Pidgey"))!!
        assertEquals(listOf("Route 29", "Route 30"), pidgey.map { lookupLocation(it.mapsec).mapSecName })

        assertEquals(listOf(LevelMove(1, 33), LevelMove(1, 45), LevelMove(3, 22)), DexDetails.levelUp(t, g, 1)!!.take(3))
        assertTrue(TeachMove("", 92) in DexDetails.teachable(t, g, 1)!!) // TOXIC, among its TMs / tutors
    }

    @Test fun `SoulGold v1_2 - the same data at its own addresses`() {
        assumeTrue(load(NATIVE_SOULGOLD_V1_2, GameKind.SOULGOLD, "Soulgold (v1.2).gba"))
        assertTrue(DexDetails.evolutions(POKEDEX_SOULGOLD_V1_2)!!.size > 300)
        assertTrue(DexDetails.catchSpots(GUIDE_TABLES_SOULGOLD_V1_2, byName("Pidgey"))!!.isNotEmpty())
    }

    @Test fun `Heart and Soul - places, times of day`() {
        assumeTrue(load(NATIVE_HEART_AND_SOUL, GameKind.HEART_AND_SOUL, "Pokémon Heart and Soul (v2.0.6).gba"))
        val t = POKEDEX_HEART_AND_SOUL
        assertTrue(DexDetails.evolutions(t)!!.values.flatten().none { EvoReq.Special in it.reqs })
        assertTrue(listOf(EvoReq.LevelUp, EvoReq.AtPlace(40)) in reqsTo(t, "MAGNETON", "MAGNEZONE"))
        // PIDGEY is out by day only on Route 29: the DAY set (bit 1).
        val route29 = DexDetails.catchSpots(GUIDE_TABLES_HEART_AND_SOUL, byName("PIDGEY"))!!.first { lookupLocation(it.mapsec).mapSecName.equals("Route 29", true) }
        assertEquals(1 shl 1, route29.times)
    }

    @Test fun `older expansion and its forks name every method`() {
        for ((cfg, kind, rom) in listOf(
            Triple(NATIVE_IMPERIUM, GameKind.IMPERIUM, "Emerald Imperium (v1.3.1).gba"),
            Triple(NATIVE_EMERALD_SEAGLASS, GameKind.EMERALD_SEAGLASS, "Pokemon Emerald Seaglass (v3.0).gba"),
            Triple(NATIVE_LAZARUS, GameKind.LAZARUS, "Pokemon Lazarus (v2.0).gba"),
            Triple(NATIVE_EMERALD_ROGUE, GameKind.EMERALD_ROGUE, "Pokemon Emerald Rogue (v2.2.1-EX).gba"),
            Triple(NATIVE_TMT2, GameKind.TMT2, "Pokemon Too Many Types 2 (v1.5.2).gba"),
            Triple(NATIVE_RADICAL_RED_V4_1, GameKind.RADICAL_RED, "Pokemon - Radical Red (v4.1).gba"),
            Triple(NATIVE_AMETHYST, GameKind.AMETHYST, "Pokemon Amethyst (v1.3.0).gba"),
            Triple(NATIVE_GAIA_V3_2, GameKind.GAIA, "Pokemon - Gaia (v3.2).gba"),
        )) {
            if (!load(cfg, kind, rom)) continue
            val all = DexDetails.evolutions(cfg.pokedex!!)
            assertNotNull(rom, all)
            assertTrue(rom, all!!.size > 150)
            assertTrue(rom, all.values.flatten().none { EvoReq.Special in it.reqs })
        }
    }

    @Test fun `Lazarus - its numbering one lower`() {
        assumeTrue(load(NATIVE_LAZARUS, GameKind.LAZARUS, "Pokemon Lazarus (v2.0).gba"))
        val t = POKEDEX_LAZARUS
        assertEquals(listOf(listOf(EvoReq.Level(16))), reqsTo(t, "Fennekin", "Braixen"))
        assertEquals(listOf(listOf(EvoReq.Steps(1000))), reqsTo(t, "Pawmo", "Pawmot"))
        assertEquals(listOf(LevelMove(1, 10), LevelMove(1, 39)), DexDetails.levelUp(t, GUIDE_TABLES_LAZARUS, byName("Fennekin"))!!.take(2))
    }

    @Test fun `Unbound - CFRU's table and learnsets, no TMs`() {
        assumeTrue(load(NATIVE_UNBOUND_WITH_DEX, GameKind.UNBOUND, "Pokémon Unbound (v2.1.1.1).gba"))
        val t = POKEDEX_UNBOUND
        assertEquals(listOf(listOf(EvoReq.LevelUp, EvoReq.KnowsMoveType(23))), reqsTo(t, "Eevee", "Sylveon"))
        assertEquals(listOf(listOf(EvoReq.Level(32), EvoReq.TypeInParty(17))), reqsTo(t, "Pancham", "Pangoro"))
        assertEquals(listOf(listOf(EvoReq.LevelUp, EvoReq.Hold(231), EvoReq.Time(EvoTime.NIGHT))), reqsTo(t, "Gligar", "Gliscor"))
        // Megas are forms, not evolutions.
        assertTrue(links(t, "Venusaur").isEmpty())
        assertEquals(listOf(LevelMove(1, 33), LevelMove(1, 45)), DexDetails.levelUp(t, GUIDE_TABLES_UNBOUND, 1)!!.take(2))
        assertNull(DexDetails.teachable(t, GUIDE_TABLES_UNBOUND, 1))
    }

    @Test fun `Quetzal and ROWE - word learnsets from their own tables`() {
        if (load(NATIVE_QUETZAL, GameKind.QUETZAL, "PokemonQuetzalEnglishAlpha9v0.gba")) {
            assertEquals(listOf(LevelMove(1, 33), LevelMove(1, 45)), DexDetails.levelUp(POKEDEX_QUETZAL, GUIDE_TABLES_QUETZAL, 1)!!.take(2))
            assertEquals(listOf(listOf(EvoReq.Level(7), EvoReq.Gender(female = true))), reqsTo(POKEDEX_QUETZAL, "Wurmple", "Silcoon"))
        }
        if (load(NATIVE_ROWE, GameKind.ROWE, "Pokémon R.O.W.E. (v2.1.9.1 Experimental).gba")) {
            assertEquals(LevelMove(1, 33), DexDetails.levelUp(POKEDEX_ROWE, null, 1)!!.first())
            // Its unnamed level-up twin of each stone evolution gives way to the stone.
            assertEquals(listOf(listOf(EvoReq.UseItem(102))), reqsTo(POKEDEX_ROWE, "Nidorina", "Nidoqueen"))
        }
    }
}
