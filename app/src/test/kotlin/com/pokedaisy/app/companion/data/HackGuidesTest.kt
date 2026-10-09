package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The GUIDEs of Odyssey, Gaia, Amethyst and Celia's hack: each game's tables in
 * the user's ROM (skipped when it isn't on this machine), every boss team the
 * NEXT BOSS page lists, a wild table, NEXT BOSS from the real save and the
 * generated WHERE IS.
 */
class HackGuidesTest {
    private class Game(val rom: String, val kind: GameKind, val cfg: NativeConfig, val fixture: String) {
        val t get() = cfg.guideTables!!
    }

    private val odyssey = Game("Pokémon Odyssey (English) (v4.1.1).gba", GameKind.ODYSSEY, NATIVE_ODYSSEY, "odyssey")
    private val gaia = Game("Pokemon - Gaia (v3.2).gba", GameKind.GAIA, NATIVE_GAIA_V3_2, "gaia")
    private val amethyst = Game("Pokemon Amethyst (v1.3.0).gba", GameKind.AMETHYST, NATIVE_AMETHYST, "amethyst")
    private val amethyst141 = Game("Pokemon Amethyst (v1.4.1).gba", GameKind.AMETHYST, NATIVE_AMETHYST_V1_4_1, "amethyst_v141")
    private val celia = Game("Pokemon Celia's Stupid Romhack (v1.1.4).gba", GameKind.CELIA, NATIVE_CELIA, "celia")
    private val all = listOf(odyssey, gaia, amethyst, amethyst141, celia)

    private fun load(g: Game): RomFileReader? = RomFileReader.load(RETAIL_ROM_DIR + g.rom)?.also {
        select(g)
        PokedexSource.reader = it
    }

    private fun select(g: Game) {
        activeGame = g.kind
        amethystV141 = g.cfg === NATIVE_AMETHYST_V1_4_1
    }

    private val noProgress = SaveProgress(ByteArray(0x120), ByteArray(0x200))

    @Test fun `tables are where the configs say`() = all.forEach { g ->
        val rom = load(g) ?: return@forEach
        assertTrue(g.kind.name, guideTablesMatchRom(rom, g.t))
    }

    /** Every listed team: 1-6 real Pokémon with levels and moves. */
    @Test fun `boss teams`() = all.forEach { g ->
        load(g) ?: return@forEach
        val guide = gameGuide(g.t.guide)!!
        for (boss in guide.bosses) for ((label, id) in boss.teams(noProgress)) {
            val team = GuideRomSource.party(g.t, id)
            val what = "${g.kind} ${boss.title} $label ($id)"
            assertTrue(what, team != null && team.size in 1..6)
            assertTrue(what, team!!.all { it.species > 0 && it.level in 1..100 && it.moves.isNotEmpty() })
        }
    }

    /** Odyssey's KARIN: 4 Pokémon on NORMAL (Lv16-17), 5 on HARD (Lv18). */
    @Test fun `odyssey normal and hard`() {
        assumeTrue(load(odyssey) != null)
        assertEquals(listOf(16, 16, 17, 17), GuideRomSource.party(odyssey.t, 33)!!.map { it.level })
        assertEquals(listOf(18, 18, 18, 18, 18), GuideRomSource.party(odyssey.t, 34)!!.map { it.level })
    }

    /** Amethyst's TERRENCE: one more Pokémon on HARD, a different second one on DIVERGENT. */
    @Test fun `amethyst tables`() {
        assumeTrue(load(amethyst) != null)
        val terrence = GUIDE_AMETHYST.bosses.first().teams(noProgress).map { GuideRomSource.party(amethyst.t, it.second)!! }
        assertEquals(listOf(1102 to 11, 780 to 13), terrence[0].map { it.species to it.level })
        assertEquals(3, terrence[1].size)
        assertEquals(638, terrence[2][1].species)
        assertEquals(3, terrence[3].size)
    }

    /** v1.4.1 moved the four tables; TERRENCE's teams are the same. */
    @Test fun `amethyst v1_4_1 tables`() {
        assumeTrue(load(amethyst141) != null)
        val terrence = GUIDE_AMETHYST.bosses.first().teams(noProgress).map { GuideRomSource.party(amethyst141.t, it.second)!! }
        assertEquals(listOf(1102 to 11, 780 to 13), terrence[0].map { it.species to it.level })
        assertEquals(3, terrence[1].size)
        assertEquals(638, terrence[2][1].species)
        assertEquals(3, terrence[3].size)
        try {
            assertEquals("Tepig", speciesName(551))
            assertEquals("Growlithe", speciesName(1234)) // a Hisuian form, where v1.3.0 had Gigantamax VENUSAUR
            assertEquals("Venusaur", speciesName(1260))
        } finally {
            amethystV141 = false
        }
    }

