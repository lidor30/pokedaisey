package com.pokedaisey.app

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Minimal ViewTree owner (lifecycle / saved-state / view-model) for hosting a
 * `ComposeView` outside a `ComponentActivity` — a `Presentation` (Dialog) or a
 * plain `android.app.Activity` neither supplies these automatically the way
 * `ComponentActivity` does. Shared by [DualScreenPresentation] (the real
 * bottom-screen companion) and [PokeDaiseyActivity]'s debug-only mirror of it
 * onto the main display (see PLAN.md's testing-support note — the Thor's
 * second screen can't be `screencap`'d at the hardware level, the main
 * display can).
 */
class ComposeHostOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun create() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
