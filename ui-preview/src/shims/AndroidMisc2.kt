package android.widget

class Toast {
    fun show() {}
    fun cancel() {}

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        fun makeText(c: android.content.Context, t: CharSequence, d: Int) = Toast()
    }
}
