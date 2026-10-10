package android.content.res

/** DarkMode reads the device's night flag from here: always light on the desktop (-Pdark forces it). */
class Configuration {
    val uiMode = UI_MODE_NIGHT_NO

    companion object {
        const val UI_MODE_NIGHT_MASK = 0x30
        const val UI_MODE_NIGHT_NO = 0x10
        const val UI_MODE_NIGHT_YES = 0x20
    }
}

class Resources {
    val configuration = Configuration()
}
