package com.pokedaisey.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AmethystDecodeTest {
    @Test
    fun `party and bag populated with the real live state`() {
        // Fixture is a live in-progress save: 1 Tepig (species=551 in
        // Amethyst's own relocated species table - see
        // SpeciesNamesAmethyst.kt - not National Dex #498 or the internal
        // Gen3 index), level 6, full HP, at Route 17. Unlike the
        // Emerald-based hacks (Lazarus/Emerald Rogue), Amethyst is a
        // FireRed-based ASM hack that kept vanilla's RAM layout unchanged -
        // NATIVE_AMETHYST needed zero address changes, only its own species
        // table (party/bag/location all read correctly out of the box).
        val t = decodeNative("amethyst", NATIVE_AMETHYST)
        assertEquals(1, t.party.size)
        val mon = t.party[0]
        assertEquals(551, mon.species)
        assertEquals(6, mon.level)
        assertEquals(25, mon.hp)
        assertEquals(25, mon.maxHp)
        assertEquals(10, t.items.size)
        assertPocketsInRange(t)
    }

    @Test
    fun `native gender matches the game's own party screen`() {
        // PID + genderRatiosAmethyst (the live gBaseStats, not the stale
        // vanilla copy - that one has Tepig's slot at a different ratio); the
        // game shows this Tepig as female (headless mgba_dump `shot`, 2026-09-27).
        activeGame = GameKind.AMETHYST
        val t = decodeNative("amethyst", NATIVE_AMETHYST)
        assertEquals(GENDER_SYMBOL_FEMALE, t.party[0].genderSymbol)
    }
}
