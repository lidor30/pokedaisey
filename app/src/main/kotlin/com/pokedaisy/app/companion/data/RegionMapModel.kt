package com.pokedaisy.app.companion.data

/**
 * One region map's cursor grid: which map section each tile is - the table
 * the game's own map cursor reads the name under it from. [layers] are
 * [w]x[h] mapsec bytes ([none] = nothing there): FireRed has two, the map
 * itself and the dungeons on it (Mt. Moon's icon on Route 4); Emerald one. The grid starts [offX], [offY] tiles into the region map's
 * 30x20 screen, the space [MapSecInfo]'s rects are in. [cellBytes] 2: u16
 * mapsec ids (SoulGold's 314 sections).
 */
class RegionLayout(
    val offX: Int, val offY: Int, val w: Int, val h: Int, val none: Int, val layers: List<ByteArray>, val cellBytes: Int = 1,
) {
    /** The mapsec at screen tile ([tx], [ty]) on [layer], or -1. */
    fun at(layer: Int, tx: Int, ty: Int): Int {
        val x = tx - offX
        val y = ty - offY
        if (layer >= layers.size || x !in 0 until w || y !in 0 until h) return -1
        return cell(layer, y * w + x).takeIf { it != none } ?: -1
    }

    /** Every cell of [layer], [none] included. */
    fun cells(layer: Int): List<Int> = (0 until w * h).map { cell(layer, it) }

    private fun cell(layer: Int, i: Int): Int {
        val l = layers[layer]
        return if (cellBytes == 2) (l[2 * i].toInt() and 0xFF) or ((l[2 * i + 1].toInt() and 0xFF) shl 8) else l[i].toInt() and 0xFF
    }
}

/** [hex] (two digits a byte) as bytes - the generated layouts' format. */
internal fun hexBytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

/** A tile rect on a region map's screen, in 8px tiles. */
data class MapTiles(val x: Int, val y: Int, val w: Int, val h: Int) {
    operator fun contains(t: Pair<Int, Int>) = t.first in x until x + w && t.second in y until y + h
}

/** What's under a tile: its map section and, on FireRed, a dungeon there. */
data class MapPick(val mapsec: Int, val dungeon: Int)

/**
 * A game's region maps for the Map tab: the screens ([images], regionmap/<name>.png),
 * the map sections on them ([sections], [MapSecInfo.region] indexes [images])
 * and, where known, each screen's cursor grid ([layouts], by image).
 */
