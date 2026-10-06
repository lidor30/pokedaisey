package com.pokedaisey.app.companion

import com.pokedaisey.app.companion.data.BATTLE_INPUT_BAG_OPEN
import com.pokedaisey.app.companion.data.BATTLE_INPUT_NONE
import com.pokedaisey.app.companion.data.BATTLE_INPUT_PARTY_OPEN

/**
 * Whether SMART fast-forward drops to 1x right now. In a battle the game's own
 * battle state decides ([battleState], BATTLE_INPUT_*, from the battle input
 * poll - NONE outside battles): only its bag and party screens (the summary
 * opened from the party included) slow down, the battle itself never does.
 * Outside battles, and in a game that doesn't report its battle state, the
 * menu watch ([menuOpen], FfMenuWatch) and the region map ([mapOpen]) decide.
 */
fun smartSlows(battleState: Int, menuOpen: Boolean, mapOpen: Boolean): Boolean =
    if (battleState != BATTLE_INPUT_NONE) battleState == BATTLE_INPUT_PARTY_OPEN || battleState == BATTLE_INPUT_BAG_OPEN
    else menuOpen || mapOpen
