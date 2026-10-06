package com.pokedaisey.app.companion.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The app's corners are pixel art, never smooth arcs: every "rounded" rect is
 * a staircase of whole GBA pixels ([gbaPixelPx] screen pixels each - the same
 * unit as [GbaTextMetrics.px]), like the game's own window corners.
 */

/** One GBA pixel in screen pixels at this density (matches [rememberGbaTextMetrics]). */
fun Density.gbaPixelPx(): Float = (20.dp.toPx() / 16f).roundToInt().coerceAtLeast(2).toFloat()

/**
 * A pixel-stepped rounded rect path. [radius] is coerced to half the smaller
 * side (so a huge radius makes a pixel pill / circle) and to whole [step]s.
 */
fun pixelRoundRectPath(size: Size, radius: Float, step: Float, offset: Offset = Offset.Zero): Path {
    val w = size.width
    val h = size.height
    val n = floor(radius.coerceAtMost(minOf(w, h) / 2f).coerceAtLeast(0f) / step).toInt()
    // Row j of a corner (from the outer edge) is inset insets[j] steps: a
    // quarter circle of radius n, sampled at each row's outer edge.
    val insets = IntArray(n) { j -> n - floor(sqrt((n * n - (n - j) * (n - j)).toFloat())).toInt() }
    fun inset(j: Int) = if (j < n) insets[j] * step else 0f
    val x0 = offset.x
    val y0 = offset.y
    return Path().apply {
        moveTo(x0 + inset(0), y0)
        lineTo(x0 + w - inset(0), y0)
        // Top-right, walking down.
        for (j in 0 until n) {
            lineTo(x0 + w - inset(j), y0 + (j + 1) * step)
            lineTo(x0 + w - inset(j + 1), y0 + (j + 1) * step)
        }
        lineTo(x0 + w, y0 + h - n * step)
        // Bottom-right, walking down.
        for (j in n - 1 downTo 0) {
            lineTo(x0 + w - inset(j), y0 + h - (j + 1) * step)
            lineTo(x0 + w - inset(j), y0 + h - j * step)
        }
        lineTo(x0 + inset(0), y0 + h)
        // Bottom-left, walking up.
        for (j in 0 until n) {
            lineTo(x0 + inset(j), y0 + h - (j + 1) * step)
            lineTo(x0 + inset(j + 1), y0 + h - (j + 1) * step)
        }
        lineTo(x0, y0 + n * step)
        // Top-left, walking up.
        for (j in n - 1 downTo 0) {
            lineTo(x0 + inset(j), y0 + (j + 1) * step)
            lineTo(x0 + inset(j), y0 + j * step)
        }
        close()
    }
}

/** [RoundedCornerShape]'s pixel-art stand-in. [radius] Dp.Infinity = a pill / circle. */
class PixelRoundedShape(private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val step = density.gbaPixelPx()
        val r = if (radius == Dp.Infinity) Float.MAX_VALUE else with(density) { radius.toPx() }
        return Outline.Generic(pixelRoundRectPath(size, r, step))
    }

    override fun equals(other: Any?) = other is PixelRoundedShape && other.radius == radius
    override fun hashCode() = radius.hashCode()
}

/** A pixel pill (or circle, when square) - replaces `CircleShape` / `RoundedCornerShape(50)`. */
val PixelPillShape = PixelRoundedShape(Dp.Infinity)

/** drawRoundRect, pixel-stepped. [radius] and [step] in px. */
fun DrawScope.drawPixelRoundRect(
    color: Color,
    topLeft: Offset = Offset.Zero,
    size: Size = this.size,
    radius: Float,
    step: Float = gbaPixelPx(),
) {
    if (size.width <= 0f || size.height <= 0f) return
    drawPath(pixelRoundRectPath(size, radius, step, topLeft), color)
}

/** A pixel-stepped rounded frame [width] px thick: the outer shape minus the inner one. */
fun DrawScope.drawPixelRoundFrame(
    color: Color,
    topLeft: Offset,
    size: Size,
    radius: Float,
    width: Float,
    step: Float = gbaPixelPx(),
) {
    if (size.width <= 2 * width || size.height <= 2 * width) {
        drawPixelRoundRect(color, topLeft, size, radius, step)
        return
    }
    val outer = pixelRoundRectPath(size, radius, step, topLeft)
    val inner = pixelRoundRectPath(
        Size(size.width - 2 * width, size.height - 2 * width),
        (radius - width).coerceAtLeast(0f), step,
        Offset(topLeft.x + width, topLeft.y + width),
    )
    drawPath(Path().apply { op(outer, inner, PathOperation.Difference) }, color)
}
