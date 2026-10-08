package com.pokedaisy.app

import android.util.Log
import com.pokedaisy.app.companion.data.BATTLE_INPUT_ACTION_SELECT
import com.pokedaisy.app.companion.data.BATTLE_INPUT_BUSY
import com.pokedaisy.app.companion.data.BATTLE_INPUT_MOVE_SELECT
import com.pokedaisy.app.companion.data.BATTLE_INPUT_NONE
import com.pokedaisy.app.companion.data.BATTLE_INPUT_PARTY_OPEN
import com.pokedaisy.app.companion.data.BATTLE_INPUT_TARGET_SELECT
import com.pokedaisy.app.companion.data.Gfx
import com.pokedaisy.app.companion.data.InProcessReader
import com.pokedaisy.app.companion.data.MemoryReader

/**
 * Drives the real in-game battle menu (FIGHT/BAG/POKEMON/RUN, then a move, or
 * a Pokémon to switch to) via synthetic button presses injected through
 * [GbaInput.setScript] - exclusively: while a sequence runs, the player's own
 * keys are ignored so they can't throw it off. No ROM patch involved: this
 * just presses buttons a real player could press. See PLAN.md
 * Phase 5 for the full design (why no cursor-position read is needed, why
 * this works unchanged under any GAME SPEED / fast-forward setting, etc).
 *
 * Thread model: [selectAction]/[selectMove]/[cancel] are called from the UI
 * thread (Compose taps); [tick] and [onState] are called from the emulator
 * thread once per real frame / on the fast telemetry-poll cadence
 * respectively. All mutable state is behind a lock since the two sides run
 * on different threads.
 */
