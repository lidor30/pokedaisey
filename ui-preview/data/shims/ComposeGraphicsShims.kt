package androidx.compose.ui.graphics

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo

/** The Android-only android.graphics.Bitmap <-> ImageBitmap bridges, over Skia. */
fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap {
    val b = org.jetbrains.skia.Bitmap()
    b.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.UNPREMUL))
    val px = IntArray(width * height)
    getPixels(px, 0, width, 0, 0, width, height)
    val bytes = ByteArray(width * height * 4)
    for (i in px.indices) {
        val c = px[i]
        bytes[i * 4] = (c and 0xFF).toByte()
        bytes[i * 4 + 1] = (c shr 8 and 0xFF).toByte()
        bytes[i * 4 + 2] = (c shr 16 and 0xFF).toByte()
        bytes[i * 4 + 3] = (c ushr 24).toByte()
    }
    b.installPixels(bytes)
    return b.asComposeImageBitmap()
}

fun ImageBitmap.asAndroidBitmap(): android.graphics.Bitmap {
    val px = IntArray(width * height)
    readPixels(px)
    return android.graphics.Bitmap.createBitmap(px, width, height, android.graphics.Bitmap.Config.ARGB_8888)
}
