package com.pokedaisey.app.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt

/**
 * Pixel-art icons: one string per row, '1' = a filled pixel. Drawn as flat
 * blocks (no anti-aliasing) at a whole number of screen pixels per cell, so
 * they stay as crisp as the pixel font. Rows may be any (equal) length.
 */
object PixelIcons {
    /** A cog: 8 teeth (4 straight, 4 diagonal) around a round axle hole. */
    val gear = listOf(
        "0000001111000000",
        "0000001111000000",
        "0011001111001100",
        "0011111111111100",
        "0001111111111000",
        "0001110000111000",
        "1111100000011111",
        "1111100000011111",
        "1111100000011111",
        "1111100000011111",
        "0001110000111000",
        "0001111111111000",
        "0011111111111100",
        "0011001111001100",
        "0000001111000000",
        "0000001111000000",
    )

    val arrowUp = listOf(
        "0000000110000000",
        "0000001111000000",
        "0000011111100000",
        "0000111111110000",
        "0001111111111000",
        "0011111111111100",
        "0111111111111110",
        "1111111111111111",
        "0000011111100000",
        "0000011111100000",
        "0000011111100000",
        "0000011111100000",
        "0000011111100000",
        "0000011111100000",
        "0000011111100000",
        "0000011111100000",
    )

    val arrowDown = arrowUp.reversed()

    /** The DS-style "return" U-turn arrow. */
    val back = listOf(
        "0000100000000000",
        "0001100000000000",
        "0011100000000000",
        "0111111111110000",
        "1111111111111100",
        "0111111111111110",
        "0011100000001111",
        "0001100000000111",
        "0000100000000111",
        "0000000000000111",
        "0000000000000111",
        "0000000000001111",
        "0011111111111110",
        "0011111111111100",
        "0011111111110000",
    )

    val male = listOf(
        "00001111",
        "00000011",
        "00000101",
        "01111000",
        "10001000",
        "10001000",
        "10001000",
        "01110000",
    )

    val female = listOf(
        "01110",
        "10001",
        "10001",
        "10001",
        "01110",
        "00100",
        "11111",
        "00100",
    )

    /** A left-pointing cursor (Pixel Operator has no ◀ glyph; the fallback font's is taller than a line). */
    val cursorLeft = listOf(
        "0001",
        "0011",
        "0111",
        "1111",
        "0111",
        "0011",
        "0001",
    )

    /** An empty party slot, for [PixelArt]: K rim, G fill. */
    val disc = listOf(
        "....KKKK....",
        "..KKGGGGKK..",
        ".KGGGGGGGGK.",
        ".KGGGGGGGGK.",
        "KGGGGGGGGGGK",
        "KGGGGGGGGGGK",
        "KGGGGGGGGGGK",
        "KGGGGGGGGGGK",
        ".KGGGGGGGGK.",
        ".KGGGGGGGGK.",
        "..KKGGGGKK..",
        "....KKKK....",
    )

    /** "i" - the INFO button. */
    val info = listOf(
        "00111000",
        "00111000",
        "00111000",
        "00000000",
        "01111000",
        "01111000",
        "00111000",
        "00111000",
        "00111000",
        "00111000",
        "00111000",
        "01111100",
        "01111100",
    )

    /** A light bulb - the SUGGESTIONS button. */
    val bulb = listOf(
        "000011110000",
        "001111111100",
        "011111111110",
        "111111111111",
        "111111111111",
        "111111111111",
        "011111111110",
        "001111111100",
        "000111111000",
        "000111111000",
        "000000000000",
        "000111111000",
        "000000000000",
        "000011110000",
    )

    /** A Poké Ball, for [PixelArt]: K outline, R top, W bottom / button, H highlight. */
    val pokeBall = listOf(
        "....KKKK....",
        "..KKRRRRKK..",
        ".KRRRRRRHHK.",
        ".KRRRRRRRHK.",
        "KRRRRKKRRRRK",
        "KKKKKWWKKKKK",
        "KKKKKWWKKKKK",
        "KWWWWKKWWWWK",
        "KWWWWWWWWWWK",
        ".KWWWWWWWWK.",
        "..KKWWWWKK..",
        "....KKKK....",
    )
}

/** Draws a one-color [bitmap] ('1' = filled, see [PixelIcons]) centered in this composable's bounds. */
@Composable
fun PixelIcon(bitmap: List<String>, color: Color, modifier: Modifier = Modifier) {
    PixelArt(bitmap, mapOf('1' to color), modifier)
}

/** Draws [bitmap] with each character colored by [palette] (unmapped = transparent), centered. */
@Composable
fun PixelArt(bitmap: List<String>, palette: Map<Char, Color>, modifier: Modifier = Modifier) {
    val cols = bitmap.maxOf { it.length }
    Canvas(modifier = modifier) {
        // Whole screen pixels per cell, so every block is the same size.
        val cell = kotlin.math.floor(minOf(size.width / cols, size.height / bitmap.size)).coerceAtLeast(1f)
        val left = kotlin.math.floor((size.width - cell * cols) / 2f)
        val top = kotlin.math.floor((size.height - cell * bitmap.size) / 2f)
        bitmap.forEachIndexed { row, bits ->
            bits.forEachIndexed { col, bit ->
                val color = palette[bit]
                if (color != null) {
                    drawRect(color, Offset(left + col * cell, top + row * cell), Size(cell, cell))
                }
            }
        }
    }
}

/** The Poké Ball rocking like a capture shake (tip left, tip right, rest) -
 * the app's "working on it" animation. Turns in 10° steps, pivoting at its base. */
@Composable
fun RockingPokeBall(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rocking-ball")
    val phase by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "rocking-ball-phase",
    )
    val angle = when {
        phase < 0.15f -> -20f * (phase / 0.15f)
        phase < 0.45f -> -20f + 40f * ((phase - 0.15f) / 0.3f)
        phase < 0.6f -> 20f * (1f - (phase - 0.45f) / 0.15f)
        else -> 0f
    }
    PixelArt(
        PixelIcons.pokeBall,
        mapOf('K' to Color(0xFF202020), 'R' to Color(0xFFE83028), 'W' to Color.White, 'H' to Color(0xFFF8A8A0)),
        modifier.graphicsLayer {
            rotationZ = (angle / 10f).roundToInt() * 10f
            transformOrigin = TransformOrigin(0.5f, 1f)
        },
    )
}
