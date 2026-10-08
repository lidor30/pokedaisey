package com.pokedaisy.app

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.pokedaisy.app.companion.ui.CompanionBack
import com.pokedaisy.app.companion.ui.LocalClickSound
import com.pokedaisy.app.companion.ui.SidePanelCloseTab
import com.pokedaisy.app.companion.ui.SidePanelCompanion
import com.pokedaisy.app.companion.ui.SidePanelHandle
import com.pokedaisy.app.companion.ui.SidePanelOpenTab
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The companion on a device with one screen (no second display for
 * [DualScreenPresentation]): a panel on the right of the game's screen. A BACK
 * tap slides it in over the game; the tab on its edge locks it beside the game
 * instead (the game then fits in what's left - [game]'s right margin, which
 * [GameStageLayout] honours) and unlocks it again. Dragging that tab sideways
 * resizes the panel (the width is a fraction of the screen, snapped so the game
 * lands on a whole-number scale when it's close). Locked or not and the width
 * are remembered ([Prefs.sidePanelDocked], [Prefs.sidePanelWidth]); a locked
 * panel comes back with the game.
 *
 * BACK with the panel open is the companion's back ([back]); with nothing left
 * to go back from, it closes an unlocked panel (a locked one stays). Two more
 * tabs for touch: one on the screen's right edge, bottom, opens a closed panel;
 * one outside the panel's bottom-left corner closes it, locked or not. While
 * locked, both are see-through ([LOCKED_TAB_ALPHA]) - they sit over the letterbox
 * then. The touch pad stays on the game's side whenever the panel is open.
 */
class SidePanel(
    private val root: FrameLayout,
    private val game: View,
    private val touchControls: View,
    private val prefs: Prefs,
    private val back: CompanionBack,
    private val clickSound: () -> Unit,
    private val companion: @Composable () -> Unit,
) {
    private val context: Context get() = root.context

    /** No second screen: the companion lives here. */
    var enabled = false
        private set
    private var open = false
    private val docked = mutableStateOf(prefs.sidePanelDocked)
    private var fraction = prefs.sidePanelWidth.coerceIn(MIN_FRACTION, MAX_FRACTION)

    private var panel: View? = null
    private var handle: View? = null
    private var openTab: View? = null
    private var closeTab: View? = null
    private var lastRootWidth = 0

    private val rootWidth get() = root.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
    private val rootHeight get() = root.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
    private val panelWidth get() = (rootWidth * fraction).roundToInt()

    private val relayout = View.OnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
        if (r - l != lastRootWidth) {
            lastRootWidth = r - l
            root.post { apply() }
        }
    }

    /** On with no second screen, off when one shows up (the companion moves there). */
    fun setEnabled(on: Boolean) {
        if (on == enabled) return
        enabled = on
        if (on) {
            docked.value = prefs.sidePanelDocked
            open = docked.value
            val at = root.indexOfChild(touchControls) + 1
            panel = CompanionColors.track(ComposeView(context)).apply {
                setContent { SidePanelCompanion(companion) }
            }.also { root.addView(it, at, FrameLayout.LayoutParams(panelWidth, -1, Gravity.END)) }
            handle = DragFrame(context, vertical = false, ::onDragStart, ::onDrag, ::onDragEnd).apply {
                addView(CompanionColors.track(ComposeView(context)).apply {
                    setContent {
                        CompositionLocalProvider(LocalClickSound provides clickSound) {
                            SidePanelHandle(docked.value, ::toggleDock)
                        }
                    }
                })
            }.also {
                root.addView(it, at + 1, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                    topMargin = (HANDLE_TOP_DP * context.resources.displayMetrics.density).roundToInt()
                })
            }
            val bottom = (TAB_MARGIN_DP * context.resources.displayMetrics.density).roundToInt()
            closeTab = tabView { SidePanelCloseTab(::hide) }.also {
                root.addView(it, at + 2, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { bottomMargin = bottom })
            }
            openTab = tabView { SidePanelOpenTab(::show) }.also {
                root.addView(it, at + 3, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { bottomMargin = bottom })
            }
            root.addOnLayoutChangeListener(relayout)
        } else {
            root.removeOnLayoutChangeListener(relayout)
            for (v in listOfNotNull(panel, handle, closeTab, openTab)) root.removeView(v)
            panel = null
            handle = null
            closeTab = null
            openTab = null
            open = false
        }
        apply()
    }

    private fun tabView(content: @Composable () -> Unit) = CompanionColors.track(ComposeView(context)).apply {
        setContent { CompositionLocalProvider(LocalClickSound provides clickSound, content = content) }
    }

    /** A BACK tap; false = not ours (no side panel - the second screen's companion takes it). */
    fun onBack(): Boolean {
        if (!enabled) return false
        when {
            !open -> show()
            back.back() -> Unit
            !docked.value -> hide()
        }
        return true
    }

    private fun show() {
        if (open) return
        open = true
        apply()
        val w = panelWidth.toFloat()
        for (v in listOfNotNull(panel, handle, closeTab)) {
            v.translationX = w
            v.animate().translationX(0f).setDuration(SLIDE_MS).setUpdateListener { punchThrough() }.withEndAction(null).start()
        }
    }

    private fun hide() {
        if (!open) return
        open = false
        // The game and the touch pad take the whole screen back now; the panel slides out over them.
        setRightMargin(game, 0)
        setRightMargin(touchControls, 0)
        val w = panelWidth.toFloat() + (handle?.width ?: 0)
        for (v in listOfNotNull(panel, handle, closeTab)) {
            v.animate().translationX(w).setDuration(SLIDE_MS).setUpdateListener { punchThrough() }
                .withEndAction { if (!open) apply() }.start()
        }
    }

    /**
     * The game is a SurfaceView: the window leaves a hole over it, minus the
     * views drawn on top - measured where they were at the last layout. A
     * translation alone doesn't lay out again, so a sliding panel (and its tab)
     * stayed hidden over the game where it hadn't been yet.
     */
    private fun punchThrough() {
        panel?.let { root.requestTransparentRegion(it) }
    }

    private fun toggleDock() {
        docked.value = !docked.value
        prefs.sidePanelDocked = docked.value
        apply()
    }

    /** Lays everything out for the current state. */
    private fun apply() {
        val w = panelWidth
        val shown = enabled && open
        panel?.apply {
            visibility = if (shown) View.VISIBLE else View.GONE
            translationX = 0f
            layoutParams = layoutParams.apply { width = w }
        }
        handle?.apply {
            visibility = if (shown) View.VISIBLE else View.GONE
            translationX = 0f
            setRightMargin(this, w)
        }
        closeTab?.apply {
            visibility = if (shown) View.VISIBLE else View.GONE
            translationX = 0f
            alpha = if (docked.value) LOCKED_TAB_ALPHA else 1f
            setRightMargin(this, w)
        }
        openTab?.apply {
            visibility = if (enabled && !open) View.VISIBLE else View.GONE
            alpha = if (docked.value) LOCKED_TAB_ALPHA else 1f
        }
        setRightMargin(game, if (shown && docked.value) w else 0)
        setRightMargin(touchControls, if (shown) w else 0)
    }

    private fun setRightMargin(v: View, margin: Int) {
        val lp = v.layoutParams as FrameLayout.LayoutParams
        if (lp.rightMargin == margin) return
        lp.rightMargin = margin
        v.layoutParams = lp
    }

    // --- resizing by the tab -------------------------------------------------

    private var dragStartWidth = 0

    private fun onDragStart() {
        dragStartWidth = panelWidth
    }

    private fun onDrag(dx: Float) {
        fraction = ((dragStartWidth - dx) / rootWidth).coerceIn(MIN_FRACTION, MAX_FRACTION)
        apply()
    }

    private fun onDragEnd() {
        // A game area within a few pixels of a whole-number scale snaps to it (half a 1080p
        // screen is exactly 4x already).
        val area = rootWidth - panelWidth
        val n = (area / GBA_W.toFloat()).roundToInt()
        if (n >= 1 && GBA_H * n <= rootHeight && abs(GBA_W * n - area) <= SNAP_PX) {
            fraction = ((rootWidth - GBA_W * n).toFloat() / rootWidth).coerceIn(MIN_FRACTION, MAX_FRACTION)
        }
        prefs.sidePanelWidth = fraction
        apply()
    }

    private companion object {
        const val MIN_FRACTION = 0.3f
        const val MAX_FRACTION = 0.75f
        const val HANDLE_TOP_DP = 12
        const val TAB_MARGIN_DP = 12
        /** The open / close tabs while the panel is locked: there, but out of the way. */
        const val LOCKED_TAB_ALPHA = 0.35f
        const val SLIDE_MS = 160L
        const val GBA_W = 240
        const val GBA_H = 160
        const val SNAP_PX = 48
    }
}
