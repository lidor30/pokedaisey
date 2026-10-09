package com.pokedaisy.app.companion.ui

import com.pokedaisy.app.companion.i18n.tr
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokedaisy.app.companion.data.GameKind
import com.pokedaisy.app.companion.data.MapTiles
import com.pokedaisy.app.companion.data.PlayerMapTile
import com.pokedaisy.app.companion.data.RegionMapModel
import com.pokedaisy.app.companion.data.SnapshotView
import com.pokedaisy.app.companion.data.activeRegionMap
import com.pokedaisy.app.companion.data.gameCase
import kotlin.math.floor

/**
 * The game's region map, stretched to the whole tab (cropped to the map
 * itself by [regionMapContentBounds]) in a rounded frame, drawn the way the
 * game draws its own: the player's head on the tile the game puts it, the
 * blinking cursor, and the place name in the game's own label - FireRed's
 * dark strip top-left, Emerald's window bottom-right ([MapLabelStyle]).
 *
 * Tapping the map moves the cursor there and names the place under it (the
 * game's own cursor grid, [RegionMapModel.pick]); PLACES lists every town,
 * route and place to jump to; the region button flips between a game's maps
 * (Kanto / the Sevii Islands); ME (or tapping the head) goes back to the player.
 */
@Composable
fun MapScreen(snapshot: SnapshotView, modifier: Modifier = Modifier) {
    val gen = rememberArtGeneration()
    // RomRegionMap lands a hack's own map in the background and announces it as new art.
    val model = remember(snapshot.game, gen) { activeRegionMap() }
    Box(modifier = modifier.fillMaxSize()) {
        val playerRegion = model?.sections?.get(snapshot.regionMapSectionId)?.region?.takeIf { it in model.images.indices }
        var selection by remember(model) { mutableStateOf<MapSelection?>(null) }
        var shownOverride by remember(model) { mutableStateOf<Int?>(null) }
        var placesOpen by remember { mutableStateOf(false) }
        val shown = selection?.region ?: shownOverride ?: playerRegion ?: 0
        val bitmap = regionMapBitmap(model?.images?.getOrNull(shown))
        // No early return out of Box's inline content: it corrupts the slot table (seen on Lazarus).
        if (model == null || bitmap == null) {
            NoRegionMap(snapshot)
        } else {
            val style = MapLabelStyle.of(snapshot.game)
            val playerTile = if (playerRegion == shown) {
                PlayerMapTile.of(model, snapshot.regionMapSectionId, snapshot.x, snapshot.y, snapshot.mapWidth, snapshot.mapHeight, snapshot.mapType)
            } else null
            val sel = selection
            val (name, dungeon) = when {
                sel != null -> sel.name to sel.dungeon
                playerRegion == null || playerRegion == shown -> snapshot.location.mapSecName to null
                else -> "" to null
            }
            // The map fills the tab while that stretches it only a little (the Thor's
            // bottom screen: ~16% taller), else keeps its own shape, centred (a wide screen).
            val crop = remember(bitmap) { regionMapContentBounds(bitmap) }
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val mapAspect = crop.width.toFloat() / crop.height
                val boxAspect = maxWidth / maxHeight
                val fitted = when {
                    maxOf(boxAspect / mapAspect, mapAspect / boxAspect) <= MAX_MAP_STRETCH -> Modifier.fillMaxSize()
                    boxAspect > mapAspect -> Modifier.size(maxHeight * mapAspect, maxHeight)
                    else -> Modifier.size(maxWidth, maxWidth / mapAspect)
                }
                Box(fitted) {
                    RegionMap(
                        bitmap = bitmap,
                        style = style,
                        head = regionMapBitmap(style.headAsset(snapshot.playerGender)),
                        headTile = playerTile,
                        cursor = sel?.tiles ?: if (playerRegion == shown) model.tilesOf(snapshot.regionMapSectionId) else emptyList(),
                        label = gameCase(name),
                        dungeon = dungeon?.let(::gameCase),
                        onTap = { tx, ty ->
                            selection = if (playerTile != null && tx to ty == playerTile) null else model.select(shown, tx, ty)
                        },
                    )
                    MapButtons(
                        style = style,
                        regionLabel = if (model.images.size > 1) regionName(model.images, (shown + 1) % model.images.size) else null,
                        awayFromPlayer = selection != null || (playerRegion != null && shown != playerRegion),
                        onPlaces = { placesOpen = true },
                        onRegion = { selection = null; shownOverride = (shown + 1) % model.images.size },
                        onMe = { selection = null; shownOverride = null },
                    )
                }
            }
            if (placesOpen) {
                PlacesList(
                    model,
                    onPick = { id ->
                        placesOpen = false
                        selection = model.sections[id]?.let { MapSelection(it.region, model.tilesOf(id), it.name, null) }
                    },
                    onMe = { placesOpen = false; selection = null; shownOverride = null },
                    onDismiss = { placesOpen = false },
                )
            }
        }
    }
}

