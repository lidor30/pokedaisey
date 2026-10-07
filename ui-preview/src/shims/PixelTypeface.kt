package com.pokedaisy.app

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

// Desktop stand-in for the app's PixelTypeface.kt. Skia doesn't fall back glyph
// by glyph inside a FontFamily, so Japanese renders in the system's CJK font here
// (bigger, not pixel art) - Paparazzi shows the real PixelMplusJP fallback.
fun pixelFontFamily(context: android.content.Context): FontFamily = FontFamily(
    Font("fonts/PixelOperator.ttf", context.assets),
    Font("fonts/PixelMplusJP.ttf", context.assets),
)
