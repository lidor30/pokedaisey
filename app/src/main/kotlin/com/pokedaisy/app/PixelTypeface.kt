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
 */
fun pixelFontFamily(context: Context): FontFamily {
    val plain = { FontFamily(ComposeFont("fonts/PixelOperator.ttf", context.assets)) }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return plain()
    fun family(path: String) = AndroidFontFamily.Builder(Font.Builder(context.assets, path).build()).build()
    return runCatching {
        val typeface = Typeface.CustomFallbackBuilder(family("fonts/PixelOperator.ttf"))
            .addCustomFallback(family("fonts/PixelMplusJP.ttf"))
            .setSystemFallback("sans-serif")
            .build()
        FontFamily(androidx.compose.ui.text.font.Typeface(typeface))
    }.getOrElse { plain() }
}
