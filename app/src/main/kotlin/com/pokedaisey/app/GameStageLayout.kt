package com.pokedaisey.app

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import kotlin.math.min

/**
 * The top screen. While [bar] (the status bar) is shown, [game] is fitted at
 * the GBA's 3:2 in the space left under it, [bar] exactly as wide as the game
 * right above it, the pair centred; [hud] moves down under the bar. With the
 * bar gone it's a plain FrameLayout ([game] fills its area and letterboxes
 * itself), as before. [game]'s margins bound its area either way.
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (!barShown) return
        val lp = game.layoutParams as LayoutParams
        val areaW = measuredWidth - lp.leftMargin - lp.rightMargin
        val areaH = measuredHeight - lp.topMargin - lp.bottomMargin
        bar.measure(
            MeasureSpec.makeMeasureSpec(areaW, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(areaH, MeasureSpec.AT_MOST),
        )
        barH = bar.measuredHeight
        gameW = min(areaW.toFloat(), (areaH - barH) * GBA_ASPECT).toInt().coerceAtLeast(0)
        gameH = (gameW / GBA_ASPECT).toInt()
        game.measure(exactly(gameW), exactly(gameH))
        bar.measure(exactly(gameW), exactly(barH))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!barShown) return
        val lp = game.layoutParams as LayoutParams
        val areaW = width - lp.leftMargin - lp.rightMargin
        val areaH = height - lp.topMargin - lp.bottomMargin
        val x = lp.leftMargin + (areaW - gameW) / 2
        val y = lp.topMargin + (areaH - barH - gameH) / 2
        bar.layout(x, y, x + gameW, y + barH)
        game.layout(x, y + barH, x + gameW, y + barH + gameH)
        hud.offsetTopAndBottom(y + barH)
    }

    private fun exactly(size: Int) = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)

    private companion object {
        const val GBA_ASPECT = 240f / 160f
    }
}
