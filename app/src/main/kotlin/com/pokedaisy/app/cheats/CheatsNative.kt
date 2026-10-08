package com.pokedaisy.app.cheats

import com.pokedaisy.app.MgbaCore

/** mGBA's code parser for [CheatCheck] (ui-preview shims this file). */
internal object CheatsNative {
    fun check(code: String, type: Int, directive: String): String? =
        runCatching { MgbaCore.pkCheatsCheck(code, type, directive) }.getOrNull()
}
