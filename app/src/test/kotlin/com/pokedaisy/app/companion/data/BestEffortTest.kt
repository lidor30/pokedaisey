package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * BEST EFFORT over the real saves: each game, treated as an unknown ROM of its base game, finds
 * its own config - every part holding (a FULL match: a re-hashed build of a supported version
 * loses its "not supported"); a game with no party yet finds nothing.
 */
class BestEffortTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun reader(rom: String, fixture: String): MemoryReader? =
        RomFileReader.load(RETAIL_ROM_DIR + rom)?.withRam(FixtureMemoryReader.load(fixture))

    private fun expect(rom: String, fixture: String, code: String, id: String) {
        val r = reader(rom, fixture)
        assumeTrue("$rom not on this machine", r != null)
        val (m, miss) = BestEffort.match(r!!, code)
        assertNull("$fixture: $miss", miss)
        assertEquals(fixture, id, m!!.candidate.id)
        assertTrue("$fixture off ${m.off}", m.full)
    }

    @Test fun fireRed() = expect("Pokemon - FireRed Version (USA, Europe) (Rev 1).gba", "firered_vanilla", "BPRE", "FIRERED_REV1")
    @Test fun emerald() = expect("Pokemon - Emerald Version (USA, Europe).gba", "emerald_vanilla", "BPEE", "EMERALD")
    @Test fun unbound() = expect("Pokémon Unbound (v2.1.1.1).gba", "unbound", "BPRE", "UNBOUND")
    @Test fun orangeIslands() = expect("Pokemon Orange Islands.gba", "orange_islands", "BPRE", "ORANGE_ISLANDS")
    @Test fun soulGoldV12() = expect("Soulgold (v1.2).gba", "soulgold_v12", "BPEE", "SOULGOLD_V1_2")
    @Test fun heartAndSoul() = expect("Pokémon Heart and Soul (v2.0.6).gba", "heart_and_soul", "BPEE", "HEART_AND_SOUL")
    @Test fun quetzal() = expect("PokemonQuetzalEnglishAlpha9v0.gba", "quetzal", "BPEE", "QUETZAL")

    /** Orange Islands as it was before it had a config: retail FireRed's RAM reads, its tables don't all hold. */
    @Test fun orangeIslandsWithoutItsConfig() {
        val r = reader("Pokemon Orange Islands.gba", "orange_islands") ?: return
        val (m, _) = BestEffort.match(r, "BPRE")
        assumeTrue(m != null)
        // Its own config wins when there; the retail ones still read the party.
        val fr = BestEffort.candidates.first { it.id == "FIRERED_REV0" }
        assertTrue(BestEffort.candidates.contains(fr))
    }

    @Test fun noPartyNoMatch() {
        val rom = RomFileReader.load(RETAIL_ROM_DIR + "Pokemon - FireRed Version (USA, Europe) (Rev 1).gba") ?: return
        val empty = object : MemoryReader {
            override fun readCoreMemory(address: Long, length: Int): ByteArray =
                if (address >= 0x08000000L) rom.readCoreMemory(address, length) else ByteArray(length)
        }
        val (m, miss) = BestEffort.match(empty, "BPRE")
        assertNull(m)
        assertEquals(BestEffort.Miss.NO_PARTY, miss)
    }

    @Test fun storeKeepsAMatch() {
        BestEffortStore.dir = tmp.root
        try {
            val c = BestEffort.candidate("SOULGOLD_V1_2")!!
            BestEffortStore.save("ABC", BestEffort.Match(c, full = false, off = setOf(BestEffort.Part.GUIDE), score = 1))
            val e = BestEffortStore.load("abc")!!
            assertEquals("SOULGOLD_V1_2", e.id)
            assertFalse(e.full)
            assertEquals(setOf(BestEffort.Part.GUIDE), e.off)
            val m = BestEffortStore.matchOf(e)!!
            assertNull(m.config.guideTables)
            BestEffortStore.forget("abc")
            assertNull(BestEffortStore.load("abc"))
        } finally {
            BestEffortStore.dir = null
        }
    }
}
