package com.pokedaisy.app.companion

import com.pokedaisy.app.companion.data.ItemUseOutcome

/** Using a bag item from the ITEMS tab (FieldItems.kt); null where the game has no such support. */
interface ItemUse {
    /** Whether [itemId] gets a USE button in this game. */
    fun canUse(itemId: Int): Boolean

    /** Uses one [itemId] in the game; [done] runs on the main thread with what happened. */
    fun use(itemId: Int, done: (ItemUseOutcome) -> Unit)
}