class RegionMapModel(
    val images: Array<String>,
    val sections: Map<Int, MapSecInfo>,
    val layouts: List<RegionLayout?>,
) {
    /**
     * The section and dungeon at tile ([tx], [ty]) of image [region]: the grid
     * if there is one, else the smallest rect there - with a grid, only among
     * sections it leaves out (Emerald Rogue's hub areas).
     */
    fun pick(region: Int, tx: Int, ty: Int): MapPick? {
        val layout = layouts.getOrNull(region)
        if (layout != null) {
            val m = layout.at(0, tx, ty).takeIf { it in sections } ?: -1
            val d = layout.at(1, tx, ty).takeIf { it in sections } ?: -1
            if (m >= 0 || d >= 0) return MapPick(m, d)
        }
        val onGrid = layout?.let { gridSections.getOrPut(region) { it.layers.indices.flatMap { l -> it.cells(l) }.toSet() } }
        val m = sections.entries
            .filter { (id, s) -> s.region == region && s.w > 0 && s.h > 0 && (tx to ty) in MapTiles(s.x, s.y, s.w, s.h) && (onGrid == null || id !in onGrid) }
            .minByOrNull { (_, s) -> s.w * s.h }?.key ?: return null
        return MapPick(m, -1)
    }

    private val gridSections = HashMap<Int, Set<Int>>()

    /**
     * Where [mapsec] is on its image: its rect, or - for a section the
     * tables leave off the map (FireRed's dungeons) - every grid tile that
     * names it, one rect each (Diglett's Cave has both ends). Empty = nowhere.
     */
    fun tilesOf(mapsec: Int): List<MapTiles> {
        val s = sections[mapsec] ?: return emptyList()
        if (s.region !in images.indices) return emptyList()
        if (s.w > 0 && s.h > 0) return listOf(MapTiles(s.x, s.y, s.w, s.h))
        val layout = layouts.getOrNull(s.region) ?: return emptyList()
        val out = ArrayList<MapTiles>()
        for (layer in layout.layers.indices) for (y in 0 until layout.h) for (x in 0 until layout.w) {
            val t = MapTiles(x + layout.offX, y + layout.offY, 1, 1)
            if (layout.at(layer, t.x, t.y) == mapsec && t !in out) out += t
        }
        return out
    }

    /**
     * The places the PLACES list offers: every named section that's on a
     * map, once per name (FireRed names some twice), in three groups.
     */
    fun places(): List<Pair<String, List<Int>>> {
        val seen = HashSet<String>()
        val towns = ArrayList<Int>()
        val routes = ArrayList<Int>()
        val other = ArrayList<Int>()
        for ((id, s) in sections.entries.sortedBy { it.key }) {
            if (s.name.isBlank() || tilesOf(id).isEmpty() || !seen.add(s.name.lowercase())) continue
            when {
                isRoute(s.name) -> routes += id
                isTown(s.name) -> towns += id
                else -> other += id
            }
        }
        routes.sortWith(compareBy({ routeNumber(sections.getValue(it).name) }, { sections.getValue(it).name }))
        other.sortBy { sections.getValue(it).name.lowercase() }
        return listOf("TOWNS & CITIES" to towns, "ROUTES" to routes, "OTHER PLACES" to other).filter { it.second.isNotEmpty() }
    }

    private fun isRoute(name: String) = name.startsWith("route ", ignoreCase = true)

    private fun routeNumber(name: String) = name.substringAfter(' ').takeWhile { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE

    private fun isTown(name: String): Boolean {
        val last = name.trim().substringAfterLast(' ').lowercase()
        return last in TOWN_WORDS
    }

    private companion object {
        val TOWN_WORDS = setOf("town", "city", "village", "island", "plateau")
    }
}

/** The Map tab's maps for the running game - the same source [lookupLocation] names places from. */
fun activeRegionMap(): RegionMapModel? {
    val rom = if (activeGame == GameKind.FIRERED) null else RomRegionMap.current
    if (rom != null) return RegionMapModel(rom.images, rom.sections, rom.layouts)
    if (activeGame == GameKind.ODYSSEY) return null // see lookupLocation
    val images = activeRegionMapImages
    if (images.isEmpty()) return null
    val layouts = when (activeGame) {
        GameKind.FIRERED -> regionLayoutsFireRed
        GameKind.EMERALD, GameKind.EMERALD_SEAGLASS, GameKind.TMT2 -> regionLayoutsEmerald
        GameKind.EMERALD_ROGUE -> regionLayoutsRogue
        GameKind.LAZARUS -> regionLayoutsLazarus
        GameKind.SOULGOLD -> regionLayoutsSoulGold
        else -> emptyList()
    }
    return RegionMapModel(images, activeMapSecData, layouts)
}

/** gMapHeader.mapType values (both decomps' MAP_TYPE_*) whose own position places the player on the map. */
private val OUTDOOR_MAP_TYPES = setOf(1, 2, 3, 5, 6) // TOWN, CITY, ROUTE, UNDERWATER, OCEAN_ROUTE

/**
 * The tile the game's region map puts the player's icon on: inside a
 * section of several tiles, the one the player's position on the map
 * ([x], [y] of a [mapW]x[mapH] map) falls in - region_map.c's
 * GetPlayerPositionOnRegionMap. Indoors and underground the game uses the
 * warp back outside instead, which isn't read here: there it's the tile
 * last worked out outdoors in the same section, else the section's first.
 */
object PlayerMapTile {
    private var lastMapsec = -1
    private var lastTile: Pair<Int, Int>? = null

    fun of(model: RegionMapModel, mapsec: Int, x: Int, y: Int, mapW: Int, mapH: Int, mapType: Int): Pair<Int, Int>? {
        val r = model.tilesOf(mapsec).firstOrNull() ?: return null
        if (mapType in OUTDOOR_MAP_TYPES && mapW > 0 && mapH > 0) {
            val dx = minOf(x / maxOf(1, mapW / r.w), r.w - 1).coerceAtLeast(0)
            val dy = minOf(y / maxOf(1, mapH / r.h), r.h - 1).coerceAtLeast(0)
            val t = r.x + dx to r.y + dy
            lastMapsec = mapsec
            lastTile = t
            return t
        }
        lastTile?.let { if (mapsec == lastMapsec && it in r) return it }
        return r.x to r.y
    }
}
