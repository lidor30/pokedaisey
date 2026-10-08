package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.pokedaisy.app.companion.ui.theme.pixelFontFamily
import kotlin.math.roundToInt

/**
 * Frames/spacing are sized in "GBA pixels" ([u], a whole number of physical
 * pixels so 1-2u lines stay crisp), following the real bag screen's 2px frame
 * lines. Text is a bit larger than 1:1 with the frame (the game's 16px em
 * would be ~20dp here, which read too small on the handheld): ~1.25x that,
 * rounded so each font pixel is still a whole number of screen pixels (the
 * pixel font's strokes go uneven otherwise); [fontPixel] is one of the font's own pixels (for the GBA drop
 * shadow and the drawn cursor) and [lineHeight] one line of text. Shared by
 * the Items tab (the bag screen) and the Settings tab (the OPTION screen).
 */
class GbaTextMetrics(
    val px: Int,
    val u: Dp,
    val fontSize: TextUnit,
    val fontPixel: Float,
    val lineHeight: Dp,
    val rowHeight: Dp,
)

/** [textScale]: text size relative to the frame's GBA pixel - 1.25 for the
 * main lists, 1 for denser secondary text (timestamps, paths, captions).
 * [wholePixels] false allows sizes between the whole-pixel steps (at the cost
 * of slightly uneven strokes) - the steps are 33% apart at typical densities. */
@Composable
fun rememberGbaTextMetrics(textScale: Float = 1.25f, wholePixels: Boolean = true, gameScaled: Boolean = true): GbaTextMetrics {
    val density = LocalDensity.current
    // A game font with smaller letters than Pixel Operator's (Gen 1's 7px caps vs 9) is set larger -
    // except where a layout is sized to the line ([gameScaled] false: the tab chips, battle INFO).
    val gameScale = if (gameScaled) com.pokedaisy.app.companion.ui.theme.LocalGameTextScale.current else 1f
    return remember(density, textScale, wholePixels, gameScale) {
        with(density) {
            val k = (20.dp.toPx() / 16f).roundToInt().coerceAtLeast(2)
            val scale = textScale * gameScale
            // ~textScale x, normally rounded to a whole number of screen pixels per font pixel.
            val fontPx = if (wholePixels) 16f * (k * scale).roundToInt().coerceAtLeast(1)
            else (16f * k * scale).roundToInt().toFloat()
            GbaTextMetrics(k, k.toDp(), fontPx.toSp(), fontPx / 16f, fontPx.toDp(), (fontPx * 1.25f).toDp())
        }
    }
}

fun gbaTextStyle(font: FontFamily, m: GbaTextMetrics, scaleX: Float = 1f) = TextStyle(
    fontFamily = font,
    fontSize = m.fontSize,
    textGeometricTransform = if (scaleX == 1f) null else TextGeometricTransform(scaleX = scaleX),
    lineHeight = m.fontSize,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/**
 * GBA-style text: the game draws its shadow one pixel right, down AND
 * diagonally (a thick bottom-right edge), which a single Compose Shadow can't
 * do - so three offset copies sit under the text.
 */
@Composable
fun GbaText(
    text: String,
    color: Color,
    shadow: Color,
    m: GbaTextMetrics,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    /** Drawn twice, half a font pixel apart ([boldOffsetPx]): heavier strokes for a thin game font. */
    bold: Boolean = false,
) {
    val scaleX = gameFontScaleX(m)
    val style = gbaTextStyle(pixelFontFamily(), m, scaleX)
    val k = m.fontPixel.roundToInt()
    val b = if (bold) boldOffsetPx(m.fontPixel * scaleX) else 0
    Box(modifier) {
        if (shadow.alpha > 0f) for ((dx, dy) in listOf(k to 0, 0 to k, k to k)) {
            Text(
                text, color = shadow, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset { IntOffset(dx + b, dy) },
            )
        }
        if (b > 0) {
            Text(
                text, color = color, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset { IntOffset(b, 0) },
            )
        }
        Text(text, color = color, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** How far right [GbaText]'s bold copy goes: half a font pixel ([pixelWidthPx] screen pixels wide), at least one. */
fun boldOffsetPx(pixelWidthPx: Float): Int = (pixelWidthPx * 0.5f).roundToInt().coerceAtLeast(1)

/** Labels on tabs and buttons are bold in a thin game font (Gen 1's 1px strokes). */
@Composable
fun gameBoldLabels(): Boolean = com.pokedaisy.app.companion.ui.theme.LocalGameFont.current != null

/**
 * [LocalGameFontWidth] at [m]'s size: on whole-pixel text, rounded so a font pixel is a whole
 * number of screen pixels wide too (4 tall -> 3 wide, 5 -> 4, 3 -> 2).
 */
@Composable
fun gameFontScaleX(m: GbaTextMetrics): Float = gameFontScaleX(m.fontPixel)

/** [gameFontScaleX] for text whose font pixel is [p] screen pixels tall. */
@Composable
fun gameFontScaleX(p: Float): Float {
    val w = com.pokedaisy.app.companion.ui.theme.LocalGameFontWidth.current
    if (w >= 1f) return 1f
    val whole = p.roundToInt()
    if (whole < 1 || kotlin.math.abs(p - whole) > 0.01f) return w
    return (whole * w).roundToInt().coerceAtLeast(1) / whole.toFloat()
}

fun List<Pair<Color, Int>>.inPx(px: Float) = map { (color, u) -> color to u * px }

/**
 * Nested pixel-cornered rects, outermost layer first, then [fill]. [openRight] runs
 * the shape past the right edge and clips it, dropping the right-hand frame
 * (a folder tab that continues into the window beside it); [openBottom] and
 * [openTop] do the same downwards / upwards.
 */
fun DrawScope.drawLayeredBox(
    layers: List<Pair<Color, Float>>,
    fill: Color,
    radius: Float,
    openRight: Boolean = false,
    openBottom: Boolean = false,
    openTop: Boolean = false,
) {
    val run = radius + layers.sumOf { it.second.toDouble() }.toFloat() + 1f
    val extraW = if (openRight) run else 0f
    val top = if (openTop) run else 0f
    val extraH = (if (openBottom) run else 0f) + top
    clipRect {
        var inset = 0f
        var r = radius
        for ((color, width) in layers) {
            drawPixelRoundRect(
                color, Offset(inset, inset - top), Size(size.width + extraW - 2 * inset, size.height + extraH - 2 * inset),
                r.coerceAtLeast(0f),
            )
            inset += width
            r -= width
        }
        drawPixelRoundRect(
            fill, Offset(inset, inset - top), Size(size.width + extraW - 2 * inset, size.height + extraH - 2 * inset),
            r.coerceAtLeast(0f),
        )
    }
}