/** Where the cursor is when it's not on the player: an image, the tiles it frames, and the label's text. */
private data class MapSelection(val region: Int, val tiles: List<MapTiles>, val name: String, val dungeon: String?)

/** What tile ([tx], [ty]) of image [region] is: its section's tiles (a dungeon: just that tile) and names; blank on empty map. */
private fun RegionMapModel.select(region: Int, tx: Int, ty: Int): MapSelection {
    val pick = pick(region, tx, ty) ?: return MapSelection(region, listOf(MapTiles(tx, ty, 1, 1)), "", null)
    val main = pick.mapsec.takeIf { it >= 0 } ?: pick.dungeon
    val dungeon = sections[pick.dungeon]?.name?.takeIf { pick.mapsec >= 0 && pick.dungeon >= 0 }
    val tiles = if (pick.dungeon >= 0) listOf(MapTiles(tx, ty, 1, 1)) else tilesOf(main).ifEmpty { listOf(MapTiles(tx, ty, 1, 1)) }
    return MapSelection(region, tiles, sections[main]?.name.orEmpty(), dungeon)
}

/**
 * Each game family's own region-map label, sampled from the game (headless
 * mGBA, the Town Map / wall map); sizes in map pixels.
 */
private enum class MapLabelStyle(val width: Int, val height: Int) {
    /** FireRed: a strip that darkens the map (GBA blend, ~10/16) from the map's top-left corner, white text, grey shadow. */
    FIRERED(120, 16),

    /** Emerald: the standard framed window, white, in the full-screen map's bottom-right corner. */
    EMERALD(110, 30),

    /** Gen 1 (Yellow): the name in black on a white strip over the town map's top row. */
    GEN1(128, 8);

    /** The map cursor's corners: white on the GBA maps, black on Gen 1's light town map. */
    val cursorColor: Color get() = if (this == GEN1) Color(0xFF181818) else Color.White

    /** The player's 16x16 head on the map (regionmap/player_*.png, from the ROM by RomArt). */
    fun headAsset(gender: Int): String = when (this) {
        FIRERED -> if (gender == 1) "player_leaf" else "player_red"
        EMERALD -> if (gender == 1) "player_may" else "player_brendan"
        GEN1 -> "player_yellow"   // Red's standing sprite, from the ROM (Gen1Art)
    }

    companion object {
        private val EMERALD_FAMILY = setOf(
            GameKind.EMERALD, GameKind.EMERALD_SEAGLASS, GameKind.TMT2, GameKind.ROWE,
            GameKind.EMERALD_ROGUE, GameKind.HEART_AND_SOUL, GameKind.LAZARUS, GameKind.SOULGOLD,
            GameKind.GLAZED, GameKind.IMPERIUM,
        )

        fun of(game: GameKind?) = when (game) {
            in EMERALD_FAMILY -> EMERALD
            GameKind.YELLOW -> GEN1
            else -> FIRERED
        }
    }
}

/** A name for region-map image [i] on the region button: FireRed's own maps by name, others numbered. */
private fun regionName(images: Array<String>, i: Int): String = when (images[i]) {
    "kanto" -> "KANTO"
    "sevii123" -> "SEVII 1-3"
    "sevii45" -> "SEVII 4-5"
    "sevii67" -> "SEVII 6-7"
    else -> tr("MAP {0}", i + 1)
}

