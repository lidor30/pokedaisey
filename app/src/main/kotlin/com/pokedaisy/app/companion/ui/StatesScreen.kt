package com.pokedaisy.app.companion.ui

import com.pokedaisy.app.companion.i18n.tr
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pokedaisy.app.companion.StateSlots
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Savestate slots for the bottom screen, in the SETTINGS tab's OPTION-screen
 * language (white title window, grey framed windows, grey/red pixel text)
 * straight over the game's backdrop - but as a grid of slot cards rather
 * than one list. Tap a slot's screenshot (or LOAD) to load it, SAVE to
 * overwrite. [tick] just forces a re-list after a save/load.
 */
@Composable
fun StatesScreen(slots: StateSlots?, tick: Long) {
    val m = rememberGbaTextMetrics()
    val small = rememberGbaTextMetrics(textScale = 1f)
    if (slots == null) {
        Column {
            OptionTitleWindow(tr("SAVE STATES"), m)
            Spacer(Modifier.height(m.u * 4))
            OptionListWindow(m, Modifier.fillMaxWidth()) {
                GbaText(tr("NO GAME RUNNING"), OptionColors.label, OptionColors.labelShadow, m, Modifier.padding(m.u * 4))
            }
        }
        return
    }
    val list = remember(tick) { slots.list() }
    Column(modifier = Modifier.fillMaxSize()) {
        OptionTitleWindow(tr("SAVE STATES"), m, trailing = tr("SLOT {0}", slots.currentIndex))
        Spacer(Modifier.height(m.u * 4))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(m.u * 4)) {
            OptionButton(tr("UNDO SAVE"), m, onClick = { slots.requestUndoSave() }, modifier = Modifier.weight(1f))
            OptionButton(tr("UNDO LOAD"), m, onClick = { slots.requestUndoLoad() }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(m.u * 4))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(m.u * 4),
            verticalArrangement = Arrangement.spacedBy(m.u * 4),
            contentPadding = PaddingValues(bottom = m.u * 4),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(list, key = { it.index }) { slot ->
                SlotCard(
                    slot = slot,
                    isCurrent = slot.index == slots.currentIndex,
                    m = m, small = small,
                    onLoad = { slots.requestLoad(slot.index) },
                    onSave = { slots.requestSave(slot.index) },
                )
            }
        }
    }
}

@Composable
private fun SlotCard(
    slot: StateSlots.Slot,
    isCurrent: Boolean,
    m: GbaTextMetrics,
    small: GbaTextMetrics,
    onLoad: () -> Unit,
    onSave: () -> Unit,
) {
    val u = m.u
    // The slot the hotkeys target gets the OPTION list's white "cursor" fill.
    OptionListWindow(m, Modifier.fillMaxWidth(), fill = if (isCurrent) OptionColors.rowSelected else OptionColors.listFill) {
        Column {
            // Fixed height: every card's header is one line, cursor or not.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(m.lineHeight).padding(horizontal = u * 2)) {
                GbaText(tr("SLOT {0}", slot.index), OptionColors.label, OptionColors.labelShadow, m, Modifier.weight(1f))
                if (isCurrent) {
                    PixelIcon(PixelIcons.cursorLeft, OptionColors.value, Modifier.width(m.lineHeight * 0.3f).height(m.lineHeight * 0.5f))
                }
            }
            Spacer(Modifier.height(u * 2))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 2f)
                    .clip(PixelRoundedShape(u * 3))
                    .background(Color(0xFF20242C))
                    .soundClickable(enabled = slot.present, onClick = onLoad)
                    // Frame on top of the screenshot: the list window's dark + blue lines.
                    .drawWithContent {
                        drawContent()
                        val px = u.toPx()
                        drawLayeredFrame(
                            listOf(OptionColors.frameDark to px, OptionColors.frameLight to px),
                            radius = 3 * px,
                        )
                    },
            ) {
                if (slot.present) {
                    val bmp by produceState<Bitmap?>(null, slot.thumbPath, slot.savedAtMillis) {
                        value = slot.thumbPath?.let { p ->
                            withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(p) }.getOrNull() }
                        }
                    }
                    bmp?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = tr("Slot {0}", slot.index),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.None,
                        )
                    }
                } else {
                    GbaText(tr("EMPTY"), OptionColors.muted, Color(0xFF101418), small)
                }
            }
            Spacer(Modifier.height(u * 2))
            GbaText(
                if (slot.present) {
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(slot.savedAtMillis))
                } else "-",
                OptionColors.muted, OptionColors.mutedShadow, small,
                modifier = Modifier.padding(horizontal = u * 2),
            )
            Spacer(Modifier.height(u * 3))
            Row(horizontalArrangement = Arrangement.spacedBy(u * 3)) {
                OptionButton(tr("SAVE"), m, onClick = onSave, emphasis = true, modifier = Modifier.weight(1f))
                OptionButton(tr("LOAD"), m, onClick = onLoad, enabled = slot.present, modifier = Modifier.weight(1f))
            }
        }
    }
}
