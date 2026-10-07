package com.pokedaisy.app.companion

/**
 * Bridge for the bottom-screen savestate panel: it lists slots (backed by
 * SaveStates on disk) and asks the emulator to save/load. Implemented by
 * PokeDaisyActivity so the companion module stays independent of the emulator.
 */
interface StateSlots {
    data class Slot(
        val index: Int,
        val present: Boolean,
        val savedAtMillis: Long,
        val thumbPath: String?,
    )

    /** All slots, 0..9, in order. */
    fun list(): List<Slot>

    /** The slot the hardware save/load hotkeys currently target. */
    val currentIndex: Int

    fun requestSave(index: Int)
    fun requestLoad(index: Int)

    /** Undo the most recent save (restore the current slot's previous contents). */
    fun requestUndoSave()

    /** Undo the most recent load (return to the state from just before it). */
    fun requestUndoLoad()
}
