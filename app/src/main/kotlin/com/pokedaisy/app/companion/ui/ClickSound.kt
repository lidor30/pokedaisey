package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Plays the running game's own menu click (SE_SELECT, rendered from the ROM -
 * see GameClickSound). Silent unless the host provides it, so the
 * top-screen Library / Settings, Paparazzi and ui-preview stay quiet.
 */
val LocalClickSound = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * [clickable] that plays the game's click on every tap - the companion's
 * buttons use this. Not for buttons that press the game's own buttons
 * (battle FIGHT / moves / BACK): the game plays its own sound for those.
 * The click follows the action, so the CLICK SOUND row itself clicks as it
 * turns on and stays quiet as it turns off.
 */
fun Modifier.soundClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val sound = LocalClickSound.current
    clickable(enabled = enabled) { onClick(); sound() }
}

/** [soundClickable] with the caller's [interactionSource] / [indication], like [clickable]'s overload. */
fun Modifier.soundClickable(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val sound = LocalClickSound.current
    clickable(interactionSource = interactionSource, indication = indication, enabled = enabled) { onClick(); sound() }
}
