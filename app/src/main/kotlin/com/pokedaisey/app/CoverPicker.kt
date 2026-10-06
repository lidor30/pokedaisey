package com.pokedaisey.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pokedaisey.app.SteamGridDbClient.GameHit
import com.pokedaisey.app.SteamGridDbClient.Icon
import com.pokedaisey.app.companion.ui.GbaText
import com.pokedaisey.app.companion.ui.GbaTextMetrics
import com.pokedaisey.app.companion.ui.OptionButton
import com.pokedaisey.app.companion.ui.OptionColors
import com.pokedaisey.app.companion.ui.OptionLine
import com.pokedaisey.app.companion.ui.OptionListWindow
import com.pokedaisey.app.companion.ui.OptionOverlay
import com.pokedaisey.app.companion.ui.OptionTextField
import com.pokedaisey.app.companion.ui.OptionTitleWindow
import com.pokedaisey.app.companion.ui.PixelRoundedShape
import com.pokedaisey.app.companion.ui.RockingPokeBall
import com.pokedaisey.app.companion.ui.drawLayeredFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where the cover picker's games, icons and thumbnails come from - SteamGridDB,
 * or canned data in the ui-preview harness. All blocking; called off the UI thread. */
interface CoverSource {
    /** The SteamGridDB game this ROM is known to be, if any ([SteamGridDbGames.forRom]). */
    fun gameFor(rom: File): GameHit?
    fun search(term: String): List<GameHit>
    fun icons(gameId: Int): List<Icon>
    fun thumbBytes(url: String): ByteArray?
}

class SteamGridDbCoverSource(private val apiKey: String) : CoverSource {
    override fun gameFor(rom: File) = SteamGridDbGames.forRom(rom)?.let { GameHit(it.id, it.displayName) }
    override fun search(term: String) = SteamGridDbClient.searchGames(apiKey, term)
    override fun icons(gameId: Int) = SteamGridDbClient.listIcons(apiKey, gameId)
    override fun thumbBytes(url: String) = SteamGridDbClient.fetchBytes(url)
}

/**
 * REPLACE COVER: every SteamGridDB icon for the ROM's game (the preferred
 * uploader's first, like the automatic pick), tap one to use it. Starts on
 * the game [SteamGridDbGames] knows the ROM as; otherwise - or after a
 * search - a list of matching games to choose from first. [source] null =
 * no API key yet. [onPick] downloads the icon; [saving] shows while it does.
 */
