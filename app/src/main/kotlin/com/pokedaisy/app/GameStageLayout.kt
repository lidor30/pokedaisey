package com.pokedaisy.app

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import kotlin.math.min

/**
 * The top screen. While [bar] (the status bar) is shown, [game] is fitted at
 * the GBA's 3:2 in the space left under it, [bar] exactly as wide as the game
 * right above it, the pair centred; [hud] moves down under the bar. With the
 * bar gone it's a plain FrameLayout ([game] fills its area and letterboxes
 * itself), as before. [game]'s margins bound its area either way. With
 * [stretch] the game takes all the space under the bar (and stretches itself
 * to fill it, see [EmulatorView.stretch]). With [topAligned] (a phone held
 * upright, see [PortraitPanel]) the game (and bar) sit at the top of their area
 * at the game's own shape, whatever [stretch] says, leaving the rest below.
 */
class GameStageLayout(
    context: Context,
    private val game: View,
    private val bar: View,
    private val hud: View,
) : FrameLayout(context) {

    private var gameW = 0
    private var gameH = 0
    private var barH = 0

    private val barShown get() = bar.visibility != GONE

    /** The game screen's width / height: the GBA's 3:2, a Game Boy's 10:9 (set from the core). */
    var aspect = GBA_ASPECT
        set(v) {
            if (field == v || v <= 0f) return
            field = v
            requestLayout()
        }

    /** Fill the area instead of fitting [aspect] in it - see [Prefs.stretchGame]. */
    var stretch = false
        set(v) {
            if (field == v) return
            field = v
            requestLayout()
        }

    /** Game and bar at the top of their area, at the game's shape (see the class comment). */
    var topAligned = false
        set(v) {
            if (field == v) return
            field = v
            requestLayout()
        }

    /** How tall the game (and the bar over it, when shown) comes out at [width] wide, fitted. A bar not
     * attached to a window yet counts as 0: measuring a ComposeView then throws ("Cannot locate
     * windowRecomposer" - issue #31, PortraitPanel enabled from onResume); its first layout corrects it. */
    fun gameHeightFor(width: Int): Int {
        val b = if (!barShown || !bar.isAttachedToWindow) 0 else bar.measuredHeight.takeIf { it > 0 } ?: run {
            bar.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            bar.measuredHeight
        }
        return (width / aspect).toInt() + b
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (!barShown && !topAligned) return
        val lp = game.layoutParams as LayoutParams
        val areaW = measuredWidth - lp.leftMargin - lp.rightMargin
        val areaH = measuredHeight - lp.topMargin - lp.bottomMargin
        barH = if (barShown) {
            bar.measure(
                MeasureSpec.makeMeasureSpec(areaW, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(areaH, MeasureSpec.AT_MOST),
            )
            bar.measuredHeight
        } else 0
        if (stretch && !topAligned) {
            gameW = areaW.coerceAtLeast(0)
            gameH = (areaH - barH).coerceAtLeast(0)
        } else {
            gameW = min(areaW.toFloat(), (areaH - barH) * aspect).toInt().coerceAtLeast(0)
            gameH = (gameW / aspect).toInt()
        }
        game.measure(exactly(gameW), exactly(gameH))
        if (barShown) bar.measure(exactly(gameW), exactly(barH))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!barShown && !topAligned) return
        val lp = game.layoutParams as LayoutParams
        val areaW = width - lp.leftMargin - lp.rightMargin
        val areaH = height - lp.topMargin - lp.bottomMargin
        val x = lp.leftMargin + (areaW - gameW) / 2
        val y = lp.topMargin + if (topAligned) 0 else (areaH - barH - gameH) / 2
        if (barShown) bar.layout(x, y, x + gameW, y + barH)
        game.layout(x, y + barH, x + gameW, y + barH + gameH)
        hud.offsetTopAndBottom(y + barH)
    }

    private fun exactly(size: Int) = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)

    private companion object {
        const val GBA_ASPECT = 240f / 160f
    }
}
