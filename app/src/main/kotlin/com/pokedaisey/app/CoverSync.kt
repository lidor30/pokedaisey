package com.pokedaisey.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.pokedaisey.app.companion.ui.GbaText
import com.pokedaisey.app.companion.ui.GbaTextMetrics
import com.pokedaisey.app.companion.ui.OptionColors
import com.pokedaisey.app.companion.ui.OptionListWindow
import com.pokedaisey.app.companion.ui.drawLayeredBox
import com.pokedaisey.app.companion.ui.drawPixelRoundRect
import com.pokedaisey.app.companion.ui.inPx
import java.io.File

/**
 * One pass of [CoverArtSync.fetchOne] over the whole library, with progress
 * Compose can watch - Settings > Cover Art's SAVE / REPLACE ALL and first-time
 * setup's cover step. Runs off the UI thread (hashing + network I/O).
 */
class CoverSync {
    var running by mutableStateOf(false)
        private set
    var replace by mutableStateOf(false)
        private set
    var index by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    /** Newest first: each ROM's label and outcome. */
    val log = mutableStateListOf<Pair<String, CoverArtSync.Status>>()

    /** [replace]: re-fetch every ROM's cover, manual ones included; one with no
     * SteamGridDB match (or a failed download) keeps what it has. */
    fun start(activity: ComponentActivity, prefs: Prefs, replace: Boolean) {
        if (running) return
        log.clear()
        index = 0
        total = 0
        this.replace = replace
        running = true
        val apiKey = prefs.steamGridDbApiKey
        Thread({
            val coversDir = File(activity.getExternalFilesDir(null) ?: activity.filesDir, "covers").apply { mkdirs() }
            val roms = RomFolder.libraryRoms(activity, prefs)
            roms.forEachIndexed { i, rom ->
                val label = prefs.romDisplayName(rom) ?: rom.nameWithoutExtension
                val status = CoverArtSync.fetchOne(rom, File(coversDir, "${rom.name}.png"), apiKey, prefs.romCoverManual(rom), replace)
                // A replaced manual cover is an auto one now (RESET COVER no longer applies).
                if (status == CoverArtSync.Status.REPLACED) prefs.setRomCoverManual(rom, false)
                activity.runOnUiThread {
                    index = i + 1
                    total = roms.size
                    log.add(0, label to status)
                }
            }
            activity.runOnUiThread { running = false }
        }, "pokedaisey-cover-sync").apply { isDaemon = true; start() }
    }
}

/** [sync]'s progress window: a count, a bar and each ROM's outcome. Nothing until it has run. */
@Composable
fun CoverSyncPanel(sync: CoverSync, m: GbaTextMetrics, small: GbaTextMetrics, modifier: Modifier = Modifier) {
    if (!sync.running && sync.log.isEmpty()) return
    OptionListWindow(m, modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = m.u * 4)) {
            CoverSyncStatus(sync, m)
            Spacer(Modifier.height(m.u * 3))
            sync.log.forEach { (label, status) -> SyncLogRow(label, status, small) }
        }
    }
}

/** [sync]'s count line and bar alone, without the per-ROM log. */
@Composable
fun CoverSyncStatus(sync: CoverSync, m: GbaTextMetrics) {
    val verb = if (sync.replace) "REPLACING" else "FETCHING"
    val fetched = sync.log.count { it.second == CoverArtSync.Status.REPLACED || it.second == CoverArtSync.Status.FETCHED }
    GbaText(
        when {
            sync.running -> "$verb COVERS… ${sync.index} / ${sync.total}"
            sync.replace -> "DONE — $fetched NEW, ${sync.total - fetched} UNCHANGED"
            else -> "DONE — ${sync.total} ROM(S) CHECKED, $fetched NEW COVER(S)"
        },
        OptionColors.label, OptionColors.labelShadow, m,
    )
    Spacer(Modifier.height(m.u * 3))
    SyncProgressBar(fraction = if (sync.total > 0) sync.index.toFloat() / sync.total else 0f, m = m)
}

/** A white framed bar filled in the value red, like the game's HP / EXP bars. */
@Composable
fun SyncProgressBar(fraction: Float, m: GbaTextMetrics) {
    val u = m.u
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // The 3u frame on each side leaves a 4u-tall fill.
            .height(u * 10)
            .drawBehind {
                val px = u.toPx()
                drawLayeredBox(OptionColors.titleLayers.inPx(px), OptionColors.titleFill, radius = 3 * px)
                val inset = 3 * px
                val w = (size.width - 2 * inset) * fraction.coerceIn(0f, 1f)
                if (w > 0f) {
                    drawPixelRoundRect(
                        OptionColors.value, Offset(inset, inset), Size(w, size.height - 2 * inset),
                        radius = px,
                    )
                }
            },
    )
}

@Composable
private fun SyncLogRow(label: String, status: CoverArtSync.Status, small: GbaTextMetrics) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = small.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GbaText(label, OptionColors.label, OptionColors.labelShadow, small, Modifier.weight(1f))
        GbaText(status.label(), status.tint(), OptionColors.labelShadow, small)
    }
}

private fun CoverArtSync.Status.label(): String = when (this) {
    CoverArtSync.Status.FETCHED -> "FETCHED"
    CoverArtSync.Status.REPLACED -> "REPLACED"
    CoverArtSync.Status.KEPT -> "KEPT (NO NEW ART)"
    CoverArtSync.Status.ALREADY_CACHED -> "CACHED"
    CoverArtSync.Status.MANUAL_COVER -> "MANUAL"
    CoverArtSync.Status.UNKNOWN_GAME -> "NOT SUPPORTED"
    CoverArtSync.Status.FETCH_FAILED -> "NO ICON FOUND"
    CoverArtSync.Status.NO_API_KEY -> "NO API KEY"
}

private fun CoverArtSync.Status.tint(): Color = when (this) {
    CoverArtSync.Status.FETCHED, CoverArtSync.Status.REPLACED -> Color(0xFF208038)
    CoverArtSync.Status.FETCH_FAILED, CoverArtSync.Status.NO_API_KEY -> OptionColors.value
    else -> OptionColors.muted
}
