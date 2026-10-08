package com.pokedaisy.app

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Hands a drag along one axis ([vertical] or sideways) on its child to
 * [onDrag], in screen coordinates (the child moves with the finger, so its own
 * would chase themselves). A tap still reaches the child. Used by the side
 * panel's lock tab and the portrait companion's grip to resize them.
 */
@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class DragFrame(
    context: Context,
    private val vertical: Boolean,
    private val onStart: () -> Unit,
    /** Distance from where the drag started, along the axis. */
    private val onDrag: (Float) -> Unit,
    private val onEnd: () -> Unit,
) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var down = 0f
    private var dragging = false

    private fun pos(e: MotionEvent) = if (vertical) e.rawY else e.rawX

    private fun startIfMoved(e: MotionEvent): Boolean {
        if (!dragging && abs(pos(e) - down) > slop) {
            dragging = true
            onStart()
        }
        return dragging
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { down = pos(e); dragging = false }
            MotionEvent.ACTION_MOVE -> return startIfMoved(e)
        }
        return dragging
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { down = pos(e); dragging = false }
            MotionEvent.ACTION_MOVE -> if (startIfMoved(e)) onDrag(pos(e) - down)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                onEnd()
            }
        }
        return true
    }
}