/** The map image in its rounded frame with the head, the cursor and the label over it; [onTap] gets the tapped tile. */
@Composable
private fun RegionMap(
    bitmap: Bitmap,
    style: MapLabelStyle,
    head: Bitmap?,
    headTile: Pair<Int, Int>?,
    cursor: List<MapTiles>,
    label: String,
    dungeon: String?,
    onTap: (Int, Int) -> Unit,
) {
    // FireRed's region-map cursor (graphics/region_map/cursor.png) swaps
    // between its two frames every 20 game frames. Read only while drawing,
    // so a swap redraws the map without recomposing it.
    val bigCursor = rememberBlink(CURSOR_FRAME_MS, enabled = CompanionTweaks[CompanionTweaks.Tweak.MAP_CURSOR_BLINK], label = "map-cursor")

    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val headImage = remember(head) { head?.asImageBitmap() }
    // FireRed's region-map screens carry the game's own black border (and
    // a white inner strip) around the map; draw only the map itself.
    val crop = remember(bitmap) { regionMapContentBounds(bitmap) }
    val shape = PixelRoundedShape(12.dp)
    val sound = LocalClickSound.current
    val tap by rememberUpdatedState(onTap)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .drawWithContent {
                drawContent()
                val px = (MAP_FRAME / 5).toPx().coerceAtLeast(1f)
                drawLayeredFrame(
                    listOf(OptionColors.frameDark to 2 * px, OptionColors.frameLight to px, Color.White to 2 * px),
                    radius = 12.dp.toPx(),
                )
            },
    ) {
        val density = LocalDensity.current
        // Screen pixels per map pixel: the map is stretched to its box (MapScreen sizes it).
        val sx = with(density) { maxWidth.toPx() } / crop.width
        val sy = with(density) { maxHeight.toPx() } / crop.height
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(crop, sx, sy) {
                    detectTapGestures { p ->
                        sound()
                        tap(floor((crop.left + p.x / sx) / TILE).toInt(), floor((crop.top + p.y / sy) / TILE).toInt())
                    }
                },
        ) {
            drawImage(
                image,
                srcOffset = IntOffset(crop.left, crop.top),
                srcSize = IntSize(crop.width, crop.height),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.None,
            )
            if (headImage != null && headTile != null) {
                // Centred on its tile, as the game's sprite is.
                val hx = headTile.first * TILE + TILE / 2 - HEAD / 2 - crop.left
                val hy = headTile.second * TILE + TILE / 2 - HEAD / 2 - crop.top
                drawImage(
                    headImage,
                    dstOffset = IntOffset((hx * sx).toInt(), (hy * sy).toInt()),
                    dstSize = IntSize((HEAD * sx).toInt(), (HEAD * sy).toInt()),
                    filterQuality = FilterQuality.None,
                )
            }
            for (t in cursor) {
                drawMapCursor(
                    left = t.x * TILE - crop.left,
                    top = t.y * TILE - crop.top,
                    right = (t.x + t.w) * TILE - crop.left,
                    bottom = (t.y + t.h) * TILE - crop.top,
                    sx = sx,
                    sy = sy,
                    big = bigCursor.value,
                    color = style.cursorColor,
                )
            }
        }
        MapLabel(style, label, dungeon, crop, sx, sy)
    }
}

/**
 * The place name in [style]'s label, scaled with the map: one font pixel per
 * whole screen pixels per map pixel, so the pixel font stays crisp. FireRed
 * puts a dungeon's name (Mt. Moon on Route 4) in a second strip under it.
 */
@Composable
private fun MapLabel(style: MapLabelStyle, label: String, dungeon: String?, crop: IntRect, sx: Float, sy: Float) {
    val density = LocalDensity.current
    val k = floor(minOf(sx, sy)).toInt().coerceAtLeast(1)
    val m = remember(k, density) {
        with(density) { GbaTextMetrics(k, k.toDp(), (16f * k).toSp(), k.toFloat(), (16f * k).toDp(), (20f * k).toDp()) }
    }
    @Composable
    fun box(row: Int, text: String) {
        with(density) {
            val w = style.width * sx
            val h = style.height * sy
            val (x, y) = when (style) {
                MapLabelStyle.FIRERED -> 0f to row * h
                // The tab's frame covers the map's top edge: the row starts inside it.
                MapLabelStyle.GEN1 -> 0f to MAP_FRAME.toPx()
                // The game's window ends 1px from the screen's edge; the tab's frame
                // covers that edge, so it ends 1px inside the frame instead.
                MapLabelStyle.EMERALD -> {
                    val frame = MAP_FRAME.toPx()
                    (crop.width * sx - frame - sx - w) to (crop.height * sy - frame - sy - h)
                }
            }
            Box(
                contentAlignment = if (style == MapLabelStyle.GEN1) Alignment.TopStart else Alignment.CenterStart,
                modifier = Modifier
                    .offset { IntOffset(x.toInt(), y.toInt()) }
                    .size(w.toDp(), h.toDp())
                    .drawBehind {
                        when (style) {
                            MapLabelStyle.FIRERED -> drawRect(FR_STRIP)
                            MapLabelStyle.GEN1 -> drawRect(Color.White)
                            MapLabelStyle.EMERALD -> drawLayeredBox(OptionColors.listLayers.map { (c, lw) -> c to lw * sy }, Color.White, radius = sy)
                        }
                    }
                    // FireRed's text sits 2px in, but the tab's frame covers the map's edge here.
                    .padding(start = ((if (style == MapLabelStyle.EMERALD) 7 else 4) * sx).toDp()),
            ) {
                when (style) {
                    MapLabelStyle.FIRERED -> GbaText(text, Color.White, FR_SHADOW, m)
                    // Its letters fill the 8px row (glyph rows 0-6 sit 6 font pixels under the
                    // line's top), wider than the row's line box: measured unbounded, pulled up.
                    MapLabelStyle.GEN1 -> GbaText(
                        text, OptionColors.titleText, Color.Transparent, m,
                        Modifier.wrapContentHeight(Alignment.Top, unbounded = true).offset { IntOffset(0, -6 * k + (sy / 2).toInt()) },
                    )
                    MapLabelStyle.EMERALD -> GbaText(text, OptionColors.titleText, OptionColors.titleShadow, m)
                }
            }
        }
    }
    // FireRed drops its strip over empty map; Emerald's window stays, blank.
    if (label.isNotEmpty() || style == MapLabelStyle.EMERALD) box(0, label)
    if (dungeon != null && style == MapLabelStyle.FIRERED) box(1, dungeon)
}

