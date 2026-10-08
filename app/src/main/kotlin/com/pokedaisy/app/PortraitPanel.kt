package com.pokedaisy.app

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.pokedaisy.app.companion.ui.CompanionBack
import com.pokedaisy.app.companion.ui.LocalClickSound
import com.pokedaisy.app.companion.ui.LocalCompanionTopInset
import com.pokedaisy.app.companion.ui.PortraitGrip
import com.pokedaisy.app.companion.ui.PortraitLayout
import com.pokedaisy.app.companion.ui.SidePanelCompanion

/**
 * The companion on a single-screen device held upright (a phone in portrait):
 * the game across the top of the screen ([GameStageLayout.topAligned]), the
 * companion under it, always open - along the screen's bottom, or right under
 * the game with the touch pad below it ([Prefs.portraitCompanionUnderGame]; see
 * [PortraitLayout.arrange]). Its height is the player's
 * ([Prefs.portraitCompanionRatio]): drag the grip on its edge, or tap it to step
 * through small / the Thor's shape / everything under the game. The gap left
 * takes the touch pad when it's shown and fits; else the pad lies over the game
 * as usual. The grip never covers the game: with no room for it above the
 * companion it sits inside the companion's top. BACK is the companion's back
 * ([back]).
 *
 * Held sideways the same device gets [SidePanel] instead (PokeDaisyActivity
 * switches between them on rotation).
 */
class PortraitPanel(
    private val root: GameStageLayout,
    private val game: View,
    private val touchControls: View,
    private val prefs: Prefs,
    private val back: CompanionBack,
    private val clickSound: () -> Unit,
    private val companion: @Composable () -> Unit,
) {
    private val context: Context get() = root.context

    var enabled = false
        private set
    private var ratio = prefs.portraitCompanionRatio
    private var underGame = prefs.portraitCompanionUnderGame

    private var panel: View? = null
    private var gripView: View? = null
    private val grip = mutableStateOf(PortraitLayout.Grip.ABOVE)
    private val topInset = mutableIntStateOf(0)
    private var laidOutFor = Triple(0, 0, 0)

    private val screenW get() = root.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
    private val screenH get() = root.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
    private val gameH get() = root.gameHeightFor(screenW)
    private val gripH get() = gripView?.height ?: 0
    private val reserve get() = if (underGame) gripH else 0
    private fun arrangement() = PortraitLayout.arrange(screenW, screenH, gameH, gripH, ratio, underGame)

    // A rotation, the status bar coming or going, a Game Boy game's 10:9: lay out again.
    private val relayout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        val now = Triple(screenW, screenH, gameH)
        if (now != laidOutFor) root.post { apply() }
    }

    fun setEnabled(on: Boolean) {
        if (on == enabled) return
        enabled = on
        if (on) {
            ratio = prefs.portraitCompanionRatio
            underGame = prefs.portraitCompanionUnderGame
            val at = root.indexOfChild(touchControls) + 1
            panel = CompanionColors.track(ComposeView(context)).apply {
                setContent {
                    CompositionLocalProvider(LocalCompanionTopInset provides topInset.intValue) { SidePanelCompanion(companion) }
                }
            }.also { root.addView(it, at, FrameLayout.LayoutParams(-1, 0, Gravity.TOP)) }
            gripView = DragFrame(context, vertical = true, ::onDragStart, ::onDrag, ::onDragEnd).apply {
                addView(CompanionColors.track(ComposeView(context)).apply {
                    setContent {
                        CompositionLocalProvider(LocalClickSound provides clickSound) { PortraitGrip(grip.value, ::stepSize) }
                    }
                })
            }.also {
                root.addView(it, at + 1, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            }
            // The grip's own height decides where things go: place them again once it's measured.
            gripView?.addOnLayoutChangeListener { v, _, t, _, b, _, ot, _, ob -> if (b - t != ob - ot) v.post { apply() } }
            root.addOnLayoutChangeListener(relayout)
            root.topAligned = true
        } else {
            root.removeOnLayoutChangeListener(relayout)
            for (v in listOfNotNull(panel, gripView)) root.removeView(v)
            panel = null
            gripView = null
            root.topAligned = false
            setMargins(game, 0, 0)
            setMargins(touchControls, 0, 0)
        }
        apply()
    }

    /** Picks up SETTINGS' COMPANION (along the bottom / under the game) and the size from [prefs]. */
    fun refresh() {
        if (!enabled) return
        ratio = prefs.portraitCompanionRatio
        underGame = prefs.portraitCompanionUnderGame
        apply()
    }

    /** A BACK tap; false = not ours (held sideways, or a second screen has the companion). */
    fun onBack(): Boolean {
        if (!enabled) return false
        back.back()
        return true
    }

    private fun apply() {
        if (!enabled) return
        val h = screenH
        val a = arrangement()
        laidOutFor = Triple(screenW, h, gameH)
        grip.value = a.grip
        topInset.intValue = a.topInset(gripH)
        panel?.let { setFrame(it, a.companionTop, a.companionHeight) }
        gripView?.let { setMargins(it, a.gripTop, 0) }
        setMargins(game, 0, h - a.gameAreaBottom)
        setMargins(touchControls, a.padTop, h - a.padBottom)
    }

    private fun setFrame(v: View, top: Int, height: Int) {
        val lp = v.layoutParams as FrameLayout.LayoutParams
        if (lp.topMargin == top && lp.height == height) return
        lp.topMargin = top
        lp.height = height
        v.layoutParams = lp
    }

    private fun setMargins(v: View, top: Int, bottom: Int) {
        val lp = v.layoutParams as FrameLayout.LayoutParams
        if (lp.topMargin == top && lp.bottomMargin == bottom) return
        lp.topMargin = top
        lp.bottomMargin = bottom
        v.layoutParams = lp
    }

    private fun save() {
        prefs.portraitCompanionRatio = ratio
        apply()
    }

    private val currentRatio get() = arrangement().companionHeight.toFloat() / screenW

    private fun stepSize() {
        ratio = PortraitLayout.next(screenW, screenH, gameH, currentRatio, reserve)
        save()
    }

    // --- resizing by the grip --------------------------------------------------

    private var dragStartHeight = 0
    private var dragGrows = -1

    private fun onDragStart() {
        dragStartHeight = arrangement().companionHeight
        // Along the bottom the grip is on the companion's top edge: up = taller. Under the game it hangs below: down.
        dragGrows = if (underGame) 1 else -1
    }

    private fun onDrag(dy: Float) {
        ratio = ((dragStartHeight + dragGrows * dy) / screenW).coerceAtLeast(0f)
        apply()
    }

    private fun onDragEnd() {
        ratio = PortraitLayout.snap(screenW, screenH, gameH, currentRatio, reserve)
        save()
    }
}