    /** RAINE's and CHANCE's teams follow the badges held. */
    @Test fun `amethyst middle gyms by badges`() {
        fun save(badges: Int) = SaveProgress(ByteArray(0x120).also { b -> repeat(badges) { b[(0x820 + it) / 8] = (b[(0x820 + it) / 8].toInt() or (1 shl ((0x820 + it) % 8))).toByte() } }, ByteArray(0x200))
        val raine = GUIDE_AMETHYST.bosses.single { it.title == "LEADER RAINE" }
        val chance = GUIDE_AMETHYST.bosses.single { it.title == "LEADER CHANCE" }
        assertEquals(listOf(105, 256, 257), listOf(2, 3, 4).map { raine.trainer(save(it)) })
        assertEquals(listOf(154, 154, 258), listOf(2, 3, 4).map { chance.trainer(save(it)) })
        assertEquals(257 or (1 shl TRAINER_TABLE_SHIFT), raine.teams(save(4))[1].second)
    }

    /** A wild table that HERE would show: the first map with grass encounters. */
    @Test fun `wild encounters`() = all.forEach { g ->
        load(g) ?: return@forEach
        val e = (0 until 60).asSequence().flatMap { n -> (0 until 4).asSequence().map { it to n } }
            .mapNotNull { (grp, n) -> GuideRomSource.encounters(g.t, grp, n)?.takeIf { it.grass.isNotEmpty() } }.firstOrNull()
        assertNotNull(g.kind.name, e)
        assertEquals(g.kind.name, 100, e!!.grass.sumOf { it.percent })
    }

    @Test fun `next boss from the real saves`() = all.forEach { g ->
        val progress = readSaveProgress(FixtureMemoryReader.load(g.fixture), g.cfg, g.t)
        assertNotNull(g.kind.name, progress)
        val next = gameGuide(guideId(g.kind, g.t))!!.bosses.firstOrNull { !it.isDone(progress!!) }
        assertNotNull(g.kind.name, next)
    }

    /** Glazed: no area data yet, so its own checks - the tables, Tunod's leaders, the save's map. */
    private val glazed = Game("Glazed (9.2.0).gba", GameKind.GLAZED, NATIVE_GLAZED, "glazed")

    @Test fun `glazed tables, leaders and HERE`() {
        val rom = load(glazed) ?: return
        assertTrue(guideTablesMatchRom(rom, glazed.t))
        for (boss in GUIDE_GLAZED.bosses) {
            val team = GuideRomSource.party(glazed.t, boss.trainer(noProgress))!!
            assertTrue(boss.title, team.size in 1..6 && team.all { it.species in 1..411 && it.level in 1..100 })
        }
        // Forest Pass (0.17), where the save stands: SENTRET is in its grass.
        val here = GuideRomSource.encounters(glazed.t, 0, 17)!!
        assertEquals(100, here.grass.sumOf { it.percent })
        assertTrue(here.grass.any { speciesNamesGlazed[it.species] == "SENTRET" })
        val progress = readSaveProgress(FixtureMemoryReader.load(glazed.fixture), glazed.cfg, glazed.t)!!
        assertEquals("LEADER SPARKY", GUIDE_GLAZED.bosses.first { !it.isDone(progress) }.title)
    }

    private val imperium = Game("Emerald Imperium (v1.3.1).gba", GameKind.IMPERIUM, NATIVE_IMPERIUM, "imperium")
    private val quetzal = Game("PokemonQuetzalEnglishAlpha9v0.gba", GameKind.QUETZAL, NATIVE_QUETZAL, "quetzal")

    /** Every boss team of Imperium's and Quetzal's guides (Quetzal's NORMAL and HARD, three regions' tables). */
    @Test fun `imperium and quetzal tables and teams`() = listOf(imperium, quetzal).forEach { g ->
        val rom = load(g) ?: return@forEach
        assertTrue(g.kind.name, guideTablesMatchRom(rom, g.t))
        for (boss in gameGuide(g.t.guide)!!.bosses) for ((label, id) in boss.teams(noProgress)) {
            val team = GuideRomSource.party(g.t, id)
            val what = "${g.kind} ${boss.title} $label ($id)"
            assertTrue(what, team != null && team.size in 1..6)
            assertTrue(what, team!!.all { it.species > 0 && it.level in 1..100 && it.moves.isNotEmpty() })
        }
    }

