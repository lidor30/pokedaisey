package com.pokedaisy.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily as AndroidFontFamily
import android.os.Build
import androidx.compose.ui.text.font.Font as ComposeFont
import androidx.compose.ui.text.font.FontFamily

/**
 * The app's one font: Pixel Operator, with PixelMplusJP (scripts/gen_jp_font.py:
 * the same pixel grid) filling in the kana / kanji it lacks, glyph by glyph -
 * so Japanese stays pixel art and Latin text keeps Pixel Operator. Android 10+
 * (Typeface.CustomFallbackBuilder); older ones fall back to the system's CJK font.
 *
 * Built once per process and shared by every text on both screens: building it
 * loads the 1 MB PixelMplusJP, and a family per call site (every GbaText) cost
 * ~1 MB and several ms of main thread per text - tens of MB per tab switch,
 * which ran the app out of memory when tabs were switched quickly (v1.1.0).
 */
fun pixelFontFamily(context: Context): FontFamily {
    pixelFamily?.let { return it }
    return synchronized(fontLock) {
        pixelFamily ?: buildPixelFamily(context.applicationContext).also { pixelFamily = it }
    }
}

/** A game's own font file ([file], e.g. Yellow's from Gen1Art) over Pixel Operator for anything it lacks. */
fun gameFontFamily(context: Context, file: java.io.File): FontFamily? {
    val stamp = file.absolutePath + ":" + file.length() + ":" + file.lastModified()
    synchronized(fontLock) { gameFamilies[stamp]?.let { return it } }
    val family = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@runCatching FontFamily(ComposeFont(file))
        val assets = assetFamilies(context.applicationContext)
        val typeface = Typeface.CustomFallbackBuilder(AndroidFontFamily.Builder(Font.Builder(file).build()).build())
            .addCustomFallback(assets.first)
            .addCustomFallback(assets.second)
            .setSystemFallback("sans-serif")
            .build()
        FontFamily(androidx.compose.ui.text.font.Typeface(typeface))
    }.getOrNull() ?: return null
    synchronized(fontLock) { gameFamilies[stamp] = family }
    return family
}

private val fontLock = Any()
@Volatile private var pixelFamily: FontFamily? = null
private var assetFonts: Pair<AndroidFontFamily, AndroidFontFamily>? = null
// A game font per file version (a rescan rewrites it); a handful at most.
private val gameFamilies = HashMap<String, FontFamily>()

/** Pixel Operator and PixelMplusJP as platform families, loaded once (Android 10+ only). */
private fun assetFamilies(context: Context): Pair<AndroidFontFamily, AndroidFontFamily> = synchronized(fontLock) {
    assetFonts ?: run {
        fun family(path: String) = AndroidFontFamily.Builder(Font.Builder(context.assets, path).build()).build()
        (family("fonts/PixelOperator.ttf") to family("fonts/PixelMplusJP.ttf")).also { assetFonts = it }
    }
}

private fun buildPixelFamily(context: Context): FontFamily {
    val plain = { FontFamily(ComposeFont("fonts/PixelOperator.ttf", context.assets)) }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return plain()
    return runCatching {
        val assets = assetFamilies(context)
        val typeface = Typeface.CustomFallbackBuilder(assets.first)
            .addCustomFallback(assets.second)
            .setSystemFallback("sans-serif")
            .build()
        FontFamily(androidx.compose.ui.text.font.Typeface(typeface))
    }.getOrElse { plain() }
}
