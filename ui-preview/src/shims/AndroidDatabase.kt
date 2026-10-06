package android.database

interface Cursor : java.io.Closeable {
    fun moveToFirst(): Boolean
    fun getColumnIndex(n: String): Int
    fun getString(i: Int): String?
}
