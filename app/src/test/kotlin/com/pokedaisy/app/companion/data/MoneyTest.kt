package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Money (the top-screen status bar), per fixture save. */
class MoneyTest {
    private fun money(key: String, cfg: NativeConfig) = readMoney(FixtureMemoryReader.load(key), cfg)

    @Test
    fun `retail saves`() {
        assertEquals(224300L, money("firered_vanilla", NATIVE_FIRERED_REV1))
        assertEquals(6098L, money("leafgreen_rev1", NATIVE_LEAFGREEN_REV1))
        assertEquals(185092L, money("emerald_vanilla", NATIVE_EMERALD_RETAIL))
        // Ruby / Sapphire don't XOR it.
        assertEquals(4961L, money("ruby_rev2", NATIVE_RUBY))
        assertEquals(398179L, money("sapphire_rev2", NATIVE_SAPPHIRE))
    }

    @Test
    fun `hacks keep their base game's offset`() {
        assertEquals(3848L, money("unbound", NATIVE_UNBOUND))
        assertEquals(2999L, money("celia", NATIVE_CELIA))
        // Heart and Soul's SaveBlock1 has 4 extra bytes up front.
        assertEquals(3000L, money("heart_and_soul", NATIVE_HEART_AND_SOUL))
        assertEquals(3000L, money("lazarus", NATIVE_LAZARUS))
        // Rogue's SaveBlock1 grew (104-byte party mons): money at +0x4A8; this save has none.
        assertEquals(0L, money("emerald_rogue", NATIVE_EMERALD_ROGUE))
        assertEquals(3000L, money("glazed", NATIVE_GLAZED))
        assertEquals(3000L, money("imperium", NATIVE_IMPERIUM))
        // Quetzal's SaveBlock1 is its own: money at +0x918, the key at SB2+0x2C.
        assertEquals(7450L, money("quetzal", NATIVE_QUETZAL))
    }

    @Test
    fun `QoL ROMs - found through the struct's map`() {
        val reader = FixtureMemoryReader.load("emerald_qol")
        val t = decodeTelemetry(reader.readCoreMemory(reader.findMagic("QOLT".toByteArray(Charsets.US_ASCII)), TELEMETRY_SIZE))
        assertEquals(185092L, findStructMoney(reader, GameKind.EMERALD, t.mapGroup, t.mapNum)?.second)
        // Another map: no candidate matches, so no money rather than a guess.
        assertNull(findStructMoney(reader, GameKind.EMERALD, t.mapGroup, t.mapNum + 1))
    }

    @Test
    fun `FireRed QoL - its own save block pointers, not retail's`() {
        // This capture's struct is a refresh behind its save (map 3.56 vs the
        // save's 3.16 - the struct refreshes about once a second), so match
        // the save's map, as the app does a refresh later.
        val reader = FixtureMemoryReader.load("firered_qol")
        val (cfg, money) = findStructMoney(reader, GameKind.FIRERED, 3, 16)!!
        assertEquals(224300L, money)
        assertEquals(0x03005018L, cfg.saveBlock1Ptr)
        assertNull(findStructMoney(reader, GameKind.FIRERED, 3, 56))
    }
}
