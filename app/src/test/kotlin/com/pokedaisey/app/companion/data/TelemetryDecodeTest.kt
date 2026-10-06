package com.pokedaisey.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decode-logic regression tests for the gQolTelemetry STRUCT path
 * (decodeTelemetry) - the FireRed/Emerald QoL builds. Fixtures are frozen
 * real RAM snapshots; a fixture is only (re)captured after confirming the
 * ROM decodes correctly, so these tests exist to catch a REGRESSION in the
 * Kotlin decode logic, not to prove correctness from scratch each time.
 *
 * Assertions run against buildSnapshotView(t).items (List<ItemView>,
 * truncated to the real itemCount), not raw Telemetry.items - deliberately.
 * Telemetry.items is the full 186-slot array *including* trailing padding
 * past itemCount (SnapshotView.buildSnapshotView does the truncation - see
 * its own `t.itemCount.coerceAtMost(t.items.size)`), and this project's own
 * memory notes call out a real, unexplained padding anomaly in one captured
 * emerald_qol fixture (a handful of non-zero slots around index 177-183,
 * bracketed by hundreds of correctly-zeroed ones) that's never observed via
 * this truncated view since no real caller reads padding past itemCount -
 * see the "headless capture, emerald padding anomaly" memory entry before
 * asserting anything about raw Telemetry.items[] beyond itemCount again.
 *
 * If a real ROM/struct change means these values SHOULD change, recapture
 * the fixture and update the expected values here deliberately - don't just
 * loosen the assertions.
 */
class TelemetryDecodeTest {

    private val qoltMagic = "QOLT".toByteArray(Charsets.US_ASCII)

    private fun decodeItems(key: String): List<ItemView> {
        val reader = FixtureMemoryReader.load(key)
        val addr = reader.findMagic(qoltMagic)
        assertTrue("$key: no QOLT magic found in the captured fixture", addr >= 0)
        val t = decodeTelemetry(reader.readCoreMemory(addr, TELEMETRY_SIZE))
        assertEquals(6, t.party.size)
        t.party.forEach { assertTrue(it.species > 0) }
        return buildSnapshotView(t).items
    }

    private fun assertPocketsInRange(items: List<ItemView>) {
        items.forEach {
            assertTrue("pocket ${it.pocket} out of the valid 0..4 range", it.pocket in 0..4)
        }
    }

    @Test
    fun `firered_qol - party is fully populated, pockets are in range`() {
        assertPocketsInRange(decodeItems("firered_qol"))
    }

    @Test
    fun `firered_qol - party-menu gender symbols decode from the former padding byte`() {
        // Fixture recaptured 2026-09-27 on a ROM with QOL_GENDER_* - matches
        // the real game's own party screen for this save: LAPRAS female,
        // KADABRA male, JOLTEON female, CHARIZARD male, (egg), MEOWTH male.
        val reader = FixtureMemoryReader.load("firered_qol")
        val t = decodeTelemetry(reader.readCoreMemory(reader.findMagic(qoltMagic), TELEMETRY_SIZE))
        assertEquals(
            listOf(GENDER_SYMBOL_FEMALE, GENDER_SYMBOL_MALE, GENDER_SYMBOL_FEMALE, GENDER_SYMBOL_MALE),
            t.party.take(4).map { it.genderSymbol },
        )
        assertEquals(GENDER_SYMBOL_MALE, t.party[5].genderSymbol)
    }

    @Test
    fun `firered_qol - an egg exports as SPECIES_EGG and shows as an egg`() {
        // Slot 4 of this save is an egg (a Togepi inside). The ROM now exports
        // MON_DATA_SPECIES_OR_EGG like the party menu's icon, and no gender.
        activeGame = GameKind.FIRERED
        val reader = FixtureMemoryReader.load("firered_qol")
        val t = decodeTelemetry(reader.readCoreMemory(reader.findMagic(qoltMagic), TELEMETRY_SIZE))
        assertEquals(SPECIES_EGG_VANILLA, t.party[4].species)
        assertEquals(GENDER_SYMBOL_NONE, t.party[4].genderSymbol)
        val view = buildSnapshotView(t).party[4]
        assertTrue(view.isEgg)
        assertEquals("EGG", view.name) // FireRed prints it in caps (gameCase)
        assertTrue(buildSnapshotView(t).party.filterIndexed { i, _ -> i != 4 }.none { it.isEgg })
    }

    @Test
    fun `firered_qol - party EXP comes from the RAM party, checked against the struct`() {
        // Same save as above: LAPRAS 50 (Slow), KADABRA 50 / CHARIZARD 54
        // (Medium Slow), (egg), MEOWTH 18 (Medium Fast). The QoL struct has
        // no EXP; it's read from gPlayerParty's Growth substruct, and every
        // value sits between its growth rate's thresholds for that level.
        activeGame = GameKind.FIRERED
        val reader = FixtureMemoryReader.load("firered_qol")
        val t = decodeTelemetry(reader.readCoreMemory(reader.findMagic(qoltMagic), TELEMETRY_SIZE))
        val raw = reader.readCoreMemory(NATIVE_FIRERED_REV1.playerParty, 6 * MON_STRUCT_SIZE)
        val ram = (0 until 6).map { decodePartyMon(raw, it * MON_STRUCT_SIZE) }
        assertEquals(listOf(158_730L, 121_427L, 123_102L, 152_087L), ram.take(4).map { it?.exp })
        assertEquals(6_810L, ram[5]?.exp)
        // The struct and the RAM party agree slot for slot (species, level).
        listOf(0, 1, 2, 3, 5).forEach { i ->
            assertEquals(t.party[i].species, ram[i]?.species)
            assertEquals(t.party[i].level, ram[i]?.level)
        }
        val charizard = expProgress(ram[3]!!.species, ram[3]!!.level, ram[3]!!.exp)!!
        assertEquals(150_476L, charizard.levelStart) // Medium Slow, Lv54
        assertEquals(159_635L, charizard.nextLevel)  // Lv55
        assertEquals(7_548L, charizard.toNext)
    }

    @Test
    fun `emerald_qol - gender symbols decode (Emerald's port of the same byte)`() {
        // Recaptured 2026-09-27 on an Emerald QoL ROM with QOL_GENDER_*; the
        // real game's party screen for this save shows all six as male.
        val reader = FixtureMemoryReader.load("emerald_qol")
        val t = decodeTelemetry(reader.readCoreMemory(reader.findMagic(qoltMagic), TELEMETRY_SIZE))
        assertEquals(List(6) { GENDER_SYMBOL_MALE }, t.party.map { it.genderSymbol })
    }

    @Test
    fun `emerald_qol - party is fully populated, items span multiple pockets`() {
        val items = decodeItems("emerald_qol")
        assertPocketsInRange(items)
        val pocketsPresent = items.map { it.pocket }.toSet()
        assertTrue(
            "expected items in >1 pocket (regression: everything collapsing to one " +
                "pocket is exactly the 2026-09-21 bug this guards against), got $pocketsPresent",
            pocketsPresent.size > 1,
        )
    }
}
