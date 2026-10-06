package android.os

open class Bundle

object Environment {
    fun isExternalStorageManager() = true
    fun getExternalStorageDirectory() = java.io.File("/sdcard")
}
