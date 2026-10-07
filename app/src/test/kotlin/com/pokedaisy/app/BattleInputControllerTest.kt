package com.pokedaisy.app

import com.pokedaisy.app.companion.data.BATTLE_INPUT_ACTION_SELECT
import com.pokedaisy.app.companion.data.BATTLE_INPUT_BUSY
import com.pokedaisy.app.companion.data.BATTLE_INPUT_MOVE_SELECT
import com.pokedaisy.app.companion.data.BATTLE_INPUT_NONE
import com.pokedaisy.app.companion.data.BATTLE_INPUT_PARTY_OPEN
import com.pokedaisy.app.companion.data.BATTLE_INPUT_TARGET_SELECT
import com.pokedaisy.app.companion.data.MemoryReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BattleInputController]'s switch and RUN sequences against a simulated game:
 * a party menu whose cursor moves one slot per DOWN (wrapping through CANCEL,
 * like FireRed's / Emerald's), checked headless on both games first.
 */
class BattleInputControllerTest {
    private class Game : MemoryReader {
        val party = ByteArray(6 * 100)
        var slot = 0
        val presses = mutableListOf<String>()

        fun pid(i: Int, v: Long) { for (k in 0 until 4) party[i * 100 + k] = (v shr (8 * k)).toByte() }

        override fun readCoreMemory(addr: Long, size: Int): ByteArray = when (addr) {
            MENU + 9 -> byteArrayOf(slot.toByte())
            PARTY -> party.copyOf(size)
            else -> ByteArray(size)
        }
    }

    private val input = GbaInput()
    private val game = Game().apply { for (i in 0 until 6) pid(i, 0x1000L + i) }
    private val c = BattleInputController(input, game, log = {}).apply {
        switchAddrs = BattleInputController.SwitchAddrs(partyMenu = MENU, party = PARTY)
    }

    /** Runs [frames] frames, the simulated game reacting to each new press. */
    private fun run(frames: Int, onPress: (String) -> Unit = {}) {
        var last = 0
        repeat(frames) {
            c.tick()
            val now = input.mask
            val new = now and last.inv()
            last = now
            for ((bit, name) in KEYS) if (new and bit != 0) { game.presses += name; onPress(name) }
        }
    }

    @Test fun switchPicksPokemonThenSteersTheCursorToTheMonAndShifts() {
        c.onState(0, BATTLE_INPUT_ACTION_SELECT)
        c.switchTo(0x1003L) // the mon in slot 3
        var aPresses = 0
        run(600) { key ->
            when (key) {
                "DOWN" -> if (aPresses >= 1) game.slot = (game.slot + 1) % 7 // the party menu's list, after POKéMON
                "A" -> {
                    aPresses++
                    if (aPresses == 1) c.onState(0, BATTLE_INPUT_PARTY_OPEN) // POKéMON opened the party menu
                    if (aPresses == 3) c.onState(0, BATTLE_INPUT_BUSY)       // SHIFT taken
                }
            }
        }
        // LEFT/UP/DOWN/A to POKéMON, then three DOWNs to slot 3, A on it, A on SHIFT.
        assertEquals(listOf("LEFT", "UP", "DOWN", "A", "DOWN", "DOWN", "DOWN", "A", "A"), game.presses)
        assertEquals(3, game.slot)
        assertFalse(c.isBusy)
    }

    @Test fun switchFollowsTheBattleOrderWhileTheMenuIsOpen() {
        // The game moved the mon to slot 1 while the menu is open (battle order): go there.
        game.pid(1, 0x1004L)
        game.pid(4, 0x1001L)
        c.onState(0, BATTLE_INPUT_PARTY_OPEN) // e.g. after a faint
        c.switchTo(0x1004L)
        run(400) { if (it == "DOWN") game.slot = (game.slot + 1) % 7 }
        assertEquals(listOf("DOWN", "A", "A"), game.presses)
    }

    @Test fun aTwoColumnPartyMenuIsSteeredAcrossAndDown() {
        // Lazarus: 0 2 4 | 1 3 5, DOWN / UP within a column, LEFT / RIGHT across (checked headless).
        c.switchAddrs = BattleInputController.SwitchAddrs(partyMenu = MENU, party = PARTY, grid = true)
        c.onState(0, BATTLE_INPUT_PARTY_OPEN)
        c.switchTo(0x1005L)
        run(400) { key ->
            when (key) {
                "RIGHT" -> if (game.slot % 2 == 0) game.slot++
                "LEFT" -> if (game.slot % 2 == 1) game.slot--
                "DOWN" -> if (game.slot + 2 < 6) game.slot += 2
                "UP" -> if (game.slot - 2 >= 0) game.slot -= 2
            }
        }
        assertEquals(listOf("RIGHT", "DOWN", "DOWN", "A", "A"), game.presses)
        assertEquals(5, game.slot)
    }

