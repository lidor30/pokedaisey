package com.pokedaisy.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * useFieldItem's rules, against a fake game (what the functions return), and its
 * addresses against the retail ROMs (checked live headless: see FieldItems.kt).
 */
class FieldItemsTest {
    private class FakeGame(val fns: FieldItemFns, var locked: Boolean = false, var repel: Int = 0, var repels: Int = 1) {
        val calls = mutableListOf<Long>()
        fun call(fn: Long, a0: Int, a1: Int): Long {
            calls += fn
            return when (fn) {
                fns.fieldControlsLocked -> if (locked) 1 else 0
                fns.varGet -> { assertEquals(fns.repelVar, a0); repel.toLong() }
                fns.varSet -> { assertEquals(fns.repelVar, a0); repel = a1; 1 }
                fns.checkBagHasItem -> if (repels >= a1) 1 else 0
                fns.removeBagItem -> if (repels >= a1) { repels -= a1; 1 } else 0
                else -> -1
            }
        }
    }

    private val rom = File(RomFileReader.EMERALD_PATH).takeIf { it.isFile }?.readBytes()
    private fun readRom(addr: Long, len: Int): ByteArray = rom!!.copyOfRange((addr - 0x08000000).toInt(), (addr - 0x08000000).toInt() + len)

    @Test
    fun `a Repel sets the step count and leaves the bag`() {
        assumeTrue(rom != null)
        val g = FakeGame(FIELD_ITEMS_EMERALD)
        assertEquals(ItemUseOutcome(ItemUseResult.USED, 100), useFieldItem(g.fns, 86, false, g::call, ::readRom))
        assertEquals(100 to 0, g.repel to g.repels)
        // A second one while the first still works: the game's own "still lingering" rule.
        g.repels = 1
        assertEquals(ItemUseResult.STILL_ACTIVE, useFieldItem(g.fns, 83, false, g::call, ::readRom).result)
        assertEquals(1, g.repels)
    }

    @Test
    fun `never while the game is busy`() {
        assumeTrue(rom != null)
        val g = FakeGame(FIELD_ITEMS_EMERALD, locked = true)
        assertEquals(ItemUseResult.NOT_NOW, useFieldItem(g.fns, 86, false, g::call, ::readRom).result)
        g.locked = false
        assertEquals(ItemUseResult.NOT_NOW, useFieldItem(g.fns, 86, busy = true, call = g::call, readRom = ::readRom).result)
        assertEquals(0, g.repel)
        assertEquals(ItemUseResult.FAILED, useFieldItem(g.fns, 13, false, g::call, ::readRom).result) // a Potion
    }

    @Test
    fun `Super and Max Repel steps come from the ROM`() {
        assumeTrue(rom != null)
        val g = FakeGame(FIELD_ITEMS_EMERALD, repels = 2)
        assertEquals(200, useFieldItem(g.fns, 83, false, g::call, ::readRom).steps)
        g.repel = 0
        assertEquals(250, useFieldItem(g.fns, 84, false, g::call, ::readRom).steps)
    }

    @Test
    fun `retail code is where the tables say, and FireRed's QoL build is left alone`() {
        val fr = File(RomFileReader.FIRERED_REV1_PATH).takeIf { it.isFile }?.readBytes()
        assumeTrue(rom != null && fr != null)
        assertTrue(fieldItemCodeMatches(FIELD_ITEMS_EMERALD, ::readRom))
        val frRead = { a: Long, n: Int -> fr!!.copyOfRange((a - 0x08000000).toInt(), (a - 0x08000000).toInt() + n) }
        assertTrue(fieldItemCodeMatches(FIELD_ITEMS_FIRERED_REV1, frRead))
        assertFalse(fieldItemCodeMatches(FIELD_ITEMS_FIRERED_REV1, ::readRom))
        // gItems: REPEL's 100 steps at the address each table names.
        assertEquals(100, frRead(FIELD_ITEMS_FIRERED_REV1.items + 86 * 44 + 0x13, 1)[0].toInt())
    }
}
