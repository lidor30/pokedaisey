package android.view

class KeyEvent {
    companion object {
        fun keyCodeFromString(s: String): Int = s.hashCode() and 0xFFFF
        fun keyCodeToString(c: Int): String = "KEYCODE_$c"
        const val KEYCODE_BACK = 4
    }
}
