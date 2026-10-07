package com.pokedaisy.app.companion

import com.pokedaisy.app.companion.data.BATTLE_INPUT_ACTION_SELECT
import com.pokedaisy.app.companion.data.BATTLE_INPUT_BAG_OPEN
import com.pokedaisy.app.companion.data.BATTLE_INPUT_BUSY
import com.pokedaisy.app.companion.data.BATTLE_INPUT_MOVE_SELECT
import com.pokedaisy.app.companion.data.BATTLE_INPUT_NONE
import com.pokedaisy.app.companion.data.BATTLE_INPUT_PARTY_OPEN
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [smartSlows]: battles stay fast, their bag / party and the field's menus don't. */
class SmartFfTest {
    @Test fun aBattleStaysFastWhateverTheMenuWatchThinks() {
        for (state in listOf(BATTLE_INPUT_BUSY, BATTLE_INPUT_ACTION_SELECT, BATTLE_INPUT_MOVE_SELECT)) {
            assertFalse(smartSlows(state, menuOpen = true, mapOpen = true))
        }
    }

    @Test fun theBattlesBagAndPartySlowDown() {
        assertTrue(smartSlows(BATTLE_INPUT_PARTY_OPEN, menuOpen = false, mapOpen = false))
        assertTrue(smartSlows(BATTLE_INPUT_BAG_OPEN, menuOpen = false, mapOpen = false))
    }

    @Test fun outsideBattlesTheMenuWatchAndMapDecide() {
        assertTrue(smartSlows(BATTLE_INPUT_NONE, menuOpen = true, mapOpen = false))
        assertTrue(smartSlows(BATTLE_INPUT_NONE, menuOpen = false, mapOpen = true))
        assertFalse(smartSlows(BATTLE_INPUT_NONE, menuOpen = false, mapOpen = false))
    }
}