@Composable
fun CoverPicker(
    rom: File,
    displayName: String,
    source: CoverSource?,
    saving: Boolean,
    error: String?,
    m: GbaTextMetrics,
    small: GbaTextMetrics,
    onPick: (Icon) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(displayName) }
    var loading by remember { mutableStateOf(source != null) }
    var message by remember { mutableStateOf(if (source == null) NO_KEY else null) }
    var games by remember { mutableStateOf<List<GameHit>?>(null) }
    var game by remember { mutableStateOf<GameHit?>(null) }
    var icons by remember { mutableStateOf<List<Icon>>(emptyList()) }

    fun run(block: suspend () -> Unit) {
        loading = true
        message = null
        scope.launch {
            runCatching { block() }.onFailure { message = "COULDN'T REACH STEAMGRIDDB" }
            loading = false
        }
    }
    fun openGame(g: GameHit) = run {
        game = g
        icons = withContext(Dispatchers.IO) { source!!.icons(g.id) }
        if (icons.isEmpty()) message = "NO ICONS FOR THIS GAME YET"
    }
    fun search() = run {
        game = null
        icons = emptyList()
        games = withContext(Dispatchers.IO) { source!!.search(query) }
        if (games.isNullOrEmpty()) message = "NO GAMES MATCH \"${query.trim()}\""
    }

    LaunchedEffect(source) {
        if (source == null) return@LaunchedEffect
        val known = runCatching { withContext(Dispatchers.IO) { source.gameFor(rom) } }.getOrNull()
        if (known != null) openGame(known) else search()
    }

    OptionOverlay(onDismiss, Modifier.widthIn(max = 1100.dp).fillMaxHeight(0.92f)) {
        Column(Modifier.fillMaxSize()) {
            OptionTitleWindow("REPLACE COVER", m, trailing = game?.name?.uppercase())
            Spacer(Modifier.height(m.u * 4))
            OptionListWindow(m, Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize()) {
                    if (source != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.u * 4)) {
                            OptionTextField(query, { query = it }, small, Modifier.weight(1f), placeholder = "game name")
                            OptionButton("SEARCH", small, enabled = !loading && !saving, onClick = { search() })
                        }
                        Spacer(Modifier.height(m.u * 4))
                    }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        when {
                            loading || saving -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                RockingPokeBall(Modifier.size(m.u * 28))
                                Spacer(Modifier.height(m.u * 4))
                                GbaText(if (saving) "SAVING COVER…" else "LOADING…", OptionColors.label, OptionColors.labelShadow, m)
                            }
                            game != null && icons.isNotEmpty() -> IconGrid(icons, source!!, m, small, onPick)
                            game == null && !games.isNullOrEmpty() -> GameList(games!!, m) { openGame(it) }
                            message != null -> GbaText(message!!, OptionColors.label, OptionColors.labelShadow, m, maxLines = 3)
                        }
                    }
                    // The last download's failure, under the icons it came from.
                    if (error != null && !saving) {
                        Spacer(Modifier.height(m.u * 2))
                        GbaText(error, OptionColors.value, OptionColors.valueShadow, small)
                    }
                    Spacer(Modifier.height(m.u * 4))
                    Row(horizontalArrangement = Arrangement.spacedBy(m.u * 4)) {
                        if (game != null && !games.isNullOrEmpty()) {
                            OptionButton("OTHER GAMES", m, enabled = !loading && !saving, onClick = { game = null; icons = emptyList(); message = null })
                        }
                        Spacer(Modifier.weight(1f))
                        OptionButton("CANCEL", m, enabled = !saving, onClick = onDismiss)
                    }
                }
            }
        }
    }
}

private const val NO_KEY = "Add a SteamGridDB API key in Settings > Cover Art first."

@Composable
private fun GameList(games: List<GameHit>, m: GbaTextMetrics, onOpen: (GameHit) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        GbaText("WHICH GAME?", OptionColors.muted, OptionColors.mutedShadow, m, Modifier.padding(horizontal = m.u * 8))
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(games) { i, g ->
                OptionLine(g.name, null, selected = false, m, divider = i < games.lastIndex) { onOpen(g) }
            }
        }
    }
}

@Composable
private fun IconGrid(icons: List<Icon>, source: CoverSource, m: GbaTextMetrics, small: GbaTextMetrics, onPick: (Icon) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(m.u * 4),
        verticalArrangement = Arrangement.spacedBy(m.u * 4),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(icons, key = { it.id }) { icon ->
            Column(Modifier.clickable { onPick(icon) }) {
                RemoteThumb(icon.thumb, source, m)
                // Who made it - the preferred uploader's are the ones listed first.
                GbaText(
                    icon.authorName ?: "", if (icon.preferred) OptionColors.value else OptionColors.muted,
                    if (icon.preferred) OptionColors.valueShadow else OptionColors.mutedShadow, small,
                    Modifier.padding(top = m.u * 2),
                )
            }
        }
    }
}

/** A thumbnail fetched on first sight and kept for the session. */
@Composable
private fun RemoteThumb(url: String, source: CoverSource, m: GbaTextMetrics) {
    val bitmap by produceState(ThumbCache[url], url) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                source.thumbBytes(url)?.let { bytes ->
                    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
                }
            }?.also { ThumbCache[url] = it }
        }
    }
    val u = m.u
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(PixelRoundedShape(u * 3))
            .background(Color(0xFF20242C))
            .drawWithContent {
                drawContent()
                val px = u.toPx()
                drawLayeredFrame(listOf(OptionColors.frameDark to px, OptionColors.frameLight to px), radius = 3 * px)
            },
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
        } ?: RockingPokeBall(Modifier.size(m.u * 14))
    }
}

private object ThumbCache {
    private val map = LinkedHashMap<String, ImageBitmap>()
    @Synchronized operator fun get(url: String) = map[url]
    @Synchronized operator fun set(url: String, bmp: ImageBitmap) {
        map[url] = bmp
        if (map.size > 200) map.remove(map.keys.first())
    }
}
