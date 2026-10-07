package com.pokedaisy.app.companion.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.pokedaisy.app.companion.data.DecompIconSource
import com.pokedaisy.app.companion.data.RomArt
import com.pokedaisy.app.companion.data.UnboundIconSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Art images by path, decoded once: what [RomArt] rebuilt from a ROM the
 * player has run (FireRed / Emerald party menu, backdrops, region maps), else
 * a bundled asset (the other games' art). A path missing now is retried once
 * [RomArt.updates] moves - key composables on [rememberArtGeneration].
 */
internal object GameArt {
    // ConcurrentHashMap rejects null values, so a missing path can't go in the
    // map — track those separately (with the RomArt generation they missed in).
    private val cache = ConcurrentHashMap<String, Bitmap>()
    private val missing = ConcurrentHashMap<String, Int>()

    /** [path] if it's already decoded (no disk read). */
    fun peek(path: String): Bitmap? = cache[path]

    fun get(context: Context, path: String): Bitmap? {
        cache[path]?.let { return it }
        val gen = RomArt.updates.value
        if (missing[path] == gen) return null
        val bmp = try {
            RomArt.file(context.filesDir, path)?.let { f -> BitmapFactory.decodeFile(f.path) }
                ?: context.assets.open(path).use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
        if (bmp == null) {
            missing[path] = gen
            return null
        }
        cache[path] = bmp
        return bmp
    }
}

/** Changes when [RomArt] adds art, so `remember`s keyed on it load again. */
@Composable
fun rememberArtGeneration(): Int = RomArt.updates.collectAsState().value

/**
 * Species icon sheets are 32x64: two 32x32 animation frames stacked
 * vertically (see docs/telemetry.md). Crop to just the first frame, matching
 * the web UI's background-position trick.
 */
private fun cropToFirstFrame(bitmap: Bitmap): Bitmap {
    val frameHeight = bitmap.width // icon sheets are square-frame, height = 2x width
    if (bitmap.height <= frameHeight) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, frameHeight)
}

/** The full 32x64 two-frame icon sheet for [asset], or null (not loaded yet / unknown). */
private suspend fun loadMonIconSheet(context: Context, asset: String?): Bitmap? {
    val id = asset?.substringAfterLast('/')?.substringBefore('.')?.toIntOrNull()
    return when {
        asset == null -> null
        // Unbound icons aren't bundled - pull them live from the game.
        asset.startsWith("pokemon-unbound/") -> withContext(Dispatchers.IO) {
            id?.let { UnboundIconSource.get(it) }
        }
        // v2 QoL ROMs / native configs that know gMonIconTable: decode
        // straight from the ROM (see IconTables.monPresent - gated
        // independently of item icons since some hacks relocate only one).
        id != null && DecompIconSource.tables.monPresent -> withContext(Dispatchers.IO) {
            DecompIconSource.get(id) ?: GameArt.get(context, asset)
        }
        else -> withContext(Dispatchers.IO) { GameArt.get(context, asset) }
    }
}

/** The icon sheet if [DecompIconSource] / [UnboundIconSource] already has it decoded. */
private fun peekMonIcon(asset: String?): Bitmap? {
    if (asset == null) return null
    GameArt.peek(asset)?.let { return it }   // rom-art sheets (a Game Boy game's own icons)
    val id = asset.substringAfterLast('/').substringBefore('.').toIntOrNull() ?: return null
    return if (asset.startsWith("pokemon-unbound/")) UnboundIconSource.peek(id) else DecompIconSource.peek(id)
}

/** Both animation frames (for callers that animate the icon, e.g. FrPartySlot). */
@Composable
fun rememberMonIconSheet(asset: String?): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(asset) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(asset) { bitmap = loadMonIconSheet(context, asset) }
    return bitmap
}

@Composable
fun SpeciesIcon(asset: String?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Starts from an already-decoded icon, so a list scrolling back doesn't flash blank.
    var bitmap by remember(asset) { mutableStateOf(peekMonIcon(asset)?.let { cropToFirstFrame(it) }) }
    LaunchedEffect(asset) { if (bitmap == null) bitmap = loadMonIconSheet(context, asset)?.let { cropToFirstFrame(it) } }
    Box(modifier = modifier.size(size)) {
        bitmap?.let {
            // Nearest-neighbour: a pixel-art sprite, often scaled up.
            Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size), filterQuality = FilterQuality.None)
        }
    }
}

@Composable
fun ItemIcon(asset: String?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(asset) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(asset) {
        val id = asset?.substringAfterLast('/')?.substringBefore('.')?.toIntOrNull()
        bitmap = when {
            asset == null -> null
            // Unbound item icons aren't bundled - pull them live from the game.
            asset.startsWith("items-unbound/") -> withContext(Dispatchers.IO) {
                id?.let { UnboundIconSource.getItem(it) }
            }
            id != null && DecompIconSource.tables.itemPresent -> withContext(Dispatchers.IO) {
                DecompIconSource.getItem(id) ?: GameArt.get(context, asset)
            }
            else -> withContext(Dispatchers.IO) {
                GameArt.get(context, asset)
            }
        }
    }
    Box(modifier = modifier.size(size)) {
        bitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size))
        }
    }
}

@Composable
fun regionMapBitmap(assetName: String?): Bitmap? {
    val context = LocalContext.current
    val gen = rememberArtGeneration()
    // Decoded once and cached (GameArt), so loading in composition is cheap.
    return remember(assetName, gen) { assetName?.let { GameArt.get(context, "regionmap/$it.png") } }
}