/** PLACES / region / ME, in the corner across from the label (sea on both games' maps). */
@Composable
private fun BoxScope.MapButtons(
    style: MapLabelStyle,
    regionLabel: String?,
    awayFromPlayer: Boolean,
    onPlaces: () -> Unit,
    onRegion: () -> Unit,
    onMe: () -> Unit,
) {
    val m = rememberGbaTextMetrics(1f)
    val bottom = style == MapLabelStyle.EMERALD
    // ME on its own row (nearer the edge), so the row beside the label stays short.
    Column(
        horizontalAlignment = if (bottom) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(m.u * 4),
        modifier = Modifier
            .align(if (bottom) Alignment.BottomStart else Alignment.TopEnd)
            .padding(m.u * 8),
    ) {
        if (awayFromPlayer && bottom) OptionButton(tr("ME"), m, onClick = onMe, emphasis = true)
        Row(horizontalArrangement = Arrangement.spacedBy(m.u * 4)) {
            if (regionLabel != null) OptionButton(regionLabel, m, onClick = onRegion)
            OptionButton(tr("PLACES"), m, onClick = onPlaces)
        }
        if (awayFromPlayer && !bottom) OptionButton(tr("ME"), m, onClick = onMe, emphasis = true)
    }
}

/**
 * Every place on the game's maps, to find one a guide names: towns and
 * cities in the game's own order, routes by number, the rest A-Z. Picking
 * one shows it on its map with the cursor on it.
 */
@Composable
private fun PlacesList(model: RegionMapModel, onPick: (Int) -> Unit, onMe: () -> Unit, onDismiss: () -> Unit) {
    val m = rememberGbaTextMetrics()
    val groups = remember(model) { model.places() }
    OptionOverlay(onDismiss, Modifier.widthIn(max = 560.dp).fillMaxHeight()) {
        Column {
            OptionTitleWindow(tr("PLACES"), m, onBack = onDismiss)
            Spacer(Modifier.height(m.u * 4))
            OptionListWindow(m, Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item { OptionLine(tr("YOUR LOCATION"), null, selected = false, m, divider = true, onClick = onMe) }
                    for ((title, ids) in groups) {
                        item(key = title) {
                            GbaText(
                                placesGroupTitle(title), OptionColors.value, OptionColors.valueShadow, m,
                                modifier = Modifier.padding(start = m.u * 8, top = m.u * 8, bottom = m.u * 2),
                            )
                        }
                        items(ids, key = { it }) { id ->
                            val s = model.sections.getValue(id)
                            OptionLine(
                                // Only places off the first map say which one (FireRed's Sevii Islands).
                                gameCase(s.name), if (s.region > 0) regionName(model.images, s.region) else null,
                                selected = false, m, labelWeight = 0.62f, divider = true,
                            ) { onPick(id) }
                        }
                    }
                }
            }
        }
    }
}

/** [RegionMapModel.places]' group titles (English ids, also list keys) in the app's language. */
private fun placesGroupTitle(title: String): String = when (title) {
    "TOWNS & CITIES" -> tr("TOWNS & CITIES")
    "ROUTES" -> tr("ROUTES")
    "OTHER PLACES" -> tr("OTHER PLACES")
    else -> title
}

