package com.pokedaisy.app.companion.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * Dark mode for palettes sampled from the games (the bags' windows): each colour keeps its hue, so a
 * game's bag still reads as its own - FireRed's cream turns a dark warm brown, Emerald's blue a deep
 * navy - while its lightness is mapped into a dark range. A window's layers keep their order (the dark
 * outline outside, lighter bands in), the way the OPTION windows' dark versions are built.
 */

/** A window surface (fill, frame band, icon box): lightness into 0.09..0.24, saturation well down (a cream
 * at full strength turned olive). */
fun Color.nightSurface(): Color = remap { l -> 0.09f + 0.15f * l to 0.3f }

/** Text drawn on a [nightSurface]: dark ink turns light (0.93 for black), keeping a hint of its hue. */
fun Color.nightInk(): Color = remap { l -> 0.93f - 0.22f * l to 0.5f }

/** A text shadow: light mode's sits between the text and the window; here it does too, a step over the window. */
fun Color.nightShadow(): Color = remap { l -> 0.16f + 0.15f * l to 0.3f }

private inline fun Color.remap(f: (Float) -> Pair<Float, Float>): Color {
    if (alpha == 0f) return this
    val (h, s, l) = hsl(red, green, blue)
    val (l2, sk) = f(l)
    // Yellows darken to olive: lean them to amber (a dark cream reads as warm brown, like the bag's tabs).
    val yellow = h in 45f..75f
    val (r, g, b) = rgb(if (yellow) h - 18f else h, s * sk * (if (yellow) 0.7f else 1f), l2.coerceIn(0f, 1f))
    return Color(r, g, b, alpha)
}

private fun hsl(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2f
    val d = mx - mn
    if (d == 0f) return Triple(0f, 0f, l)
    val s = d / (1f - abs(2f * l - 1f))
    val h = when (mx) {
        r -> ((g - b) / d).mod(6f)
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    return Triple(h, s, l)
}

private fun rgb(h: Float, s: Float, l: Float): Triple<Float, Float, Float> {
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f).mod(2f) - 1f))
    val m = l - c / 2f
    val (r, g, b) = when ((h / 60f).toInt().coerceIn(0, 5)) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Triple((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f))
}