    @Test fun theScreenIsHeldThroughASwitchAndLetGoAfter() {
        c.onState(0, BATTLE_INPUT_PARTY_OPEN)
        c.switchTo(0x1002L)
        c.tick()
        assertTrue(c.holdFrame) // the game's party menu is never shown
        var aPresses = 0
        run(400) { key ->
            if (key == "DOWN") game.slot = (game.slot + 1) % 7
            if (key == "A" && ++aPresses == 2) c.onState(0, BATTLE_INPUT_BUSY) // SHIFT taken, back in battle
        }
        assertFalse(c.holdFrame)
    }

    @Test fun userKeysAreBlockedOnlyWhileASequenceRuns() {
        input.setTouchBits(MgbaCore.Key.R) // the player holding R on the touch pad
        c.onState(0, BATTLE_INPUT_ACTION_SELECT)
        c.selectAction(0) // FIGHT
        c.tick()
        assertTrue(c.isBusy)
        assertEquals(0, input.mask and MgbaCore.Key.R)
        run(200)
        assertFalse(c.isBusy)
        assertEquals(MgbaCore.Key.R, input.mask and MgbaCore.Key.R)
    }

    @Test fun playerKeysComeBackWhileTheTurnPlaysOut() {
        // A move, then the turn stops on a message that waits for the player's A
        // (a level up, "learn a new move?") - their keys must reach the game.
        c.onState(0, BATTLE_INPUT_MOVE_SELECT)
        c.selectMove(1)
        run(200) { if (it == "A") c.onState(0, BATTLE_INPUT_BUSY) }
        assertEquals(listOf("LEFT", "UP", "RIGHT", "A"), game.presses)
        input.setTouchBits(MgbaCore.Key.A)
        c.tick()
        assertEquals(MgbaCore.Key.A, input.mask and MgbaCore.Key.A)
    }

    @Test fun aTargetScreenAfterAMoveIsStillConfirmed() {
        c.onState(0, BATTLE_INPUT_MOVE_SELECT)
        c.selectMove(0)
        run(100)
        c.onState(0, BATTLE_INPUT_TARGET_SELECT) // the move's "who does this hit" screen
        run(50)
        assertEquals(listOf("LEFT", "UP", "A", "A"), game.presses)
        assertFalse(c.isBusy)
    }

    @Test fun theTargetWatchExpires() {
        c.onState(0, BATTLE_INPUT_MOVE_SELECT)
        c.selectMove(0)
        run(100) { if (it == "A") c.onState(0, BATTLE_INPUT_BUSY) }
        assertTrue(c.isBusy) // still watching for a target screen
        run(400)
        assertFalse(c.isBusy)
        // A target screen much later (the player's own move) is left to the player.
        c.onState(0, BATTLE_INPUT_TARGET_SELECT)
        game.presses.clear()
        run(100)
        assertEquals(emptyList<String>(), game.presses)
    }

    @Test fun runClosesTheGotAwayMessageWithB() {
        c.onState(0, BATTLE_INPUT_ACTION_SELECT)
        c.selectAction(3)
        var b = 0
        run(400) { key ->
            if (key == "A") c.onState(0, BATTLE_INPUT_BUSY)   // "Got away safely!"
            if (key == "B" && ++b == 2) c.onState(0, BATTLE_INPUT_NONE) // the battle ended
        }
        assertEquals(listOf("LEFT", "UP", "RIGHT", "DOWN", "A", "B", "B"), game.presses)
        assertFalse(c.isBusy)
    }

    @Test fun runStopsWhenTheMenuComesBack() {
        // "Can't escape!" (or a trainer battle): one B, then the action menu again.
        c.onState(0, BATTLE_INPUT_ACTION_SELECT)
        c.selectAction(3)
        run(400) { key ->
            if (key == "A") c.onState(0, BATTLE_INPUT_BUSY)
            if (key == "B") c.onState(0, BATTLE_INPUT_ACTION_SELECT)
        }
        assertEquals(1, game.presses.count { it == "B" })
        assertFalse(c.isBusy)
    }

    private companion object {
        const val MENU = 0x0203B0A0L
        const val PARTY = 0x02024284L
        val KEYS = listOf(
            MgbaCore.Key.A to "A", MgbaCore.Key.B to "B", MgbaCore.Key.LEFT to "LEFT",
            MgbaCore.Key.RIGHT to "RIGHT", MgbaCore.Key.UP to "UP", MgbaCore.Key.DOWN to "DOWN",
        )
    }
}
