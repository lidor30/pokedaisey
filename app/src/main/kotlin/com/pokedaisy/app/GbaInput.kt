package com.pokedaisy.app

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Translates Android key/motion events into a live GBA key bitmask. Face /
 * shoulder / menu buttons are remappable via [setControls] (GbaControls); the
 * D-pad and analog stick are fixed.
 */
class GbaInput {
    @Volatile
    var mask: Int = 0
        private set

    private var buttonBits = 0   // from key events: the bits of every key in heldKeys
    // Per key, not per bit: START and START 2 (X) are two keys for one bit, and
    // letting go of one mustn't release the other.
    private val heldKeys = HashMap<Int, Int>()
    private var hatBits = 0      // from D-pad HAT / analog axes
    private var touchBits = 0    // from the on-screen TouchControlsView
    private var scriptBits = 0   // synthetic presses (BattleInputController)
    private var scriptExclusive = false

    @Volatile
    private var controls: Map<Int, Int> = DEFAULT_CONTROLS

    fun setControls(map: Map<Int, Int>) { controls = map }

    /** @return true if the event was consumed. */
    fun onKey(keyCode: Int, down: Boolean): Boolean {
        val bit = keyToBit(keyCode) ?: return false
        if (down) heldKeys[keyCode] = bit else heldKeys.remove(keyCode)
        buttonBits = heldKeys.values.fold(0) { acc, b -> acc or b }
        recompute()
        return true
    }

    fun setTouchBits(bits: Int) {
        touchBits = bits
        recompute()
    }

    /**
     * Synthetic presses from the companion's battle controls. While
     * [exclusive] (an automated sequence is running) the player's own keys -
     * buttons, D-pad, touch pad - are ignored, so they can't knock the
     * sequence off course; they count again the moment it ends.
     */
    fun setScript(bits: Int, exclusive: Boolean) {
        scriptBits = bits
        scriptExclusive = exclusive
        recompute()
    }

    fun onMotion(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val x = if (abs(hx) > 0.5f) hx else event.getAxisValue(MotionEvent.AXIS_X)
        val y = if (abs(hy) > 0.5f) hy else event.getAxisValue(MotionEvent.AXIS_Y)
        var bits = 0
        if (x < -DEAD) bits = bits or MgbaCore.Key.LEFT
        if (x > DEAD) bits = bits or MgbaCore.Key.RIGHT
        if (y < -DEAD) bits = bits or MgbaCore.Key.UP
        if (y > DEAD) bits = bits or MgbaCore.Key.DOWN
        hatBits = bits
        recompute()
        return true
    }

    private fun recompute() {
        mask = if (scriptExclusive) scriptBits else buttonBits or hatBits or touchBits or scriptBits
    }

    private fun keyToBit(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> MgbaCore.Key.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> MgbaCore.Key.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> MgbaCore.Key.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> MgbaCore.Key.RIGHT
        // Some controllers (8BitDo, GameSir in a few modes) send a diagonal as one key.
        KeyEvent.KEYCODE_DPAD_UP_LEFT -> MgbaCore.Key.UP or MgbaCore.Key.LEFT
        KeyEvent.KEYCODE_DPAD_UP_RIGHT -> MgbaCore.Key.UP or MgbaCore.Key.RIGHT
        KeyEvent.KEYCODE_DPAD_DOWN_LEFT -> MgbaCore.Key.DOWN or MgbaCore.Key.LEFT
        KeyEvent.KEYCODE_DPAD_DOWN_RIGHT -> MgbaCore.Key.DOWN or MgbaCore.Key.RIGHT
        else -> controls[keyCode]   // remappable face/shoulder/menu buttons
    }

    companion object {
        private const val DEAD = 0.5f

        private val DEFAULT_CONTROLS = mapOf(
            KeyEvent.KEYCODE_BUTTON_A to MgbaCore.Key.A, KeyEvent.KEYCODE_Z to MgbaCore.Key.A,
            KeyEvent.KEYCODE_BUTTON_B to MgbaCore.Key.B, KeyEvent.KEYCODE_X to MgbaCore.Key.B,
            KeyEvent.KEYCODE_BUTTON_L1 to MgbaCore.Key.L, KeyEvent.KEYCODE_A to MgbaCore.Key.L,
            KeyEvent.KEYCODE_BUTTON_R1 to MgbaCore.Key.R, KeyEvent.KEYCODE_S to MgbaCore.Key.R,
            KeyEvent.KEYCODE_BUTTON_START to MgbaCore.Key.START, KeyEvent.KEYCODE_ENTER to MgbaCore.Key.START,
            KeyEvent.KEYCODE_BUTTON_SELECT to MgbaCore.Key.SELECT,
            KeyEvent.KEYCODE_BACKSLASH to MgbaCore.Key.SELECT, KeyEvent.KEYCODE_SHIFT_RIGHT to MgbaCore.Key.SELECT,
        )
    }
}