    /** ROXANNE's team; HERE on each save's map (Imperium: Route 101 has the Shinx a walk met; Quetzal: Kanto Route 1). */
    @Test fun `imperium and quetzal HERE and NEXT BOSS`() {
        load(imperium)?.let {
            assertEquals(listOf(15, 15, 15, 14).sorted(), GuideRomSource.party(imperium.t, 265)!!.map { m -> m.level }.sorted())
            val here = GuideRomSource.encounters(imperium.t, 0, 16)!!
            assertTrue(here.grass.any { e -> speciesNamesImperium[e.species] == "Shinx" })
            val p = readSaveProgress(FixtureMemoryReader.load("imperium"), NATIVE_IMPERIUM, imperium.t)!!
            assertEquals("LEADER ROXANNE", GUIDE_IMPERIUM.bosses.first { b -> !b.isDone(p) }.title)
        }
        load(quetzal)?.let {
            val here = GuideRomSource.encounters(quetzal.t, 0x25, 0x4C)!!
            assertTrue(listOf("Pidgey", "Rattata", "Fletchling", "Wooloo", "Lechonk").all { n -> here.grass.any { e -> speciesNamesQuetzal[e.species] == n } })
            val p = readSaveProgress(FixtureMemoryReader.load("quetzal"), NATIVE_QUETZAL, quetzal.t)!!
            // On Kanto's Route 1 (map group 0x25): Kanto's campaign first.
            assertEquals("LEADER BROCK", GUIDE_QUETZAL.bossesFor!!(0x25).first { b -> !b.isDone(p) }.title)
        }
    }

    private val lazarus = Game("Pokemon Lazarus (v2.0).gba", GameKind.LAZARUS, NATIVE_LAZARUS, "lazarus")
    private val seaglass = Game("Pokemon Emerald Seaglass (v3.0).gba", GameKind.EMERALD_SEAGLASS, NATIVE_EMERALD_SEAGLASS, "emerald_seaglass")
    private val tmt2 = Game("Pokemon Too Many Types 2 (v1.5.2).gba", GameKind.TMT2, NATIVE_TMT2, "tmt2")
    private val soulgold = Game("Pokemon-SoulGold-v1.1.4.gba", GameKind.SOULGOLD, NATIVE_SOULGOLD, "soulgold")
    private val soulgold12 = Game("Soulgold (v1.2).gba", GameKind.SOULGOLD, NATIVE_SOULGOLD_V1_2, "soulgold_v12")
    private val soulgold12b = Game("Pokemon-SoulGold-v1.2.gba", GameKind.SOULGOLD, NATIVE_SOULGOLD_V1_2B, "soulgold_v12b")
    private val expansion = listOf(lazarus, seaglass, tmt2, soulgold, soulgold12, soulgold12b)

    /** The expansion hacks' tables and every team (SoulGold's Kanto BROCK has mons with no moves listed: the game fills them). */
    @Test fun `expansion hacks tables and teams`() = expansion.forEach { g ->
        val rom = load(g) ?: return@forEach
        assertTrue(g.fixture, guideTablesMatchRom(rom, g.t))
        for (boss in gameGuide(g.t.guide)!!.bosses) for ((label, id) in boss.teams(noProgress)) {
            val team = GuideRomSource.party(g.t, id)
            val what = "${g.fixture} ${boss.title} $label ($id)"
            assertTrue(what, team != null && team.size in 1..6)
            assertTrue(what, team!!.all { it.species > 0 && it.level in 1..100 })
        }
        val progress = readSaveProgress(FixtureMemoryReader.load(g.fixture), g.cfg, g.t)
        assertNotNull(g.fixture, gameGuide(g.t.guide)!!.bosses.firstOrNull { !it.isDone(progress!!) })
    }

    /** HERE on a map each was walked on headless until a wild battle came (that foe is in the table). */
    @Test fun `expansion hacks HERE`() = listOf(lazarus to (0 to 79), seaglass to (0 to 16), tmt2 to (0 to 32), soulgold to (0 to 16)).forEach { (g, map) ->
        load(g) ?: return@forEach
        val here = GuideRomSource.encounters(g.t, map.first, map.second)
        assertNotNull(g.fixture, here)
        // TMT2's tables have 11 land slots: the game's last 1% reads past them.
        assertEquals(g.fixture, if (g === tmt2) 99 else 100, here!!.grass.sumOf { it.percent })
    }

    @Test fun `generated WHERE IS`() = all.forEach { g ->
        select(g)
        val page = generatedWhereIs(g.t.guide, { "AREA $it" }) { false }
        assertNotNull(g.kind.name, page)
        assertTrue(g.kind.name, page!!.sections.any { it.heading == "KEY ITEMS" && it.entries.size >= 5 })
    }
}
