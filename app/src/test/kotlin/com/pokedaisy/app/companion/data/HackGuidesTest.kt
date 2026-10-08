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

    @Test fun `generated WHERE IS`() = all.forEach { g ->
        select(g)
        val page = generatedWhereIs(g.t.guide, { "AREA $it" }) { false }
        assertNotNull(g.kind.name, page)
        assertTrue(g.kind.name, page!!.sections.any { it.heading == "KEY ITEMS" && it.entries.size >= 5 })
    }
}
