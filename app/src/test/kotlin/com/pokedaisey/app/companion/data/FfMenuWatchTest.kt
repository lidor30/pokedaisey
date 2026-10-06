package com.pokedaisey.app.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SMART fast-forward's menu detection ([FfMenuWatch]). */
class FfMenuWatchTest {
    private fun iwram(name: String) = javaClass.classLoader!!.getResourceAsStream("fixtures/ff_menu_gmain/$name.bin")!!.readBytes()

    @Test fun findsGMainInEveryGame() {
        // Emerald and Rogue count vblankCounter1; FireRed keeps a pointer there and counts counter2.
        assertEquals(0x030022C0L, FfMenuWatch.locate(iwram("emerald_a"), iwram("emerald_b"), 16))
        assertEquals(0x03003100L, FfMenuWatch.locate(iwram("firered_qol_a"), iwram("firered_qol_b"), 16))
        assertEquals(0x030014B4L, FfMenuWatch.locate(iwram("rogue_a"), iwram("rogue_b"), 60))
        // Frames that don't match: nothing, rather than a guess.
        assertEquals(-1L, FfMenuWatch.locate(iwram("emerald_a"), iwram("emerald_b"), 15))
    }

    /** A game whose gMain is at [G], driven frame by frame (Emerald Rogue's callbacks). */
    private class FakeGame : MemoryReader {
        val iwram = ByteArray(0x8000)
        var cb2 = FIELD
        var inBattle = false

        fun frame() {
            put(G - 0x03000000 + 0x20, get(G - 0x03000000 + 0x20) + 1)
            put(G - 0x03000000 + 0x24, get(G - 0x03000000 + 0x24) + 1)
            put(G - 0x03000000 + 4, cb2)
            iwram[(G - 0x03000000 + 0x439).toInt()] = if (inBattle) 2 else 0
        }

        private fun get(o: Long) = Gfx.u32(iwram, o.toInt())
        private fun put(o: Long, v: Long) { for (k in 0 until 4) iwram[o.toInt() + k] = (v shr (8 * k)).toByte() }

        override fun readCoreMemory(addr: Long, size: Int): ByteArray {
            val o = (addr - 0x03000000).toInt()
            return iwram.copyOfRange(o, o + size)
        }
    }

    @Test fun learnsTheFieldAndBattleAndFlagsEverythingElse() {
        val g = FakeGame()
        FfMenuWatch.load("BPEE", 0L, 0L)
        fun run(n: Int): Boolean { var menu = false; repeat(n) { g.frame(); menu = FfMenuWatch.tick(g) }; return menu }
        assertFalse(run(200)) // gMain found; nothing learned yet, so never a menu
        // The player walks on the field: that callback is the field's.
        for (x in 0 until 4) { FfMenuWatch.notePosition(x, 5, 0, 1, false); run(60) }
        assertFalse(run(1))
        g.cb2 = PARTY
        assertTrue(run(1))
        g.cb2 = FIELD
        assertFalse(run(1))
        // A battle: its main callback is learned, its bag is a menu.
        g.inBattle = true
        g.cb2 = BATTLE
        run(200)
        assertFalse(run(1))
        g.cb2 = BAG
        assertTrue(run(1))
        g.cb2 = BATTLE
        assertFalse(run(1))
    }

    @Test fun rememberedCallbacksWorkFromTheFirstFrame() {
        val g = FakeGame()
        FfMenuWatch.load("BPEE", FIELD, BATTLE)
        g.cb2 = PARTY
        var menu = false
        repeat(140) { g.frame(); menu = FfMenuWatch.tick(g) }
        assertTrue(menu)
    }

    private companion object {
        const val G = 0x030014B4L
        const val FIELD = 0x081597ADL
        const val PARTY = 0x0815DF99L
        const val BAG = 0x0812DAC9L
        const val BATTLE = 0x0807FCADL
    }
}
