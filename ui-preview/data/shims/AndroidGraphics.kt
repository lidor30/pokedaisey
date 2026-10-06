package android.graphics

import java.io.File
import java.io.InputStream
import javax.imageio.ImageIO

/** Just enough of android.graphics.Bitmap for the app's decode/UI code: an ARGB int array. */
class Bitmap(val width: Int, val height: Int, internal val px: IntArray = IntArray(width * height)) {
    enum class Config { ARGB_8888, RGB_565 }
    enum class CompressFormat { PNG, JPEG, WEBP }

    var isPremultiplied = false
    val config get() = Config.ARGB_8888

    fun getPixels(out: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        for (r in 0 until h) for (c in 0 until w) out[offset + r * stride + c] = px[(y + r) * width + x + c]
    }

    fun setPixels(src: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        for (r in 0 until h) for (c in 0 until w) px[(y + r) * width + x + c] = src[offset + r * stride + c]
    }

    fun getPixel(x: Int, y: Int) = px[y * width + x]
    fun setPixel(x: Int, y: Int, c: Int) { px[y * width + x] = c }
    fun recycle() {}
    fun compress(f: CompressFormat, q: Int, s: java.io.OutputStream): Boolean = true

    companion object {
        fun createBitmap(w: Int, h: Int, c: Config) = Bitmap(w, h)
        fun createBitmap(src: IntArray, w: Int, h: Int, c: Config) = Bitmap(w, h, src.copyOf())
        fun createBitmap(src: Bitmap, x: Int, y: Int, w: Int, h: Int): Bitmap {
            val out = Bitmap(w, h)
            src.getPixels(out.px, 0, w, x, y, w, h)
            return out
        }
        fun createScaledBitmap(src: Bitmap, w: Int, h: Int, filter: Boolean): Bitmap {
            val out = Bitmap(w, h)
            for (y in 0 until h) for (x in 0 until w) {
                out.px[y * w + x] = src.px[(y * src.height / h) * src.width + x * src.width / w]
            }
            return out
        }
    }
}

/** Non-null returns: Android's are platform types the app's code treats as non-null. */
object BitmapFactory {
    fun decodeStream(s: InputStream?): Bitmap = ImageIO.read(s!!).let { img ->
        val b = Bitmap(img.width, img.height)
        img.getRGB(0, 0, img.width, img.height, b.px, 0, img.width)
        b
    }
    fun decodeFile(p: String): Bitmap = File(p).inputStream().use { decodeStream(it) }
    fun decodeByteArray(d: ByteArray, o: Int, l: Int): Bitmap = decodeStream(d.inputStream(o, l))
}
