package com.pokedaisy.app.companion.ui

import com.pokedaisy.app.companion.i18n.tr
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pokedaisy.app.companion.data.CardStyle
import com.pokedaisy.app.companion.data.RomArt
import com.pokedaisy.app.companion.data.TrainerCardArt
import com.pokedaisy.app.companion.data.TrainerCardInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** [style]'s card art from the player's ROM ([RomArt]), once a scan has cached it; null until then. */
@Composable
fun rememberTrainerCardArt(style: CardStyle?): TrainerCardArt.Art? {
    val context = LocalContext.current
    val generation = rememberArtGeneration()
    return produceState(style?.let(TrainerCardArt::cached), style, generation) {
        value = style?.let { s -> withContext(Dispatchers.IO) { runCatching { TrainerCardArt.load(RomArt.dir(context.filesDir), s) }.getOrNull() } }
    }.value
}

/**
 * The CARD tab: the game's own TRAINER CARD, drawn from the ROM pixel for
 * pixel ([TrainerCardArt]) at the largest whole scale that fits, with its
 * play-time colon blinking like the game's. A tap flips it over the way the
 * game does - squashed shut top and bottom, opened on the other side.
 */
@Composable
fun TrainerCardScreen(card: TrainerCardInfo, art: TrainerCardArt.Art, modifier: Modifier = Modifier, initialBack: Boolean = false) {
    var back by remember { mutableStateOf(initialBack) }
    // BlinkTimeColon: the colon toggles every 60 frames. An infinite
    // transition (not a delay loop), so screenshot tests can still go idle.
    val blink = rememberInfiniteTransition(label = "card-colon")
    val phase = blink.animateFloat(0f, 2f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "card-colon-phase")
    val colon by remember { derivedStateOf { phase.value < 1f } }
    // Just the card, over the companion's own backdrop: the game's screen
    // behind it would draw a box of other stripes (and cost a scale step).
    val image = remember(art, card, back, colon) {
        val img = TrainerCardArt.render(art, card, back, colon, backdrop = false)
        val (l, t, r, b) = TrainerCardArt.cardBounds(img).toList()
        val px = IntArray((r - l) * (b - t)) { img.argb[(t + it / (r - l)) * img.width + l + it % (r - l)] }
        Bitmap.createBitmap(px, r - l, b - t, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    val squash = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var flipping by remember { mutableStateOf(false) }
    val m = rememberGbaTextMetrics(1f)
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(m.u * 4, Alignment.CenterVertically),
    ) {
        BoxWithConstraints(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val w = with(density) { maxWidth.toPx() }
            val h = with(density) { maxHeight.toPx() }
            // Whole screen pixels per GBA pixel, so the card stays crisp.
            val scale = max(1, min(w / image.width, h / image.height).toInt())
            val size = with(density) { DpSize((image.width * scale).toDp(), (image.height * scale).toDp()) }
            Canvas(
                Modifier
                    .size(size)
                    .graphicsLayer { scaleY = squash.value }
                    .soundClickable(enabled = !flipping) {
                        flipping = true
                        scope.launch {
                            squash.animateTo(0f, tween(FLIP_MS, easing = LinearEasing))
                            back = !back
                            squash.animateTo(1f, tween(FLIP_MS, easing = LinearEasing))
                            flipping = false
                        }
                    },
            ) {
                drawImage(image, dstSize = IntSize(image.width * scale, image.height * scale), dstOffset = IntOffset.Zero, filterQuality = FilterQuality.None)
            }
        }
        GbaText(
            if (back) tr("TAP TO SEE THE FRONT") else tr("TAP TO FLIP THE CARD"),
            OptionColors.titleText, OptionColors.titleShadow, m,
        )
    }
}

private const val FLIP_MS = 150
