package com.pokedaisey.app.companion.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * A flag that flips every [halfPeriodMs] (false first) - the game's two-frame
 * sprite animations (party icons, the map cursor). An infinite transition, so
 * screenshot tests still go idle; read through derivedStateOf, so whatever
 * draws from it redraws only when it flips, not every frame (a 60 Hz frame
 * counter read in a Canvas redrew the whole companion screen 60 times a second).
 */
@Composable
fun rememberBlink(halfPeriodMs: Int, enabled: Boolean = true, label: String = "blink"): State<Boolean> {
    if (!enabled || halfPeriodMs <= 0) return remember { mutableStateOf(false) }
    val phase = rememberInfiniteTransition(label = label).animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(halfPeriodMs * 2, easing = LinearEasing)),
        label = label,
    )
    return remember(phase) { derivedStateOf { phase.value >= 1f } }
}

/** GBA frames (60 per second) to milliseconds. */
fun gbaFramesMs(frames: Int): Int = frames * 1000 / 60
