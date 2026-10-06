package com.pokedaisey.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FireRedVanillaDecodeTest {
    @Test
    fun `party populated, items span multiple pockets`() {
        val t = decodeNative("firered_vanilla", NATIVE_FIRERED_REV1)
        assertTrue(t.party.isNotEmpty())
        t.party.forEach { assertTrue(it.species > 0) }
        assertPocketsInRange(t)
        val pockets = t.items.map { it.pocket }.toSet()
        assertTrue("expected items in >1 pocket, got $pockets", pockets.size > 1)
    }

    @Test
    fun `native gender matches the game's own party screen`() {
        // Computed from PID + genderRatiosFireRed (no struct on a retail ROM).
        // Expected values read off the real game's party menu for this exact
        // save (headless mgba_dump `shot`, 2026-09-27): LAPRAS female,
        // KADABRA male, JOLTEON female, CHARIZARD male, (egg), MEOWTH male.
        activeGame = GameKind.FIRERED
        val t = decodeNative("firered_vanilla", NATIVE_FIRERED_REV1)
        assertEquals(
            listOf(GENDER_SYMBOL_FEMALE, GENDER_SYMBOL_MALE, GENDER_SYMBOL_FEMALE, GENDER_SYMBOL_MALE),
            t.party.take(4).map { it.genderSymbol },
        )
        assertEquals(GENDER_SYMBOL_MALE, t.party[5].genderSymbol)
    }

    @Test
    fun `native read shows the egg as an egg`() {
        // BoxPokemon +0x13 isEgg flag -> SPECIES_EGG, no gender (the game's
        // party screen for this save shows slot 4 as "EGG").
        activeGame = GameKind.FIRERED
        val t = decodeNative("firered_vanilla", NATIVE_FIRERED_REV1)
        assertEquals(SPECIES_EGG_VANILLA, t.party[4].species)
        assertEquals(GENDER_SYMBOL_NONE, t.party[4].genderSymbol)
        assertEquals(5, t.party.count { it.species != SPECIES_EGG_VANILLA })
    }
}
