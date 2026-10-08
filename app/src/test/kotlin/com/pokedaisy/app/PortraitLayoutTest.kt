package com.pokedaisy.app

import com.pokedaisy.app.companion.ui.PortraitLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PortraitPanel's sizes on a Pixel 6-class phone (1080x2400, the game 720 tall), and controller D-pad diagonals. */
class PortraitLayoutTest {
    private val w = 1080
    private val h = 2400
    private val game = 720

    @Test
    fun companionKeepsItsShapeAndStaysOffTheGame() {
        assertEquals(941, PortraitLayout.companionHeight(w, h, game, PortraitLayout.DEFAULT_RATIO))
        assertEquals(648, PortraitLayout.companionHeight(w, h, game, 0.1f))      // no smaller than MIN_RATIO
        assertEquals(h - game, PortraitLayout.companionHeight(w, h, game, 10f))  // never over the game
    }

    @Test
    fun dragsSnapToTheDefaultAndTheLargest() {
        val max = PortraitLayout.maxRatio(w, h, game)
        assertEquals(PortraitLayout.DEFAULT_RATIO, PortraitLayout.snap(w, h, game, PortraitLayout.DEFAULT_RATIO + 40f / w), 0f)
        assertEquals(max, PortraitLayout.snap(w, h, game, max - 30f / w), 0f)
        assertEquals(1.2f, PortraitLayout.snap(w, h, game, 1.2f), 0f)
    }

    @Test
    fun tapsStepThroughTheSizes() {
        val max = PortraitLayout.maxRatio(w, h, game)
        assertEquals(PortraitLayout.DEFAULT_RATIO, PortraitLayout.next(w, h, game, PortraitLayout.MIN_RATIO), 0f)
        assertEquals(max, PortraitLayout.next(w, h, game, PortraitLayout.DEFAULT_RATIO), 0f)
        assertEquals(PortraitLayout.MIN_RATIO, PortraitLayout.next(w, h, game, max), 0f)
    }

    @Test
    fun touchPadTakesTheGapOnlyWhenItFits() {
        assertTrue(PortraitLayout.padInGap(w, h - game - 941))
        assertFalse(PortraitLayout.padInGap(w, 100))
    }

    @Test
    fun gripNeverCoversTheGame() {
        val grip = 63
        // Along the bottom, full height: no room above the companion, so the grip goes inside its top.
        val full = PortraitLayout.arrange(w, h, game, grip, 10f, underGame = false)
        assertEquals(PortraitLayout.Grip.INSIDE, full.grip)
        assertEquals(game, full.gripTop)
        assertEquals(grip, full.topInset(grip))
        // Default size: above the companion, the touch pad in the gap over it.
        val normal = PortraitLayout.arrange(w, h, game, grip, PortraitLayout.DEFAULT_RATIO, underGame = false)
        assertEquals(PortraitLayout.Grip.ABOVE, normal.grip)
        assertEquals(h - 941 - grip, normal.gripTop)
        assertEquals(game, normal.padTop)
        assertEquals(normal.gripTop, normal.padBottom)
    }

    @Test
    fun underTheGameThePadTakesTheBottom() {
        val grip = 63
        val a = PortraitLayout.arrange(w, h, game, grip, PortraitLayout.DEFAULT_RATIO, underGame = true)
        assertEquals(game, a.companionTop)
        assertEquals(PortraitLayout.Grip.BELOW, a.grip)
        assertEquals(game + 941, a.gripTop)
        assertEquals(game + 941 + grip, a.padTop)
        assertEquals(h, a.padBottom)
        // Full height leaves the grip its strip at the bottom.
        val full = PortraitLayout.arrange(w, h, game, grip, 10f, underGame = true)
        assertEquals(h - game - grip, full.companionHeight)
        assertEquals(h - grip, full.gripTop)
    }

    @Test
    fun diagonalKeysPressBothDirections() {
        val input = GbaInput()
        input.onKey(android.view.KeyEvent.KEYCODE_DPAD_UP_LEFT, true)
        assertEquals(MgbaCore.Key.UP or MgbaCore.Key.LEFT, input.mask)
        input.onKey(android.view.KeyEvent.KEYCODE_DPAD_UP_LEFT, false)
        assertEquals(0, input.mask)
    }
}
