package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import com.pokedaisy.app.companion.i18n.tr
import kotlin.math.min

/**
 * The companion on a single-screen device: a panel beside (or over) the game
 * instead of a second screen. The companion's layouts are made for the Thor's
 * bottom screen (1240x1080 @ 2.625 = 472x411 dp), so the panel draws it at the
 * density that gives it at least that much room: the Thor's own where the panel
 * is that big, smaller (the pixel font steps down to 2 screen pixels per font
 * pixel, still crisp) for a narrower one - half a 1080p screen comes out at
 * ~1.9, where every tab fits without truncating.
 */
fun sidePanelDensity(widthPx: Int, heightPx: Int): Float =
    min(heightPx / THOR_BOTTOM_HEIGHT_DP, widthPx / SIDE_PANEL_MIN_WIDTH_DP).coerceAtLeast(1f)

/** The Thor's bottom screen height in dp (1080 px @ 2.625). */
private const val THOR_BOTTOM_HEIGHT_DP = 1080f / 2.625f

/** A bit over the Thor's 472 dp, so half a 1080p screen lands below 2.0 (whole-pixel text steps down). */
private const val SIDE_PANEL_MIN_WIDTH_DP = 500f

/** [content] (the companion) at [sidePanelDensity] for the space it's given. */
@Composable
fun SidePanelCompanion(content: @Composable () -> Unit) {
    BoxWithConstraints {
        val outer = LocalDensity.current
        val d = sidePanelDensity(constraints.maxWidth, constraints.maxHeight)
        CompositionLocalProvider(LocalDensity provides Density(d, outer.fontScale)) { content() }
    }
}

/**
 * The tab on the side panel's edge: a white title-window tab running into the
 * panel (like a folder tab) with a padlock - open = the panel lies over the
 * game, shut = locked beside it - in OPTION's grey / red, with the text's
 * shadow. A tap locks / unlocks; dragging it sideways (handled by the view
 * around it, in screen coordinates, since the tab moves with the finger)
 * resizes the panel.
 */
@Composable
fun SidePanelHandle(docked: Boolean, onToggle: () -> Unit) {
    val m = rememberGbaTextMetrics()
    val u = m.u
    val noRipple = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(u * 32, u * 32)
            .drawBehind {
                drawLayeredBox(OptionColors.titleLayers.inPx(u.toPx()), OptionColors.titleFill, radius = 3 * u.toPx(), openRight = true)
            }
            .semantics { contentDescription = if (docked) tr("Unlock the companion") else tr("Lock the companion beside the game") }
            .soundClickable(interactionSource = noRipple, indication = null, onClick = onToggle),
    ) {
        if (docked) ShadowedPixelIcon(PixelIcons.lockClosed, OptionColors.value, OptionColors.valueShadow, m)
        else ShadowedPixelIcon(PixelIcons.lockOpen, OptionColors.label, OptionColors.labelShadow, m)
    }
}
