package com.pokedaisey.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadicalRedDecodeTest {
    @Test
    fun `party populated, pockets are in range`() {
        // Fixture is a fresh/near-start save (party=1, one item) - see
        // NativeDecodeSupport.kt for the fixture-testing philosophy.
        val t = decodeNative("radical_red", NATIVE_RADICAL_RED_V4_1)
        assertTrue(t.party.isNotEmpty())
        t.party.forEach { assertTrue(it.species > 0) }
        assertPocketsInRange(t)
    }

    @Test
    fun `native gender matches the game's own party screen`() {
        // PID + genderRatiosRadicalRed; the game shows this Charmander as male
        // (headless mgba_dump `shot`, 2026-09-27).
        activeGame = GameKind.RADICAL_RED
        val t = decodeNative("radical_red", NATIVE_RADICAL_RED_V4_1)
        assertEquals(GENDER_SYMBOL_MALE, t.party[0].genderSymbol)
    }
}
