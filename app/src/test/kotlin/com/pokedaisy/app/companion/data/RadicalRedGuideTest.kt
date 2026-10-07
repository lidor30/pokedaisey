package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Radical Red's GUIDE: its tables in the user's ROM (skipped when it isn't on
 * this machine), the seven fixed gym leaders' teams, NEXT BOSS from the real
 * save, and the WHERE IS generated from the ROM-read area data.
 */
class RadicalRedGuideTest {
    private val t = GUIDE_TABLES_RADICAL_RED

    private fun rom(): RomFileReader? = RomFileReader.load(RETAIL_ROM_DIR + "Pokemon - Radical Red (v4.1).gba")?.also {
        activeGame = GameKind.RADICAL_RED
        PokedexSource.reader = it
    }

    @Test fun `tables are where the config says`() {
        val rom = rom()
        assumeTrue("Radical Red ROM not on this machine", rom != null)
        assertTrue(guideTablesMatchRom(rom!!, t))
    }

    @Test fun `gym leaders' teams`() {
        assumeTrue(rom() != null)
        for (boss in GUIDE_RADICAL_RED.bosses) {
            val id = boss.trainers(SaveProgress(ByteArray(0x120), ByteArray(0x200))).single()
            val team = GuideRomSource.party(t, id)!!
            assertTrue(boss.title, team.size in 2..6 && team.all { it.species > 0 && it.level in 1..100 && it.moves.isNotEmpty() })
        }
    }

    /** ROUTE 1 (map 3.19): a full grass table. */
    @Test fun route1() {
        assumeTrue(rom() != null)
        val e = GuideRomSource.encounters(t, 3, 19)!!
        assertEquals(100, e.grass.sumOf { it.percent })
    }

    @Test fun `next boss from the real save`() {
        val progress = readSaveProgress(FixtureMemoryReader.load("radical_red"), NATIVE_RADICAL_RED_V4_1, t)
        assertNotNull(progress)
        val next = GUIDE_RADICAL_RED.bosses.firstOrNull { !it.isDone(progress!!) }
        assertTrue(next == null || next.title.startsWith("LEADER"))
    }

    @Test fun `generated WHERE IS`() {
        activeGame = GameKind.RADICAL_RED
        val page = generatedWhereIs(GuideId.RADICAL_RED, { "AREA $it" }) { false }!!
        val hms = page.sections.single { it.heading == "HMs" }.entries.map { it.title }
        assertTrue(hms.size >= 5)
        assertTrue(page.sections.single { it.heading == "KEY ITEMS" }.entries.size >= 10)
    }
}