class BattleInputController(
    private val input: GbaInput,
    private val reader: MemoryReader = InProcessReader,
    private val log: (String) -> Unit = { Log.i("pokedaisy-battle", it) },
) {

    /**
     * Where the party menu keeps its cursor and the party, for [switchTo]:
     * FireRed / Emerald (retail and the QoL builds share the addresses) and
     * Lazarus. [partyMenu] is gPartyMenu (`slotId` at +9), [party] gPlayerParty.
     * [grid]: the slots sit in two columns (0 2 4 | 1 3 5, Lazarus), where
     * DOWN stays in its column - else one list that DOWN walks through.
     */
    data class SwitchAddrs(val partyMenu: Long, val party: Long, val monStride: Int = 100, val grid: Boolean = false)

    /**
     * A Gen 1 game (Yellow): its battle menu is FIGHT / PkMn over ITEM / RUN, two one-column
     * menus, and its moves are one list - not Gen 3's 2x2 grids. So presses go from the cursor
     * the last poll read ([gen1Cursor], see readGen1BattleInput) straight to the target: no
     * LEFT / UP normalising, which would wrap in a list.
     */
    @Volatile
    var gen1 = false

    /** The open Gen 1 menu's cursor (action cell column + 2 x row, or move index); -1 unknown. */
    @Volatile
    private var gen1Cursor = -1

    /** Set once the running game is known to support [switchTo]; null = it doesn't. */
    @Volatile
    var switchAddrs: SwitchAddrs? = null

    private enum class SwitchPhase { WAIT_MENU, NAVIGATE, CONFIRM, WAIT_CLOSE }

    private data class Step(val bits: Int, val frames: Int)

    private val lock = Any()
    private val queue = ArrayDeque<Step>()
    private var currentBits = 0
    private var framesLeft = 0
    private var pendingMoveIndex: Int? = null
    private var lastKnownState: Int = BATTLE_INPUT_NONE

    /** The game's battle input state as last polled (BATTLE_INPUT_NONE outside battles), for SMART fast-forward. */
    @Volatile
    var gameState: Int = BATTLE_INPUT_NONE
        private set
    // Set right after queuing a move-cell confirm. Some moves (anything
    // flagged MOVE_TARGET_USER_OR_SELECTED, e.g. certain status moves) don't
    // submit on that A press even in a single battle - they open a *third*
    // "who does this hit" screen instead, which needs a second A to accept
    // the game's own default target. Without watching for this, such a move
    // would look exactly like "cursor lands on the right move, never fires".
    private var awaitingTargetConfirm = false

    // [switchTo] in flight: the chosen mon's personality, where the closed
    // loop is, and counters that keep a stuck menu from blocking input forever.
    private var switchTarget: Long? = null
    private var switchPhase = SwitchPhase.WAIT_MENU
    private var switchFrames = 0
    private var switchPresses = 0
    private var switchSettle = 0

    // RUN's follow-up: B presses left to close "Got away safely!" (or "Can't
    // escape!"), the frames until the next one, and whether the game has left
    // action-select since RUN was picked.
    private var runPresses = 0
    private var runGap = 0
    private var runLeftMenu = false

    // Frames spent in a passive watch (target confirm / pending move) with
    // nothing queued, and frames the player's keys have been shut out in a
    // row - both bounded, so nothing here can lock the player out for good.
    private var watchFrames = 0
    private var exclusiveFrames = 0

    // Frames the screen stays held after a switch ends (see [holdFrame]).
    private var holdTail = 0

    /**
     * True while the screen should keep showing the frame from before a
     * [switchTo]: the switch drives the game's own party menu, which fades the
     * battle to black and back - shown, that's a black flash and a party menu
     * the player never asked to see. Held from the switch's start until the
     * game is back in the battle (WaitForMonSelection only hands back once the
     * battle has faded in), plus [HOLD_TAIL_FRAMES] for the state poll to catch up.
     */
    @Volatile
    var holdFrame = false
        private set

    /** True while a queued sequence hasn't fully drained, a move selection is
     * waiting on the FIGHT screen to open, or a submitted move is waiting on
     * a target-confirm we haven't sent yet. UI should disable further taps
     * while this is true, to avoid overlapping sequences. */
    val isBusy: Boolean
        get() = synchronized(lock) { busyLocked() }

    private fun busyLocked() = exclusiveLocked() || pendingMoveIndex != null || awaitingTargetConfirm

    /**
     * Whether the player's own keys are shut out right now: only while presses
     * are queued or playing, or a closed loop (switch / RUN) is steering. Not
     * during the passive watches (a target screen that may follow a move, a
     * move waiting on FIGHT to open): the turn plays out under those, and it can
     * stop on a message that waits for the player's A (a level up, "learn a new
     * move?", a trainer's "Will you change Pokémon?") - shutting them out there
     * left the game stuck with no way to press anything.
     */
    private fun exclusiveLocked() = queue.isNotEmpty() || framesLeft > 0 || switchTarget != null || runPresses > 0

    /** Call once per real emulated frame, before MgbaCore.pkSetKeys. */
    fun tick() {
        val (bitsToSend, exclusive) = synchronized(lock) {
            // The closed-loop steps (party cursor, RUN's B) queue their next
            // presses only once the last ones have played out.
            if (framesLeft <= 0 && queue.isEmpty()) {
                driveSwitchLocked()
                driveRunLocked()
            }
            if (framesLeft <= 0) {
                val next = queue.removeFirstOrNull()
                currentBits = next?.bits ?: 0
                framesLeft = next?.frames ?: 0
            }
            if (framesLeft > 0) framesLeft--
            // A target screen follows a move's A within a few frames, and FIGHT
            // opens as fast: a watch still unanswered after that is stale.
            if (queue.isEmpty() && framesLeft <= 0 && (awaitingTargetConfirm || pendingMoveIndex != null)) {
                if (++watchFrames > WATCH_TIMEOUT_FRAMES) {
                    log("watch expired (awaitingTarget=$awaitingTargetConfirm, pending=$pendingMoveIndex)")
                    awaitingTargetConfirm = false
                    pendingMoveIndex = null
                    watchFrames = 0
                }
            } else {
                watchFrames = 0
            }
            // Every sequence is bounded on its own; this is the backstop.
            if (exclusiveLocked()) {
                if (++exclusiveFrames > EXCLUSIVE_TIMEOUT_FRAMES) {
                    log("keys shut out for $exclusiveFrames frames - cancelling")
                    cancelLocked()
                }
            } else {
                exclusiveFrames = 0
            }
            if (switchTarget == null && holdTail > 0) holdTail--
            holdFrame = switchTarget != null || holdTail > 0
            currentBits to exclusiveLocked()
        }
        if (bitsToSend != lastLoggedBits) {
            log("tick sending bits=$bitsToSend")
            lastLoggedBits = bitsToSend
        }
        input.setScript(bitsToSend, exclusive)
    }

    /**
     * Switches to the party mon whose personality is [personality]: picks
     * POKéMON if the action menu is up (or uses the party menu the game
     * already opened, e.g. after a faint), then steers the menu's cursor to
     * that mon and picks SHIFT. Closed loop on the game's own memory: the
     * battle party menu shows the party in battle order (UpdatePartyToBattleOrder
     * really reorders gPlayerParty while it's open), so the mon is looked up
     * there by personality, and DOWN is pressed until gPartyMenu.slotId is its
     * slot - no assumptions about where the cursor starts or how it wraps.
     */
    fun switchTo(personality: Long) {
        synchronized(lock) {
            if (switchAddrs == null || personality == 0L) return
            log("switchTo(pid=0x${personality.toString(16)}) lastKnownState=$lastKnownState")
            pendingMoveIndex = null
            if (lastKnownState == BATTLE_INPUT_ACTION_SELECT) queueCellLocked(col(2), row(2)) // POKéMON
            switchTarget = personality
            switchPhase = SwitchPhase.WAIT_MENU
            switchFrames = 0
            switchPresses = 0
            switchSettle = 0
        }
    }

    private fun driveSwitchLocked() {
        val target = switchTarget ?: return
        val addrs = switchAddrs ?: return endSwitchLocked("no addresses")
        switchFrames++
        when (switchPhase) {
            // The party menu takes a moment to fade in after the controller
            // starts waiting on it; presses before then are dropped.
            SwitchPhase.WAIT_MENU ->
                if (lastKnownState == BATTLE_INPUT_PARTY_OPEN) {
                    if (++switchSettle >= MENU_SETTLE_FRAMES) {
                        switchPhase = SwitchPhase.NAVIGATE
                        switchFrames = 0
                    }
                } else if (switchFrames > MENU_TIMEOUT_FRAMES) {
                    endSwitchLocked("party menu never opened")
                }
            SwitchPhase.NAVIGATE -> {
                val slot = runCatching { reader.readCoreMemory(addrs.partyMenu + PARTY_MENU_SLOT_ID, 1)[0].toInt() }.getOrNull()
                    ?: return endSwitchLocked("cursor unreadable")
                val want = slotOf(addrs, target) ?: return endSwitchLocked("mon not in the party")
                if (slot == want) {
                    pressLocked(MgbaCore.Key.A)      // the mon -> its SHIFT / SUMMARY / CANCEL menu
                    waitFramesLocked(SUBMENU_FRAMES)
                    switchPhase = SwitchPhase.CONFIRM
                } else if (++switchPresses > MAX_CURSOR_PRESSES) {
                    endSwitchLocked("cursor didn't reach slot $want")
                } else {
                    pressLocked(cursorKey(addrs, slot, want))
                    waitFramesLocked(CURSOR_FRAMES)
                }
            }
            SwitchPhase.CONFIRM -> {
                pressLocked(MgbaCore.Key.A)          // SHIFT, the menu's first entry
                switchPhase = SwitchPhase.WAIT_CLOSE
                switchFrames = 0
            }
            // Keep the player's keys out until the game has taken the switch.
            SwitchPhase.WAIT_CLOSE ->
                if (lastKnownState != BATTLE_INPUT_PARTY_OPEN || switchFrames > CLOSE_TIMEOUT_FRAMES) endSwitchLocked("done")
        }
    }

    /** The press that moves the party cursor from [slot] toward [want]. */
    private fun cursorKey(addrs: SwitchAddrs, slot: Int, want: Int): Int = when {
        !addrs.grid -> MgbaCore.Key.DOWN          // wraps through CANCEL back to the top
        slot !in 0 until 6 -> MgbaCore.Key.UP     // CANCEL: back into the grid
        slot % 2 != want % 2 -> if (want % 2 == 1) MgbaCore.Key.RIGHT else MgbaCore.Key.LEFT
        else -> if (want > slot) MgbaCore.Key.DOWN else MgbaCore.Key.UP
    }

    /** gPlayerParty's slot holding [personality] (the menu's slot while it's open), or null. */
    private fun slotOf(addrs: SwitchAddrs, personality: Long): Int? {
        val raw = runCatching { reader.readCoreMemory(addrs.party, 6 * addrs.monStride) }.getOrNull() ?: return null
        return (0 until 6).firstOrNull { Gfx.u32(raw, it * addrs.monStride) == personality }
    }

    private fun endSwitchLocked(why: String) {
        log("switch ended: $why")
        switchTarget = null
        holdTail = HOLD_TAIL_FRAMES
    }

    private fun driveRunLocked() {
        if (runPresses <= 0) return
        when {
            // The battle ended (got away), or it's back on the menu (couldn't escape): done.
            lastKnownState == BATTLE_INPUT_NONE -> runPresses = 0
            runLeftMenu && lastKnownState == BATTLE_INPUT_ACTION_SELECT -> runPresses = 0
            else -> {
                if (lastKnownState != BATTLE_INPUT_ACTION_SELECT) runLeftMenu = true
                if (--runGap <= 0) {
                    pressLocked(MgbaCore.Key.B)
                    runPresses--
                    runGap = RUN_B_GAP
                }
            }
        }
    }
    private var lastLoggedBits = -1

    /** Feed the latest (battleActiveBattler, battleInputState) from the fast
     * telemetry poll (not the ~1 Hz full refresh). Resumes a move selection
     * that's waiting for the FIGHT screen to actually open. */
    fun onState(activeBattler: Int, state: Int, cursor: Int = -1) {
        synchronized(lock) {
            gen1Cursor = cursor
            if (state != lastKnownState) {
                log("state $lastKnownState -> $state (battler=$activeBattler, queue=${queue.size}, framesLeft=$framesLeft, pending=$pendingMoveIndex, awaitingTarget=$awaitingTargetConfirm)")
            }
            lastKnownState = state
            gameState = state

            if (awaitingTargetConfirm && queue.isEmpty() && framesLeft == 0) {
                when (state) {
                    BATTLE_INPUT_TARGET_SELECT -> {
                        log("target-select seen, sending confirm A")
                        awaitingTargetConfirm = false
                        pressLocked(MgbaCore.Key.A) // accept the game's own default target
                    }
                    // Anything else here means the move submitted directly
                    // (no target-select needed) or something unrelated
                    // happened - either way, stop watching for it.
                    BATTLE_INPUT_BUSY -> Unit // still resolving; keep waiting
                    else -> {
                        log("dropping awaitingTargetConfirm, state=$state")
                        awaitingTargetConfirm = false
                    }
                }
            }

            val pending = pendingMoveIndex ?: return
            // Doubles' battler-id-to-position mapping isn't handled yet (see
            // PLAN.md) - if the choosing battler changed out from under a
            // pending move (e.g. the partner mon's turn came up), drop it
            // rather than risk firing into the wrong mon's menu.
            if (activeBattler != 0) {
                log("dropping pendingMoveIndex=$pending, activeBattler=$activeBattler != 0")
                pendingMoveIndex = null
                return
            }
            if (state == BATTLE_INPUT_MOVE_SELECT && queue.isEmpty() && framesLeft == 0) {
                log("resuming pendingMoveIndex=$pending now that MOVE_SELECT is open")
                pendingMoveIndex = null
                queueMoveCellLocked(pending)
            }
        }
    }

    /** Cancels any in-flight or pending sequence and releases all keys. */
    fun cancel() {
        synchronized(lock) { cancelLocked() }
    }

    private fun cancelLocked() {
        queue.clear()
        pendingMoveIndex = null
        awaitingTargetConfirm = false
        switchTarget = null
        runPresses = 0
        currentBits = 0
        framesLeft = 0
        watchFrames = 0
        exclusiveFrames = 0
        holdTail = 0
        holdFrame = false
    }

    /** actionIndex: 0=FIGHT, 1=BAG, 2=POKEMON, 3=RUN — matches the ROM's own
     * gActionSelectionCursor values (B_ACTION_USE_MOVE/USE_ITEM/SWITCH/RUN). */
    fun selectAction(actionIndex: Int) {
        synchronized(lock) {
            pendingMoveIndex = null
            if (gen1) queueGen1ActionLocked(actionIndex) else queueCellLocked(col(actionIndex), row(actionIndex))
            if (actionIndex == ACTION_RUN) {
                // "Got away safely!" waits for a button: close it with B (a press
                // while it's still printing just finishes the text).
                runPresses = RUN_B_PRESSES
                runGap = RUN_FIRST_GAP
                runLeftMenu = false
            }
        }
    }

    /** moveIndex 0-3. The move-select screen only ever gets tapped while it's
     * already open (that's the only time the UI shows move cards at all), so
     * this navigates that grid directly - it must NOT re-run the "open
     * FIGHT" detour here, since LEFT/UP/A would then be misread as move-grid
     * navigation and confirm whatever the game's own default move slot is
     * (0) regardless of which card was tapped. The FIGHT-then-continue path
     * stays available for [onState] to resume a pending selection made while
     * still on the action-select screen (not currently reachable from the
     * UI, since it only calls this once already in MOVE_SELECT, but kept so
     * a future caller doesn't have to re-solve this). */
    fun selectMove(moveIndex: Int) {
        synchronized(lock) {
            log("selectMove($moveIndex) lastKnownState=$lastKnownState col=${col(moveIndex)} row=${row(moveIndex)}")
            if (lastKnownState == BATTLE_INPUT_ACTION_SELECT) {
                pendingMoveIndex = moveIndex
                if (gen1) queueGen1ActionLocked(0) else queueCellLocked(col(0), row(0)) // open FIGHT (action index 0)
            } else {
                pendingMoveIndex = null
                queueMoveCellLocked(moveIndex)
            }
        }
    }

    /** A single B press — backs out of the move-select screen to action-select,
     * same as the real B button. */
    fun back() {
        synchronized(lock) {
            log("back()")
            pendingMoveIndex = null
            pressLocked(MgbaCore.Key.B)
        }
    }

    // 2x2 grid shared by both the action-select and move-select screens:
    // col = LEFT/RIGHT bit, row = UP/DOWN bit (see battle_controller_player.c's
    // gActionSelectionCursor/gMoveSelectionCursor XOR-toggle logic).
    private fun col(index: Int) = index and 1
    private fun row(index: Int) = (index shr 1) and 1

    /** The normalize-then-walk script: LEFT/UP unconditionally (each a no-op
     * if already there), then RIGHT/DOWN only if the target needs them, then
     * A - each press followed by a settling gap. Lands on the right cell
     * with no need to know the current cursor.
     *
     * The settling gap exists because a cursor move that actually takes
     * effect starts a cosmetic palette fade (BeginNormalPaletteFade), and at
     * least Unbound's rewritten move-select screen appears to ignore *any*
     * further input while that fade is still active - confirmed live twice:
     * first a move whose cell required only one real DPAD move landed the
     * cursor correctly but the immediately-following A never registered;
     * fixed by gapping before A. Then a move needing *two* real moves (e.g.
     * bottom-left -> top-right: UP lands on top-left, then RIGHT) landed on
     * the *intermediate* cell instead of the target - the second directional
     * press was getting swallowed by the first press's own fade the same
     * way, since only the gap before A existed yet. Gapping after every
     * press (not just the last one) fixes both without needing to read
     * gPaletteFade.active, which we have no address for. A press whose guard
     * doesn't apply (a no-op, cursor already there) doesn't start a fade, so
     * the extra gap after it is just unused wait time, not a correctness
     * risk. */
    private fun queueCellLocked(col: Int, row: Int) {
        pressAndSettleLocked(MgbaCore.Key.LEFT)
        pressAndSettleLocked(MgbaCore.Key.UP)
        if (col == 1) pressAndSettleLocked(MgbaCore.Key.RIGHT)
        if (row == 1) pressAndSettleLocked(MgbaCore.Key.DOWN)
        pressLocked(MgbaCore.Key.A)
    }

    private fun pressAndSettleLocked(bit: Int) {
        pressLocked(bit)
        waitFramesLocked(20)
    }

    private fun waitFramesLocked(frames: Int) {
        queue.addLast(Step(0, frames))
    }

    /** Same as [queueCellLocked] but for a move specifically - also arms the
     * target-confirm watch in [onState], since submitting a move (unlike an
     * action) can land on a third "who does this hit" screen instead of
     * completing outright. */
    private fun queueMoveCellLocked(moveIndex: Int) {
        if (gen1) {
            // One list (items 1-4): UP / DOWN from the read cursor, then A. No target screen.
            val from = gen1Cursor.takeIf { it in 0..3 } ?: 0
            repeat(kotlin.math.abs(moveIndex - from)) { pressAndSettleLocked(if (moveIndex > from) MgbaCore.Key.DOWN else MgbaCore.Key.UP) }
            pressLocked(MgbaCore.Key.A)
            return
        }
        queueCellLocked(col(moveIndex), row(moveIndex))
        awaitingTargetConfirm = true
    }

    /**
     * Gen 1's battle menu: [actionIndex] in Gen 3's numbering (0 FIGHT, 1 BAG, 2 POKEMON,
     * 3 RUN) onto its cells - FIGHT and PkMn on top, ITEM and RUN below. RIGHT / LEFT
     * switches column on the same row, UP / DOWN the row; then A.
     */
    private fun queueGen1ActionLocked(actionIndex: Int) {
        val target = when (actionIndex) { 0 -> 0; 1 -> 2; 2 -> 1; else -> 3 }
        val from = gen1Cursor.takeIf { it in 0..3 } ?: 0
        if ((from and 1) != (target and 1)) pressAndSettleLocked(if (target and 1 == 1) MgbaCore.Key.RIGHT else MgbaCore.Key.LEFT)
        if ((from shr 1) != (target shr 1)) pressAndSettleLocked(if (target shr 1 == 1) MgbaCore.Key.DOWN else MgbaCore.Key.UP)
        pressLocked(MgbaCore.Key.A)
    }

    /** One real frame pressed, one real frame released — JOY_NEW only fires
     * on the frame a bit transitions from unset to set, so back-to-back
     * presses of the same button need an explicit release between them. */
    private fun pressLocked(bit: Int) {
        log("queue press bit=$bit (${keyName(bit)})")
        queue.addLast(Step(bit, 1))
        queue.addLast(Step(0, 1))
    }

    private companion object {
        const val ACTION_RUN = 3
        const val PARTY_MENU_SLOT_ID = 9L      // struct PartyMenu.slotId, both games
        const val MENU_SETTLE_FRAMES = 45      // after the controller starts waiting on the party menu (Emerald ignored a DOWN at ~35)
        const val MENU_TIMEOUT_FRAMES = 240
        const val CURSOR_FRAMES = 10           // after each DOWN, before reading the cursor again
        const val MAX_CURSOR_PRESSES = 16      // two laps of six slots + CANCEL, then give up
        const val SUBMENU_FRAMES = 20          // the SHIFT / SUMMARY / CANCEL menu opening
        const val CLOSE_TIMEOUT_FRAMES = 240
        const val RUN_B_PRESSES = 6
        const val RUN_FIRST_GAP = 45           // the escape message starts printing
        const val RUN_B_GAP = 30
        const val HOLD_TAIL_FRAMES = 12        // the ~4-frame state poll, and a little more
        const val WATCH_TIMEOUT_FRAMES = 300   // a target screen / FIGHT that hasn't shown by then won't
        const val EXCLUSIVE_TIMEOUT_FRAMES = 1200 // a switch's worst case (every step timing out) is ~800
    }

    private fun keyName(bit: Int) = when (bit) {
        MgbaCore.Key.A -> "A"
        MgbaCore.Key.B -> "B"
        MgbaCore.Key.LEFT -> "LEFT"
        MgbaCore.Key.RIGHT -> "RIGHT"
        MgbaCore.Key.UP -> "UP"
        MgbaCore.Key.DOWN -> "DOWN"
        else -> "?$bit"
    }
}
