package android.util

object Log {
    fun d(t: String, m: String) = 0
    fun i(t: String, m: String) = 0
    fun w(t: String, m: String) = 0
    fun w(t: String, m: String, e: Throwable?) = 0
    fun e(t: String, m: String) = 0
    fun e(t: String, m: String, e: Throwable?) = 0
}
