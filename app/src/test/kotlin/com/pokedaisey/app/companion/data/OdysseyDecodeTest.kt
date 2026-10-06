package com.pokedaisey.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OdysseyDecodeTest {
    @Test
    fun `party populated, pockets are in range`() {
        // Fixture is a live in-progress save (party=2, two items at Viridian
        // City) - see NativeDecodeSupport.kt for the fixture-testing philosophy.
        val t = decodeNative("odyssey", NATIVE_ODYSSEY)
        assertTrue(t.party.isNotEmpty())
        t.party.forEach { assertTrue(it.species > 0) }
        assertPocketsInRange(t)
    }

    @Test
    fun `native gender matches the game's own party screen`() {
        // PID + genderRatiosOdyssey; the game shows Plusle female, Minun male
        // (headless mgba_dump `shot`, 2026-09-27).
        activeGame = GameKind.ODYSSEY
        val t = decodeNative("odyssey", NATIVE_ODYSSEY)
        assertEquals(listOf(GENDER_SYMBOL_FEMALE, GENDER_SYMBOL_MALE), t.party.map { it.genderSymbol })
    }
}
