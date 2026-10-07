package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** NATIVE_SOULGOLD on the user's save (see the soulgold fixture's README). */
class SoulGoldDecodeTest {
    @Test
    fun `party - its own 96-byte struct`() {
        activeGame = GameKind.SOULGOLD
        val t = decodeNative("soulgold", NATIVE_SOULGOLD)
        assertFalse(t.inBattle)
        val mon = t.party.single()
        assertEquals(155, mon.species)
        assertEquals("Cyndaquil", speciesName(mon.species))
        assertEquals(6, mon.level)
        assertEquals(21 to 21, mon.hp to mon.maxHp)
        assertEquals(listOf("Tackle", "Leer"), mon.moves.filter { it != 0 }.map { lookupMove(it).name })
        assertEquals(listOf(35, 30), mon.pp.take(2))
        assertEquals(GENDER_SYMBOL_MALE, mon.genderSymbol) // the party menu's blue symbol
    }

    @Test
    fun `location, money and bag`() {
        activeGame = GameKind.SOULGOLD
        val t = decodeNative("soulgold", NATIVE_SOULGOLD)
        assertEquals(0xCD, t.regionMapSectionId) // a u16 mapsec
        assertEquals("Cherrygrove City", lookupLocation(t.regionMapSectionId).mapSecName)
        // MAP_TYPE_CITY, a byte later than vanilla (readMapShape also needs the ROM's layout).
        assertEquals(2, FixtureMemoryReader.load("soulgold").readCoreMemory(NATIVE_SOULGOLD.mapHeader + 0x18, 1)[0].toInt())
        assertEquals(3080L, t.money) // the trainer card
        val potion = t.items.single()
        assertEquals("Potion", itemName(potion.itemId))
        assertEquals(1, potion.quantity)
        assertEquals(POCKET_ITEMS, potion.pocket) // the Medicine pocket
        assertTrue(itemDescription(potion.itemId).contains("20 points"))
    }

    @Test
    fun `battle - both battlers, the switched-in mon and the action menu`() {
        activeGame = GameKind.SOULGOLD
        val ram = FixtureMemoryReader.load("soulgold_battle")
        val t = readNativeTelemetry(ram, NATIVE_SOULGOLD)
        assertTrue(t.inBattle)
        assertFalse(t.isDoubleBattle)
        val me = t.battleMons[BATTLE_POS_PLAYER_LEFT]
        assertEquals(155 to 6, me.species to me.level)
        assertEquals(9 to 21, me.hp to me.maxHp) // the third Cyndaquil (13 HP), hit as it came in
        val foe = t.battleMons[BATTLE_POS_OPPONENT_LEFT]
        assertEquals(25 to 5, foe.species to foe.level)
        assertEquals(18 to 18, foe.hp to foe.maxHp)
        assertEquals(14 to 14, foe.type1 to foe.type2) // Electric
        assertEquals(listOf(39, 84, 45, 86), foe.moves.toList()) // Tail Whip, Thunder Shock, Growl, Thunder Wave
        assertEquals(3, t.party.size)
        assertEquals(0 to BATTLE_INPUT_ACTION_SELECT, readNativeBattleInputFast(ram, NATIVE_SOULGOLD))
        // gBattlerPartyIndexes[0]: the third slot is out.
        assertEquals(2, u16le(ram.readCoreMemory(NATIVE_SOULGOLD.battlerPartyIndexes, 2), 0))
        // The wild Pikachu sits in gEnemyParty (a wild battle shows no FOE TEAM).
        val foeMon = decodePartyMon(ram.readCoreMemory(NATIVE_SOULGOLD.enemyParty, 96), 0, SOULGOLD_PARTY_MON)!!
        assertEquals(25 to 5, (foeMon.species and 0x7FF) to foeMon.level)
    }
}
