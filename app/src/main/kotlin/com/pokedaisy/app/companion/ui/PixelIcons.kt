package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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

    /** A trophy cup, for [PixelArt]: '1' the cup, '2' its shading and base. A badge not loaded (yet). */
    val trophy = listOf(
        "111111111111",
        "1.11111112.1",
        "1.11111112.1",
        ".1111111121.",
        "..11111112..",
        "...111112...",
        ".....12.....",
        ".....12.....",
        "...222222...",
        "...222222...",
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

    /** [cursorLeft] mirrored: a row that opens a page. */
    val cursorRight = cursorLeft.map { it.reversed() }

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

    /** A steaming coffee cup on a saucer, for [PixelArt]: K outline, B coffee, C cup, S steam. */
    val coffee = listOf(
        "....S..S........",
        "...S..S.........",
        "....S..S........",
        "...S..S.........",
        "................",
        ".KKKKKKKKKKKK...",
        ".KBBBBBBBBBBKKK.",
        ".KCCCCCCCCCCK..K",
        ".KCCCCCCCCCCK..K",
        ".KCCCCCCCCCCK..K",
        ".KCCCCCCCCCCKKK.",
        ".KCCCCCCCCCCK...",
        "..KCCCCCCCCK....",
        "...KKKKKKKK.....",
        "KKKKKKKKKKKKKK..",
        "................",
    )

    /** GitHub's mark: a disc with the cat cut out of it ('1' = the disc). */
    val github = listOf(
        "0000011111100000",
        "0001111111111000",
        "0011111111111100",
        "0111011111101110",
        "0111001111001110",
        "1111000000001111",
        "1110000000000111",
        "1110000000000111",
        "1110000000000111",
        "1110000000000111",
        "1111000000001111",
        "0101110000111110",
        "0110110000111110",
        "0011000000111100",
        "0001110000111000",
        "0000010000100000",
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

    /** A shut padlock: the side panel locked beside the game (see SidePanelHandle). Draw it with [ShadowedPixelIcon]. */
    val lockClosed = listOf(
        "................",
        "......1111......",
        ".....111111.....",
        "....111..111....",
        "....11....11....",
        "....11....11....",
        "....11....11....",
        "....11....11....",
    ) + lockBody()

    /** [lockClosed] with the shackle up and open: the side panel over the game. */
    val lockOpen = listOf(
        "......1111......",
        ".....111111.....",
        "....111..111....",
        "....11....11....",
        "....11..........",
        "....11..........",
        "....11..........",
        "....11..........",
    ) + lockBody()

    private fun lockBody() = listOf(
        "..111111111111..",
        ".11111111111111.",
        ".111111....1111.",
        ".111111....1111.",
        ".1111111..11111.",
        ".1111111..11111.",
        ".11111111111111.",
        "..111111111111..",
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

/**
 * A one-colour [bitmap] with the GBA text's shadow (one cell right, down and
 * diagonally, like [GbaText]), one cell per GBA pixel of [m].
 */
@Composable
fun ShadowedPixelIcon(bitmap: List<String>, color: Color, shadow: Color, m: GbaTextMetrics, modifier: Modifier = Modifier) {
    val h = bitmap.size + 1
    val w = bitmap.maxOf { it.length } + 1
    fun on(x: Int, y: Int) = bitmap.getOrNull(y)?.getOrNull(x) == '1'
    val shadowed = List(h) { y ->
        String(CharArray(w) { x ->
            when {
                on(x, y) -> 'F'
                on(x - 1, y) || on(x, y - 1) || on(x - 1, y - 1) -> 'S'
                else -> '.'
            }
        })
    }
    PixelArt(shadowed, mapOf('F' to color, 'S' to shadow), modifier.size(m.u * w, m.u * h))
}

/** [PixelIcons.trophy] in gold (the badge colours), e.g. beside RetroAchievements' name. */
@Composable
fun GoldTrophy(modifier: Modifier = Modifier) =
    PixelArt(PixelIcons.trophy, mapOf('1' to Color(0xFFF8C800), '2' to Color(0xFFB07000)), modifier.fillMaxSize())

/** [PixelIcons.coffee] in Buy Me a Coffee's yellow, beside its link. */
@Composable
fun CoffeeCup(modifier: Modifier = Modifier) = PixelArt(
    PixelIcons.coffee,
    mapOf('K' to Color(0xFF202020), 'B' to Color(0xFF784020), 'C' to Color(0xFFFFDD00), 'S' to Color(0xFF909090)),
    modifier.fillMaxSize(),
)

/** [PixelIcons.github] in GitHub's near-black, beside the repo link. */
@Composable
fun GitHubMark(modifier: Modifier = Modifier) = PixelIcon(PixelIcons.github, Color(0xFF202020), modifier.fillMaxSize())

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
