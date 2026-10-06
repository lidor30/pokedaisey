package com.pokedaisey.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Semi-transparent on-screen GBA controls: D-pad + A/B + Start/Select + L/R.
 * Multi-touch; each active pointer contributes to a key bitmask. Meant to be
 * shown only when no gamepad is connected (see PokeDaiseyActivity).
 */
class TouchControlsView(context: Context) : View(context) {

    init {
        isClickable = true
    }

    /** Called on the UI thread whenever the pressed-key bitmask changes. */
    var onMask: ((Int) -> Unit)? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val fillHot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0x88FFFFFF.toInt(); strokeWidth = dp(2f)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt(); textAlign = Paint.Align.CENTER; textSize = dp(16f)
    }

    // Geometry, set in onSizeChanged.
    private var dpadCx = 0f; private var dpadCy = 0f; private var dpadR = 0f
    private val aBtn = RectF(); private val bBtn = RectF()
    private val startBtn = RectF(); private val selectBtn = RectF()
    private val lBtn = RectF(); private val rBtn = RectF()
    private var btnR = 0f

    private var mask = 0

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val unit = min(w, h)
        dpadR = unit * 0.20f
        btnR = unit * 0.11f
        val pad = dp(18f)

        dpadCx = pad + dpadR
        dpadCy = h - pad - dpadR

        val bx = w - pad - btnR
        aBtn.set(bx - btnR, h - pad - btnR * 3f, bx + btnR, h - pad - btnR)
        bBtn.set(bx - btnR * 3f, h - pad - btnR * 2.4f, bx - btnR, h - pad - btnR * 0.4f)

        val cx = w / 2f
        startBtn.set(cx + dp(6f), h - pad - dp(34f), cx + dp(6f) + dp(78f), h - pad)
        selectBtn.set(cx - dp(6f) - dp(78f), h - pad - dp(34f), cx - dp(6f), h - pad)

        lBtn.set(pad, pad, pad + dp(90f), pad + dp(40f))
        rBtn.set(w - pad - dp(90f), pad, w - pad, pad + dp(40f))
    }

    override fun onDraw(c: Canvas) {
        // D-pad
        c.drawCircle(dpadCx, dpadCy, dpadR, fill)
        c.drawCircle(dpadCx, dpadCy, dpadR, stroke)
        val arm = dpadR * 0.42f
        fun dPaint(bit: Int) = if (mask and bit != 0) fillHot else fill
        c.drawRect(dpadCx - arm, dpadCy - dpadR, dpadCx + arm, dpadCy - arm, dPaint(MgbaCore.Key.UP))
        c.drawRect(dpadCx - arm, dpadCy + arm, dpadCx + arm, dpadCy + dpadR, dPaint(MgbaCore.Key.DOWN))
        c.drawRect(dpadCx - dpadR, dpadCy - arm, dpadCx - arm, dpadCy + arm, dPaint(MgbaCore.Key.LEFT))
        c.drawRect(dpadCx + arm, dpadCy - arm, dpadCx + dpadR, dpadCy + arm, dPaint(MgbaCore.Key.RIGHT))

        circle(c, aBtn, "A", MgbaCore.Key.A)
        circle(c, bBtn, "B", MgbaCore.Key.B)
        pill(c, startBtn, "START", MgbaCore.Key.START)
        pill(c, selectBtn, "SELECT", MgbaCore.Key.SELECT)
        pill(c, lBtn, "L", MgbaCore.Key.L)
        pill(c, rBtn, "R", MgbaCore.Key.R)
    }

    private fun circle(c: Canvas, r: RectF, text: String, bit: Int) {
        c.drawCircle(r.centerX(), r.centerY(), btnR, if (mask and bit != 0) fillHot else fill)
        c.drawCircle(r.centerX(), r.centerY(), btnR, stroke)
        c.drawText(text, r.centerX(), r.centerY() + label.textSize / 3f, label)
    }

    private fun pill(c: Canvas, r: RectF, text: String, bit: Int) {
        val rad = r.height() / 2f
        c.drawRoundRect(r, rad, rad, if (mask and bit != 0) fillHot else fill)
        c.drawRoundRect(r, rad, rad, stroke)
        c.drawText(text, r.centerX(), r.centerY() + label.textSize / 3f, label)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        var m = 0
        for (i in 0 until e.pointerCount) {
            if (e.actionMasked == MotionEvent.ACTION_POINTER_UP && i == e.actionIndex) continue
            if (e.actionMasked == MotionEvent.ACTION_UP) continue
            val x = e.getX(i); val y = e.getY(i)
            m = m or hitTest(x, y)
        }
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) m = 0
        if (m != mask) {
            mask = m
            onMask?.invoke(m)
            invalidate()
        }
        return true
    }

    private fun hitTest(x: Float, y: Float): Int {
        var bits = 0
        // D-pad: direction from centre, with a small dead zone.
        val dx = x - dpadCx; val dy = y - dpadCy
        val d = hypot(dx, dy)
        if (d <= dpadR * 1.35f && d > dpadR * 0.22f) {
            if (dy < -dpadR * 0.22f) bits = bits or MgbaCore.Key.UP
            if (dy > dpadR * 0.22f) bits = bits or MgbaCore.Key.DOWN
            if (dx < -dpadR * 0.22f) bits = bits or MgbaCore.Key.LEFT
            if (dx > dpadR * 0.22f) bits = bits or MgbaCore.Key.RIGHT
        }
        if (inCircle(x, y, aBtn)) bits = bits or MgbaCore.Key.A
        if (inCircle(x, y, bBtn)) bits = bits or MgbaCore.Key.B
        if (startBtn.contains(x, y)) bits = bits or MgbaCore.Key.START
        if (selectBtn.contains(x, y)) bits = bits or MgbaCore.Key.SELECT
        if (lBtn.contains(x, y)) bits = bits or MgbaCore.Key.L
        if (rBtn.contains(x, y)) bits = bits or MgbaCore.Key.R
        return bits
    }

    private fun inCircle(x: Float, y: Float, r: RectF) =
        hypot(x - r.centerX(), y - r.centerY()) <= btnR * 1.2f

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
