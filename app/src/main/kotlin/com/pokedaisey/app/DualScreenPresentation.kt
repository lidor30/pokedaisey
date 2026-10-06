package com.pokedaisey.app

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pokedaisey.app.companion.TelemetryStore
import com.pokedaisey.app.companion.ui.CompanionScreen

/**
 * The bottom-screen companion, shown on the Thor's secondary [Display] via the
 * Android Presentation API. Hosts the reused android-companion Compose UI; a
 * Presentation is a Dialog, so it has to supply its own ViewTree lifecycle /
 * saved-state / view-model owners for Compose to run.
 */
class DualScreenPresentation(
    /** The game's activity (a Presentation's own [getContext] is a display context, not it). */
    private val host: Context,
    display: Display,
    private val store: TelemetryStore,
    private val slots: com.pokedaisey.app.companion.StateSlots? = null,
    private val settings: com.pokedaisey.app.companion.CompanionSettings? = null,
    private val battleInput: com.pokedaisey.app.companion.BattleInput? = null,
    private val back: com.pokedaisey.app.companion.ui.CompanionBack? = null,
    private val clickSound: () -> Unit = {},
) : Presentation(host, display) {

    private val owner = ComposeHostOwner()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        owner.create()
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                val snap by store.snapshot.collectAsState()
                CompanionScreen(snap, slots, settings, battleInput, back = back, clickSound = clickSound)
            }
        }
        setContentView(view)
    }

    /**
     * Keys belong to the game: once the bottom screen is touched it holds key
     * focus, and a Dialog's own BACK would dismiss the companion while the game
     * keeps running. Handing every key to the activity makes BACK (tap = the
     * companion's back, hold = the library) and the buttons work the same from
     * either screen.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        (host as? Activity)?.dispatchKeyEvent(event) ?: super.dispatchKeyEvent(event)

    override fun onStart() {
        super.onStart()
        owner.resume()
        android.util.Log.i("pokedaisey", "companion presentation shown on display ${display.displayId} (${display.name})")
    }

    override fun onStop() {
        super.onStop()
        owner.destroy()
    }
}