/** [directionNames]' words in the app's language. */
private fun directionText(dir: String): String = when (dir) {
    "South" -> tr("South")
    "North" -> tr("North")
    "West" -> tr("West")
    "East" -> tr("East")
    "Southwest" -> tr("Southwest")
    "Southeast" -> tr("Southeast")
    "Northwest" -> tr("Northwest")
    "Northeast" -> tr("Northeast")
    else -> dir
}

/** No map image for this game or area: the name and position in a label instead. */
@Composable
private fun BoxScope.NoRegionMap(snapshot: SnapshotView) {
    val location = snapshot.location
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .align(Alignment.Center)
            .padding(24.dp)
            // Readable on every game's backdrop.
            .background(Color.Black.copy(alpha = 0.55f), PixelRoundedShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (location.mapSecName.isNotEmpty()) {
            Text(location.mapSecName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
        }
        Text(
            tr("({0}, {1}) facing {2}", snapshot.x, snapshot.y, directionText(snapshot.facingDirection)),
            color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        Text(
            // No name at all = the game's own region isn't mapped yet (Odyssey
            // before RomRegionMap has read its ROM).
            if (location.mapSecName.isEmpty()) tr("(no region map for this game yet)") else tr("(no region map for this area)"),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private const val CURSOR_FRAME_MS = 20 * 1000 / 60
/** The tab's frame around the map (2 + 1 + 2 lines). */
private val MAP_FRAME = 5.dp

/** How far the map may be stretched (either way) to fill the tab before it keeps its shape instead. */
private const val MAX_MAP_STRETCH = 1.2f
private const val TILE = 8
private const val HEAD = 16
private val FR_STRIP = Color.Black.copy(alpha = 6f / 16f)
private val FR_SHADOW = Color(0xFF636363)

/**
 * FireRed's map cursor around the map pixels [left]..[right) x [top]..[bottom)
 * (a tile, or a whole map section): four white corners 2 pixels thick and 5
 * long, 2 pixels out from it, or 3 on the [big] frame - as the cursor sprite
 * draws them around its tile. [sx]/[sy] are screen pixels per map pixel.
 */
private fun DrawScope.drawMapCursor(left: Int, top: Int, right: Int, bottom: Int, sx: Float, sy: Float, big: Boolean, color: Color = Color.White) {
    val out = if (big) 3 else 2
    val x0 = left - out
    val y0 = top - out
    val x1 = right + out
    val y1 = bottom + out
    fun px(x: Int, y: Int, w: Int, h: Int) =
        drawRect(color, Offset(x * sx, y * sy), Size(w * sx, h * sy))
    for ((cx, cy) in listOf(x0 to y0, x1 - CURSOR_ARM to y0, x0 to y1 - CURSOR_ARM, x1 - CURSOR_ARM to y1 - CURSOR_ARM)) {
        // Each corner: a 5x2 bar on the box's edge row and a 2x5 one on its edge column.
        val barY = if (cy == y0) y0 else y1 - CURSOR_THICK
        val barX = if (cx == x0) x0 else x1 - CURSOR_THICK
        px(cx, barY, CURSOR_ARM, CURSOR_THICK)
        px(barX, cy, CURSOR_THICK, CURSOR_ARM)
    }
}

private const val CURSOR_ARM = 5
private const val CURSOR_THICK = 2

/**
 * The part of a region-map image that's actually map: trims whole rows/columns
 * of solid black or white off each edge (FireRed's region-map screen has a
 * 16px black border plus an 8px white strip each side; Emerald's has none).
 * Only near-black/near-white lines count, so a map edge that's all sea (blue)
 * is never mistaken for border, and at most a quarter of each side goes.
 */
fun regionMapContentBounds(bitmap: Bitmap): IntRect {
    val w = bitmap.width
    val h = bitmap.height
    val px = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
    fun border(c: Int): Boolean {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (r < 24 && g < 24 && b < 24) || (r > 240 && g > 240 && b > 240)
    }
    fun col(x: Int, top: Int, bottom: Int) = (top until bottom).all { border(px[it * w + x]) }
    fun row(y: Int, left: Int, right: Int) = (left until right).all { border(px[y * w + it]) }
    var left = 0
    var right = w
    var top = 0
    var bottom = h
    while (left < w / 4 && col(left, top, bottom)) left++
    while (right > w - w / 4 && col(right - 1, top, bottom)) right--
    while (top < h / 4 && row(top, left, right)) top++
    while (bottom > h - h / 4 && row(bottom - 1, left, right)) bottom--
    return IntRect(left, top, right, bottom)
}
