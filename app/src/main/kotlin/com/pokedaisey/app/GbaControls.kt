package com.pokedaisey.app

import android.os.Build
import android.util.Log
import android.view.KeyEvent
import java.io.File
import java.util.Properties

/**
 * Remappable mapping of physical keys/buttons to GBA buttons, from
 * `files/controls.properties`. One or more key names per GBA button
 * (comma-separated; any of them triggers it). The D-pad and analog stick stay
 * fixed — only the face/shoulder/menu buttons are here.
 */
object GbaControls {

    enum class Btn(val bit: Int) {
        A(MgbaCore.Key.A), B(MgbaCore.Key.B),
        L(MgbaCore.Key.L), R(MgbaCore.Key.R),
        START(MgbaCore.Key.START), SELECT(MgbaCore.Key.SELECT);

        val prop get() = name.lowercase()
    }

    /**
     * AYN (Thor) and Retroid handhelds print A on the right face button and B on
     * the bottom one, like the GBA, but Android reports them by Xbox position:
     * the bottom button is BUTTON_A. So there the defaults swap them, and GBA A
     * is the button labelled A.
     */
    private val swapFaceButtons: Boolean = run {
        val ids = listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL).map { it.orEmpty().lowercase() }
        ids[0] == "ayn" || ids[1] == "ayn" || ids.any { "retroid" in it }
    }

    private const val A_DEFAULT = "BUTTON_A, Z"
    private const val B_DEFAULT = "BUTTON_B, X"
    private const val A_SWAPPED = "BUTTON_B, Z"
    private const val B_SWAPPED = "BUTTON_A, X"
    /** Marks a file whose A/B defaults were settled for this device (see [readProps]). */
    private const val AB_CHECKED = "ab_defaults_checked"

    private val DEFAULTS = linkedMapOf(
        "a" to if (swapFaceButtons) A_SWAPPED else A_DEFAULT,
        "b" to if (swapFaceButtons) B_SWAPPED else B_DEFAULT,
        "l" to "BUTTON_L1, A",
        "r" to "BUTTON_R1, S",
        "start" to "BUTTON_START, ENTER",
        "select" to "BUTTON_SELECT, BACKSLASH, SHIFT_RIGHT",
    )

    private fun file(dir: File) = File(dir, "controls.properties")

    private fun readProps(dir: File): Properties {
        val p = Properties()
        val f = file(dir)
        if (f.isFile) runCatching { f.inputStream().use(p::load) }
        var changed = false
        // A file written before the swap existed: still exactly the old defaults
        // means nobody chose them, so this device gets its swapped ones. Once only.
        if (!p.containsKey(AB_CHECKED)) {
            if (swapFaceButtons && p.getProperty("a") == A_DEFAULT && p.getProperty("b") == B_DEFAULT) {
                p.setProperty("a", A_SWAPPED)
                p.setProperty("b", B_SWAPPED)
            }
            p.setProperty(AB_CHECKED, "1")
            changed = true
        }
        for ((k, v) in DEFAULTS) if (!p.containsKey(k)) { p.setProperty(k, v); changed = true }
        if (changed) runCatching {
            f.outputStream().use { p.store(it, "PokeDaisey GBA button map — Android key names, comma-separated") }
        }
        return p
    }

    /** keycode -> GBA key bit. */
    fun load(dir: File): Map<Int, Int> {
        val props = readProps(dir)
        val out = HashMap<Int, Int>()
        for (btn in Btn.entries) {
            props.getProperty(btn.prop).orEmpty().split(',').forEach { n ->
                resolveKey(n.trim())?.let { out[it] = btn.bit }
            }
        }
        return out
    }

    fun rawBindings(dir: File): Map<Btn, List<String>> {
        val props = readProps(dir)
        return Btn.entries.associateWith {
            props.getProperty(it.prop).orEmpty().split(',').map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
        }
    }

    fun setBinding(dir: File, btn: Btn, keyCode: Int) {
        val props = readProps(dir)
        props.setProperty(btn.prop, Hotkeys.keyName(keyCode))
        runCatching { file(dir).outputStream().use { props.store(it, "PokeDaisey GBA button map") } }
    }

    private fun resolveKey(name: String): Int? {
        if (name.isEmpty()) return null
        return runCatching { KeyEvent::class.java.getField("KEYCODE_$name").getInt(null) }
            .onFailure { Log.w("pokedaisey", "unknown control key: $name") }.getOrNull()
    }
}
