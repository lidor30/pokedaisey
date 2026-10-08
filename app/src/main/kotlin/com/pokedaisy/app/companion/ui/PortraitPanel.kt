package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pokedaisy.app.companion.i18n.tk
import com.pokedaisy.app.companion.i18n.tr
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A phone held upright (one screen, taller than wide): the game across the top,
 * the companion under it ([com.pokedaisy.app.PortraitPanel]), as wide as the
 * screen and as tall as the player picks - a height / width ratio, so it keeps
 * its shape across phones - from [MIN_RATIO] to everything under the game. It
 * sits along the screen's bottom, or right under the game ([arrange]'s
 * `underGame`) so the touch pad gets the screen's bottom instead. What's left
 * is the touch pad's when it's shown and fits there ([padInGap]); else black.
 */
object PortraitLayout {
    /** The Thor's bottom screen (1240x1080), the shape the companion is made for. */
    const val DEFAULT_RATIO = 1080f / 1240f

    /** Shorter than the Thor's top screen's 16:9 squeezes the tabs. */
    const val MIN_RATIO = 0.6f

    /** A drag that ends this close to the default or the largest size lands on it. */
    const val SNAP_PX = 48

    /** The touch pad goes in the gap beside the companion once that's at least this much of the width. */
    private const val PAD_GAP_RATIO = 0.45f

    /**
     * The companion's height on a [w]x[h] screen whose game (status bar included)
     * is [gameH] tall, with [reserve] px kept free under it (the grip, when it
     * hangs below the companion).
     */
    fun companionHeight(w: Int, h: Int, gameH: Int, ratio: Float, reserve: Int = 0): Int {
        val lo = minOf((w * MIN_RATIO).roundToInt(), h / 2)
        return (w * ratio).roundToInt().coerceIn(lo, maxOf(lo, h - gameH - reserve))
    }

    /** The largest companion: everything under the game (but [reserve]), as a ratio. */
    fun maxRatio(w: Int, h: Int, gameH: Int, reserve: Int = 0): Float = maxOf(MIN_RATIO, (h - gameH - reserve).toFloat() / w)

    /** Where a drag to [ratio] settles: on the default or the largest size when it's within [SNAP_PX] of it. */
    fun snap(w: Int, h: Int, gameH: Int, ratio: Float, reserve: Int = 0): Float {
        val max = maxRatio(w, h, gameH, reserve)
        return when {
            abs((ratio - DEFAULT_RATIO) * w) <= SNAP_PX -> DEFAULT_RATIO
            (max - ratio) * w <= SNAP_PX -> max
            else -> ratio.coerceIn(MIN_RATIO, max)
        }
    }

    /** A tap on the grip: smallest, the Thor's shape, everything under the game, round again. */
    fun next(w: Int, h: Int, gameH: Int, ratio: Float, reserve: Int = 0): Float {
        val max = maxRatio(w, h, gameH, reserve)
        val stops = listOf(MIN_RATIO, DEFAULT_RATIO, max).filter { it <= max }.distinct().sorted()
        return stops.firstOrNull { it > ratio + 0.01f } ?: stops.first()
    }

    /** The touch pad fits in a [gap]-tall strip on a [w]-wide screen. */
    fun padInGap(w: Int, gap: Int): Boolean = gap >= w * PAD_GAP_RATIO

    /** Where the grip goes: on the companion's top edge, inside its top (no room above), or under it. */
    enum class Grip { ABOVE, INSIDE, BELOW }

    /** Where everything goes, in px from the screen's top. */
    data class Arrangement(
        /** The game (with its status bar) ends here; the game's area ends at [gameAreaBottom]. */
        val gameBottom: Int,
        val gameAreaBottom: Int,
        val companionTop: Int,
        val companionHeight: Int,
        val grip: Grip,
        val gripTop: Int,
        /** The touch pad's strip. */
        val padTop: Int,
        val padBottom: Int,
    ) {
        /** Kept clear at the companion's top for an [Grip.INSIDE] grip. */
        fun topInset(gripH: Int) = if (grip == Grip.INSIDE) gripH else 0
    }

    /**
     * Lays the screen out. Along the bottom, the grip sits on the companion's top
     * edge, or inside its top once there's no room above (it never covers the
     * game); dragging it up makes the companion taller. Right under the game
     * ([underGame]), the grip hangs below the companion (room kept for it) and
     * dragging it down makes it taller.
     */
    fun arrange(w: Int, h: Int, gameH: Int, gripH: Int, ratio: Float, underGame: Boolean): Arrangement {
        val reserve = if (underGame) gripH else 0
        val c = companionHeight(w, h, gameH, ratio, reserve)
        val gameBottom = minOf(gameH, h - c - reserve)
        if (underGame) {
            val gripTop = gameBottom + c
            val below = gripTop + gripH
            val inGap = padInGap(w, h - below)
            return Arrangement(
                gameBottom, gameBottom, gameBottom, c, Grip.BELOW, gripTop,
                if (inGap) below else 0, if (inGap) h else gameBottom,
            )
        }
        val companionTop = h - c
        val inside = companionTop - gameBottom < gripH
        val gripTop = if (inside) companionTop else companionTop - gripH
        val inGap = padInGap(w, gripTop - gameBottom)
        return Arrangement(
            gameBottom, gripTop, companionTop, c, if (inside) Grip.INSIDE else Grip.ABOVE, gripTop,
            if (inGap) gameBottom else 0, gripTop,
        )
    }
}

/** SETTINGS > COMPANION's value with a phone upright: where the companion sits. */
fun portraitPlaceLabel(underGame: Boolean) = if (underGame) tk("UNDER GAME") else tk("AT BOTTOM")

/**
 * The px [CompanionScreen] keeps clear at its top (inside its backdrop): where the
 * portrait companion's grip sits when there's no room for it above.
 */
val LocalCompanionTopInset = compositionLocalOf { 0 }

/**
 * The grip on the portrait companion: a white title-window tab with arrows up and
 * down, running into the companion from above ([PortraitLayout.Grip.ABOVE]) or
 * below ([PortraitLayout.Grip.BELOW]), or whole inside its top. Dragging it
 * (handled by the view around it, in screen coordinates) resizes the companion; a
 * tap steps through the sizes ([PortraitLayout.next]).
 */
@Composable
fun PortraitGrip(grip: PortraitLayout.Grip = PortraitLayout.Grip.ABOVE, onClick: () -> Unit) {
    val m = rememberGbaTextMetrics()
    val u = m.u
    val noRipple = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(u * 48, u * 24)
            .drawBehind {
                drawLayeredBox(
                    OptionColors.titleLayers.inPx(u.toPx()), OptionColors.titleFill, radius = 3 * u.toPx(),
                    openBottom = grip == PortraitLayout.Grip.ABOVE, openTop = grip == PortraitLayout.Grip.BELOW,
                )
            }
            .semantics { contentDescription = tr("Resize the companion") }
            .soundClickable(interactionSource = noRipple, indication = null, onClick = onClick),
    ) {
        ShadowedPixelIcon(PixelIcons.resizeVertical, OptionColors.label, OptionColors.labelShadow, m)
    }
}
